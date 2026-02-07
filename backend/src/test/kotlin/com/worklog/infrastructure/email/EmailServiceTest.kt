package com.worklog.infrastructure.email

import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for EmailService (T035)
 * 
 * Tests email sending functionality using GreenMail mock SMTP server.
 * 
 * Tests cover:
 * - Email verification messages
 * - Password reset messages
 * - Email content validation (recipients, subject, body)
 * - HTML template rendering
 * - Link generation
 */
@SpringBootTest(classes = [EmailServiceTest.TestConfig::class])
@TestPropertySource(
    properties = [
        "spring.mail.host=localhost",
        "spring.mail.port=3025",
        "spring.mail.username=test",
        "spring.mail.password=test",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
    ],
)
class EmailServiceTest {
    @Configuration
    @Import(MailSenderAutoConfiguration::class)
    class TestConfig {
        @Bean
        fun emailService(mailSender: JavaMailSender): EmailService = EmailServiceImpl(mailSender, "http://localhost:3000")
    }

    companion object {
        /**
         * GreenMail SMTP server for testing.
         * Lifecycle: per-method (clears emails between tests)
         */
        @JvmField
        @RegisterExtension
        val greenMail: GreenMailExtension =
            GreenMailExtension(ServerSetupTest.SMTP)
                .withConfiguration(GreenMailConfiguration.aConfig().withUser("test", "test"))
                .withPerMethodLifecycle(true)
    }

    @Autowired
    private lateinit var emailService: EmailService

    // ============================================================
    // Email Verification Tests
    // ============================================================

    @Test
    fun `sendVerificationEmail should send email to recipient`() {
        // Given
        val email = "newuser@example.com"
        val token = "verification-token-123"

        // When
        emailService.sendVerificationEmail(email, token)

        // Then - Wait for email to be received
        greenMail.waitForIncomingEmail(5000, 1)
        val receivedMessages = greenMail.receivedMessages
        assertEquals(1, receivedMessages.size, "Should receive exactly 1 email")

        val message = receivedMessages[0]
        assertEquals(email, message.allRecipients[0].toString(), "Email should be sent to correct recipient")
    }

    @Test
    fun `sendVerificationEmail should have correct subject`() {
        // Given
        val email = "user@example.com"
        val token = "token-abc"

        // When
        emailService.sendVerificationEmail(email, token)

        // Then
        greenMail.waitForIncomingEmail(5000, 1)
        val message = greenMail.receivedMessages[0]
        assertEquals("Verify your email address", message.subject, "Subject should match")
    }

    @Test
    fun `sendVerificationEmail should contain verification link in body`() {
        // Given
        val email = "test@example.com"
        val token = "test-token-456"

        // When
        emailService.sendVerificationEmail(email, token)

        // Then
        greenMail.waitForIncomingEmail(5000, 1)
        val message = greenMail.receivedMessages[0]
        val body = getEmailBody(message)

        assertTrue(
            body.contains(token),
            "Email body should contain the verification token",
        )
        assertTrue(
            body.contains("verify-email"),
            "Email body should contain verify-email link",
        )
    }

    @Test
    fun `sendVerificationEmail should send HTML email`() {
        // Given
        val email = "html@example.com"
        val token = "html-token"

        // When
        emailService.sendVerificationEmail(email, token)

        // Then
        greenMail.waitForIncomingEmail(5000, 1)
        val message = greenMail.receivedMessages[0]

        // Check if content type is HTML
        val contentType = message.contentType.lowercase()
        assertTrue(
            contentType.contains("text/html") || contentType.contains("multipart"),
            "Email should be HTML or multipart (for both text and HTML)",
        )
    }

    // ============================================================
    // Password Reset Tests
    // ============================================================

    @Test
    fun `sendPasswordResetEmail should send email to recipient`() {
        // Given
        val email = "resetuser@example.com"
        val token = "reset-token-789"

        // When
        emailService.sendPasswordResetEmail(email, token)

        // Then
        greenMail.waitForIncomingEmail(5000, 1)
        val receivedMessages = greenMail.receivedMessages
        assertEquals(1, receivedMessages.size, "Should receive exactly 1 email")

        val message = receivedMessages[0]
        assertEquals(email, message.allRecipients[0].toString(), "Email should be sent to correct recipient")
    }

    @Test
    fun `sendPasswordResetEmail should have correct subject`() {
        // Given
        val email = "reset@example.com"
        val token = "token-xyz"

        // When
        emailService.sendPasswordResetEmail(email, token)

        // Then
        greenMail.waitForIncomingEmail(5000, 1)
        val message = greenMail.receivedMessages[0]

        assertTrue(
            message.subject.contains("password", ignoreCase = true) &&
                message.subject.contains("reset", ignoreCase = true),
            "Subject should mention password reset",
        )
    }

    @Test
    fun `sendPasswordResetEmail should contain reset link in body`() {
        // Given
        val email = "user@test.com"
        val token = "reset-abc-123"

        // When
        emailService.sendPasswordResetEmail(email, token)

        // Then
        greenMail.waitForIncomingEmail(5000, 1)
        val message = greenMail.receivedMessages[0]
        val body = getEmailBody(message)

        assertTrue(
            body.contains(token),
            "Email body should contain the reset token",
        )
        assertTrue(
            body.contains("reset-password"),
            "Email body should contain reset-password link",
        )
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    /**
     * Extracts email body text from MimeMessage.
     * Handles both plain text and HTML emails.
     */
    private fun getEmailBody(message: MimeMessage): String =
        com.icegreen.greenmail.util.GreenMailUtil
            .getBody(message)
}
