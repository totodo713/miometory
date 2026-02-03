package com.worklog.application.auth;

import com.worklog.domain.user.User;

/**
 * Authentication service interface (to be implemented)
 * 
 * This is a stub to allow tests to compile. Implementation will follow TDD.
 */
public interface AuthService {
    
    /**
     * Register a new user account
     */
    User signup(RegistrationRequest request);
    
    /**
     * Authenticate user and create session
     */
    LoginResponse login(LoginRequest request);
    
    /**
     * Verify email with token
     */
    void verifyEmail(String token);
}
