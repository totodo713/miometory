package com.worklog.api

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Integration tests for AdminUserController (T060a).
 *
 * Tests list with filters, role change, lock/unlock, password reset,
 * and @PreAuthorize enforcement with user.* permissions.
 */
class AdminUserControllerTest : AdminIntegrationTestBase() {

    private lateinit var adminEmail: String
    private lateinit var regularEmail: String
    private lateinit var targetUserId: UUID

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        adminEmail = "sysadmin-$suffix@test.com"
        regularEmail = "user-$suffix@test.com"
        val targetEmail = "target-$suffix@test.com"

        createUser(adminEmail, SYSTEM_ADMIN_ROLE_ID, "System Admin")
        createUser(regularEmail, USER_ROLE_ID, "Regular User")
        targetUserId = createUser(targetEmail, USER_ROLE_ID, "Target User")
    }

    @Test
    fun `list users returns 200 for system admin`() {
        mockMvc.perform(get("/api/v1/admin/users").with(user(adminEmail)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
    }

    @Test
    fun `list users with search filter returns 200`() {
        mockMvc.perform(
            get("/api/v1/admin/users")
                .with(user(adminEmail))
                .param("search", "target"),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `change role returns 200`() {
        mockMvc.perform(
            put("/api/v1/admin/users/$targetUserId/role")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"roleId":"$SUPERVISOR_ROLE_ID"}"""),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `lock user returns 200`() {
        mockMvc.perform(
            patch("/api/v1/admin/users/$targetUserId/lock")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"durationMinutes":30}"""),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `unlock user returns 200`() {
        mockMvc.perform(
            patch("/api/v1/admin/users/$targetUserId/unlock")
                .with(user(adminEmail)),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `password reset returns 200`() {
        mockMvc.perform(
            post("/api/v1/admin/users/$targetUserId/password-reset")
                .with(user(adminEmail)),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `lock user returns 403 for user without permission`() {
        // USER role has user.view but not user.lock
        mockMvc.perform(
            patch("/api/v1/admin/users/$targetUserId/lock")
                .with(user(regularEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"durationMinutes":30}"""),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `change role returns 403 for user without permission`() {
        mockMvc.perform(
            put("/api/v1/admin/users/$targetUserId/role")
                .with(user(regularEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"roleId":"$SUPERVISOR_ROLE_ID"}"""),
        )
            .andExpect(status().isForbidden)
    }
}
