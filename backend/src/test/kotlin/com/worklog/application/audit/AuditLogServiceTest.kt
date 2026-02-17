package com.worklog.application.audit

import com.worklog.domain.audit.AuditLog
import com.worklog.domain.user.UserId
import com.worklog.infrastructure.persistence.AuditLogRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for AuditLogService (T013).
 *
 * Verifies:
 * - logEvent() calls repository insertAuditLog with correct parameters
 * - Exception in insertAuditLog is caught and logged (not propagated)
 * - Null userId handling for system events
 */
class AuditLogServiceTest {

    private val auditLogRepository: AuditLogRepository = mockk(relaxed = true)
    private val auditLogService = AuditLogService(auditLogRepository)

    @Test
    fun `logEvent should call insertAuditLog with correct parameters`() {
        val userId = UserId.of(UUID.randomUUID())

        auditLogService.logEvent(userId, AuditLog.LOGIN_SUCCESS, "192.168.1.1", """{"agent":"test"}""")

        val idSlot = slot<UUID>()
        val userIdSlot = slot<UUID>()
        val eventTypeSlot = slot<String>()
        val ipSlot = slot<String>()
        val tsSlot = slot<Instant>()
        val detailsSlot = slot<String>()
        val retSlot = slot<Int>()

        verify(exactly = 1) {
            auditLogRepository.insertAuditLog(
                capture(idSlot),
                capture(userIdSlot),
                capture(eventTypeSlot),
                capture(ipSlot),
                capture(tsSlot),
                capture(detailsSlot),
                capture(retSlot),
            )
        }

        assertNotNull(idSlot.captured)
        assertEquals(userId.value(), userIdSlot.captured)
        assertEquals(AuditLog.LOGIN_SUCCESS, eventTypeSlot.captured)
        assertEquals("192.168.1.1", ipSlot.captured)
        assertEquals("""{"agent":"test"}""", detailsSlot.captured)
        assertEquals(90, retSlot.captured)
    }

    @Test
    fun `logEvent should not propagate exception when insertAuditLog fails`() {
        every {
            auditLogRepository.insertAuditLog(any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException("DB connection failed")

        // Should not throw
        auditLogService.logEvent(
            UserId.of(UUID.randomUUID()),
            AuditLog.LOGIN_SUCCESS,
            "10.0.0.1",
            null,
        )

        verify(exactly = 1) {
            auditLogRepository.insertAuditLog(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `logEvent should pass null userId for system events`() {
        auditLogService.logEvent(null, AuditLog.AUDIT_LOG_CLEANUP, null, """{"deleted":10}""")

        verify(exactly = 1) {
            auditLogRepository.insertAuditLog(
                any(),
                isNull(),
                eq(AuditLog.AUDIT_LOG_CLEANUP),
                isNull(),
                any(),
                eq("""{"deleted":10}"""),
                any(),
            )
        }
    }

    @Test
    fun `logEvent should pass null details`() {
        val userId = UserId.of(UUID.randomUUID())

        auditLogService.logEvent(userId, AuditLog.LOGOUT, "10.0.0.1", null)

        verify(exactly = 1) {
            auditLogRepository.insertAuditLog(
                any(),
                eq(userId.value()),
                eq(AuditLog.LOGOUT),
                eq("10.0.0.1"),
                any(),
                isNull(),
                any(),
            )
        }
    }

    @Test
    fun `logEvent should pass null ipAddress`() {
        val userId = UserId.of(UUID.randomUUID())

        auditLogService.logEvent(userId, AuditLog.EMAIL_VERIFICATION, null, "verified")

        verify(exactly = 1) {
            auditLogRepository.insertAuditLog(
                any(),
                eq(userId.value()),
                eq(AuditLog.EMAIL_VERIFICATION),
                isNull(),
                any(),
                eq("verified"),
                any(),
            )
        }
    }
}
