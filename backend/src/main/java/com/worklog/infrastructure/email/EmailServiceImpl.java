package com.worklog.infrastructure.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Simple email service implementation.
 * 
 * For MVP, this logs emails instead of actually sending them.
 * In production, integrate with:
 * - Spring Mail (SMTP)
 * - AWS SES
 * - SendGrid
 * - Mailgun
 */
@Service
public class EmailServiceImpl implements EmailService {
    
    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);
    
    @Override
    public void sendVerificationEmail(String email, String token) {
        // TODO: In production, send actual email with HTML template
        // For now, log the verification link
        String verificationLink = "http://localhost:3000/auth/verify-email?token=" + token;
        
        log.info("==============================================");
        log.info("EMAIL VERIFICATION");
        log.info("To: {}", email);
        log.info("Subject: Verify your email address");
        log.info("Link: {}", verificationLink);
        log.info("==============================================");
    }
    
    @Override
    public void sendPasswordResetEmail(String email, String token) {
        // TODO: In production, send actual email with HTML template
        String resetLink = "http://localhost:3000/auth/reset-password?token=" + token;
        
        log.info("==============================================");
        log.info("PASSWORD RESET");
        log.info("To: {}", email);
        log.info("Subject: Reset your password");
        log.info("Link: {}", resetLink);
        log.info("==============================================");
    }
}
