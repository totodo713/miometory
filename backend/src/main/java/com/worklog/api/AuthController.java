package com.worklog.api;

import com.worklog.application.auth.AuthService;
import com.worklog.application.auth.LoginRequest;
import com.worklog.application.auth.LoginResponse;
import com.worklog.application.auth.RegistrationRequest;
import com.worklog.domain.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * REST controller for authentication endpoints.
 * Handles user signup, login, logout, and email verification.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Register a new user account.
     * 
     * @param request Registration details (email, name, password)
     * @return 201 Created with user details
     * @throws IllegalArgumentException if validation fails (400)
     * @throws IllegalStateException if email already exists (409)
     */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@RequestBody RegistrationRequest request) {
        User user = authService.signup(request);
        
        return new SignupResponse(
            user.getId().value(),
            user.getEmail(),
            user.getName(),
            user.getAccountStatus().name(),
            "Account created successfully. Please check your email to verify your account."
        );
    }

    /**
     * Authenticate user and create session.
     * 
     * @param request Login credentials (email, password, rememberMe)
     * @param httpRequest HTTP request for session management
     * @return 200 OK with session details and optional remember-me token
     * @throws IllegalArgumentException if credentials are invalid (401)
     * @throws IllegalStateException if account is locked (401)
     */
    @PostMapping("/login")
    public LoginResponseDto login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        
        LoginResponse response = authService.login(request);
        
        // Prevent session fixation attack: invalidate old session and create new one
        HttpSession oldSession = httpRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        
        // Create new HTTP session after successful login
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("userId", response.user().getId().value());
        session.setAttribute("sessionId", response.sessionId());
        
        // Calculate session expiration (30 minutes from now)
        Instant sessionExpiresAt = Instant.now().plusSeconds(session.getMaxInactiveInterval());
        
        return new LoginResponseDto(
            new UserDto(
                response.user().getId().value(),
                response.user().getEmail(),
                response.user().getName(),
                response.user().getAccountStatus().name()
            ),
            sessionExpiresAt,
            response.rememberMeToken(),
            null // No warning for now
        );
    }

    /**
     * Log out user and invalidate session.
     * 
     * @param httpRequest HTTP request for session invalidation
     * @return 204 No Content
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    /**
     * Verify user's email address with token.
     * 
     * @param request Token from verification email
     * @return 200 OK with success message
     * @throws IllegalArgumentException if token is invalid or expired (404)
     */
    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token is required");
        }
        
        authService.verifyEmail(token);
        
        return new MessageResponse("Email verified successfully. Your account is now active.");
    }

    /**
     * Global exception handler for validation errors.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(IllegalArgumentException e) {
        // Check if it's an authentication error (should be 401)
        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("invalid email or password")) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_CREDENTIALS", e.getMessage()));
        }
        
        // Check if it's a token error (should be 404)
        if (e.getMessage() != null && 
            (e.getMessage().toLowerCase().contains("token") || 
             e.getMessage().toLowerCase().contains("verification"))) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("INVALID_TOKEN", e.getMessage()));
        }
        
        // Otherwise it's a validation error (400)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("validation_error", e.getMessage()));
    }

    /**
     * Global exception handler for duplicate email and account state errors.
     * Maps to 409 Conflict for email already exists scenarios,
     * 401 Unauthorized for account locked scenarios.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleStateError(IllegalStateException e) {
        if (e.getMessage() != null && e.getMessage().contains("already exists")) {
            return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("duplicate_email", e.getMessage()));
        }
        
        // Check if it's an account locked error
        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("locked")) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("ACCOUNT_LOCKED", e.getMessage()));
        }
        
        // For other state errors
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse.of("unauthorized", e.getMessage()));
    }

}
