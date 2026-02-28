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
 * Integration tests for UserStatusController.
 *
 * Tests:
 * - GET /api/v1/user/status returns correct response format and affiliation status
 * - POST /api/v1/user/select-tenant validates tenant membership
 */
class UserStatusControllerTest : AdminIntegrationTestBase() {

    private lateinit var userEmail: String
    private lateinit var unaffiliatedEmail: String

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        userEmail = "status-user-$suffix@test.com"
        unaffiliatedEmail = "unaffiliated-$suffix@test.com"

        // Create a user with a member record (FULLY_ASSIGNED: has organization)
        createUser(userEmail, USER_ROLE_ID, "Status Test User")
        createMemberForUser(userEmail)

        // Create a user without any member record (UNAFFILIATED)
        createUser(unaffiliatedEmail, USER_ROLE_ID, "Unaffiliated User")
    }

    @Test
    fun `get status returns 200 with FULLY_ASSIGNED for user with member record`() {
        mockMvc.perform(get("/api/v1/user/status").with(user(userEmail)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(userEmail))
            .andExpect(jsonPath("$.state").value("FULLY_ASSIGNED"))
            .andExpect(jsonPath("$.memberships").isArray)
            .andExpect(jsonPath("$.memberships[0].tenantId").value(ADM_TEST_TENANT_ID))
            .andExpect(jsonPath("$.memberships[0].tenantName").isNotEmpty)
            .andExpect(jsonPath("$.memberships[0].organizationId").value(ADM_TEST_ORG_ID))
            .andExpect(jsonPath("$.memberships[0].organizationName").isNotEmpty)
    }

    @Test
    fun `get status returns 200 with UNAFFILIATED for user without member record`() {
        mockMvc.perform(get("/api/v1/user/status").with(user(unaffiliatedEmail)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(unaffiliatedEmail))
            .andExpect(jsonPath("$.state").value("UNAFFILIATED"))
            .andExpect(jsonPath("$.memberships").isArray)
            .andExpect(jsonPath("$.memberships").isEmpty)
    }

    @Test
    fun `get status returns userId field`() {
        mockMvc.perform(get("/api/v1/user/status").with(user(userEmail)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").isNotEmpty)
    }

    @Test
    fun `get status returns AFFILIATED_NO_ORG for member without organization`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val noOrgEmail = "no-org-$suffix@test.com"
        createUser(noOrgEmail, USER_ROLE_ID, "No Org User")

        // Insert member record without organization_id (null org)
        baseJdbcTemplate.update(
            """INSERT INTO members (id, tenant_id, organization_id, email, display_name,
                   manager_id, is_active, version, created_at, updated_at)
               VALUES (?, ?::UUID, NULL, ?, ?, NULL, true, 0, NOW(), NOW())
               ON CONFLICT (id) DO NOTHING""",
            UUID.randomUUID(),
            ADM_TEST_TENANT_ID,
            noOrgEmail,
            "No Org User",
        )

        mockMvc.perform(get("/api/v1/user/status").with(user(noOrgEmail)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("AFFILIATED_NO_ORG"))
            .andExpect(jsonPath("$.memberships").isArray)
            .andExpect(jsonPath("$.memberships[0].organizationId").doesNotExist())
    }

    @Test
    fun `select-tenant returns structured error when no session exists`() {
        // MockMvc doesn't create a real HttpSession with sessionId attribute,
        // so the controller throws DomainException("SESSION_NOT_FOUND", ...)
        // which maps to 404 via GlobalExceptionHandler._NOT_FOUND pattern
        mockMvc.perform(
            post("/api/v1/user/select-tenant")
                .with(user(userEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tenantId":"$ADM_TEST_TENANT_ID"}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"))
            .andExpect(jsonPath("$.message").isNotEmpty)
    }
}
