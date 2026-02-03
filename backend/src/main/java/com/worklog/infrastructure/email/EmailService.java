package com.worklog.infrastructure.email;

/**
 * Email service interface (to be implemented)
 * 
 * This is a stub to allow tests to compile. Implementation will follow TDD.
 */
public interface EmailService {
    
    /**
     * Send email verification message
     */
    void sendVerificationEmail(String email, String token);
    
    /**
     * Send password reset email
     */
    void sendPasswordResetEmail(String email, String token);
}
