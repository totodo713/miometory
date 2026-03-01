package com.worklog.api

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Authorization tests for MonthlyPeriodPatternController.
 *
 * Verifies @PreAuthorize + TenantAccessValidator enforcement:
 * - SYSTEM_ADMIN can access any tenant's patterns
 * - TENANT_ADMIN can access own tenant's patterns only
 * - Regular users are denied access
 */
class MonthlyPeriodPatternControllerAuthTest : AdminIntegrationTestBase() {

    private lateinit var systemAdminEmail: String
    private lateinit var tenantAdminEmail: String
    private lateinit var regularEmail: String
    private val otherTenantId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        systemAdminEmail = "sysadmin-mp-$suffix@test.com"
        tenantAdminEmail = "tadmin-mp-$suffix@test.com"
        regularEmail = "user-mp-$suffix@test.com"

        createUser(systemAdminEmail, SYSTEM_ADMIN_ROLE_ID, "System Admin")
        createUser(tenantAdminEmail, TENANT_ADMIN_ROLE_ID, "Tenant Admin")
        createUser(regularEmail, USER_ROLE_ID, "Regular User")

        // Link TENANT_ADMIN to ADM_TEST_TENANT_ID via member record
        createMemberForUser(tenantAdminEmail)
    }

    @Test
    fun `GET list returns 200 for system admin`() {
        mockMvc.perform(
            get("/api/v1/tenants/$ADM_TEST_TENANT_ID/monthly-period-patterns")
                .with(user(systemAdminEmail)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    fun `GET list returns 200 for tenant admin accessing own tenant`() {
        mockMvc.perform(
            get("/api/v1/tenants/$ADM_TEST_TENANT_ID/monthly-period-patterns")
                .with(user(tenantAdminEmail)),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `GET list returns 403 for tenant admin accessing other tenant`() {
        mockMvc.perform(
            get("/api/v1/tenants/$otherTenantId/monthly-period-patterns")
                .with(user(tenantAdminEmail)),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET list returns 403 for regular user`() {
        mockMvc.perform(
            get("/api/v1/tenants/$ADM_TEST_TENANT_ID/monthly-period-patterns")
                .with(user(regularEmail)),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `POST returns 201 for tenant admin creating in own tenant`() {
        mockMvc.perform(
            post("/api/v1/tenants/$ADM_TEST_TENANT_ID/monthly-period-patterns")
                .with(user(tenantAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Auth Test MP ${UUID.randomUUID().toString().take(4)}","startDay":1}"""),
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `POST returns 403 for tenant admin creating in other tenant`() {
        mockMvc.perform(
            post("/api/v1/tenants/$otherTenantId/monthly-period-patterns")
                .with(user(tenantAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Other Tenant MP","startDay":15}"""),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `POST returns 403 for regular user`() {
        mockMvc.perform(
            post("/api/v1/tenants/$ADM_TEST_TENANT_ID/monthly-period-patterns")
                .with(user(regularEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Unauthorized MP","startDay":1}"""),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `SYSTEM_ADMIN can access any tenant patterns`() {
        mockMvc.perform(
            get("/api/v1/tenants/$otherTenantId/monthly-period-patterns")
                .with(user(systemAdminEmail)),
        )
            .andExpect(status().isOk)
    }
}
