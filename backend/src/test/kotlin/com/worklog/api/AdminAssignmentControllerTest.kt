package com.worklog.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Integration tests for AdminAssignmentController (T030a).
 *
 * Tests create assignment, duplicate detection, deactivate/activate,
 * list by member/project, supervisor direct-report restriction,
 * and @PreAuthorize enforcement.
 */
class AdminAssignmentControllerTest : AdminIntegrationTestBase() {

    private lateinit var adminEmail: String
    private lateinit var supervisorEmail: String
    private lateinit var regularEmail: String
    private lateinit var adminMemberId: UUID
    private lateinit var targetMemberId: UUID
    private lateinit var projectId: UUID
    private lateinit var supervisorMemberId: UUID
    private lateinit var subordinateMemberId: UUID
    private lateinit var nonSubordinateId: UUID
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        adminEmail = "tadmin-$suffix@test.com"
        supervisorEmail = "supervisor-$suffix@test.com"
        regularEmail = "user-$suffix@test.com"

        createUser(adminEmail, TENANT_ADMIN_ROLE_ID, "Tenant Admin")
        createUser(supervisorEmail, SUPERVISOR_ROLE_ID, "Supervisor")
        createUser(regularEmail, USER_ROLE_ID, "Regular User")

        // Create member records
        adminMemberId = createMemberForUser(adminEmail)
        targetMemberId = createMemberForUser("target-$suffix@test.com")
        projectId = createProjectInTenant()

        // Supervisor member with a subordinate
        supervisorMemberId = createMemberForUser(supervisorEmail)
        subordinateMemberId = createMemberForUser(
            "subordinate-$suffix@test.com",
            managerId = supervisorMemberId,
        )
        nonSubordinateId = createMemberForUser("nonsub-$suffix@test.com")
    }

    @Test
    fun `create assignment returns 201 for tenant admin`() {
        mockMvc.perform(
            post("/api/v1/admin/assignments")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memberId":"$targetMemberId","projectId":"$projectId"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNotEmpty)
    }

    @Test
    fun `create duplicate assignment returns 409`() {
        mockMvc.perform(
            post("/api/v1/admin/assignments")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memberId":"$targetMemberId","projectId":"$projectId"}"""),
        )
            .andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/admin/assignments")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memberId":"$targetMemberId","projectId":"$projectId"}"""),
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `list by member returns 200`() {
        mockMvc.perform(
            get("/api/v1/admin/assignments/by-member/$targetMemberId")
                .with(user(adminEmail)),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `list by project returns 200`() {
        mockMvc.perform(
            get("/api/v1/admin/assignments/by-project/$projectId")
                .with(user(adminEmail)),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `deactivate assignment returns 200`() {
        val result = mockMvc.perform(
            post("/api/v1/admin/assignments")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memberId":"$adminMemberId","projectId":"$projectId"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val id = objectMapper.readTree(result.response.contentAsString).get("id").asText()

        mockMvc.perform(
            patch("/api/v1/admin/assignments/$id/deactivate")
                .with(user(adminEmail)),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `supervisor can create assignment for direct report`() {
        mockMvc.perform(
            post("/api/v1/admin/assignments")
                .with(user(supervisorEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memberId":"$subordinateMemberId","projectId":"$projectId"}"""),
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `supervisor cannot create assignment for non-direct-report`() {
        mockMvc.perform(
            post("/api/v1/admin/assignments")
                .with(user(supervisorEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memberId":"$nonSubordinateId","projectId":"$projectId"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("NOT_DIRECT_REPORT"))
    }

    @Test
    fun `create assignment returns 403 for user without permission`() {
        mockMvc.perform(
            post("/api/v1/admin/assignments")
                .with(user(regularEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memberId":"$targetMemberId","projectId":"$projectId"}"""),
        )
            .andExpect(status().isForbidden)
    }
}
