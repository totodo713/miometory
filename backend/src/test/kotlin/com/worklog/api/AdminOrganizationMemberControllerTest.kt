package com.worklog.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Integration tests for US2 manager operations (T022).
 *
 * Tests assign/remove manager, transfer member, and list members by organization
 * endpoints across AdminMemberController and AdminOrganizationController.
 */
class AdminOrganizationMemberControllerTest : AdminIntegrationTestBase() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var adminEmail: String
    private lateinit var memberA: UUID
    private lateinit var memberB: UUID
    private lateinit var memberC: UUID
    private lateinit var secondOrgId: UUID

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        adminEmail = "orgadmin-$suffix@test.com"

        createUser(adminEmail, TENANT_ADMIN_ROLE_ID, "Org Admin")
        createMemberForUser(adminEmail)

        // Create three members for manager chain tests
        memberA = createMemberForUser("member-a-$suffix@test.com")
        memberB = createMemberForUser("member-b-$suffix@test.com")
        memberC = createMemberForUser("member-c-$suffix@test.com")

        // Create a second organization via admin API (event-sourced)
        secondOrgId = createOrganizationViaApi("ORG2_$suffix", "Second Org")
    }

    /**
     * Creates an organization through the admin API so that it is persisted
     * in the event store (required for event-sourced lookups in the service layer).
     */
    private fun createOrganizationViaApi(code: String, name: String): UUID {
        val result = mockMvc.perform(
            post("/api/v1/admin/organizations")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code","name":"$name"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val responseBody = objectMapper.readTree(result.response.contentAsString)
        return UUID.fromString(responseBody["id"].asText())
    }

    // --- Assign Manager Tests ---

    @Nested
    inner class AssignManager {

        @Test
        fun `assign manager returns 204 on success`() {
            mockMvc.perform(
                put("/api/v1/admin/members/$memberA/manager")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"managerId":"$memberB"}"""),
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `self-assignment returns 400 with CIRCULAR_REFERENCE`() {
            mockMvc.perform(
                put("/api/v1/admin/members/$memberA/manager")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"managerId":"$memberA"}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errorCode").value("CIRCULAR_REFERENCE"))
        }

        @Test
        fun `circular A to B to A returns 400 with CIRCULAR_REFERENCE`() {
            // First assign B as manager of A
            mockMvc.perform(
                put("/api/v1/admin/members/$memberA/manager")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"managerId":"$memberB"}"""),
            )
                .andExpect(status().isNoContent)

            // Now try to assign A as manager of B — creates circular A→B→A
            mockMvc.perform(
                put("/api/v1/admin/members/$memberB/manager")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"managerId":"$memberA"}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errorCode").value("CIRCULAR_REFERENCE"))
        }

        @Test
        fun `deep circular A to B to C to A returns 400 with CIRCULAR_REFERENCE`() {
            // Assign B as manager of A
            mockMvc.perform(
                put("/api/v1/admin/members/$memberA/manager")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"managerId":"$memberB"}"""),
            )
                .andExpect(status().isNoContent)

            // Assign C as manager of B
            mockMvc.perform(
                put("/api/v1/admin/members/$memberB/manager")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"managerId":"$memberC"}"""),
            )
                .andExpect(status().isNoContent)

            // Now try to assign A as manager of C — creates A→B→C→A cycle
            mockMvc.perform(
                put("/api/v1/admin/members/$memberC/manager")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"managerId":"$memberA"}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errorCode").value("CIRCULAR_REFERENCE"))
        }

        @Test
        fun `assign manager to inactive member returns 400 with MEMBER_INACTIVE`() {
            // Deactivate member A
            baseJdbcTemplate.update("UPDATE members SET is_active = false WHERE id = ?", memberA)

            mockMvc.perform(
                put("/api/v1/admin/members/$memberA/manager")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"managerId":"$memberB"}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errorCode").value("MEMBER_INACTIVE"))
        }

        @Test
        fun `assign inactive manager returns 400 with MANAGER_INACTIVE`() {
            // Deactivate member B (proposed manager)
            baseJdbcTemplate.update("UPDATE members SET is_active = false WHERE id = ?", memberB)

            mockMvc.perform(
                put("/api/v1/admin/members/$memberA/manager")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"managerId":"$memberB"}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errorCode").value("MANAGER_INACTIVE"))
        }
    }

    // --- Remove Manager Tests ---

    @Nested
    inner class RemoveManager {

        @Test
        fun `remove manager returns 204 on success`() {
            // First assign a manager
            mockMvc.perform(
                put("/api/v1/admin/members/$memberA/manager")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"managerId":"$memberB"}"""),
            )
                .andExpect(status().isNoContent)

            // Remove the manager
            mockMvc.perform(
                delete("/api/v1/admin/members/$memberA/manager")
                    .with(user(adminEmail)),
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `remove manager when member has no manager returns 204 idempotent`() {
            // Member A has no manager assigned — should succeed idempotently
            mockMvc.perform(
                delete("/api/v1/admin/members/$memberA/manager")
                    .with(user(adminEmail)),
            )
                .andExpect(status().isNoContent)
        }
    }

    // --- Transfer Member Tests ---

    @Nested
    inner class TransferMember {

        @Test
        fun `transfer member returns 204 and clears manager`() {
            // First assign a manager
            mockMvc.perform(
                put("/api/v1/admin/members/$memberA/manager")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"managerId":"$memberB"}"""),
            )
                .andExpect(status().isNoContent)

            // Transfer to second org
            mockMvc.perform(
                put("/api/v1/admin/members/$memberA/organization")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"organizationId":"$secondOrgId"}"""),
            )
                .andExpect(status().isNoContent)

            // Verify manager was cleared by checking the member no longer has a manager
            val managerId = baseJdbcTemplate.queryForObject(
                "SELECT manager_id FROM members WHERE id = ?",
                UUID::class.java,
                memberA,
            )
            assert(managerId == null) { "Expected manager to be cleared after transfer" }
        }

        @Test
        fun `transfer to same organization returns 400 with SAME_ORGANIZATION`() {
            mockMvc.perform(
                put("/api/v1/admin/members/$memberA/organization")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"organizationId":"$ADM_TEST_ORG_ID"}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errorCode").value("SAME_ORGANIZATION"))
        }

        @Test
        fun `transfer to inactive organization returns 400 with ORGANIZATION_INACTIVE`() {
            // Deactivate the second organization via admin API (event-sourced)
            mockMvc.perform(
                patch("/api/v1/admin/organizations/$secondOrgId/deactivate")
                    .with(user(adminEmail)),
            )
                .andExpect(status().isOk)

            mockMvc.perform(
                put("/api/v1/admin/members/$memberA/organization")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"organizationId":"$secondOrgId"}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_INACTIVE"))
        }
    }

    // --- List Members By Organization Tests ---

    @Nested
    inner class ListMembersByOrganization {

        @Test
        fun `list members by organization returns 200 with pagination`() {
            mockMvc.perform(
                get("/api/v1/admin/organizations/$ADM_TEST_ORG_ID/members")
                    .with(user(adminEmail)),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray)
                .andExpect(jsonPath("$.totalElements").isNumber)
                .andExpect(jsonPath("$.totalPages").isNumber)
                .andExpect(jsonPath("$.number").value(0))
        }

        @Test
        fun `list members includes managerIsActive field`() {
            // Assign manager B to member A via the API
            mockMvc.perform(
                put("/api/v1/admin/members/$memberA/manager")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"managerId":"$memberB"}"""),
            )
                .andExpect(status().isNoContent)

            // List members and verify response schema includes managerIsActive
            val result = mockMvc.perform(
                get("/api/v1/admin/organizations/$ADM_TEST_ORG_ID/members")
                    .with(user(adminEmail)),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray)
                .andReturn()

            // Parse response to verify managerIsActive field is present in results
            val body = objectMapper.readTree(result.response.contentAsString)
            val content = body["content"]
            assert(content.size() > 0) { "Expected at least one member in the response" }
            // Every member row should have the managerIsActive field (null or boolean)
            assert(content[0].has("managerIsActive")) {
                "Expected managerIsActive field in member response"
            }
        }

        @Test
        fun `list members filter by isActive returns only active members`() {
            // Deactivate member C
            baseJdbcTemplate.update("UPDATE members SET is_active = false WHERE id = ?", memberC)

            val result = mockMvc.perform(
                get("/api/v1/admin/organizations/$ADM_TEST_ORG_ID/members")
                    .with(user(adminEmail))
                    .param("isActive", "true"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray)
                .andReturn()

            // Verify none of the returned members are inactive
            val body = objectMapper.readTree(result.response.contentAsString)
            val content = body["content"]
            for (member in content) {
                assert(member["isActive"].asBoolean()) {
                    "Expected all members to be active when filtering by isActive=true"
                }
            }
        }

        @Test
        fun `list members filter by isActive false returns only inactive members`() {
            // Deactivate member C
            baseJdbcTemplate.update("UPDATE members SET is_active = false WHERE id = ?", memberC)

            val result = mockMvc.perform(
                get("/api/v1/admin/organizations/$ADM_TEST_ORG_ID/members")
                    .with(user(adminEmail))
                    .param("isActive", "false"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray)
                .andReturn()

            // Verify all returned members are inactive
            val body = objectMapper.readTree(result.response.contentAsString)
            val content = body["content"]
            assert(content.size() > 0) { "Expected at least one inactive member" }
            for (member in content) {
                assert(!member["isActive"].asBoolean()) {
                    "Expected all members to be inactive when filtering by isActive=false"
                }
            }
        }
    }
}
