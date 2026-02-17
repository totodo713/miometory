package com.worklog.infrastructure.persistence

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.worklog.IntegrationTestBase
import com.worklog.domain.audit.AuditLog
import com.worklog.domain.user.UserId
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for AuditLogRepository (T009 + T016).
 *
 * Verifies:
 * - AuditLog saves with JSONB details and reads back correctly
 * - AuditLog saves with IPv4/IPv6 INET ip_address
 * - AuditLog saves with null details/ip_address
 * - Factory methods produce persistable entities
 * - Each save creates a new row (INSERT, never UPDATE)
 *
 * Note: Tests use null userId to avoid foreign key constraints on users table.
 * User-specific audit logging is tested at the service/integration level.
 */
class AuditLogRepositoryTest : IntegrationTestBase() {

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

    private val objectMapper = jacksonObjectMapper()

    /**
     * Helper to insert an AuditLog using the custom @Query method with
     * explicit PostgreSQL JSONB/INET casting.
     */
    private fun insertAndFind(auditLog: AuditLog): AuditLog {
        auditLogRepository.insertAuditLog(
            auditLog.id!!,
            auditLog.userId?.value(),
            auditLog.eventType,
            auditLog.ipAddress,
            auditLog.timestamp,
            auditLog.details,
            auditLog.retentionDays,
        )
        return auditLogRepository.findById(auditLog.id!!).orElseThrow()
    }

    // ============================================================
    // T009: JSONB and INET persistence tests
    // ============================================================

    /** Compare JSON strings semantically (ignoring whitespace differences from PostgreSQL JSONB normalization). */
    private fun assertJsonEquals(expected: String, actual: String?) {
        assertNotNull(actual, "JSON details should not be null")
        assertEquals(objectMapper.readTree(expected), objectMapper.readTree(actual))
    }

    @Test
    fun `should save AuditLog with JSONB details and read back correctly`() {
        val jsonDetails = """{"reason":"invalid_credentials","attempts":3}"""
        val auditLog = AuditLog.createSystemEvent(AuditLog.LOGIN_FAILURE, jsonDetails)

        val found = insertAndFind(auditLog)

        assertJsonEquals(jsonDetails, found.details)
        assertEquals(AuditLog.LOGIN_FAILURE, found.eventType)
    }

    @Test
    fun `should save AuditLog with nested JSONB details`() {
        val jsonDetails = """{"user":{"name":"test","roles":["ADMIN","USER"]},"action":"login"}"""
        val auditLog = AuditLog.createSystemEvent(AuditLog.LOGIN_SUCCESS, jsonDetails)

        val found = insertAndFind(auditLog)

        assertJsonEquals(jsonDetails, found.details)
    }

    @Test
    fun `should save AuditLog with IPv4 INET ip_address`() {
        val ipv4 = "192.168.1.100"
        val auditLog = AuditLog.createUserAction(null, AuditLog.LOGIN_SUCCESS, ipv4, null)

        val found = insertAndFind(auditLog)

        assertEquals(ipv4, found.ipAddress)
    }

    @Test
    fun `should save AuditLog with IPv6 INET ip_address`() {
        val ipv6 = "::1"
        val auditLog = AuditLog.createUserAction(null, AuditLog.LOGIN_SUCCESS, ipv6, null)

        val found = insertAndFind(auditLog)

        assertEquals(ipv6, found.ipAddress)
    }

    @Test
    fun `should save AuditLog with null details and null ip_address`() {
        val auditLog = AuditLog.createSystemEvent(AuditLog.AUDIT_LOG_CLEANUP, null)

        val found = insertAndFind(auditLog)

        assertNull(found.details)
        assertNull(found.ipAddress)
        assertNull(found.userId)
    }

    @Test
    fun `should save AuditLog with JSONB details and INET ip_address combined`() {
        val jsonDetails = """{"changed_by":"admin"}"""
        val auditLog = AuditLog.createUserAction(
            null,
            AuditLog.PASSWORD_CHANGE,
            "10.0.0.1",
            jsonDetails,
        )

        val found = insertAndFind(auditLog)

        assertEquals(AuditLog.PASSWORD_CHANGE, found.eventType)
        assertJsonEquals(jsonDetails, found.details)
        assertEquals("10.0.0.1", found.ipAddress)
    }

    @Test
    fun `should save AuditLog created via createSystemEvent factory method`() {
        val auditLog = AuditLog.createSystemEvent(AuditLog.AUDIT_LOG_CLEANUP, """{"deleted_count":150}""")

        val found = insertAndFind(auditLog)

        assertEquals(AuditLog.AUDIT_LOG_CLEANUP, found.eventType)
        assertNull(found.userId)
    }

    @Test
    fun `should query AuditLog by event type after save`() {
        val uniqueEventType = "TEST_EVT_${UUID.randomUUID().toString().take(8)}"
        val auditLog = AuditLog.createSystemEvent(uniqueEventType, """{"test":true}""")

        auditLogRepository.insertAuditLog(
            auditLog.id!!,
            null,
            auditLog.eventType,
            null,
            auditLog.timestamp,
            auditLog.details,
            auditLog.retentionDays,
        )

        val results = auditLogRepository.findByEventType(uniqueEventType, 10)
        assertEquals(1, results.size)
        assertEquals(uniqueEventType, results[0].eventType)
    }

    // ============================================================
    // T016: INSERT-only behavior tests
    // ============================================================

    @Test
    fun `should create separate rows for each save - never UPDATE`() {
        val uniqueEventType = "INS_TEST_${UUID.randomUUID().toString().take(8)}"

        val log1 = AuditLog.createUserAction(null, uniqueEventType, "1.2.3.4", """{"seq":1}""")
        val log2 = AuditLog.createUserAction(null, uniqueEventType, "1.2.3.4", """{"seq":2}""")

        auditLogRepository.insertAuditLog(
            log1.id!!,
            null,
            log1.eventType,
            log1.ipAddress,
            log1.timestamp,
            log1.details,
            log1.retentionDays,
        )
        auditLogRepository.insertAuditLog(
            log2.id!!,
            null,
            log2.eventType,
            log2.ipAddress,
            log2.timestamp,
            log2.details,
            log2.retentionDays,
        )

        assertTrue(log1.id != log2.id, "Each factory call should produce a unique ID")

        val results = auditLogRepository.findByEventType(uniqueEventType, 10)
        assertEquals(2, results.size, "Both audit logs should exist as separate rows")
    }

    @Test
    fun `count should increase by 1 for each save`() {
        val countBefore = auditLogRepository.count()

        val auditLog = AuditLog.createSystemEvent(AuditLog.AUDIT_LOG_CLEANUP, """{"count":"test"}""")
        auditLogRepository.insertAuditLog(
            auditLog.id!!,
            null,
            auditLog.eventType,
            null,
            auditLog.timestamp,
            auditLog.details,
            auditLog.retentionDays,
        )

        val countAfter = auditLogRepository.count()
        assertEquals(countBefore + 1, countAfter, "Count should increase by exactly 1 per save")
    }

    @Test
    fun `should read back AuditLog with isNew false via PersistenceCreator`() {
        val auditLog = AuditLog.createUserAction(
            null,
            AuditLog.LOGIN_SUCCESS,
            "10.0.0.1",
            """{"source":"test"}""",
        )

        val found = insertAndFind(auditLog)

        assertFalse(found.isNew, "Rehydrated AuditLog from DB should have isNew=false")
    }
}
