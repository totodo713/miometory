package com.worklog.domain

import com.worklog.domain.member.StandardDailyHoursUpdated
import com.worklog.domain.organization.OrganizationStandardDailyHoursAssigned
import com.worklog.domain.tenant.TenantStandardDailyHoursAssigned
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

/**
 * Unit tests for standard daily hours domain events across all aggregates.
 */
class StandardDailyHoursEventsTest {

    // --- StandardDailyHoursUpdated (Member) ---

    @Test
    fun `StandardDailyHoursUpdated create should produce valid event`() {
        val memberId = UUID.randomUUID()
        val hours = BigDecimal("8.00")

        val event = StandardDailyHoursUpdated.create(memberId, hours)

        assertNotNull(event.eventId())
        assertNotNull(event.occurredAt())
        assertEquals(memberId, event.aggregateId())
        assertEquals(hours, event.standardDailyHours())
    }

    @Test
    fun `StandardDailyHoursUpdated eventType should return correct string`() {
        val event = StandardDailyHoursUpdated.create(UUID.randomUUID(), BigDecimal("7.50"))

        assertEquals("StandardDailyHoursUpdated", event.eventType())
    }

    @Test
    fun `StandardDailyHoursUpdated create should accept null hours`() {
        val memberId = UUID.randomUUID()

        val event = StandardDailyHoursUpdated.create(memberId, null)

        assertEquals(memberId, event.aggregateId())
        assertEquals(null, event.standardDailyHours())
    }

    // --- OrganizationStandardDailyHoursAssigned ---

    @Test
    fun `OrganizationStandardDailyHoursAssigned create should produce valid event`() {
        val organizationId = UUID.randomUUID()
        val hours = BigDecimal("7.50")

        val event = OrganizationStandardDailyHoursAssigned.create(organizationId, hours)

        assertNotNull(event.eventId())
        assertNotNull(event.occurredAt())
        assertEquals(organizationId, event.aggregateId())
        assertEquals(hours, event.standardDailyHours())
    }

    @Test
    fun `OrganizationStandardDailyHoursAssigned eventType should return correct string`() {
        val event = OrganizationStandardDailyHoursAssigned.create(UUID.randomUUID(), BigDecimal("8.00"))

        assertEquals("OrganizationStandardDailyHoursAssigned", event.eventType())
    }

    @Test
    fun `OrganizationStandardDailyHoursAssigned create should accept null hours`() {
        val organizationId = UUID.randomUUID()

        val event = OrganizationStandardDailyHoursAssigned.create(organizationId, null)

        assertEquals(organizationId, event.aggregateId())
        assertEquals(null, event.standardDailyHours())
    }

    // --- TenantStandardDailyHoursAssigned ---

    @Test
    fun `TenantStandardDailyHoursAssigned create should produce valid event`() {
        val tenantId = UUID.randomUUID()
        val hours = BigDecimal("8.00")

        val event = TenantStandardDailyHoursAssigned.create(tenantId, hours)

        assertNotNull(event.eventId())
        assertNotNull(event.occurredAt())
        assertEquals(tenantId, event.aggregateId())
        assertEquals(hours, event.standardDailyHours())
    }

    @Test
    fun `TenantStandardDailyHoursAssigned eventType should return correct string`() {
        val event = TenantStandardDailyHoursAssigned.create(UUID.randomUUID(), BigDecimal("8.00"))

        assertEquals("TenantStandardDailyHoursAssigned", event.eventType())
    }

    @Test
    fun `TenantStandardDailyHoursAssigned create should accept null hours`() {
        val tenantId = UUID.randomUUID()

        val event = TenantStandardDailyHoursAssigned.create(tenantId, null)

        assertEquals(tenantId, event.aggregateId())
        assertEquals(null, event.standardDailyHours())
    }
}
