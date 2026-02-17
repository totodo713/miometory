package com.worklog.domain.password

import com.worklog.domain.user.UserId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for PasswordResetToken domain entity.
 *
 * Tests pure domain logic without Spring context or database:
 * - markAsUsed domain method
 * - isValid / isExpired
 * - equals / hashCode
 * - toString
 * - Constructor validation (empty token, short token, invalid expiry)
 */
class PasswordResetTokenTest {

    private fun createValidToken(): PasswordResetToken {
        val userId = UserId.of(UUID.randomUUID())
        val tokenString = "domain-test-token-" + UUID.randomUUID().toString().replace("-", "")
        return PasswordResetToken.create(userId, tokenString, 60)
    }

    @Test
    fun `markAsUsed - domain method should set used flag and usedAt`() {
        // Given
        val token = createValidToken()

        // When
        token.markAsUsed()

        // Then
        assertTrue(token.isUsed)
        assertNotNull(token.usedAt)
        assertFalse(token.isValid)
    }

    @Test
    fun `markAsUsed - should throw when token already used`() {
        // Given
        val token = createValidToken()
        token.markAsUsed()

        // When/Then
        assertThrows<IllegalStateException> {
            token.markAsUsed()
        }
    }

    @Test
    fun `isValid - should return true for unused non-expired token`() {
        // Given
        val token = createValidToken()

        // Then
        assertTrue(token.isValid)
        assertFalse(token.isUsed)
        assertFalse(token.isExpired)
    }

    @Test
    fun `equals and hashCode - tokens with same id should be equal`() {
        // Given
        val userId = UserId.of(UUID.randomUUID())
        val tokenString = "equals-test-token-" + UUID.randomUUID().toString().replace("-", "")
        val token = PasswordResetToken.create(userId, tokenString, 60)

        // Create another token with same ID via rehydration constructor
        val sameIdToken = PasswordResetToken(
            token.id,
            userId,
            tokenString,
            token.createdAt,
            token.expiresAt,
        )

        // Then
        assertEquals(token, sameIdToken)
        assertEquals(token.hashCode(), sameIdToken.hashCode())
    }

    @Test
    fun `toString - should return readable representation`() {
        // Given
        val token = createValidToken()

        // When
        val result = token.toString()

        // Then
        assertTrue(result.contains("PasswordResetToken"))
        assertTrue(result.contains(token.id.toString()))
    }

    @Test
    fun `constructor - should reject empty token`() {
        assertThrows<IllegalArgumentException> {
            PasswordResetToken(
                UUID.randomUUID(),
                UserId.of(UUID.randomUUID()),
                "",
                Instant.now(),
                Instant.now().plusSeconds(3600),
            )
        }
    }

    @Test
    fun `constructor - should reject short token`() {
        assertThrows<IllegalArgumentException> {
            PasswordResetToken(
                UUID.randomUUID(),
                UserId.of(UUID.randomUUID()),
                "short",
                Instant.now(),
                Instant.now().plusSeconds(3600),
            )
        }
    }

    @Test
    fun `constructor - should reject expiresAt before createdAt`() {
        assertThrows<IllegalArgumentException> {
            PasswordResetToken(
                UUID.randomUUID(),
                UserId.of(UUID.randomUUID()),
                "valid-token-string-that-is-at-least-32-chars",
                Instant.now(),
                Instant.now().minusSeconds(3600),
            )
        }
    }
}
