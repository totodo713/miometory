package com.worklog.api

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Integration tests for DailyApprovalController (T047a).
 *
 * Tests list daily entries, approve/reject flows, @PreAuthorize enforcement,
 * and supervisor direct-report scoping.
 */
class DailyApprovalControllerTest : AdminIntegrationTestBase() {

    private lateinit var supervisorEmail: String
    private lateinit var regularEmail: String
    private lateinit var supervisorMemberId: UUID
    private lateinit var subordinateMemberId: UUID
    private lateinit var projectId: UUID

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        supervisorEmail = "supervisor-$suffix@test.com"
        regularEmail = "user-$suffix@test.com"

        createUser(supervisorEmail, SUPERVISOR_ROLE_ID, "Supervisor")
        createUser(regularEmail, USER_ROLE_ID, "Regular User")

        supervisorMemberId = createMemberForUser(supervisorEmail)
        subordinateMemberId = createMemberForUser(
            "subordinate-$suffix@test.com",
            managerId = supervisorMemberId,
        )

        projectId = createProjectInTenant()
    }

    @Test
    fun `list daily entries returns 200 for supervisor`() {
        mockMvc.perform(
            get("/api/v1/worklog/daily-approvals")
                .with(user(supervisorEmail)),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `list daily entries with date range returns 200`() {
        mockMvc.perform(
            get("/api/v1/worklog/daily-approvals")
                .with(user(supervisorEmail))
                .param("dateFrom", "2026-02-01")
                .param("dateTo", "2026-02-20"),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `list daily entries returns 403 for user without permission`() {
        mockMvc.perform(
            get("/api/v1/worklog/daily-approvals")
                .with(user(regularEmail)),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `approve returns 403 for user without permission`() {
        mockMvc.perform(
            post("/api/v1/worklog/daily-approvals/approve")
                .with(user(regularEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"entryIds":["${UUID.randomUUID()}"],"comment":null}"""),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `reject returns 403 for user without permission`() {
        mockMvc.perform(
            post("/api/v1/worklog/daily-approvals/reject")
                .with(user(regularEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"entryId":"${UUID.randomUUID()}","comment":"Rejected"}"""),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `recall returns 403 for user without permission`() {
        mockMvc.perform(
            post("/api/v1/worklog/daily-approvals/${UUID.randomUUID()}/recall")
                .with(user(regularEmail)),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `approve returns 204 for supervisor with valid entry`() {
        val entryId = createSubmittedEntry(subordinateMemberId, projectId)
        mockMvc.perform(
            post("/api/v1/worklog/daily-approvals/approve")
                .with(user(supervisorEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"entryIds":["$entryId"],"comment":"Looks good"}"""),
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `reject returns 204 for supervisor with valid entry`() {
        val entryId = createSubmittedEntry(subordinateMemberId, projectId)
        mockMvc.perform(
            post("/api/v1/worklog/daily-approvals/reject")
                .with(user(supervisorEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"entryId":"$entryId","comment":"Please fix hours"}"""),
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `recall returns 204 for supervisor with approved entry`() {
        val entryId = createSubmittedEntry(subordinateMemberId, projectId)

        // First approve the entry
        mockMvc.perform(
            post("/api/v1/worklog/daily-approvals/approve")
                .with(user(supervisorEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"entryIds":["$entryId"],"comment":null}"""),
        )
            .andExpect(status().isNoContent)

        // Get the approval ID
        val approvalId = baseJdbcTemplate.queryForObject(
            "SELECT id FROM daily_entry_approvals WHERE work_log_entry_id = ? AND status <> 'RECALLED'",
            UUID::class.java,
            entryId,
        )

        // Recall the approval
        mockMvc.perform(
            post("/api/v1/worklog/daily-approvals/$approvalId/recall")
                .with(user(supervisorEmail)),
        )
            .andExpect(status().isNoContent)
    }

    private fun createSubmittedEntry(memberId: UUID, projectId: UUID): UUID {
        val entryId = UUID.randomUUID()
        baseJdbcTemplate.update(
            """INSERT INTO work_log_entries_projection
               (id, member_id, organization_id, project_id, work_date, hours, notes, status, version, created_at, updated_at)
               VALUES (?, ?, ?::UUID, ?, CURRENT_DATE - INTERVAL '1 day', 8.0, 'Test entry', 'SUBMITTED', 0, NOW(), NOW())""",
            entryId,
            memberId,
            ADM_TEST_ORG_ID,
            projectId,
        )
        return entryId
    }
}
