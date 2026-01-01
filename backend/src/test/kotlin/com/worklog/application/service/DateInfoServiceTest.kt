package com.worklog.application.service

import com.worklog.IntegrationTestBase
import com.worklog.domain.fiscalyear.FiscalYearPattern
import com.worklog.domain.monthlyperiod.MonthlyPeriodPattern
import com.worklog.domain.organization.Organization
import com.worklog.domain.organization.OrganizationId
import com.worklog.domain.shared.Code
import com.worklog.domain.tenant.Tenant
import com.worklog.domain.tenant.TenantId
import com.worklog.infrastructure.repository.OrganizationRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Integration tests for DateInfoService.
 * 
 * Tests the calculation of fiscal year and monthly period information
 * for organizations, including pattern inheritance from parent organizations.
 * 
 * Success Criteria (SC-002): 20+ test cases covering:
 * - Basic fiscal year calculation
 * - Basic monthly period calculation
 * - Boundary conditions (year-end, fiscal year transitions)
 * - Pattern inheritance from parent organizations
 * - Root organization pattern requirement
 */
class DateInfoServiceTest : IntegrationTestBase() {

    @Autowired
    private lateinit var dateInfoService: DateInfoService

    @Autowired
    private lateinit var organizationRepository: OrganizationRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `getDateInfo should calculate correct fiscal year and monthly period for root organization`() {
        // Setup
        val tenantId = createTenant()
        val fyPatternId = createFiscalYearPattern(tenantId, 4, 1) // April 1 start
        val mpPatternId = createMonthlyPeriodPattern(tenantId, 21) // 21st cutoff

        val org = createOrganization(tenantId, null, "ROOT", fyPatternId, mpPatternId)

        // Test date: January 25, 2024
        val testDate = LocalDate.of(2024, 1, 25)
        val dateInfo = dateInfoService.getDateInfo(org.id.value(), testDate)

        // Assert
        assertNotNull(dateInfo)
        assertEquals(testDate, dateInfo.date)
        
        // FY 2023 (April 1, 2023 - March 31, 2024)
        assertEquals(2023, dateInfo.fiscalYear)
        assertEquals(LocalDate.of(2023, 4, 1), dateInfo.fiscalYearStart)
        assertEquals(LocalDate.of(2024, 3, 31), dateInfo.fiscalYearEnd)
        
        // Period: Jan 21 - Feb 20
        assertEquals(LocalDate.of(2024, 1, 21), dateInfo.monthlyPeriodStart)
        assertEquals(LocalDate.of(2024, 2, 20), dateInfo.monthlyPeriodEnd)
        
        assertEquals(fyPatternId, dateInfo.fiscalYearPatternId)
        assertEquals(mpPatternId, dateInfo.monthlyPeriodPatternId)
    }

    @Test
    fun `getDateInfo should calculate correct values on fiscal year boundary`() {
        val tenantId = createTenant()
        val fyPatternId = createFiscalYearPattern(tenantId, 4, 1)
        val mpPatternId = createMonthlyPeriodPattern(tenantId, 1)

        val org = createOrganization(tenantId, null, "ROOT", fyPatternId, mpPatternId)

        // Test on fiscal year start: April 1, 2024
        val dateInfo = dateInfoService.getDateInfo(org.id.value(), LocalDate.of(2024, 4, 1))
        assertEquals(2024, dateInfo.fiscalYear)
        assertEquals(LocalDate.of(2024, 4, 1), dateInfo.fiscalYearStart)

        // Test on fiscal year end: March 31, 2024
        val dateInfo2 = dateInfoService.getDateInfo(org.id.value(), LocalDate.of(2024, 3, 31))
        assertEquals(2023, dateInfo2.fiscalYear)
        assertEquals(LocalDate.of(2023, 4, 1), dateInfo2.fiscalYearStart)
    }

    @Test
    fun `getDateInfo should calculate correct values on monthly period boundary`() {
        val tenantId = createTenant()
        val fyPatternId = createFiscalYearPattern(tenantId, 1, 1)
        val mpPatternId = createMonthlyPeriodPattern(tenantId, 21)

        val org = createOrganization(tenantId, null, "ROOT", fyPatternId, mpPatternId)

        // On cutoff day: January 21
        val dateInfo1 = dateInfoService.getDateInfo(org.id.value(), LocalDate.of(2024, 1, 21))
        assertEquals(LocalDate.of(2024, 1, 21), dateInfo1.monthlyPeriodStart)
        assertEquals(LocalDate.of(2024, 2, 20), dateInfo1.monthlyPeriodEnd)

        // Before cutoff: January 20
        val dateInfo2 = dateInfoService.getDateInfo(org.id.value(), LocalDate.of(2024, 1, 20))
        assertEquals(LocalDate.of(2023, 12, 21), dateInfo2.monthlyPeriodStart)
        assertEquals(LocalDate.of(2024, 1, 20), dateInfo2.monthlyPeriodEnd)
    }

