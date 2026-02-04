package com.worklog.application.auth;

import com.worklog.application.token.EmailVerificationTokenStore;
import com.worklog.application.validation.PasswordValidator;
import com.worklog.domain.role.RoleId;
import com.worklog.domain.session.UserSession;
import com.worklog.domain.user.User;
import com.worklog.domain.user.UserId;
import com.worklog.infrastructure.email.EmailService;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.persistence.JdbcUserSessionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Authentication service implementation.
 * 
 * Handles user registration, login, and email verification.
 */
@Service
@Transactional
public class AuthServiceImpl implements AuthService {
    
    private final JdbcUserRepository userRepository;
    private final JdbcUserSessionRepository sessionRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationTokenStore tokenStore;
    
    // Default role for new users (TODO: load from database)
    private static final UUID DEFAULT_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    
    public AuthServiceImpl(
        JdbcUserRepository userRepository,
        JdbcUserSessionRepository sessionRepository,
        EmailService emailService,
        PasswordEncoder passwordEncoder,
        EmailVerificationTokenStore tokenStore
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
    }
    
    @Override
    public User signup(RegistrationRequest request) {
        // Validate password strength
        PasswordValidator.validate(request.password());
        
        // Normalize email to lowercase
        String normalizedEmail = request.email().toLowerCase();
        
        // Check for duplicate email
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email already registered");
        }
        
        // Hash password
        String hashedPassword = passwordEncoder.encode(request.password());
        
        // Create user entity
        User user = User.create(
            normalizedEmail,
            request.name(),
            hashedPassword,
            RoleId.of(DEFAULT_ROLE_ID)
        );
        
        // Save user
        User savedUser = userRepository.save(user);
        
        // Generate verification token and send email
        String token = tokenStore.generateToken(savedUser.getId().value());
        emailService.sendVerificationEmail(savedUser.getEmail(), token);
        
        return savedUser;
    }
    
    @Override
    public LoginResponse login(LoginRequest request) {
        // Normalize email
        String normalizedEmail = request.email().toLowerCase();
        
        // Find user
        User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        
        // Check if account can login (not deleted, lock expired)
        if (!user.canLogin()) {
            if (user.isLocked()) {
                throw new IllegalStateException(
                    "Account is locked until " + user.getLockedUntil() + 
                    ". Please try again later or contact support."
                );
            }
            throw new IllegalStateException("Account is not active");
        }
        
        // Verify password
        if (!passwordEncoder.matches(request.password(), user.getHashedPassword())) {
            // Record failed attempt
            user.recordFailedLogin(5, 15); // Lock after 5 failures for 15 minutes
            userRepository.save(user);
            throw new IllegalArgumentException("Invalid email or password");
        }
        
        // Record successful login
        user.recordSuccessfulLogin();
        userRepository.save(user);
        
        // Create session (TODO: capture IP address and user agent from HTTP request)
        UserSession session = UserSession.create(
            UserId.of(user.getId().value()),
            null, // ipAddress - will be captured from HttpServletRequest in controller
            null, // userAgent - will be captured from HttpServletRequest in controller
            30 // 30 minutes timeout
        );
        sessionRepository.save(session);
        
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
        }
    }
    
    /**
     * Generates a secure remember-me token (min 32 characters).
     */
    private String generateRememberMeToken() {
        return UUID.randomUUID().toString() + UUID.randomUUID().toString().replace("-", "");
    }
}
