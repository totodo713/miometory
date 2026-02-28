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
 * Integration tests for tenant assignment endpoints.
 *
 * Tests:
 * - GET /api/v1/admin/users/search-for-assignment (AdminUserController)
 * - POST /api/v1/admin/members/assign-tenant (AdminMemberController)
 *
 * Both endpoints require the 'member.assign_tenant' permission,
 * granted to SYSTEM_ADMIN and TENANT_ADMIN roles.
 */
class TenantAssignmentControllerTest : AdminIntegrationTestBase() {

    private lateinit var sysAdminEmail: String
    private lateinit var tenantAdminEmail: String
    private lateinit var regularEmail: String
    private lateinit var targetUserId: UUID
    private lateinit var targetEmail: String

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        sysAdminEmail = "sysadmin-ta-$suffix@test.com"
        tenantAdminEmail = "tadmin-ta-$suffix@test.com"
        regularEmail = "user-ta-$suffix@test.com"
        targetEmail = "target-ta-$suffix@test.com"

        // System admin (has member.assign_tenant via V27 migration)
        createUser(sysAdminEmail, SYSTEM_ADMIN_ROLE_ID, "System Admin")
        createMemberForUser(sysAdminEmail)

        // Tenant admin (has member.assign_tenant via V27 migration)
        createUser(tenantAdminEmail, TENANT_ADMIN_ROLE_ID, "Tenant Admin")
        createMemberForUser(tenantAdminEmail)

        // Regular user (no member.assign_tenant permission)
        createUser(regularEmail, USER_ROLE_ID, "Regular User")

        // Target user to search for / assign
        targetUserId = createUser(targetEmail, USER_ROLE_ID, "Target User")
    }

    // ============================================================
    // GET /api/v1/admin/users/search-for-assignment
    // ============================================================

    @Test
    fun `search-for-assignment returns 200 with wrapped results for system admin`() {
        mockMvc.perform(
            get("/api/v1/admin/users/search-for-assignment")
                .with(user(sysAdminEmail))
                .param("email", "target-ta"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.users").isArray)
            .andExpect(jsonPath("$.users[0].userId").isNotEmpty)
            .andExpect(jsonPath("$.users[0].email").isNotEmpty)
            .andExpect(jsonPath("$.users[0].name").isNotEmpty)
            .andExpect(jsonPath("$.users[0].isAlreadyInTenant").isBoolean)
    }

    @Test
    fun `search-for-assignment returns 200 with wrapped results for tenant admin`() {
        mockMvc.perform(
            get("/api/v1/admin/users/search-for-assignment")
                .with(user(tenantAdminEmail))
                .param("email", "target-ta"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.users").isArray)
    }

    @Test
    fun `search-for-assignment returns empty users array for non-matching email`() {
        mockMvc.perform(
            get("/api/v1/admin/users/search-for-assignment")
                .with(user(sysAdminEmail))
                .param("email", "nonexistent-xyz-99999"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.users").isArray)
            .andExpect(jsonPath("$.users").isEmpty)
    }

    @Test
    fun `search-for-assignment returns isAlreadyInTenant true for assigned user`() {
        // Target user already has a member record in the test tenant
        createMemberForUser(targetEmail)

        mockMvc.perform(
            get("/api/v1/admin/users/search-for-assignment")
                .with(user(sysAdminEmail))
                .param("email", targetEmail),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.users[0].isAlreadyInTenant").value(true))
    }

    @Test
    fun `search-for-assignment returns isAlreadyInTenant false for unassigned user`() {
        // Target user has no member record
        mockMvc.perform(
            get("/api/v1/admin/users/search-for-assignment")
                .with(user(sysAdminEmail))
                .param("email", targetEmail),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.users[0].isAlreadyInTenant").value(false))
    }

    @Test
    fun `search-for-assignment returns 403 for user without permission`() {
        mockMvc.perform(
            get("/api/v1/admin/users/search-for-assignment")
                .with(user(regularEmail))
                .param("email", "target"),
        )
            .andExpect(status().isForbidden)
    }

    // ============================================================
    // POST /api/v1/admin/members/assign-tenant
    // ============================================================

    @Test
    fun `assign-tenant returns 201 for system admin`() {
        mockMvc.perform(
            post("/api/v1/admin/members/assign-tenant")
                .with(user(sysAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$targetUserId","displayName":"Assigned User"}"""),
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `assign-tenant returns 201 for tenant admin`() {
        // Create a new target user for this test to avoid conflicts with other tests
        val suffix = UUID.randomUUID().toString().take(8)
        val newTargetEmail = "newtarget-$suffix@test.com"
        val newTargetUserId = createUser(newTargetEmail, USER_ROLE_ID, "New Target")

        mockMvc.perform(
            post("/api/v1/admin/members/assign-tenant")
                .with(user(tenantAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$newTargetUserId","displayName":"Assigned By Tenant Admin"}"""),
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `assign-tenant returns 409 for duplicate assignment`() {
        // First assignment succeeds
        mockMvc.perform(
            post("/api/v1/admin/members/assign-tenant")
                .with(user(sysAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$targetUserId","displayName":"First Assignment"}"""),
        )
            .andExpect(status().isCreated)

        // Second assignment to same tenant returns conflict
        mockMvc.perform(
            post("/api/v1/admin/members/assign-tenant")
                .with(user(sysAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$targetUserId","displayName":"Duplicate"}"""),
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `assign-tenant returns 404 for non-existent user`() {
        val fakeUserId = UUID.randomUUID()
        mockMvc.perform(
            post("/api/v1/admin/members/assign-tenant")
                .with(user(sysAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$fakeUserId","displayName":"Ghost User"}"""),
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `assign-tenant returns 403 for user without permission`() {
        mockMvc.perform(
            post("/api/v1/admin/members/assign-tenant")
                .with(user(regularEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$targetUserId","displayName":"Unauthorized"}"""),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `assign-tenant returns 400 for missing userId`() {
        mockMvc.perform(
            post("/api/v1/admin/members/assign-tenant")
                .with(user(sysAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"displayName":"No User ID"}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `assign-tenant returns 400 for missing displayName`() {
        mockMvc.perform(
            post("/api/v1/admin/members/assign-tenant")
                .with(user(sysAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$targetUserId"}"""),
        )
            .andExpect(status().isBadRequest)
    }
}
