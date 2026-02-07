package com.worklog.application.auth;

import com.worklog.application.validation.PasswordValidator;
import com.worklog.domain.audit.AuditLog;
import com.worklog.domain.role.Role;
import com.worklog.domain.role.RoleId;
import com.worklog.domain.session.UserSession;
import com.worklog.domain.user.User;
import com.worklog.domain.user.UserId;
import com.worklog.infrastructure.email.EmailService;
import com.worklog.infrastructure.persistence.AuditLogRepository;
import com.worklog.infrastructure.persistence.JdbcEmailVerificationTokenStore;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.persistence.JdbcUserSessionRepository;
import com.worklog.infrastructure.persistence.RoleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

import java.time.Instant;
import java.util.UUID;

/**
 * Authentication service implementation.
 * 
 * Handles user registration, login, and email verification with audit logging.
 */
@Service
@Transactional
public class AuthServiceImpl implements AuthService {
    
    private final JdbcUserRepository userRepository;
    private final JdbcUserSessionRepository sessionRepository;
    private final RoleRepository roleRepository;
    private final AuditLogRepository auditLogRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcEmailVerificationTokenStore tokenStore;
    private final String defaultRoleName;
    
    public AuthServiceImpl(
        JdbcUserRepository userRepository,
        JdbcUserSessionRepository sessionRepository,
        RoleRepository roleRepository,
        AuditLogRepository auditLogRepository,
        EmailService emailService,
        PasswordEncoder passwordEncoder,
        JdbcEmailVerificationTokenStore tokenStore,
        @Value("${worklog.auth.default-role-name:USER}") String defaultRoleName
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.roleRepository = roleRepository;
        this.auditLogRepository = auditLogRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
        this.defaultRoleName = defaultRoleName;
    }
    
    @Override
    public User signup(RegistrationRequest request) {
        // Validate password strength
        PasswordValidator.validate(request.password());
        
        // Normalize email to lowercase
        String normalizedEmail = request.email().toLowerCase();
        
        // Check for duplicate email
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalStateException("Email already registered");
        }
        
        // Hash password
        String hashedPassword = passwordEncoder.encode(request.password());
        
        // Load default role from database
        Role defaultRole = roleRepository.findByName(defaultRoleName)
            .orElseThrow(() -> new IllegalStateException(
                "Default role '" + defaultRoleName + "' not found in database. " +
                "Please ensure roles are properly initialized."
            ));
        
        // Create user entity
        User user = User.create(
            normalizedEmail,
            request.name(),
            hashedPassword,
            defaultRole.getId()
        );
        
        // Save user
        User savedUser = userRepository.save(user);
        
        // Generate verification token and send email
        String token = tokenStore.generateToken(savedUser.getId().value());
        emailService.sendVerificationEmail(savedUser.getEmail(), token);
        
        return savedUser;
    }
    
    @Override
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        // Normalize email
        String normalizedEmail = request.email().toLowerCase();
        
        // Find user
        User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> {
                // Log failed login attempt for non-existent user
                logAuditEvent(null, AuditLog.LOGIN_FAILURE, ipAddress, 
                    "Failed login attempt for non-existent email: " + normalizedEmail);
                return new IllegalArgumentException("Invalid email or password");
            });
        
        // Check if account can login (not deleted, lock expired)
        if (!user.canLogin()) {
            if (user.isLocked()) {
                // Log account locked login attempt
                logAuditEvent(user.getId(), AuditLog.LOGIN_FAILURE, ipAddress, 
                    "Login attempt on locked account. Lock expires at: " + user.getLockedUntil());
                throw new IllegalStateException(
                    "Account is locked until " + user.getLockedUntil() + 
                    ". Please try again later or contact support."
                );
            }
            // Log inactive account login attempt
            logAuditEvent(user.getId(), AuditLog.LOGIN_FAILURE, ipAddress, 
                "Login attempt on inactive account. Status: " + user.getAccountStatus());
            throw new IllegalStateException("Account is not active");
        }
        
        // Verify password
        if (!passwordEncoder.matches(request.password(), user.getHashedPassword())) {
            // Record failed attempt
            user.recordFailedLogin(5, 15); // Lock after 5 failures for 15 minutes
            userRepository.save(user);
            
            // Log failed login with wrong password
            logAuditEvent(user.getId(), AuditLog.LOGIN_FAILURE, ipAddress, 
                "Failed login attempt: incorrect password. Failed attempts: " + user.getFailedLoginAttempts());
            
            // If account got locked due to this failure
            if (user.isLocked()) {
                logAuditEvent(user.getId(), AuditLog.ACCOUNT_LOCKED, ipAddress, 
                    "Account locked due to too many failed login attempts. Lock expires at: " + user.getLockedUntil());
            }
            
            throw new IllegalArgumentException("Invalid email or password");
        }
        
        // Record successful login
        user.recordSuccessfulLogin();
        userRepository.save(user);
        
        // Create session with IP address and user agent for security audit
        UserSession session = UserSession.create(
            UserId.of(user.getId().value()),
            ipAddress,
            userAgent,
            30 // 30 minutes timeout
        );
        sessionRepository.save(session);
        
        // Log successful login
        logAuditEvent(user.getId(), AuditLog.LOGIN_SUCCESS, ipAddress, 
            "User logged in successfully. User-Agent: " + (userAgent != null ? userAgent : "unknown"));
        
        // Generate remember-me token if requested
        String rememberMeToken = request.rememberMe() 
            ? generateRememberMeToken() 
            : null;
        
        return new LoginResponse(user, session.getSessionId().toString(), rememberMeToken);
    }
    
    @Override
    public void verifyEmail(String token) {
        // Validate and consume token
        UUID userId = tokenStore.validateAndConsume(token);
        
        // Find user
        User user = userRepository.findById(UserId.of(userId))
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        // Verify email (idempotent operation)
        if (!user.isVerified()) {
            user.verifyEmail();
            userRepository.save(user);
            
            // Log email verification
            logAuditEvent(user.getId(), AuditLog.EMAIL_VERIFICATION, null, 
                "Email verified successfully for user: " + user.getEmail());
        }
    }
    
    /**
     * Generates a cryptographically secure remember-me token (256-bit).
     * Uses SecureRandom instead of UUID for better security against prediction attacks.
     */
    private String generateRememberMeToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32]; // 256 bits
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * Helper method to log audit events.
     * 
     * @param userId User ID (can be null for anonymous events)
     * @param eventType Type of security event
     * @param ipAddress Client IP address (can be null)
     * @param details Additional details about the event
     */
    private void logAuditEvent(UserId userId, String eventType, String ipAddress, String details) {
        try {
            AuditLog auditLog = AuditLog.createUserAction(userId, eventType, ipAddress, details);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // Log to system log but don't fail the operation
            // In production, this should be sent to a monitoring system
            System.err.println("Failed to create audit log: " + e.getMessage());
        }
    }
}
