package com.worklog.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Integration tests for the tenant bootstrap endpoint (POST /api/v1/admin/tenants/{tenantId}/bootstrap).
 *
 * Verifies that SYSTEM_ADMIN can bootstrap a newly created tenant with organizations and members,
 * including TENANT_ADMIN role assignment.
 */
class AdminTenantBootstrapTest : AdminIntegrationTestBase() {

    private lateinit var adminEmail: String
    private lateinit var regularEmail: String
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        adminEmail = "sysadmin-$suffix@test.com"
        regularEmail = "user-$suffix@test.com"

        createUser(adminEmail, SYSTEM_ADMIN_ROLE_ID, "System Admin")
        createUser(regularEmail, USER_ROLE_ID, "Regular User")
    }

    private fun createTenant(): String {
        val code = "BT${UUID.randomUUID().toString().take(6).replace("-", "")}"
        val result = mockMvc.perform(
            post("/api/v1/admin/tenants")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code","name":"Bootstrap Test Tenant"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readTree(result.response.contentAsString).get("id").asText()
    }

    @Test
    fun `bootstrap tenant creates orgs, members, and assigns tenant admin`() {
        val tenantId = createTenant()
        val email1 = "owner1-${UUID.randomUUID().toString().take(6)}@test.com"
        val email2 = "owner2-${UUID.randomUUID().toString().take(6)}@test.com"

        val result = mockMvc.perform(
            post("/api/v1/admin/tenants/$tenantId/bootstrap")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "organizations" to listOf(
                                mapOf("code" to "ORG_A", "name" to "Org Alpha"),
                                mapOf("code" to "ORG_B", "name" to "Org Beta"),
                            ),
                            "members" to listOf(
                                mapOf(
                                    "email" to email1,
                                    "displayName" to "Owner One",
                                    "organizationCode" to "ORG_A",
                                    "tenantAdmin" to true,
                                ),
                                mapOf(
                                    "email" to email2,
                                    "displayName" to "Owner Two",
                                    "organizationCode" to "ORG_B",
                                    "tenantAdmin" to false,
                                ),
                            ),
                        ),
                    ),
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.result.organizations").isArray)
            .andExpect(jsonPath("$.result.organizations.length()").value(2))
            .andExpect(jsonPath("$.result.organizations[0].code").value("ORG_A"))
            .andExpect(jsonPath("$.result.organizations[1].code").value("ORG_B"))
            .andExpect(jsonPath("$.result.members").isArray)
            .andExpect(jsonPath("$.result.members.length()").value(2))
            .andExpect(jsonPath("$.result.members[0].email").value(email1))
            .andExpect(jsonPath("$.result.members[0].temporaryPassword").isNotEmpty)
            .andExpect(jsonPath("$.result.members[1].email").value(email2))
            .andReturn()

        // Verify TENANT_ADMIN role was assigned to owner1
        val body = objectMapper.readTree(result.response.contentAsString)
        val owner1Email = body.at("/result/members/0/email").asText()
        val roleId = baseJdbcTemplate.queryForObject(
            "SELECT role_id FROM users WHERE email = ?",
            UUID::class.java,
            owner1Email,
        )
        assertEquals(TENANT_ADMIN_ROLE_ID, roleId.toString(), "Expected TENANT_ADMIN role for $owner1Email")

        // Verify owner2 was NOT promoted (tenantAdmin=false)
        val owner2Email = body.at("/result/members/1/email").asText()
        val roleId2 = baseJdbcTemplate.queryForObject(
            "SELECT role_id FROM users WHERE email = ?",
            UUID::class.java,
            owner2Email,
        )
        assertNotEquals(TENANT_ADMIN_ROLE_ID, roleId2.toString(), "Expected USER role for $owner2Email")
    }

    @Test
    fun `bootstrap tenant returns 403 for non-system-admin`() {
        val tenantId = createTenant()
        val minimalBody =
            """{"organizations":[{"code":"ORG","name":"Org"}],"members":[""" +
                """{"email":"a@b.com","displayName":"A","organizationCode":"ORG","tenantAdmin":false}]}"""

        mockMvc.perform(
            post("/api/v1/admin/tenants/$tenantId/bootstrap")
                .with(user(regularEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content(minimalBody),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `bootstrap tenant returns 404 for non-existent tenant`() {
        val fakeTenantId = UUID.randomUUID()
        val minimalBody =
            """{"organizations":[{"code":"ORG","name":"Org"}],"members":[""" +
                """{"email":"a@b.com","displayName":"A","organizationCode":"ORG","tenantAdmin":false}]}"""

        mockMvc.perform(
            post("/api/v1/admin/tenants/$fakeTenantId/bootstrap")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content(minimalBody),
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `double bootstrap returns 409`() {
        val tenantId = createTenant()
        val email1 = "first-${UUID.randomUUID().toString().take(6)}@test.com"
        val email2 = "second-${UUID.randomUUID().toString().take(6)}@test.com"

        val body =
            """{"organizations":[{"code":"ORG","name":"Org"}],"members":[""" +
                """{"email":"$email1","displayName":"A","organizationCode":"ORG","tenantAdmin":false}]}"""

        // First bootstrap succeeds
        mockMvc.perform(
            post("/api/v1/admin/tenants/$tenantId/bootstrap")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isCreated)

        // Second bootstrap fails
        val body2 =
            """{"organizations":[{"code":"ORG2","name":"Org2"}],"members":[""" +
                """{"email":"$email2","displayName":"B","organizationCode":"ORG2","tenantAdmin":false}]}"""

        mockMvc.perform(
            post("/api/v1/admin/tenants/$tenantId/bootstrap")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body2),
        )
            .andExpect(status().isConflict)
    }
}
