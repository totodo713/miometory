package com.worklog.eventsourcing

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for JdbcAuditLogger.
 *
 * Tests audit logging with various scenarios against the audit_logs table.
 */
class AuditLoggerTest : IntegrationTestBase() {
    @Autowired
    private lateinit var auditLogger: AuditLogger

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var testUserId: UUID

    companion object {
        private const val USER_ROLE_ID = "00000000-0000-0000-0000-000000000002"
    }

    @BeforeEach
    fun setUp() {
        // Clean up audit_logs before each test
        jdbcTemplate.execute("DELETE FROM audit_logs")

        // Create a test user for FK constraint on audit_logs.user_id
        testUserId = UUID.randomUUID()
        jdbcTemplate.update(
            """INSERT INTO users (id, email, hashed_password, name, role_id, account_status, created_at, updated_at)
               VALUES (?, ?, 'hashed', ?, ?::UUID, 'active', NOW(), NOW())
               ON CONFLICT (id) DO NOTHING""",
            testUserId,
            "audit-test-${testUserId.toString().take(8)}@test.com",
            "Audit Test User",
            USER_ROLE_ID,
        )
    }

    @Test
    fun `log should store audit entry with all fields`() {
        val tenantId = UUID.randomUUID()
        val resourceId = UUID.randomUUID()
        val details = mapOf("oldName" to "Old", "newName" to "New")

        auditLogger.log(tenantId, testUserId, "UPDATE", "Tenant", resourceId, details)

        val entries =
            jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE user_id = ?",
                testUserId,
            )

        assertEquals(1, entries.size)
        assertEquals(testUserId.toString(), entries[0]["user_id"].toString())
        assertEquals("UPDATE", entries[0]["event_type"])
        assertNotNull(entries[0]["timestamp"])
        assertEquals(90, entries[0]["retention_days"]) // Uses DB default (V11)

        // tenant_id, resource_type, resource_id are now merged into details JSONB
        val detailsJson = entries[0]["details"].toString()
        assertTrue(detailsJson.contains(tenantId.toString()))
        assertTrue(detailsJson.contains("Tenant"))
        assertTrue(detailsJson.contains(resourceId.toString()))
        assertTrue(detailsJson.contains("oldName"))
        assertTrue(detailsJson.contains("newName"))
    }

    @Test
    fun `log should handle null tenant and user for system actions`() {
        val resourceId = UUID.randomUUID()

        auditLogger.log(null, null, "SYSTEM_INIT", "Database", resourceId, mapOf("action" to "migration"))

        val entries =
            jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE event_type = 'SYSTEM_INIT'",
            )

        assertEquals(1, entries.size)
        assertEquals(null, entries[0]["user_id"])
        assertEquals("SYSTEM_INIT", entries[0]["event_type"])

        val detailsJson = entries[0]["details"].toString()
        assertTrue(detailsJson.contains("Database"))
        assertTrue(detailsJson.contains(resourceId.toString()))
    }

    @Test
    fun `logSystemAction should log with null tenant and user`() {
        val resourceId = UUID.randomUUID()

        auditLogger.logSystemAction("CLEANUP", "ExpiredSessions", resourceId, mapOf("count" to 42))

        val entries =
            jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE event_type = 'CLEANUP'",
            )

        assertEquals(1, entries.size)
        assertEquals(null, entries[0]["user_id"])
        assertEquals("CLEANUP", entries[0]["event_type"])
    }

    @Test
    fun `log should handle empty details`() {
        val tenantId = UUID.randomUUID()
        val resourceId = UUID.randomUUID()

        auditLogger.log(tenantId, testUserId, "DELETE", "Organization", resourceId, emptyMap())

        val entries =
            jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE user_id = ?",
                testUserId,
            )

        assertEquals(1, entries.size)
        // Details should still contain tenant_id, resource_type, resource_id even with empty original details
        val detailsJson = entries[0]["details"].toString()
        assertTrue(detailsJson.contains(tenantId.toString()))
        assertTrue(detailsJson.contains("Organization"))
        assertTrue(detailsJson.contains(resourceId.toString()))
    }

    @Test
    fun `log should handle null details`() {
        val tenantId = UUID.randomUUID()
        val resourceId = UUID.randomUUID()

        auditLogger.log(tenantId, testUserId, "CREATE", "Project", resourceId, null)

        val entries =
            jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE user_id = ?",
                testUserId,
            )

        assertEquals(1, entries.size)
        // Details should contain tenant_id, resource_type, resource_id even with null original details
        val detailsJson = entries[0]["details"].toString()
        assertTrue(detailsJson.contains(tenantId.toString()))
        assertTrue(detailsJson.contains("Project"))
        assertTrue(detailsJson.contains(resourceId.toString()))
    }

    @Test
    fun `multiple log entries should be stored independently`() {
        val tenantId = UUID.randomUUID()
        val resourceId = UUID.randomUUID()

        auditLogger.log(tenantId, testUserId, "CREATE", "Tenant", resourceId, mapOf("name" to "New Tenant"))
        auditLogger.log(tenantId, testUserId, "UPDATE", "Tenant", resourceId, mapOf("name" to "Updated Tenant"))
        auditLogger.log(tenantId, testUserId, "DEACTIVATE", "Tenant", resourceId, mapOf("reason" to "User request"))

        val entries =
            jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE user_id = ? ORDER BY timestamp",
                testUserId,
            )

        assertEquals(3, entries.size)
        assertEquals("CREATE", entries[0]["event_type"])
        assertEquals("UPDATE", entries[1]["event_type"])
        assertEquals("DEACTIVATE", entries[2]["event_type"])
    }

    @Test
    fun `log should handle complex nested details`() {
        val tenantId = UUID.randomUUID()
        val resourceId = UUID.randomUUID()
        val complexDetails =
            mapOf(
                "changes" to
                    mapOf(
                        "field1" to mapOf("old" to "a", "new" to "b"),
                        "field2" to mapOf("old" to 1, "new" to 2),
                    ),
                "metadata" to listOf("tag1", "tag2", "tag3"),
            )

        auditLogger.log(tenantId, testUserId, "UPDATE", "Organization", resourceId, complexDetails)

        val entries =
            jdbcTemplate.queryForList(
                "SELECT details::text FROM audit_logs WHERE user_id = ?",
                testUserId,
            )

        assertEquals(1, entries.size)
        val detailsJson = entries[0]["details"].toString()
        assertTrue(detailsJson.contains("changes"))
        assertTrue(detailsJson.contains("field1"))
        assertTrue(detailsJson.contains("tag1"))
    }
}
