package com.worklog.infrastructure.repository

import com.worklog.IntegrationTestBase
import com.worklog.domain.organization.Organization
import com.worklog.domain.organization.OrganizationId
import com.worklog.domain.shared.Code
import com.worklog.domain.tenant.TenantId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.UUID

@DisplayName("OrganizationRepository -- standardDailyHours persistence")
class OrganizationRepositoryTest : IntegrationTestBase() {

    @Autowired
    private lateinit var repository: OrganizationRepository

    private val tenantId = TenantId.of(UUID.fromString(TEST_TENANT_ID))

    @Test
    @DisplayName("should persist and reload standardDailyHours via event sourcing")
    fun shouldPersistStandardDailyHours() {
        val orgId = OrganizationId.generate()
        val code = Code.of("ORG_${orgId.value.toString().substring(0, 8).replace("-", "")}")
        val org = Organization.create(orgId, tenantId, null, code, "Test Org SDH", 1)

        // Assign standard daily hours
        org.assignStandardDailyHours(BigDecimal("7.50"))

        // Save (persists events + updates projection)
        executeInNewTransaction { repository.save(org) }

        // Reload from event store (tests event deserialization including OrganizationStandardDailyHoursAssigned)
        val reloaded = repository.findById(orgId)
        assertTrue(reloaded.isPresent)
        assertEquals(0, BigDecimal("7.50").compareTo(reloaded.get().standardDailyHours))

        // Verify projection table also has the value
        val projectedHours = baseJdbcTemplate.queryForObject(
            "SELECT standard_daily_hours FROM organization WHERE id = ?",
            BigDecimal::class.java,
            orgId.value,
        )
        assertEquals(0, BigDecimal("7.50").compareTo(projectedHours))
    }

    @Test
    @DisplayName("should persist null standardDailyHours for inheritance")
    fun shouldPersistNullStandardDailyHours() {
        val orgId = OrganizationId.generate()
        val code = Code.of("ORG_${orgId.value.toString().substring(0, 8).replace("-", "")}")
        val org = Organization.create(orgId, tenantId, null, code, "Test Org Null SDH", 1)

        // Do not assign any standard daily hours (remains null)
        executeInNewTransaction { repository.save(org) }

        // Reload from event store
        val reloaded = repository.findById(orgId)
        assertTrue(reloaded.isPresent)
        assertNull(reloaded.get().standardDailyHours)

        // Verify projection also has null
        val projectedHours = baseJdbcTemplate.queryForObject(
            "SELECT standard_daily_hours FROM organization WHERE id = ?",
            BigDecimal::class.java,
            orgId.value,
        )
        assertNull(projectedHours)
    }

    @Test
    @DisplayName("should persist standardDailyHours then clear to null via separate save")
    fun shouldClearStandardDailyHoursToNull() {
        val orgId = OrganizationId.generate()
        val code = Code.of("ORG_${orgId.value.toString().substring(0, 8).replace("-", "")}")
        val org = Organization.create(orgId, tenantId, null, code, "Test Org Clear SDH", 1)
        org.assignStandardDailyHours(BigDecimal("8.00"))

        // Save with value set
        executeInNewTransaction { repository.save(org) }

        // Reload, then clear to null
        val reloaded = repository.findById(orgId).get()
        assertEquals(0, BigDecimal("8.00").compareTo(reloaded.standardDailyHours))

        reloaded.assignStandardDailyHours(null)
        executeInNewTransaction { repository.save(reloaded) }

        // Verify event store round-trip after clearing
        val reloaded2 = repository.findById(orgId).get()
        assertNull(reloaded2.standardDailyHours)

        // Verify projection table is also null
        val projectedHours = baseJdbcTemplate.queryForObject(
            "SELECT standard_daily_hours FROM organization WHERE id = ?",
            BigDecimal::class.java,
            orgId.value,
        )
        assertNull(projectedHours)
    }
}
