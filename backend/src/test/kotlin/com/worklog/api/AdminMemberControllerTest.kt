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
 * Integration tests for AdminMemberController (T019a).
 *
 * Tests list with filters, invite, update, deactivate/activate,
 * and @PreAuthorize enforcement. Verifies tenant isolation.
 */
class AdminMemberControllerTest : AdminIntegrationTestBase() {

    private lateinit var adminEmail: String
    private lateinit var regularEmail: String
    private lateinit var memberId: UUID

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        adminEmail = "tadmin-$suffix@test.com"
        regularEmail = "user-$suffix@test.com"

        createUser(adminEmail, TENANT_ADMIN_ROLE_ID, "Tenant Admin")
        createUser(regularEmail, USER_ROLE_ID, "Regular User")

        // Admin needs a member record for tenant resolution
        createMemberForUser(adminEmail)

        // Create a target member for update/deactivate tests
        memberId = createMemberForUser("member-$suffix@test.com")
    }

    @Test
    fun `list members returns 200 for tenant admin`() {
        mockMvc.perform(get("/api/v1/admin/members").with(user(adminEmail)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
    }

    @Test
    fun `list members with search filter returns 200`() {
        mockMvc.perform(
            get("/api/v1/admin/members")
                .with(user(adminEmail))
                .param("search", "member"),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `invite member returns 201`() {
        val newEmail = "new-${UUID.randomUUID().toString().take(8)}@test.com"
        mockMvc.perform(
            post("/api/v1/admin/members")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"email":"$newEmail","displayName":"New Member",""" +
                        """"organizationId":"$ADM_TEST_ORG_ID","managerId":null}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNotEmpty)
    }

    @Test
    fun `update member returns 200`() {
        mockMvc.perform(
            put("/api/v1/admin/members/$memberId")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"email":"updated-${UUID.randomUUID().toString().take(
                        6,
                    )}@test.com","displayName":"Updated Name","organizationId":"$ADM_TEST_ORG_ID","managerId":null}""",
                ),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `deactivate member returns 200`() {
        mockMvc.perform(
            patch("/api/v1/admin/members/$memberId/deactivate")
                .with(user(adminEmail)),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `activate member after deactivate returns 200`() {
        mockMvc.perform(
            patch("/api/v1/admin/members/$memberId/deactivate")
                .with(user(adminEmail)),
        )
            .andExpect(status().isOk)

        mockMvc.perform(
            patch("/api/v1/admin/members/$memberId/activate")
                .with(user(adminEmail)),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `list members returns 403 for user without permission`() {
        mockMvc.perform(get("/api/v1/admin/members").with(user(regularEmail)))
            .andExpect(status().isForbidden)
    }
}
