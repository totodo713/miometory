package com.worklog.infrastructure.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Email service implementation using Spring Mail (SMTP).
 *
 * Sends HTML emails for:
 * - Email verification
 * - Password reset
 */
@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final String frontendBaseUrl;

    public EmailServiceImpl(JavaMailSender mailSender, @Value("${worklog.frontend.base-url}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void sendVerificationEmail(String email, String token) {
        try {
            // Next.js route groups (auth) don't create URL paths, so path is /verify-email
            String verificationLink = frontendBaseUrl + "/verify-email?token=" + token;

            String htmlBody = buildEmailVerificationHtml(verificationLink);

            sendHtmlEmail(email, "Verify your email address", htmlBody);

            log.info("Verification email sent to: {}", email);
        } catch (MessagingException e) {
            log.error("Failed to send verification email to: {}", email, e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    @Override
    public void sendPasswordResetEmail(String email, String token) {
        try {
            // Next.js route groups (auth) don't create URL paths, so path is /reset-password
            String resetLink = frontendBaseUrl + "/reset-password?token=" + token;

            String htmlBody = buildPasswordResetHtml(resetLink);

            sendHtmlEmail(email, "Reset your password", htmlBody);

            log.info("Password reset email sent to: {}", email);
        } catch (MessagingException e) {
            log.error("Failed to send password reset email to: {}", email, e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    private void sendHtmlEmail(String to, String subject, String htmlBody) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true); // true = HTML

        mailSender.send(message);
    }

    private String buildEmailVerificationHtml(String verificationLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Verify Your Email</title>
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2>Welcome to Miometry!</h2>
                    <p>Thank you for registering. Please verify your email address to activate your account.</p>
                    <p>
                        <a href="%s"
                           style="display: inline-block; padding: 12px 24px; background-color: #007bff; color: #ffffff; text-decoration: none; border-radius: 4px;">
                            Verify Email Address
                        </a>
                    </p>
                    <p>Or copy and paste this link into your browser:</p>
                    <p style="word-break: break-all; color: #666;">%s</p>
                    <p><small>This link will expire in 24 hours.</small></p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;">
                    <p><small>If you did not create an account, please ignore this email.</small></p>
                </div>
            </body>
            </html>
            """.formatted(verificationLink, verificationLink);
    }

    private String buildPasswordResetHtml(String resetLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Reset Your Password</title>
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2>Password Reset Request</h2>
                    <p>We received a request to reset your password.</p>
                    <p>
                        <a href="%s"
                           style="display: inline-block; padding: 12px 24px; background-color: #dc3545; color: #ffffff; text-decoration: none; border-radius: 4px;">
                            Reset Password
                        </a>
                    </p>
                    <p>Or copy and paste this link into your browser:</p>
                    <p style="word-break: break-all; color: #666;">%s</p>
                    <p><small>This link will expire in 24 hours.</small></p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;">
                    <p><small>If you did not request a password reset, please ignore this email. Your password will remain unchanged.</small></p>
                </div>
            </body>
            </html>
            """.formatted(resetLink, resetLink);
    }
}
