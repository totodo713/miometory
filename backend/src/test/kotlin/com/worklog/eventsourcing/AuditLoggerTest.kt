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
 * Tests audit logging with various scenarios.
 */
class AuditLoggerTest : IntegrationTestBase() {
    @Autowired
    private lateinit var auditLogger: AuditLogger

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        // Clean up audit log before each test
        jdbcTemplate.execute("DELETE FROM audit_log")
    }

    @Test
    fun `log should store audit entry with all fields`() {
        val tenantId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val resourceId = UUID.randomUUID()
        val details = mapOf("oldName" to "Old", "newName" to "New")

        auditLogger.log(tenantId, userId, "UPDATE", "Tenant", resourceId, details)

        val entries =
            jdbcTemplate.queryForList(
                "SELECT * FROM audit_log WHERE tenant_id = ?",
                tenantId,
            )

        assertEquals(1, entries.size)
        assertEquals(tenantId.toString(), entries[0]["tenant_id"].toString())
        assertEquals(userId.toString(), entries[0]["user_id"].toString())
        assertEquals("UPDATE", entries[0]["action"])
        assertEquals("Tenant", entries[0]["resource_type"])
        assertEquals(resourceId.toString(), entries[0]["resource_id"].toString())
        assertNotNull(entries[0]["created_at"])

        val detailsJson = entries[0]["details"].toString()
        assertTrue(detailsJson.contains("oldName"))
        assertTrue(detailsJson.contains("newName"))
    }

    @Test
    fun `log should handle null tenant and user for system actions`() {
        val resourceId = UUID.randomUUID()

        auditLogger.log(null, null, "SYSTEM_INIT", "Database", resourceId, mapOf("action" to "migration"))

        val entries =
            jdbcTemplate.queryForList(
                "SELECT * FROM audit_log WHERE resource_id = ?",
                resourceId,
            )

        assertEquals(1, entries.size)
        assertEquals(null, entries[0]["tenant_id"])
        assertEquals(null, entries[0]["user_id"])
        assertEquals("SYSTEM_INIT", entries[0]["action"])
    }

    @Test
    fun `logSystemAction should log with null tenant and user`() {
        val resourceId = UUID.randomUUID()

        auditLogger.logSystemAction("CLEANUP", "ExpiredSessions", resourceId, mapOf("count" to 42))

        val entries =
            jdbcTemplate.queryForList(
                "SELECT * FROM audit_log WHERE resource_id = ?",
                resourceId,
            )

        assertEquals(1, entries.size)
        assertEquals(null, entries[0]["tenant_id"])
        assertEquals(null, entries[0]["user_id"])
        assertEquals("CLEANUP", entries[0]["action"])
    }

    @Test
    fun `log should handle empty details`() {
        val tenantId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val resourceId = UUID.randomUUID()

        auditLogger.log(tenantId, userId, "DELETE", "Organization", resourceId, emptyMap())

        val entries =
            jdbcTemplate.queryForList(
                "SELECT * FROM audit_log WHERE resource_id = ?",
                resourceId,
            )

        assertEquals(1, entries.size)
        assertEquals("{}", entries[0]["details"].toString())
    }

    @Test
    fun `log should handle null details`() {
        val tenantId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val resourceId = UUID.randomUUID()

        auditLogger.log(tenantId, userId, "CREATE", "Project", resourceId, null)

        val entries =
            jdbcTemplate.queryForList(
                "SELECT * FROM audit_log WHERE resource_id = ?",
                resourceId,
            )

        assertEquals(1, entries.size)
        assertEquals("{}", entries[0]["details"].toString())
    }

    @Test
    fun `multiple log entries should be stored independently`() {
        val tenantId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val resourceId = UUID.randomUUID()

        auditLogger.log(tenantId, userId, "CREATE", "Tenant", resourceId, mapOf("name" to "New Tenant"))
        auditLogger.log(tenantId, userId, "UPDATE", "Tenant", resourceId, mapOf("name" to "Updated Tenant"))
        auditLogger.log(tenantId, userId, "DEACTIVATE", "Tenant", resourceId, mapOf("reason" to "User request"))

        val entries =
            jdbcTemplate.queryForList(
                "SELECT * FROM audit_log WHERE tenant_id = ? ORDER BY created_at",
                tenantId,
            )

        assertEquals(3, entries.size)
        assertEquals("CREATE", entries[0]["action"])
        assertEquals("UPDATE", entries[1]["action"])
        assertEquals("DEACTIVATE", entries[2]["action"])
    }

    @Test
    fun `log should handle complex nested details`() {
        val tenantId = UUID.randomUUID()
        val userId = UUID.randomUUID()
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

        auditLogger.log(tenantId, userId, "UPDATE", "Organization", resourceId, complexDetails)

        val entries =
            jdbcTemplate.queryForList(
                "SELECT details::text FROM audit_log WHERE resource_id = ?",
                resourceId,
            )

        assertEquals(1, entries.size)
        val detailsJson = entries[0]["details"].toString()
        assertTrue(detailsJson.contains("changes"))
        assertTrue(detailsJson.contains("field1"))
        assertTrue(detailsJson.contains("tag1"))
    }
}
