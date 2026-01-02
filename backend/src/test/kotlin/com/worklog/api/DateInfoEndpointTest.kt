package com.worklog.api

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DateInfoEndpointTest : IntegrationTestBase() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    // ========== Basic Calculation Tests ==========

    @Test
    fun `should calculate date info for organization with patterns`() {
        // Given: tenant, patterns, and organization
        val tenantId = createTenantWithProjection()
        val fiscalYearPatternId = createFiscalYearPattern(tenantId, 4, 1) // April 1
        val monthlyPeriodPatternId = createMonthlyPeriodPattern(tenantId, 21) // 21st
        val orgId = createOrganization(tenantId, null, fiscalYearPatternId, monthlyPeriodPatternId)

        // When: request date info for January 25, 2024
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val requestBody = """{"date": "2024-01-25"}"""
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations/$orgId/date-info",
                HttpEntity(requestBody, headers),
                String::class.java,
            )

        // Then: should return 200 with correct calculations
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body!!
        assert(body.contains("\"fiscalYear\":2023")) // Jan 2024 is in FY2023 (Apr 2023 - Mar 2024)
        assert(body.contains("\"fiscalYearStart\":\"2023-04-01\""))
        assert(body.contains("\"fiscalYearEnd\":\"2024-03-31\""))
        assert(body.contains("\"monthlyPeriodStart\":\"2024-01-21\""))
        assert(body.contains("\"monthlyPeriodEnd\":\"2024-02-20\""))
    }

    @Test
    fun `should calculate date info for date in different fiscal year`() {
        // Given: tenant with patterns and organization
        val tenantId = createTenantWithProjection()
        val fiscalYearPatternId = createFiscalYearPattern(tenantId, 7, 1) // July 1
        val monthlyPeriodPatternId = createMonthlyPeriodPattern(tenantId, 1) // 1st
        val orgId = createOrganization(tenantId, null, fiscalYearPatternId, monthlyPeriodPatternId)

        // When: request date info for June 15, 2024 (end of FY2023)
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val requestBody = """{"date": "2024-06-15"}"""
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations/$orgId/date-info",
                HttpEntity(requestBody, headers),
                String::class.java,
            )

        // Then: should be in FY2023 (Jul 2023 - Jun 2024)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body!!
        assert(body.contains("\"fiscalYear\":2023"))
        assert(body.contains("\"fiscalYearStart\":\"2023-07-01\""))
        assert(body.contains("\"fiscalYearEnd\":\"2024-06-30\""))
    }

    @Test
    fun `should calculate date info for year boundary dates`() {
        // Given: tenant with patterns and organization
        val tenantId = createTenantWithProjection()
        val fiscalYearPatternId = createFiscalYearPattern(tenantId, 1, 1) // Jan 1 (calendar year)
        val monthlyPeriodPatternId = createMonthlyPeriodPattern(tenantId, 1) // 1st
        val orgId = createOrganization(tenantId, null, fiscalYearPatternId, monthlyPeriodPatternId)

        // When: request date info for December 31, 2023
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val requestBody = """{"date": "2023-12-31"}"""
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations/$orgId/date-info",
                HttpEntity(requestBody, headers),
                String::class.java,
            )

        // Then: should be in FY2023
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body!!
        assert(body.contains("\"fiscalYear\":2023"))
        assert(body.contains("\"fiscalYearStart\":\"2023-01-01\""))
        assert(body.contains("\"fiscalYearEnd\":\"2023-12-31\""))
    }

    // ========== Pattern Inheritance Tests ==========

    @Test
    fun `should inherit fiscal year pattern from parent organization`() {
        // Given: parent org with fiscal year pattern, child org with only monthly period pattern
        val tenantId = createTenantWithProjection()
        val fiscalYearPatternId = createFiscalYearPattern(tenantId, 4, 1)
        val monthlyPeriodPatternId = createMonthlyPeriodPattern(tenantId, 21)
        val childMonthlyPatternId = createMonthlyPeriodPattern(tenantId, 15)

        val parentOrgId = createOrganization(tenantId, null, fiscalYearPatternId, monthlyPeriodPatternId)
        val childOrgId = createOrganization(tenantId, parentOrgId, null, childMonthlyPatternId, level = 2)

        // When: request date info for child org
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val requestBody = """{"date": "2024-01-25"}"""
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations/$childOrgId/date-info",
                HttpEntity(requestBody, headers),
                String::class.java,
            )

        // Then: should inherit fiscal year from parent, use own monthly period
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body!!
        assert(body.contains("\"fiscalYear\":2023")) // Inherited from parent (Apr 1 start)
        assert(body.contains("\"monthlyPeriodStart\":\"2024-01-15\"")) // Own pattern (15th start)
    }

    @Test
    fun `should inherit monthly period pattern from grandparent`() {
        // Given: grandparent with both patterns, parent with fiscal year only, child with neither
        val tenantId = createTenantWithProjection()
        val fiscalYearPatternId = createFiscalYearPattern(tenantId, 4, 1)
        val monthlyPeriodPatternId = createMonthlyPeriodPattern(tenantId, 21)
        val parentFiscalPatternId = createFiscalYearPattern(tenantId, 7, 1)

        val grandparentOrgId = createOrganization(tenantId, null, fiscalYearPatternId, monthlyPeriodPatternId)
        val parentOrgId = createOrganization(tenantId, grandparentOrgId, parentFiscalPatternId, null, level = 2)
        val childOrgId = createOrganization(tenantId, parentOrgId, null, null, level = 3)

        // When: request date info for child org
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val requestBody = """{"date": "2024-01-25"}"""
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations/$childOrgId/date-info",
                HttpEntity(requestBody, headers),
                String::class.java,
            )

        // Then: should inherit fiscal year from parent, monthly period from grandparent
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body!!
        assert(body.contains("\"fiscalYear\":2023")) // From parent (Jul 1 start, so Jan is in FY2023)
        assert(body.contains("\"monthlyPeriodStart\":\"2024-01-21\"")) // From grandparent (21st start)
    }

    @Test
    fun `should use child's patterns when explicitly set`() {
        // Given: parent and child both with patterns
        val tenantId = createTenantWithProjection()
        val parentFiscalPatternId = createFiscalYearPattern(tenantId, 4, 1)
        val parentMonthlyPatternId = createMonthlyPeriodPattern(tenantId, 21)
        val childFiscalPatternId = createFiscalYearPattern(tenantId, 7, 1)
        val childMonthlyPatternId = createMonthlyPeriodPattern(tenantId, 15)

        val parentOrgId = createOrganization(tenantId, null, parentFiscalPatternId, parentMonthlyPatternId)
        val childOrgId = createOrganization(tenantId, parentOrgId, childFiscalPatternId, childMonthlyPatternId, level = 2)

        // When: request date info for child org
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val requestBody = """{"date": "2024-01-25"}"""
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations/$childOrgId/date-info",
                HttpEntity(requestBody, headers),
                String::class.java,
            )

        // Then: should use child's own patterns, not parent's
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body!!
        assert(body.contains("\"fiscalYear\":2023")) // Child's pattern (Jul 1 start)
        assert(body.contains("\"monthlyPeriodStart\":\"2024-01-15\"")) // Child's pattern (15th start)
    }

    // ========== Error Handling Tests ==========

    @Test
    fun `should return 404 when organization not found`() {
        // Given: tenant exists but organization doesn't
        val tenantId = createTenantWithProjection()
        val nonExistentOrgId = UUID.randomUUID()

        // When: request date info for non-existent org
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val requestBody = """{"date": "2024-01-25"}"""
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations/$nonExistentOrgId/date-info",
                HttpEntity(requestBody, headers),
                String::class.java,
            )

        // Then: should return 404
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `should return 400 when date is missing`() {
        // Given: tenant and organization exist
        val tenantId = createTenantWithProjection()
        val fiscalYearPatternId = createFiscalYearPattern(tenantId, 4, 1)
        val monthlyPeriodPatternId = createMonthlyPeriodPattern(tenantId, 21)
        val orgId = createOrganization(tenantId, null, fiscalYearPatternId, monthlyPeriodPatternId)

        // When: request date info without date
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val requestBody = """{}"""
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations/$orgId/date-info",
                HttpEntity(requestBody, headers),
                String::class.java,
            )

        // Then: should return 400
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `should return error when no patterns found in hierarchy`() {
        // Given: organization with no patterns (root org, so no inheritance possible)
        val tenantId = createTenantWithProjection()
        val orgId = createOrganization(tenantId, null, null, null)

        // When: request date info
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val requestBody = """{"date": "2024-01-25"}"""
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations/$orgId/date-info",
                HttpEntity(requestBody, headers),
                String::class.java,
            )

        // Then: should return error (500 or 400)
        assert(response.statusCode.is4xxClientError || response.statusCode.is5xxServerError)
    }

    // ========== Edge Case Tests ==========

    @Test
    fun `should handle leap year dates correctly`() {
        // Given: tenant with patterns and organization
        val tenantId = createTenantWithProjection()
        val fiscalYearPatternId = createFiscalYearPattern(tenantId, 3, 1) // March 1
        val monthlyPeriodPatternId = createMonthlyPeriodPattern(tenantId, 1)
        val orgId = createOrganization(tenantId, null, fiscalYearPatternId, monthlyPeriodPatternId)

        // When: request date info for Feb 29, 2024 (leap year)
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val requestBody = """{"date": "2024-02-29"}"""
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations/$orgId/date-info",
                HttpEntity(requestBody, headers),
                String::class.java,
            )

        // Then: should handle leap year correctly
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body!!
        assert(body.contains("\"fiscalYear\":2023")) // Mar 2023 - Feb 2024
        assert(body.contains("\"date\":\"2024-02-29\""))
    }

    @Test
    fun `should handle fiscal year spanning calendar year boundary`() {
        // Given: fiscal year starts Oct 1 (spans two calendar years)
        val tenantId = createTenantWithProjection()
        val fiscalYearPatternId = createFiscalYearPattern(tenantId, 10, 1) // Oct 1
        val monthlyPeriodPatternId = createMonthlyPeriodPattern(tenantId, 1)
        val orgId = createOrganization(tenantId, null, fiscalYearPatternId, monthlyPeriodPatternId)

        // When: request date info for December 15, 2024
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val requestBody = """{"date": "2024-12-15"}"""
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations/$orgId/date-info",
                HttpEntity(requestBody, headers),
                String::class.java,
            )

        // Then: should be in FY2024 (Oct 2024 - Sep 2025)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body!!
        assert(body.contains("\"fiscalYear\":2024"))
        assert(body.contains("\"fiscalYearStart\":\"2024-10-01\""))
        assert(body.contains("\"fiscalYearEnd\":\"2025-09-30\""))
    }

    // ========== Helper Methods ==========

    private fun createTenantWithProjection(): UUID {
        val shortCode = "T${System.nanoTime()}".take(32)

        // Create tenant using REST API to trigger event sourcing
        val requestBody =
            mapOf(
                "code" to shortCode,
                "name" to "Test Tenant",
            )

        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants",
                requestBody,
                Map::class.java,
            )

        if (response.statusCode != HttpStatus.CREATED) {
            throw IllegalStateException("Failed to create tenant: ${response.statusCode}")
        }

        val tenantIdStr = (response.body as Map<*, *>)["id"] as String
        val tenantId = UUID.fromString(tenantIdStr)

        // Workaround: Also create tenant in projection table for FK constraints
        // (until proper event projections are implemented)
        jdbcTemplate.update(
            """
            INSERT INTO tenant (id, code, name, status, created_at) 
            VALUES (?, ?, ?, 'ACTIVE', NOW()) 
            ON CONFLICT (id) DO NOTHING
            """,
            tenantId,
            shortCode,
            "Test Tenant",
        )

        return tenantId
    }

    private fun createFiscalYearPattern(
        tenantId: UUID,
        startMonth: Int,
        startDay: Int,
    ): UUID {
        val patternId = UUID.randomUUID()
        val patternName = "FY-$startMonth-$startDay-${System.nanoTime()}"
        jdbcTemplate.update(
            """
            INSERT INTO fiscal_year_pattern (id, tenant_id, name, start_month, start_day, created_at) 
            VALUES (?, ?, ?, ?, ?, NOW())
            """,
            patternId,
            tenantId,
            patternName,
            startMonth,
            startDay,
        )
        return patternId
    }

    private fun createMonthlyPeriodPattern(
        tenantId: UUID,
        startDay: Int,
    ): UUID {
        val patternId = UUID.randomUUID()
        val patternName = "MP-$startDay-${System.nanoTime()}"
        jdbcTemplate.update(
            """
            INSERT INTO monthly_period_pattern (id, tenant_id, name, start_day, created_at) 
            VALUES (?, ?, ?, ?, NOW())
            """,
            patternId,
            tenantId,
            patternName,
            startDay,
        )
        return patternId
    }

    private fun createOrganization(
        tenantId: UUID,
        parentId: UUID?,
        fiscalYearPatternId: UUID?,
        monthlyPeriodPatternId: UUID?,
        level: Int = if (parentId == null) 1 else 2,
    ): UUID {
        // Create organization using REST API to trigger event sourcing
        val requestBody =
            mapOf(
                "code" to "ORG_${System.nanoTime()}",
                "name" to "Test Organization",
                "level" to level,
                "parentId" to parentId?.toString(),
                "fiscalYearPatternId" to fiscalYearPatternId?.toString(),
                "monthlyPeriodPatternId" to monthlyPeriodPatternId?.toString(),
            )

        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations",
                requestBody,
                Map::class.java,
            )

        if (response.statusCode != HttpStatus.CREATED) {
            throw IllegalStateException("Failed to create organization: ${response.statusCode}")
        }

        val orgIdStr = (response.body as Map<*, *>)["id"] as String
        return UUID.fromString(orgIdStr)
    }
}
