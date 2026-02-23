package com.worklog.domain.session

import com.worklog.domain.user.UserId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserSessionTest {

    private val userId = UserId.of(UUID.randomUUID())

    @Nested
    @DisplayName("create")
    inner class CreateTests {
        @Test
        fun `should create session with correct expiration`() {
            val session = UserSession.create(userId, "127.0.0.1", "TestAgent", 30)
            assertNotNull(session.sessionId)
            assertEquals(userId, session.userId)
            assertEquals("127.0.0.1", session.ipAddress)
            assertEquals("TestAgent", session.userAgent)
            assertNotNull(session.createdAt)
            assertTrue(session.expiresAt.isAfter(session.createdAt))
            assertEquals(session.createdAt, session.lastAccessedAt)
        }

        @Test
        fun `should allow null ipAddress and userAgent`() {
            val session = UserSession.create(userId, null, null, 60)
            assertNotNull(session.sessionId)
        }
    }

    @Nested
    @DisplayName("constructor validation")
    inner class ValidationTests {
        @Test
        fun `should reject null sessionId`() {
            assertThrows<NullPointerException> {
                UserSession(null, userId, "ip", "ua", Instant.now(), Instant.now().plusSeconds(60))
            }
        }

        @Test
        fun `should reject null userId`() {
            assertThrows<NullPointerException> {
                UserSession(UUID.randomUUID(), null, "ip", "ua", Instant.now(), Instant.now().plusSeconds(60))
            }
        }

        @Test
        fun `should reject expiresAt before createdAt`() {
            val now = Instant.now()
            assertThrows<IllegalArgumentException> {
                UserSession(UUID.randomUUID(), userId, "ip", "ua", now, now.minusSeconds(60))
            }
        }
    }

    @Nested
    @DisplayName("touch")
    inner class TouchTests {
        @Test
        fun `should update lastAccessedAt and expiresAt`() {
            val session = UserSession.create(userId, "127.0.0.1", "agent", 30)
            val originalExpires = session.expiresAt

            Thread.sleep(10)
            session.touch(60)

            assertTrue(session.lastAccessedAt.isAfter(session.createdAt))
            assertNotEquals(originalExpires, session.expiresAt)
        }
    }

    @Nested
    @DisplayName("expiration")
    inner class ExpirationTests {
        @Test
        fun `isExpired should return true for past expiration`() {
            val past = Instant.now().minus(1, ChronoUnit.HOURS)
            val session = UserSession(
                UUID.randomUUID(),
                userId,
                "ip",
                "ua",
                past.minusSeconds(3600),
                past,
            )
            assertTrue(session.isExpired())
            assertFalse(session.isValid())
        }

        @Test
        fun `isExpired should return false for future expiration`() {
            val session = UserSession.create(userId, "ip", "ua", 60)
            assertFalse(session.isExpired())
            assertTrue(session.isValid())
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    inner class EqualityTests {
        @Test
        fun `same sessionId should be equal`() {
            val id = UUID.randomUUID()
            val now = Instant.now()
            val s1 = UserSession(id, userId, "ip1", "ua1", now, now.plusSeconds(60))
            val s2 = UserSession(id, userId, "ip2", "ua2", now, now.plusSeconds(120))
            assertEquals(s1, s2)
            assertEquals(s1.hashCode(), s2.hashCode())
        }

        @Test
        fun `different sessionId should not be equal`() {
            val now = Instant.now()
            val s1 = UserSession(UUID.randomUUID(), userId, "ip", "ua", now, now.plusSeconds(60))
            val s2 = UserSession(UUID.randomUUID(), userId, "ip", "ua", now, now.plusSeconds(60))
            assertNotEquals(s1, s2)
        }
    }

    @Test
    fun `toString should contain sessionId`() {
        val session = UserSession.create(userId, "127.0.0.1", "agent", 30)
        val str = session.toString()
        assertTrue(str.contains(session.sessionId.toString()), "toString should contain sessionId")
    }
}
