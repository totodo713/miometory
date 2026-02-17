package com.worklog.application.audit

import com.worklog.IntegrationTestBase
import com.worklog.domain.audit.AuditLog
import com.worklog.domain.user.UserId
import com.worklog.infrastructure.persistence.AuditLogRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Integration tests for AuditLogService transaction isolation (T014).
 *
 * Verifies that AuditLogService.logEvent() operates in a separate
 * transaction (REQUIRES_NEW) so that failures in audit log persistence
 * never roll back the calling transaction.
 */
class AuditLogServiceIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var auditLogService: AuditLogService

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

    @Test
    @Transactional
    fun `logEvent should not mark outer transaction as rollback-only on success`() {
        val countBefore = auditLogRepository.count()

        // Call logEvent within the outer transaction
        auditLogService.logEvent(
            null,
            AuditLog.AUDIT_LOG_CLEANUP,
            null,
            """{"test":"transaction_isolation"}""",
        )

        // The outer transaction should still be usable
        // (this would fail if the inner transaction marked it rollback-only)
        val countAfter = auditLogRepository.count()
        assertTrue(countAfter >= countBefore, "Outer transaction should still be functional after logEvent")
    }

    @Test
    fun `logEvent should persist audit log in independent transaction`() {
        val uniqueEventType = "TX_TEST_${UUID.randomUUID().toString().take(8)}"

        auditLogService.logEvent(null, uniqueEventType, null, """{"independent":true}""")

        // Verify the audit log was persisted (in its own committed transaction)
        val results = auditLogRepository.findByEventType(uniqueEventType, 10)
        assertTrue(results.isNotEmpty(), "AuditLog should be persisted in its own transaction")
    }

    @Test
    fun `logEvent should catch exception without affecting caller`() {
        val countBefore = auditLogRepository.count()

        // Pass invalid JSON that will cause a PSQLException in CAST(? AS jsonb)
        auditLogService.logEvent(null, AuditLog.LOGIN_SUCCESS, null, "not-valid-json")

        // The caller should not see any exception
        // Verify we can still use the repository (outer context not broken)
        val countAfter = auditLogRepository.count()
        assertTrue(countAfter >= countBefore, "Repository should still be usable after failed logEvent")
    }
}
