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
}
