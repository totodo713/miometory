package com.worklog.infrastructure.repository

import com.worklog.IntegrationTestBase
import com.worklog.domain.tenant.Tenant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.UUID

@DisplayName("TenantRepository -- standardDailyHours persistence")
class TenantRepositoryTest : IntegrationTestBase() {

    @Autowired
    private lateinit var repository: TenantRepository

    @Test
    @DisplayName("should persist and reload standardDailyHours via event sourcing")
    fun shouldPersistStandardDailyHours() {
        val code = "T_${UUID.randomUUID().toString().substring(0, 8).replace("-", "")}"
        val tenant = Tenant.create(code, "Test Tenant SDH")

        // Tenant is ACTIVE after create, assign standard daily hours
        tenant.assignStandardDailyHours(BigDecimal("7.00"))

        // Save (persists events + updates projection)
        executeInNewTransaction { repository.save(tenant) }

        // Reload from event store (tests TenantStandardDailyHoursAssigned deserialization)
        val reloaded = repository.findById(tenant.id)
        assertTrue(reloaded.isPresent)
        assertEquals(0, BigDecimal("7.00").compareTo(reloaded.get().standardDailyHours))

        // Verify projection table
        val projectedHours = baseJdbcTemplate.queryForObject(
            "SELECT standard_daily_hours FROM tenant WHERE id = ?",
            BigDecimal::class.java,
            tenant.id.value,
        )
        assertEquals(0, BigDecimal("7.00").compareTo(projectedHours))
    }

    @Test
    @DisplayName("should persist null standardDailyHours by default")
    fun shouldPersistNullStandardDailyHours() {
        val code = "T_${UUID.randomUUID().toString().substring(0, 8).replace("-", "")}"
        val tenant = Tenant.create(code, "Test Tenant Null SDH")

        // Do not assign any standard daily hours (remains null)
        executeInNewTransaction { repository.save(tenant) }

        val reloaded = repository.findById(tenant.id)
        assertTrue(reloaded.isPresent)
        assertNull(reloaded.get().standardDailyHours)
    }

    @Test
    @DisplayName("should persist standardDailyHours then clear to null via separate save")
    fun shouldClearStandardDailyHoursToNull() {
        val code = "T_${UUID.randomUUID().toString().substring(0, 8).replace("-", "")}"
        val tenant = Tenant.create(code, "Test Tenant Clear SDH")
        tenant.assignStandardDailyHours(BigDecimal("8.00"))

        // Save with value set
        executeInNewTransaction { repository.save(tenant) }

        // Reload, then clear to null
        val reloaded = repository.findById(tenant.id).get()
        assertEquals(0, BigDecimal("8.00").compareTo(reloaded.standardDailyHours))

        reloaded.assignStandardDailyHours(null)
        executeInNewTransaction { repository.save(reloaded) }

        // Verify event store round-trip after clearing
        val reloaded2 = repository.findById(tenant.id).get()
        assertNull(reloaded2.standardDailyHours)

        // Verify projection table is also null
        val projectedHours = baseJdbcTemplate.queryForObject(
            "SELECT standard_daily_hours FROM tenant WHERE id = ?",
            BigDecimal::class.java,
            tenant.id.value,
        )
        assertNull(projectedHours)
    }
}
