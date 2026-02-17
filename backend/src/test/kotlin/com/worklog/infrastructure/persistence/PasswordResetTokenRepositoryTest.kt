package com.worklog.infrastructure.persistence

import com.worklog.domain.password.PasswordResetToken
import com.worklog.domain.role.RoleId
import com.worklog.domain.user.User
import com.worklog.domain.user.UserId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Integration tests for PasswordResetTokenRepository.
 *
 * Tests repository operations against a real PostgreSQL database using Testcontainers:
 * - save + findByToken (TC-1.1)
 * - findValidByToken happy-path (TC-1.2)
 * - findValidByToken with used token (TC-1.3)
 * - findValidByToken with expired token (TC-1.4)
 * - markAsUsed (TC-1.5)
 * - invalidateUnusedTokensForUser - all unused (TC-1.6)
 * - invalidateUnusedTokensForUser - mixed (TC-1.7)
 * - deleteExpired (TC-1.8)
 * - unique constraint violation (TC-1.9)
 * - CASCADE on user deletion (TC-1.10)
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class PasswordResetTokenRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val postgresContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("worklog_test")
                .withUsername("test")
                .withPassword("test")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgresContainer::getJdbcUrl)
            registry.add("spring.datasource.username", postgresContainer::getUsername)
            registry.add("spring.datasource.password", postgresContainer::getPassword)
            registry.add("spring.flyway.enabled") { "true" }
        }
    }

    @Autowired
    private lateinit var repository: PasswordResetTokenRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var testRoleId: RoleId

    @BeforeEach
    fun setUp() {
        // Defensive cleanup: @Transactional auto-rollback handles isolation, but explicit DELETEs
        // guard against edge cases where framework rollback may not apply (e.g. DDL side-effects).
        jdbcTemplate.update("DELETE FROM password_reset_tokens")
        jdbcTemplate.update("DELETE FROM users")

        // Ensure test role exists
        testRoleId = RoleId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        jdbcTemplate.update(
            """
            INSERT INTO roles (id, name, description, created_at, updated_at)
            VALUES (?, 'TEST_ROLE', 'Test role for integration tests', NOW(), NOW())
            ON CONFLICT (id) DO NOTHING
            """,
            testRoleId.value(),
        )
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    private fun createTestUser(email: String = "test@example.com"): User {
        val user = User.create(email, "Test User", "hashed_password_123", testRoleId)
        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, name, hashed_password, role_id, account_status,
                             failed_login_attempts, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'active', 0, NOW(), NOW())
            """,
            user.id.value(),
            user.email,
            user.name,
            user.hashedPassword,
            testRoleId.value(),
        )
        return user
    }

    private fun createTestToken(
        userId: UserId,
        tokenString: String = "test-token-" + UUID.randomUUID().toString().replace("-", ""),
    ): PasswordResetToken {
        val token = PasswordResetToken.create(userId, tokenString, 60) // 60 minutes validity
        repository.save(token)
        return token
    }

    // ============================================================
    // TC-1.1: save + findByToken
    // ============================================================

    @Test
    fun `save and findByToken - should persist and retrieve token with all fields matching`() {
        // Given
        val user = createTestUser()
        val tokenString = "secure-token-" + UUID.randomUUID().toString().replace("-", "")
        val token = PasswordResetToken.create(user.id, tokenString, 60)

        // When
        repository.save(token)
        val retrieved = repository.findByToken(tokenString)

        // Then
        assertTrue(retrieved.isPresent)
        retrieved.get().let { found ->
            assertEquals(token.id, found.id)
            assertEquals(token.userId, found.userId)
            assertEquals(token.token, found.token)
            assertNotNull(found.createdAt)
            assertNotNull(found.expiresAt)
            assertNull(found.usedAt)
        }
    }

    // ============================================================
    // TC-1.2: findValidByToken - happy path
    // ============================================================

    @Test
    fun `findValidByToken - should return unused non-expired token`() {
        // Given
        val user = createTestUser()
        val tokenString = "valid-token-" + UUID.randomUUID().toString().replace("-", "")
        val token = PasswordResetToken.create(user.id, tokenString, 60)
        repository.save(token)

        // When
        val result = repository.findValidByToken(tokenString)

        // Then
        assertTrue(result.isPresent)
        assertEquals(tokenString, result.get().token)
    }

    // ============================================================
    // TC-1.3: findValidByToken - used token returns empty
    // ============================================================

    @Test
    fun `findValidByToken - should return empty for used token`() {
        // Given
        val user = createTestUser()
        val tokenString = "used-token-" + UUID.randomUUID().toString().replace("-", "")
        val token = PasswordResetToken.create(user.id, tokenString, 60)
        repository.save(token)

        // Mark as used
        repository.markAsUsed(token.id)

        // When
        val result = repository.findValidByToken(tokenString)

        // Then
        assertTrue(result.isEmpty)
    }

    // ============================================================
    // TC-1.4: findValidByToken - expired token returns empty
    // ============================================================

    @Test
    fun `findValidByToken - should return empty for expired token`() {
        // Given
        val user = createTestUser()
        val tokenString = "expired-token-" + UUID.randomUUID().toString().replace("-", "")
        val token = PasswordResetToken.create(user.id, tokenString, 60)
        repository.save(token)

        // Expire the token by moving created_at and expires_at into the past
        // (satisfies the chk_expires_at_after_created constraint: expires_at > created_at)
        jdbcTemplate.update(
            "UPDATE password_reset_tokens SET created_at = ?, expires_at = ? WHERE id = ?",
            Timestamp.from(Instant.now().minusSeconds(7200)),
            Timestamp.from(Instant.now().minusSeconds(3600)),
            token.id,
        )

        // When
        val result = repository.findValidByToken(tokenString)

        // Then
        assertTrue(result.isEmpty)
    }

    // ============================================================
    // TC-1.5: markAsUsed
    // ============================================================

    @Test
    fun `markAsUsed - should set used_at and make token invalid for findValidByToken`() {
        // Given
        val user = createTestUser()
        val tokenString = "mark-used-token-" + UUID.randomUUID().toString().replace("-", "")
        val token = PasswordResetToken.create(user.id, tokenString, 60)
        repository.save(token)

        // When
        repository.markAsUsed(token.id)

        // Then
        val retrieved = repository.findByToken(tokenString)
        assertTrue(retrieved.isPresent)
        assertNotNull(retrieved.get().usedAt)

        val validResult = repository.findValidByToken(tokenString)
        assertTrue(validResult.isEmpty)
    }

    // ============================================================
    // TC-1.6: invalidateUnusedTokensForUser - all unused
    // ============================================================

    @Test
    fun `invalidateUnusedTokensForUser - should mark all unused tokens as used`() {
        // Given
        val user = createTestUser()
        val token1String = "invalidate-token1-" + UUID.randomUUID().toString().replace("-", "")
        val token2String = "invalidate-token2-" + UUID.randomUUID().toString().replace("-", "")
        val token1 = PasswordResetToken.create(user.id, token1String, 60)
        val token2 = PasswordResetToken.create(user.id, token2String, 60)
        repository.save(token1)
        repository.save(token2)

        // When
        repository.invalidateUnusedTokensForUser(user.id)

        // Then
        val retrieved1 = repository.findByToken(token1String)
        val retrieved2 = repository.findByToken(token2String)
        assertTrue(retrieved1.isPresent)
        assertTrue(retrieved2.isPresent)
        assertNotNull(retrieved1.get().usedAt)
        assertNotNull(retrieved2.get().usedAt)
    }

    // ============================================================
    // TC-1.7: invalidateUnusedTokensForUser - mixed used/unused
    // ============================================================

    @Test
    fun `invalidateUnusedTokensForUser - should only invalidate unused tokens`() {
        // Given
        val user = createTestUser()
        val usedTokenString = "already-used-token-" + UUID.randomUUID().toString().replace("-", "")
        val unusedTokenString = "still-unused-token-" + UUID.randomUUID().toString().replace("-", "")

        val usedToken = PasswordResetToken.create(user.id, usedTokenString, 60)
        val unusedToken = PasswordResetToken.create(user.id, unusedTokenString, 60)
        repository.save(usedToken)
        repository.save(unusedToken)

        // Mark one token as used before invalidation
        repository.markAsUsed(usedToken.id)
        val usedAtBefore = repository.findByToken(usedTokenString).get().usedAt

        // When
        repository.invalidateUnusedTokensForUser(user.id)

        // Then - unused token should now have used_at set
        val retrievedUnused = repository.findByToken(unusedTokenString)
        assertTrue(retrievedUnused.isPresent)
        assertNotNull(retrievedUnused.get().usedAt)

        // The already-used token should still have its original used_at
        val retrievedUsed = repository.findByToken(usedTokenString)
        assertTrue(retrievedUsed.isPresent)
        assertNotNull(retrievedUsed.get().usedAt)
        // The used_at should be updated (invalidateUnusedTokensForUser sets used_at WHERE used_at IS NULL)
        // Since this token already had used_at, it should not be modified
        assertEquals(usedAtBefore, retrievedUsed.get().usedAt)
    }

    // ============================================================
    // TC-1.8: deleteExpired
    // ============================================================

    @Test
    fun `deleteExpired - should remove only expired tokens`() {
        // Given
        val user = createTestUser()
        val expiredTokenString = "expired-del-token-" + UUID.randomUUID().toString().replace("-", "")
        val validTokenString = "valid-del-token-" + UUID.randomUUID().toString().replace("-", "")

        val expiredToken = PasswordResetToken.create(user.id, expiredTokenString, 60)
        val validToken = PasswordResetToken.create(user.id, validTokenString, 60)
        repository.save(expiredToken)
        repository.save(validToken)

        // Expire the token by moving created_at and expires_at into the past
        // (satisfies the chk_expires_at_after_created constraint: expires_at > created_at)
        jdbcTemplate.update(
            "UPDATE password_reset_tokens SET created_at = ?, expires_at = ? WHERE id = ?",
            Timestamp.from(Instant.now().minusSeconds(7200)),
            Timestamp.from(Instant.now().minusSeconds(3600)),
            expiredToken.id,
        )

        // When
        repository.deleteExpired()

        // Then
        val retrievedExpired = repository.findByToken(expiredTokenString)
        val retrievedValid = repository.findByToken(validTokenString)
        assertTrue(retrievedExpired.isEmpty)
        assertTrue(retrievedValid.isPresent)
    }

    // ============================================================
    // TC-1.9: Unique constraint violation
    // ============================================================

    @Test
    fun `save - should throw DataIntegrityViolationException for duplicate token string`() {
        // Given
        val user = createTestUser()
        val duplicateTokenString = "duplicate-token-" + UUID.randomUUID().toString().replace("-", "")
        val token1 = PasswordResetToken.create(user.id, duplicateTokenString, 60)
        repository.save(token1)

        // When/Then
        val token2 = PasswordResetToken.create(user.id, duplicateTokenString, 60)
        assertThrows<DataIntegrityViolationException> {
            repository.save(token2)
        }
    }

    // ============================================================
    // TC-1.10: CASCADE on user deletion
    // ============================================================

    @Test
    fun `cascade - deleting user should also delete their tokens`() {
        // Given
        val user = createTestUser()
        val tokenString = "cascade-token-" + UUID.randomUUID().toString().replace("-", "")
        val token = PasswordResetToken.create(user.id, tokenString, 60)
        repository.save(token)

        // Verify token exists
        assertTrue(repository.findByToken(tokenString).isPresent)

        // When - delete user via JdbcTemplate
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", user.id.value())

        // Then - token should also be deleted (CASCADE)
        val result = repository.findByToken(tokenString)
        assertTrue(result.isEmpty)
    }
}
