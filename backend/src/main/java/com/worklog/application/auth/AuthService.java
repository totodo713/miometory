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
     *
     * @param request Login credentials
     * @param ipAddress Client IP address for audit logging
     * @param userAgent Client user agent for audit logging
     */
    LoginResponse login(LoginRequest request, String ipAddress, String userAgent);

    /**
     * Verify email with token
     */
    void verifyEmail(String token);
}
