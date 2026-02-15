package com.worklog.api

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for MemberController.
 *
 * Tests the proxy entry authorization endpoints.
 * Task: T161 - Integration test for proxy entry authorization
 */
class MemberControllerTest : IntegrationTestBase() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var tenantId: UUID
    private lateinit var organizationId: UUID
    private lateinit var managerId: UUID
    private lateinit var subordinateId: UUID
    private lateinit var nonSubordinateId: UUID

    @BeforeEach
    fun setup() {
        // Create test UUIDs
        tenantId = UUID.randomUUID()
        organizationId = UUID.randomUUID()
        managerId = UUID.randomUUID()
        subordinateId = UUID.randomUUID()
        nonSubordinateId = UUID.randomUUID()

        // Insert tenant
        jdbcTemplate.update(
            """
            INSERT INTO tenant (id, name, code, status, version)
            VALUES (?, ?, ?, 'ACTIVE', 0)
            ON CONFLICT (id) DO NOTHING
            """,
            tenantId,
            "Test Tenant",
            "TEST-${tenantId.toString().take(8)}",
        )

        // Insert organization
        jdbcTemplate.update(
            """
            INSERT INTO organization (id, tenant_id, name, code, level, status, version)
            VALUES (?, ?, ?, ?, 1, 'ACTIVE', 0)
            ON CONFLICT (id) DO NOTHING
            """,
            organizationId,
            tenantId,
            "Test Organization",
            "ORG-${organizationId.toString().take(8)}",
        )

        // Insert manager
        jdbcTemplate.update(
            """
            INSERT INTO members (id, tenant_id, organization_id, email, display_name, manager_id, is_active, version)
            VALUES (?, ?, ?, ?, ?, NULL, true, 0)
            ON CONFLICT (id) DO NOTHING
            """,
            managerId,
            tenantId,
            organizationId,
            "manager-${managerId.toString().take(8)}@test.com",
            "Test Manager",
        )

        // Insert subordinate (reports to manager)
        jdbcTemplate.update(
            """
            INSERT INTO members (id, tenant_id, organization_id, email, display_name, manager_id, is_active, version)
            VALUES (?, ?, ?, ?, ?, ?, true, 0)
            ON CONFLICT (id) DO NOTHING
            """,
            subordinateId,
            tenantId,
            organizationId,
            "subordinate-${subordinateId.toString().take(8)}@test.com",
            "Test Subordinate",
            managerId,
        )

        // Insert non-subordinate (no manager relationship)
        jdbcTemplate.update(
            """
            INSERT INTO members (id, tenant_id, organization_id, email, display_name, manager_id, is_active, version)
            VALUES (?, ?, ?, ?, ?, NULL, true, 0)
            ON CONFLICT (id) DO NOTHING
            """,
            nonSubordinateId,
            tenantId,
            organizationId,
            "nonsubordinate-${nonSubordinateId.toString().take(8)}@test.com",
            "Test Non-Subordinate",
        )
    }

    @Test
    fun `GET members by id should return member details`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/members/$managerId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertEquals(managerId.toString(), body["id"])
        assertEquals("Test Manager", body["displayName"])
    }

    @Test
    fun `GET members by id with unknown id should return 404`() {
        // Arrange
        val unknownId = UUID.randomUUID()

        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/members/$unknownId",
                String::class.java,
            )

        // Assert
        // The controller throws DomainException which gets mapped to 4xx
        assertTrue(response.statusCode.is4xxClientError)
    }

    @Test
    fun `GET subordinates should return list of direct reports`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/members/$managerId/subordinates",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        val subordinates = body["subordinates"] as List<*>
        assertEquals(1, subordinates.size)

        val subordinate = subordinates[0] as Map<*, *>
        assertEquals(subordinateId.toString(), subordinate["id"])
        assertEquals("Test Subordinate", subordinate["displayName"])
    }

    @Test
    fun `GET subordinates for member with no reports should return empty list`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/members/$subordinateId/subordinates",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        val subordinates = body["subordinates"] as List<*>
        assertTrue(subordinates.isEmpty())
    }

    @Test
    fun `GET can-proxy should return true for manager and subordinate`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/members/$managerId/can-proxy/$subordinateId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertEquals(true, body["canProxy"])
    }

    @Test
    fun `GET can-proxy should return false for non-manager relationship`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/members/$managerId/can-proxy/$nonSubordinateId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertEquals(false, body["canProxy"])
    }

    @Test
    fun `GET can-proxy should return false when member tries to proxy for their manager`() {
        // Subordinate trying to proxy for their manager
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/members/$subordinateId/can-proxy/$managerId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertEquals(false, body["canProxy"])
    }

    @Test
    fun `GET can-proxy should return true for self`() {
        // Self-entry is always allowed in the current implementation
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/members/$managerId/can-proxy/$managerId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertEquals(true, body["canProxy"])
    }
}
