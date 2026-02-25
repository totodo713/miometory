package com.worklog.api

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Cross-role permission boundary tests.
 *
 * Verifies that SYSTEM_ADMIN cannot access tenant-scoped resources
 * (members, projects) but can access system-level resources (tenants, users).
 */
class AdminPermissionBoundaryTest : AdminIntegrationTestBase() {

    private lateinit var sysAdminEmail: String

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        sysAdminEmail = "sysadmin-boundary-$suffix@test.com"
        createUser(sysAdminEmail, SYSTEM_ADMIN_ROLE_ID, "Boundary Test SysAdmin")
    }

    @Test
    fun `SYSTEM_ADMIN cannot access members endpoint`() {
        mockMvc.perform(get("/api/v1/admin/members").with(user(sysAdminEmail)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `SYSTEM_ADMIN cannot access projects endpoint`() {
        mockMvc.perform(get("/api/v1/admin/projects").with(user(sysAdminEmail)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `SYSTEM_ADMIN can access tenants endpoint`() {
        mockMvc.perform(get("/api/v1/admin/tenants").with(user(sysAdminEmail)))
            .andExpect(status().isOk)
    }

    @Test
    fun `SYSTEM_ADMIN can access users endpoint`() {
        mockMvc.perform(get("/api/v1/admin/users").with(user(sysAdminEmail)))
            .andExpect(status().isOk)
    }
}