    @Test
    fun `getDateInfo should handle leap year correctly`() {
        val tenantId = createTenant()
        val fyPatternId = createFiscalYearPattern(tenantId, 4, 1)
        val mpPatternId = createMonthlyPeriodPattern(tenantId, 1)

        val org = createOrganization(tenantId, null, "ROOT", fyPatternId, mpPatternId)

        // Feb 29, 2024 (leap year) should work
        val dateInfo = dateInfoService.getDateInfo(org.id.value(), LocalDate.of(2024, 2, 29))
        assertEquals(LocalDate.of(2024, 2, 1), dateInfo.monthlyPeriodStart)
        assertEquals(LocalDate.of(2024, 2, 29), dateInfo.monthlyPeriodEnd)
    }

    @Test
    fun `getDateInfo should handle year boundary correctly`() {
        val tenantId = createTenant()
        val fyPatternId = createFiscalYearPattern(tenantId, 4, 1)
        val mpPatternId = createMonthlyPeriodPattern(tenantId, 21)

        val org = createOrganization(tenantId, null, "ROOT", fyPatternId, mpPatternId)

        // December 31 -> period crosses year boundary
        val dateInfo = dateInfoService.getDateInfo(org.id.value(), LocalDate.of(2023, 12, 31))
        assertEquals(2023, dateInfo.fiscalYear)
        assertEquals(LocalDate.of(2023, 12, 21), dateInfo.monthlyPeriodStart)
        assertEquals(LocalDate.of(2024, 1, 20), dateInfo.monthlyPeriodEnd)
    }

    @Test
    fun `getDateInfo should inherit fiscal year pattern from parent`() {
        val tenantId = createTenant()
        val fyPatternId = createFiscalYearPattern(tenantId, 4, 1)
        val mpPatternId = createMonthlyPeriodPattern(tenantId, 21)

        // Root with patterns
        val root = createOrganization(tenantId, null, "ROOT", fyPatternId, mpPatternId)

        // Child without fiscal year pattern
        val child = createOrganization(tenantId, root.id, "CHILD", null, mpPatternId)

        val dateInfo = dateInfoService.getDateInfo(child.id.value(), LocalDate.of(2024, 1, 25))

        // Should use parent's fiscal year pattern
        assertEquals(2023, dateInfo.fiscalYear)
        assertEquals(fyPatternId, dateInfo.fiscalYearPatternId)
    }

    @Test
    fun `getDateInfo should inherit monthly period pattern from parent`() {
        val tenantId = createTenant()
        val fyPatternId = createFiscalYearPattern(tenantId, 4, 1)
        val mpPatternId = createMonthlyPeriodPattern(tenantId, 21)

        // Root with patterns
        val root = createOrganization(tenantId, null, "ROOT", fyPatternId, mpPatternId)

        // Child without monthly period pattern
        val child = createOrganization(tenantId, root.id, "CHILD", fyPatternId, null)

        val dateInfo = dateInfoService.getDateInfo(child.id.value(), LocalDate.of(2024, 1, 25))

        // Should use parent's monthly period pattern
        assertEquals(LocalDate.of(2024, 1, 21), dateInfo.monthlyPeriodStart)
        assertEquals(mpPatternId, dateInfo.monthlyPeriodPatternId)
    }

    @Test
    fun `getDateInfo should use child's own pattern when set, not parent's`() {
        val tenantId = createTenant()
        val rootFyPatternId = createFiscalYearPattern(tenantId, 4, 1) // April start
        val childFyPatternId = createFiscalYearPattern(tenantId, 1, 1) // January start
        val mpPatternId = createMonthlyPeriodPattern(tenantId, 21)

        val root = createOrganization(tenantId, null, "ROOT", rootFyPatternId, mpPatternId)
        val child = createOrganization(tenantId, root.id, "CHILD", childFyPatternId, mpPatternId)

        // Jan 25 would be FY 2023 with April start, FY 2024 with January start
        val dateInfo = dateInfoService.getDateInfo(child.id.value(), LocalDate.of(2024, 1, 25))

        assertEquals(2024, dateInfo.fiscalYear) // Child's pattern
        assertEquals(LocalDate.of(2024, 1, 1), dateInfo.fiscalYearStart)
        assertEquals(childFyPatternId, dateInfo.fiscalYearPatternId)
    }

    @Test
    fun `getDateInfo should inherit from grandparent when parent has no pattern`() {
        val tenantId = createTenant()
        val fyPatternId = createFiscalYearPattern(tenantId, 4, 1)
        val mpPatternId = createMonthlyPeriodPattern(tenantId, 21)

        val root = createOrganization(tenantId, null, "ROOT", fyPatternId, mpPatternId)
        val middle = createOrganization(tenantId, root.id, "MIDDLE", null, null)
        val child = createOrganization(tenantId, middle.id, "CHILD", null, null)

        val dateInfo = dateInfoService.getDateInfo(child.id.value(), LocalDate.of(2024, 1, 25))

        // Should use grandparent's patterns
        assertEquals(2023, dateInfo.fiscalYear)
        assertEquals(fyPatternId, dateInfo.fiscalYearPatternId)
        assertEquals(mpPatternId, dateInfo.monthlyPeriodPatternId)
    }

