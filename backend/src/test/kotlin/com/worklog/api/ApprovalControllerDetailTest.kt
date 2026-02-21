package com.worklog.api

import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Integration tests for ApprovalController detail endpoint (T051a).
 *
 * Tests the GET /api/v1/worklog/approvals/{id}/detail endpoint which returns
 * enriched monthly approval data with daily approval status summary,
 * project breakdown, and absence data.
 */
class ApprovalControllerDetailTest : AdminIntegrationTestBase() {

    @Test
    fun `get approval detail with non-existent id returns error`() {
        val unknownId = UUID.randomUUID()
        // Use any user since this endpoint has no @PreAuthorize
        val email = "detailuser-${UUID.randomUUID().toString().take(8)}@test.com"
        createUser(email, USER_ROLE_ID, "Detail User")

        mockMvc.perform(
            get("/api/v1/worklog/approvals/$unknownId/detail")
                .with(user(email)),
        )
            .andExpect(status().is4xxClientError)
    }
}
