package com.worklog.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Integration tests for TenantSettingsController.
 *
 * Tests self-service tenant settings endpoints:
 * - TENANT_ADMIN can view/update own tenant's default patterns
 * - Regular users are denied access
 * - Pattern ownership validation prevents cross-tenant assignment
 */
class TenantSettingsControllerTest : AdminIntegrationTestBase() {

    private lateinit var systemAdminEmail: String
    private lateinit var tenantAdminEmail: String
    private lateinit var regularEmail: String
    private lateinit var tenantId: String
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        systemAdminEmail = "sysadmin-ts-$suffix@test.com"
        tenantAdminEmail = "tadmin-ts-$suffix@test.com"
        regularEmail = "user-ts-$suffix@test.com"

        createUser(systemAdminEmail, SYSTEM_ADMIN_ROLE_ID, "System Admin")
        createUser(tenantAdminEmail, TENANT_ADMIN_ROLE_ID, "Tenant Admin")
        createUser(regularEmail, USER_ROLE_ID, "Regular User")

        // Create tenant via admin API (populates event store + projection)
        val code = "TS${suffix.replace("-", "")}"
        val result = mockMvc.perform(
            post("/api/v1/admin/tenants")
                .with(user(systemAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code","name":"Test Tenant $suffix"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        tenantId = objectMapper.readTree(result.response.contentAsString).get("id").asText()

        // Create member for TENANT_ADMIN linked to the tenant (org nullable since V27)
        baseJdbcTemplate.update(
            """INSERT INTO members (id, tenant_id, organization_id, email, display_name, is_active, version, created_at, updated_at)
               VALUES (?, ?::UUID, NULL, ?, ?, true, 0, NOW(), NOW())
               ON CONFLICT (id) DO NOTHING""",
            UUID.randomUUID(),
            tenantId,
            tenantAdminEmail,
            "Test Tenant Admin",
        )
    }

    @Test
    fun `GET default patterns returns 200 for tenant admin`() {
        mockMvc.perform(
            get("/api/v1/tenant-settings/default-patterns")
                .with(user(tenantAdminEmail)),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `GET default patterns returns 403 for regular user`() {
        mockMvc.perform(
            get("/api/v1/tenant-settings/default-patterns")
                .with(user(regularEmail)),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `PUT default patterns with nulls returns 204 for tenant admin`() {
        mockMvc.perform(
            put("/api/v1/tenant-settings/default-patterns")
                .with(user(tenantAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"defaultFiscalYearPatternId":null,"defaultMonthlyPeriodPatternId":null}"""),
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `PUT default patterns returns 403 for regular user`() {
        mockMvc.perform(
            put("/api/v1/tenant-settings/default-patterns")
                .with(user(regularEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"defaultFiscalYearPatternId":null,"defaultMonthlyPeriodPatternId":null}"""),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `PUT with valid pattern updates default and GET reflects change`() {
        // Create a fiscal year pattern for this tenant
        val patternResult = mockMvc.perform(
            post("/api/v1/tenants/$tenantId/fiscal-year-patterns")
                .with(user(systemAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Japan FY","startMonth":4,"startDay":1}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val patternId = objectMapper.readTree(patternResult.response.contentAsString).get("id").asText()

        // Update default pattern
        mockMvc.perform(
            put("/api/v1/tenant-settings/default-patterns")
                .with(user(tenantAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"defaultFiscalYearPatternId":"$patternId","defaultMonthlyPeriodPatternId":null}"""),
        )
            .andExpect(status().isNoContent)

        // Verify change via GET
        mockMvc.perform(
            get("/api/v1/tenant-settings/default-patterns")
                .with(user(tenantAdminEmail)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.defaultFiscalYearPatternId").value(patternId))
    }

    @Test
    fun `PUT with pattern from other tenant returns 400`() {
        // Create a second tenant
        val code2 = "TS2${UUID.randomUUID().toString().take(4).replace("-", "")}"
        val result2 = mockMvc.perform(
            post("/api/v1/admin/tenants")
                .with(user(systemAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code2","name":"Other Tenant"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val otherTenantId = objectMapper.readTree(result2.response.contentAsString).get("id").asText()

        // Create pattern for the other tenant
        val patternResult = mockMvc.perform(
            post("/api/v1/tenants/$otherTenantId/fiscal-year-patterns")
                .with(user(systemAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Other FY","startMonth":1,"startDay":1}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val otherPatternId = objectMapper.readTree(patternResult.response.contentAsString).get("id").asText()

        // Try to set other tenant's pattern as default → should fail
        mockMvc.perform(
            put("/api/v1/tenant-settings/default-patterns")
                .with(user(tenantAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"defaultFiscalYearPatternId":"$otherPatternId","defaultMonthlyPeriodPatternId":null}"""),
        )
            .andExpect(status().isBadRequest)
    }
}
