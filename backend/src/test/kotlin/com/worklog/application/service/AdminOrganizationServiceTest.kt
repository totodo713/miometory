package com.worklog.application.service

import com.worklog.IntegrationTestBase
import com.worklog.application.command.CreateOrganizationCommand
import com.worklog.domain.shared.DomainException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for AdminOrganizationService (T011).
 *
 * Tests organization CRUD operations including:
 * - Create with valid input, duplicate code, inactive parent, max depth
 * - Update name, non-existent org
 * - Deactivate with/without children
 * - Activate inactive org
 * - List with search, status filter, and pagination
 */
class AdminOrganizationServiceTest : IntegrationTestBase() {

    @Autowired
    private lateinit var service: AdminOrganizationService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var tenantId: UUID

    companion object {
        private const val TEST_TENANT_ID = "550e8400-e29b-41d4-a716-446655440001"
    }

    @BeforeEach
    fun setup() {
        tenantId = UUID.fromString(TEST_TENANT_ID)
    }

    // --- Create organization tests ---

    @Test
    fun `create organization with valid input returns UUID`() {
        val code = "ORG_${UUID.randomUUID().toString().take(8)}"
        val command = CreateOrganizationCommand(
            tenantId,
            null,
            code,
            "Test Organization",
            null,
            null,
        )

        val orgId = service.createOrganization(command)

        assertNotNull(orgId)
        // Verify in projection table
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM organization WHERE id = ?",
            Long::class.java,
            orgId,
        )
        assertEquals(1L, count)
    }

    @Test
    fun `create organization with duplicate code throws DUPLICATE_CODE`() {
        val code = "DUP_${UUID.randomUUID().toString().take(8)}"
        val command1 = CreateOrganizationCommand(
            tenantId,
            null,
            code,
            "First Org",
            null,
            null,
        )
        service.createOrganization(command1)

        val command2 = CreateOrganizationCommand(
            tenantId,
            null,
            code,
            "Second Org",
            null,
            null,
        )

        val ex = assertFailsWith<DomainException> {
            service.createOrganization(command2)
        }
        assertEquals("DUPLICATE_CODE", ex.errorCode)
    }

    @Test
    fun `create organization exceeding max depth throws INVALID_LEVEL`() {
        // Build a chain 6 levels deep, then attempt to create a 7th level child
        var currentParentId: UUID? = null
        for (lvl in 1..6) {
            val code = "DPL${lvl}_${UUID.randomUUID().toString().take(6)}"
            val command = CreateOrganizationCommand(
                tenantId,
                currentParentId,
                code,
                "Depth Level $lvl",
                null,
                null,
            )
            currentParentId = service.createOrganization(command)
        }

        // Attempting to create a 7th level child should fail
        val code = "DEEP7_${UUID.randomUUID().toString().take(6)}"
        val ex = assertFailsWith<DomainException> {
            val command = CreateOrganizationCommand(
                tenantId,
                currentParentId,
                code,
                "Too Deep Org",
                null,
                null,
            )
            service.createOrganization(command)
        }
        assertEquals("INVALID_LEVEL", ex.errorCode)
    }

    @Test
    fun `create organization with inactive parent throws PARENT_INACTIVE`() {
        // Create a parent org and deactivate it
        val parentCode = "PAR_${UUID.randomUUID().toString().take(8)}"
        val parentCommand = CreateOrganizationCommand(
            tenantId,
            null,
            parentCode,
            "Parent Org",
            null,
            null,
        )
        val parentId = service.createOrganization(parentCommand)
        service.deactivateOrganization(parentId, tenantId)

        // Now try to create a child under the inactive parent
        val childCode = "CHD_${UUID.randomUUID().toString().take(8)}"
        val childCommand = CreateOrganizationCommand(
            tenantId,
            parentId,
            childCode,
            "Child Org",
            null,
            null,
        )

        val ex = assertFailsWith<DomainException> {
            service.createOrganization(childCommand)
        }
        assertEquals("PARENT_INACTIVE", ex.errorCode)
    }

    // --- Update organization tests ---

    @Test
    fun `update organization name on active org succeeds`() {
        val code = "UPD_${UUID.randomUUID().toString().take(8)}"
        val command = CreateOrganizationCommand(
            tenantId,
            null,
            code,
            "Original Name",
            null,
            null,
        )
        val orgId = service.createOrganization(command)

        service.updateOrganization(orgId, tenantId, "Updated Name")

        val name = jdbcTemplate.queryForObject(
            "SELECT name FROM organization WHERE id = ?",
            String::class.java,
            orgId,
        )
        assertEquals("Updated Name", name)
    }

    @Test
    fun `update organization on non-existent ID throws ORGANIZATION_NOT_FOUND`() {
        val nonExistentId = UUID.randomUUID()

        val ex = assertFailsWith<DomainException> {
            service.updateOrganization(nonExistentId, tenantId, "New Name")
        }
        assertEquals("ORGANIZATION_NOT_FOUND", ex.errorCode)
    }

    // --- Deactivate organization tests ---

    @Test
    fun `deactivate organization with active children returns warning list`() {
        // Create parent org
        val parentCode = "DPRT_${UUID.randomUUID().toString().take(7)}"
        val parentCommand = CreateOrganizationCommand(
            tenantId,
            null,
            parentCode,
            "Parent For Deactivation",
            null,
            null,
        )
        val parentId = service.createOrganization(parentCommand)

        // Create two active child orgs under the parent
        for (i in 1..2) {
            val childCode = "DCH${i}_${UUID.randomUUID().toString().take(6)}"
            val childCommand = CreateOrganizationCommand(
                tenantId,
                parentId,
                childCode,
                "Child $i",
                null,
                null,
            )
            service.createOrganization(childCommand)
        }

        val warnings = service.deactivateOrganization(parentId, tenantId)

        assertTrue(warnings.isNotEmpty(), "Should have warnings about active children")
        assertTrue(
            warnings[0].contains("2 active child organizations"),
            "Warning should mention 2 active children, got: ${warnings[0]}",
        )
    }

    @Test
    fun `deactivate organization without children returns empty warnings`() {
        val code = "DNOC_${UUID.randomUUID().toString().take(7)}"
        val command = CreateOrganizationCommand(
            tenantId,
            null,
            code,
            "Org Without Children",
            null,
            null,
        )
        val orgId = service.createOrganization(command)

        val warnings = service.deactivateOrganization(orgId, tenantId)

        assertTrue(warnings.isEmpty(), "Should have no warnings for childless org")
    }

    // --- Activate organization tests ---

    @Test
    fun `activate inactive organization succeeds`() {
        val code = "ACT_${UUID.randomUUID().toString().take(8)}"
        val command = CreateOrganizationCommand(
            tenantId,
            null,
            code,
            "Org To Activate",
            null,
            null,
        )
        val orgId = service.createOrganization(command)
        service.deactivateOrganization(orgId, tenantId)

        // Verify it's inactive
        val statusBefore = jdbcTemplate.queryForObject(
            "SELECT status FROM organization WHERE id = ?",
            String::class.java,
            orgId,
        )
        assertEquals("INACTIVE", statusBefore)

        service.activateOrganization(orgId, tenantId)

        val statusAfter = jdbcTemplate.queryForObject(
            "SELECT status FROM organization WHERE id = ?",
            String::class.java,
            orgId,
        )
        assertEquals("ACTIVE", statusAfter)
    }

    // --- List organizations tests ---

    @Test
    fun `list organizations with search filter returns matching results`() {
        val uniqueName = "SearchTarget_${UUID.randomUUID().toString().take(8)}"
        val code = "SRCH_${UUID.randomUUID().toString().take(7)}"
        val command = CreateOrganizationCommand(
            tenantId,
            null,
            code,
            uniqueName,
            null,
            null,
        )
        service.createOrganization(command)

        val page = service.listOrganizations(tenantId, uniqueName, null, null, 0, 20)

        assertTrue(page.content().isNotEmpty(), "Search should return results")
        assertTrue(
            page.content().any { it.name() == uniqueName },
            "Results should contain the created org",
        )
    }

    @Test
    fun `list organizations with status filter returns filtered results`() {
        // Create an active org
        val activeCode = "FLTAC_${UUID.randomUUID().toString().take(6)}"
        val activeCommand = CreateOrganizationCommand(
            tenantId,
            null,
            activeCode,
            "Filter Active Org",
            null,
            null,
        )
        service.createOrganization(activeCommand)

        // Create an inactive org
        val inactiveCode = "FLTIN_${UUID.randomUUID().toString().take(6)}"
        val inactiveCommand = CreateOrganizationCommand(
            tenantId,
            null,
            inactiveCode,
            "Filter Inactive Org",
            null,
            null,
        )
        val inactiveOrgId = service.createOrganization(inactiveCommand)
        service.deactivateOrganization(inactiveOrgId, tenantId)

        // Filter active only
        val activePage = service.listOrganizations(tenantId, null, true, null, 0, 100)
        assertTrue(
            activePage.content().all { it.status() == "ACTIVE" },
            "All results should be ACTIVE",
        )

        // Filter inactive only
        val inactivePage = service.listOrganizations(tenantId, null, false, null, 0, 100)
        assertTrue(
            inactivePage.content().all { it.status() == "INACTIVE" },
            "All results should be INACTIVE",
        )
    }

    @Test
    fun `list organizations with pagination returns correct page`() {
        // Ensure we have enough orgs by creating some
        for (i in 1..5) {
            val code = "PG${i}_${UUID.randomUUID().toString().take(8)}"
            val command = CreateOrganizationCommand(
                tenantId,
                null,
                code,
                "Pagination Org $i",
                null,
                null,
            )
            service.createOrganization(command)
        }

        val firstPage = service.listOrganizations(tenantId, null, null, null, 0, 2)
        val secondPage = service.listOrganizations(tenantId, null, null, null, 1, 2)

        assertEquals(2, firstPage.content().size, "First page should have 2 items")
        assertEquals(0, firstPage.number(), "First page number should be 0")
        assertEquals(2, secondPage.content().size, "Second page should have 2 items")
        assertEquals(1, secondPage.number(), "Second page number should be 1")
        assertTrue(firstPage.totalElements() >= 5, "Should have at least 5 total elements")
    }
}
