package com.worklog.e2e

import com.worklog.IntegrationTestBase
import com.worklog.domain.role.RoleId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

/**
 * End-to-end tests for the password reset flow (TC-4.1 to TC-4.4).
 *
 * Tests the complete lifecycle against a real PostgreSQL database via IntegrationTestBase:
 * - TC-4.1: Complete password reset flow (request → token → reset → verify credentials)
 * - TC-4.2: Expired token rejection
 * - TC-4.3: Used token rejection
 * - TC-4.4: Session invalidation after password reset
 *
 * Note: TC-4.1 verifies credentials at the DB level (password hash comparison) instead of
 * calling /api/v1/auth/login directly. The login endpoint triggers AuditLog persistence which
 * fails due to multiple Spring Data JDBC mapping issues (JSONB/INET column types, isNew()
 * detection, and transaction rollback propagation). See GitHub Issue #15 for details and
 * the planned fix. Once resolved, TC-4.1 should be updated to use the login endpoint.
 */
@AutoConfigureWebTestClient
class PasswordResetE2ETest : IntegrationTestBase() {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private val passwordEncoder = BCryptPasswordEncoder()
    private lateinit var testRoleId: RoleId

    @BeforeEach
    fun setUp() {
        // Clean tables (order matters for FK constraints)
        jdbcTemplate.update("DELETE FROM password_reset_tokens")
        jdbcTemplate.update("DELETE FROM user_sessions")
        jdbcTemplate.update("DELETE FROM audit_logs")
        // Clear FK references from system_default_settings before deleting users
        jdbcTemplate.update("UPDATE system_default_settings SET updated_by = NULL")
        jdbcTemplate.update("DELETE FROM users")

        // Ensure test role exists
        testRoleId = RoleId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        jdbcTemplate.update(
            """
            INSERT INTO roles (id, name, description, created_at, updated_at)
            VALUES (?, 'TEST_ROLE', 'Test role for E2E tests', NOW(), NOW())
            ON CONFLICT (id) DO NOTHING
            """,
            testRoleId.value(),
        )
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    private fun createAndActivateUser(email: String, password: String): UUID {
        val userId = UUID.randomUUID()
        val hashedPassword = passwordEncoder.encode(password)
        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, name, hashed_password, role_id, account_status,
                             failed_login_attempts, created_at, updated_at, email_verified_at)
            VALUES (?, ?, 'Test User', ?, ?, 'active', 0, NOW(), NOW(), NOW())
            """,
            userId,
            email,
            hashedPassword,
            testRoleId.value(),
        )
        return userId
    }

    private fun extractTokenFromDb(userId: UUID): String = jdbcTemplate.queryForObject(
        """
            SELECT token FROM password_reset_tokens
            WHERE user_id = ? AND used_at IS NULL
            ORDER BY created_at DESC LIMIT 1
            """,
        String::class.java,
        userId,
    ) ?: error("No unused token found for user $userId")

    private fun getStoredPasswordHash(userId: UUID): String = jdbcTemplate.queryForObject(
        "SELECT hashed_password FROM users WHERE id = ?",
        String::class.java,
        userId,
    ) ?: error("No password hash found for user $userId")

    private fun createSessionForUser(userId: UUID): UUID {
        val sessionId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO user_sessions (id, user_id, session_id, ip_address, created_at, expires_at)
            VALUES (?, ?, ?, '127.0.0.1', NOW(), NOW() + INTERVAL '30 minutes')
            """,
            sessionId,
            userId,
            UUID.randomUUID().toString(),
        )
        return sessionId
    }

    // ============================================================
    // TC-4.1: Complete password reset flow
    // ============================================================

    @Test
    fun `complete password reset flow - request, confirm, verify new credentials`() {
        // Given
        val email = "reset-flow@example.com"
        val oldPassword = "OldPassword123"
        val newPassword = "NewPassword456"
        val userId = createAndActivateUser(email, oldPassword)

        // Verify old password hash
        val oldHash = getStoredPasswordHash(userId)
        assertTrue(passwordEncoder.matches(oldPassword, oldHash))

        // Step 1: Request password reset
        webTestClient.post()
            .uri("/api/v1/auth/password-reset/request")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"email":"$email"}""")
            .exchange()
            .expectStatus().isOk

        // Step 2: Extract token from database
        val token = extractTokenFromDb(userId)

        // Step 3: Confirm password reset with new password
        webTestClient.post()
            .uri("/api/v1/auth/password-reset/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"token":"$token","newPassword":"$newPassword"}""")
            .exchange()
            .expectStatus().isOk

        // Step 4: Verify new password works (DB-level verification; see class Javadoc / Issue #15)
        val newHash = getStoredPasswordHash(userId)
        assertTrue(passwordEncoder.matches(newPassword, newHash))

        // Step 5: Verify old password no longer works
        assertFalse(passwordEncoder.matches(oldPassword, newHash))

        // Step 6: Verify token was marked as used
        val usedAt = jdbcTemplate.queryForObject(
            "SELECT used_at FROM password_reset_tokens WHERE token = ?",
            java.sql.Timestamp::class.java,
            token,
        )
        assertTrue(usedAt != null, "Token should be marked as used after successful reset")
    }

    // ============================================================
    // TC-4.2: Expired token rejection
    // ============================================================

    @Test
    fun `expired token should be rejected`() {
        // Given
        val email = "expired-token@example.com"
        val userId = createAndActivateUser(email, "Password123")

        // Request password reset
        webTestClient.post()
            .uri("/api/v1/auth/password-reset/request")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"email":"$email"}""")
            .exchange()
            .expectStatus().isOk

        val token = extractTokenFromDb(userId)

        // Expire the token by moving created_at and expires_at into the past
        // (satisfies the chk_expires_at_after_created constraint: expires_at > created_at)
        jdbcTemplate.update(
            "UPDATE password_reset_tokens " +
                "SET created_at = NOW() - INTERVAL '2 hours', " +
                "    expires_at = NOW() - INTERVAL '1 hour' " +
                "WHERE user_id = ?",
            userId,
        )

        // When/Then - confirm should fail with 404
        webTestClient.post()
            .uri("/api/v1/auth/password-reset/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"token":"$token","newPassword":"NewPassword123"}""")
            .exchange()
            .expectStatus().isNotFound
    }

    // ============================================================
    // TC-4.3: Used token rejection
    // ============================================================

    @Test
    fun `used token should be rejected on second attempt`() {
        // Given
        val email = "used-token@example.com"
        val userId = createAndActivateUser(email, "Password123")

        // Request password reset
        webTestClient.post()
            .uri("/api/v1/auth/password-reset/request")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"email":"$email"}""")
            .exchange()
            .expectStatus().isOk

        val token = extractTokenFromDb(userId)

        // First confirm should succeed
        webTestClient.post()
            .uri("/api/v1/auth/password-reset/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"token":"$token","newPassword":"NewPassword123"}""")
            .exchange()
            .expectStatus().isOk

        // When/Then - second confirm with same token should fail with 404
        webTestClient.post()
            .uri("/api/v1/auth/password-reset/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"token":"$token","newPassword":"AnotherPassword456"}""")
            .exchange()
            .expectStatus().isNotFound
    }

    // ============================================================
    // TC-4.4: Session invalidation after password reset
    // ============================================================

    @Test
    fun `password reset should invalidate existing sessions`() {
        // Given
        val email = "session-test@example.com"
        val password = "Password123"
        val userId = createAndActivateUser(email, password)

        // Create a session directly in the database
        createSessionForUser(userId)

        // Verify session exists
        val sessionCountBefore = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_sessions WHERE user_id = ?",
            Int::class.java,
            userId,
        ) ?: error("Failed to count sessions for user $userId")
        assertEquals(1, sessionCountBefore, "Expected exactly one session before password reset")

        // Request password reset
        webTestClient.post()
            .uri("/api/v1/auth/password-reset/request")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"email":"$email"}""")
            .exchange()
            .expectStatus().isOk

        val token = extractTokenFromDb(userId)

        // Confirm password reset
        webTestClient.post()
            .uri("/api/v1/auth/password-reset/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"token":"$token","newPassword":"NewPassword456"}""")
            .exchange()
            .expectStatus().isOk

        // Then - all sessions should be invalidated
        val sessionCountAfter = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_sessions WHERE user_id = ?",
            Int::class.java,
            userId,
        ) ?: error("Failed to count sessions for user $userId")
        assertEquals(0, sessionCountAfter, "Expected zero sessions after password reset")
    }
}
