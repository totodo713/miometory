package com.worklog.domain.audit

import com.worklog.domain.user.UserId
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for AuditLog Persistable contract (T015).
 *
 * Verifies that factory methods produce new entities (isNew=true)
 * and the @PersistenceCreator constructor produces rehydrated entities (isNew=false).
 */
class AuditLogTest {

    @Nested
    inner class PersistableContract {

        @Test
        fun `createUserAction should return entity with isNew true`() {
            val userId = UserId.of(UUID.randomUUID())
            val auditLog = AuditLog.createUserAction(userId, AuditLog.LOGIN_SUCCESS, "192.168.1.1", "details")

            assertTrue(auditLog.isNew, "Factory-created AuditLog should be new")
        }

        @Test
        fun `createSystemEvent should return entity with isNew true`() {
            val auditLog = AuditLog.createSystemEvent(AuditLog.AUDIT_LOG_CLEANUP, "cleanup details")

            assertTrue(auditLog.isNew, "Factory-created system event should be new")
        }

        @Test
        fun `PersistenceCreator constructor should return entity with isNew false`() {
            val id = UUID.randomUUID()
            val auditLog = AuditLog(
                id,
                UserId.of(UUID.randomUUID()),
                AuditLog.LOGIN_SUCCESS,
                "127.0.0.1",
                Instant.now(),
                """{"key":"value"}""",
                90,
            )

            assertFalse(auditLog.isNew, "Rehydrated AuditLog should not be new")
        }

        @Test
        fun `getId should return non-null UUID for factory-created entity`() {
            val auditLog = AuditLog.createUserAction(
                UserId.of(UUID.randomUUID()),
                AuditLog.LOGIN_SUCCESS,
                "10.0.0.1",
                null,
            )

            assertNotNull(auditLog.id, "Factory-created AuditLog should have non-null ID")
        }

        @Test
        fun `getId should return the same UUID passed to PersistenceCreator`() {
            val expectedId = UUID.randomUUID()
            val auditLog = AuditLog(
                expectedId,
                null,
                AuditLog.AUDIT_LOG_CLEANUP,
                null,
                Instant.now(),
                null,
                90,
            )

            assertEquals(expectedId, auditLog.id)
        }

        @Test
        fun `each factory call should generate a unique ID`() {
            val userId = UserId.of(UUID.randomUUID())
            val log1 = AuditLog.createUserAction(userId, AuditLog.LOGIN_SUCCESS, "1.2.3.4", null)
            val log2 = AuditLog.createUserAction(userId, AuditLog.LOGIN_SUCCESS, "1.2.3.4", null)

            assertFalse(log1.id == log2.id, "Each factory call should produce a unique ID")
        }
    }

    @Nested
    inner class FactoryMethods {

        @Test
        fun `createUserAction should set all fields correctly`() {
            val userId = UserId.of(UUID.randomUUID())
            val auditLog = AuditLog.createUserAction(userId, AuditLog.LOGIN_FAILURE, "10.0.0.1", "some details")

            assertEquals(userId, auditLog.userId)
            assertEquals(AuditLog.LOGIN_FAILURE, auditLog.eventType)
            assertEquals("10.0.0.1", auditLog.ipAddress)
            assertEquals("some details", auditLog.details)
            assertEquals(90, auditLog.retentionDays)
            assertNotNull(auditLog.timestamp)
        }

        @Test
        fun `createSystemEvent should set userId and ipAddress to null`() {
            val auditLog = AuditLog.createSystemEvent(AuditLog.AUDIT_LOG_CLEANUP, "details")

            assertNull(auditLog.userId)
            assertNull(auditLog.ipAddress)
            assertEquals(AuditLog.AUDIT_LOG_CLEANUP, auditLog.eventType)
            assertEquals("details", auditLog.details)
        }

        @Test
        fun `createUserAction should accept null userId for anonymous events`() {
            val auditLog = AuditLog.createUserAction(null, AuditLog.LOGIN_FAILURE, "1.2.3.4", "anonymous")

            assertNull(auditLog.userId)
            assertTrue(auditLog.isNew)
        }

        @Test
        fun `createUserAction should accept null ipAddress`() {
            val userId = UserId.of(UUID.randomUUID())
            val auditLog = AuditLog.createUserAction(userId, AuditLog.EMAIL_VERIFICATION, null, "verified")

            assertNull(auditLog.ipAddress)
        }

        @Test
        fun `createUserAction should accept null details`() {
            val userId = UserId.of(UUID.randomUUID())
            val auditLog = AuditLog.createUserAction(userId, AuditLog.LOGOUT, "10.0.0.1", null)

            assertNull(auditLog.details)
        }
    }

    @Nested
    inner class Validation {

        @Test
        fun `should reject null event type`() {
            assertThrows<NullPointerException> {
                AuditLog.createUserAction(UserId.of(UUID.randomUUID()), null, "1.2.3.4", null)
            }
        }

        @Test
        fun `should reject blank event type`() {
            assertThrows<IllegalArgumentException> {
                AuditLog.createUserAction(UserId.of(UUID.randomUUID()), "  ", "1.2.3.4", null)
            }
        }

        @Test
        fun `should reject event type exceeding 50 characters`() {
            val longEventType = "A".repeat(51)
            assertThrows<IllegalArgumentException> {
                AuditLog.createUserAction(UserId.of(UUID.randomUUID()), longEventType, "1.2.3.4", null)
            }
        }
    }

    @Nested
    inner class DomainBehavior {

        @Test
        fun `isUserEvent should return true when userId is present`() {
            val auditLog = AuditLog.createUserAction(
                UserId.of(UUID.randomUUID()),
                AuditLog.LOGIN_SUCCESS,
                "1.2.3.4",
                null,
            )
            assertTrue(auditLog.isUserEvent)
        }

        @Test
        fun `isSystemEvent should return true when userId is null`() {
            val auditLog = AuditLog.createSystemEvent(AuditLog.AUDIT_LOG_CLEANUP, null)
            assertTrue(auditLog.isSystemEvent)
        }

        @Test
        fun `equals should be based on id`() {
            val id = UUID.randomUUID()
            val log1 = AuditLog(id, null, "EVENT", null, Instant.now(), null, 90)
            val log2 = AuditLog(id, null, "OTHER", null, Instant.now(), null, 30)

            assertEquals(log1, log2, "AuditLogs with same ID should be equal")
        }
    }
}