    @Test
    fun `getDateInfo should fail when no fiscal year pattern in hierarchy`() {
        val tenantId = createTenant()
        val mpPatternId = createMonthlyPeriodPattern(tenantId, 21)

        // Root without fiscal year pattern
        val org = createOrganization(tenantId, null, "ROOT", null, mpPatternId)

        assertFailsWith<IllegalStateException> {
            dateInfoService.getDateInfo(org.id.value(), LocalDate.of(2024, 1, 25))
        }
    }

    @Test
    fun `getDateInfo should fail when no monthly period pattern in hierarchy`() {
        val tenantId = createTenant()
        val fyPatternId = createFiscalYearPattern(tenantId, 4, 1)

        // Root without monthly period pattern
        val org = createOrganization(tenantId, null, "ROOT", fyPatternId, null)

        assertFailsWith<IllegalStateException> {
            dateInfoService.getDateInfo(org.id.value(), LocalDate.of(2024, 1, 25))
        }
    }

    @Test
    fun `getDateInfo should fail when organization not found`() {
        val nonExistentOrgId = UUID.randomUUID()

        assertFailsWith<IllegalArgumentException> {
            dateInfoService.getDateInfo(nonExistentOrgId, LocalDate.of(2024, 1, 25))
        }
    }

    @Test
    fun `getDateInfo should handle different fiscal years across hierarchy`() {
        val tenantId = createTenant()
        val aprilFyId = createFiscalYearPattern(tenantId, 4, 1)
        val octoberFyId = createFiscalYearPattern(tenantId, 10, 1)
        val mpPatternId = createMonthlyPeriodPattern(tenantId, 1)

        val root = createOrganization(tenantId, null, "ROOT", aprilFyId, mpPatternId)
        val child = createOrganization(tenantId, root.id, "CHILD", octoberFyId, mpPatternId)

        // Nov 15, 2023: April start -> FY 2023, October start -> FY 2023
        val rootDateInfo = dateInfoService.getDateInfo(root.id.value(), LocalDate.of(2023, 11, 15))
        assertEquals(2023, rootDateInfo.fiscalYear)
        assertEquals(LocalDate.of(2023, 4, 1), rootDateInfo.fiscalYearStart)

        val childDateInfo = dateInfoService.getDateInfo(child.id.value(), LocalDate.of(2023, 11, 15))
        assertEquals(2023, childDateInfo.fiscalYear)
        assertEquals(LocalDate.of(2023, 10, 1), childDateInfo.fiscalYearStart)
    }

    // Helper methods

    private fun createTenant(): TenantId {
        val tenant = Tenant.create("T${UUID.randomUUID().toString().take(8)}", "Test Tenant")
        jdbcTemplate.update(
            "INSERT INTO tenant (id, code, name, status) VALUES (?, ?, ?, ?)",
            tenant.id.value(),
            tenant.code.value(),
            tenant.name,
            tenant.status.name
        )
        return tenant.id
    }

    private fun createFiscalYearPattern(tenantId: TenantId, startMonth: Int, startDay: Int): UUID {
        val pattern = FiscalYearPattern.create(tenantId.value(), "FY Pattern", startMonth, startDay)
        jdbcTemplate.update(
            "INSERT INTO fiscal_year_pattern (id, tenant_id, name, start_month, start_day, created_at) " +
            "VALUES (?, ?, ?, ?, ?, NOW())",
            pattern.id.value(), tenantId.value(), pattern.name, pattern.startMonth, pattern.startDay
        )
        return pattern.id.value()
    }

    private fun createMonthlyPeriodPattern(tenantId: TenantId, startDay: Int): UUID {
        val pattern = MonthlyPeriodPattern.create(tenantId.value(), "MP Pattern", startDay)
        jdbcTemplate.update(
            "INSERT INTO monthly_period_pattern (id, tenant_id, name, start_day, created_at) " +
            "VALUES (?, ?, ?, ?, NOW())",
            pattern.id.value(), tenantId.value(), pattern.name, pattern.startDay
        )
        return pattern.id.value()
    }

    private fun createOrganization(
        tenantId: TenantId,
        parentId: OrganizationId?,
        code: String,
        fiscalYearPatternId: UUID?,
        monthlyPeriodPatternId: UUID?
    ): Organization {
        val level = if (parentId == null) 1 else 2
        val org = Organization.create(
            OrganizationId.generate(),
            tenantId,
            parentId,
            Code.of("${code}_${UUID.randomUUID().toString().take(8)}"),
            "Org $code",
            level
        )
        org.assignPatterns(fiscalYearPatternId, monthlyPeriodPatternId)
        organizationRepository.save(org)
        return org
    }
}
