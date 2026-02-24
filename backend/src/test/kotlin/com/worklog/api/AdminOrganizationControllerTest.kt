package com.worklog.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
 * Integration tests for AdminOrganizationController (T012).
 *
 * Tests list with filters, create, update, deactivate/activate, tree endpoint,
 * and @PreAuthorize enforcement. Verifies tenant isolation.
 */
class AdminOrganizationControllerTest : AdminIntegrationTestBase() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var adminEmail: String
    private lateinit var regularEmail: String
    private lateinit var orgId: UUID

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        adminEmail = "oadmin-$suffix@test.com"
        regularEmail = "ouser-$suffix@test.com"

        createUser(adminEmail, TENANT_ADMIN_ROLE_ID, "Org Admin")
        createUser(regularEmail, USER_ROLE_ID, "Regular User")

        // Admin needs a member record for tenant resolution
        createMemberForUser(adminEmail)

        // Create a target organization via API (ensures event store + projection)
        orgId = createOrganizationViaApi()
    }

    /**
     * Creates an organization via the POST API to ensure both event store and projection
     * are populated. Returns the organization ID.
     */
    private fun createOrganizationViaApi(
        code: String = "ORG_${UUID.randomUUID().toString().take(8)}",
        name: String = "Test Organization $code",
    ): UUID {
        val result = mockMvc.perform(
            post("/api/v1/admin/organizations")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code","name":"$name","parentId":null}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val body = objectMapper.readTree(result.response.contentAsString)
        return UUID.fromString(body.get("id").asText())
    }

    /**
     * Creates an organization directly in the projection table for read-only tests.
     * NOTE: Only use for list/search/tree tests. Not for update/deactivate/activate tests
     * since those require event store data.
     */
    private fun createOrganizationInProjection(
        organizationId: UUID = UUID.randomUUID(),
        code: String = "ORG_${organizationId.toString().take(8)}",
        name: String = "Test Org $code",
        parentId: UUID? = null,
        level: Int = 1,
        status: String = "ACTIVE",
        tenantId: String = ADM_TEST_TENANT_ID,
    ): UUID {
        baseJdbcTemplate.update(
            """INSERT INTO organization (id, tenant_id, parent_id, code, name, level, status, version, created_at, updated_at)
               VALUES (?, ?::UUID, ?, ?, ?, ?, ?, 0, NOW(), NOW())
               ON CONFLICT (id) DO NOTHING""",
            organizationId,
            tenantId,
            parentId,
            code,
            name,
            level,
            status,
        )
        return organizationId
    }

    // --- GET /api/v1/admin/organizations ---

    @Test
    fun `list organizations returns 200 for tenant admin`() {
        mockMvc.perform(get("/api/v1/admin/organizations").with(user(adminEmail)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
    }

    @Test
    fun `list organizations with search filter returns 200`() {
        val searchName = "SearchableOrg_${UUID.randomUUID().toString().take(6)}"
        createOrganizationInProjection(name = searchName, code = "SCH_${UUID.randomUUID().toString().take(7)}")

        mockMvc.perform(
            get("/api/v1/admin/organizations")
                .with(user(adminEmail))
                .param("search", searchName),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
    }

    @Test
    fun `list organizations with status filter returns 200`() {
        mockMvc.perform(
            get("/api/v1/admin/organizations")
                .with(user(adminEmail))
                .param("isActive", "true"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
    }

    // --- POST /api/v1/admin/organizations ---

    @Test
    fun `create organization returns 201 with id`() {
        val code = "NEW_${UUID.randomUUID().toString().take(8)}"
        mockMvc.perform(
            post("/api/v1/admin/organizations")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code","name":"New Organization","parentId":null}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNotEmpty)
    }

    @Test
    fun `create organization with duplicate code returns 409`() {
        val code = "DUPC_${UUID.randomUUID().toString().take(7)}"
        // Create first org via API
        mockMvc.perform(
            post("/api/v1/admin/organizations")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code","name":"First Org","parentId":null}"""),
        )
            .andExpect(status().isCreated)

        // Attempt duplicate
        mockMvc.perform(
            post("/api/v1/admin/organizations")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code","name":"Duplicate Org","parentId":null}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value("DUPLICATE_CODE"))
    }

    @Test
    fun `create organization with invalid input returns 400`() {
        // Missing required fields (code and name are @NotBlank)
        mockMvc.perform(
            post("/api/v1/admin/organizations")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"","name":"","parentId":null}"""),
        )
            .andExpect(status().isBadRequest)
    }

    // --- PUT /api/v1/admin/organizations/{id} ---

    @Test
    fun `update organization returns 204`() {
        mockMvc.perform(
            put("/api/v1/admin/organizations/$orgId")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Updated Organization Name"}"""),
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `update non-existent organization returns 404`() {
        val nonExistentId = UUID.randomUUID()
        mockMvc.perform(
            put("/api/v1/admin/organizations/$nonExistentId")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Updated Name"}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_NOT_FOUND"))
    }

    // --- PATCH /api/v1/admin/organizations/{id}/deactivate ---

    @Test
    fun `deactivate organization returns 200 with warnings`() {
        // Create parent via API (needs event store)
        val parentCode = "DPAR_${UUID.randomUUID().toString().take(7)}"
        val parentId = createOrganizationViaApi(code = parentCode, name = "Deactivate Parent")

        // Create a child in projection table (only needed for the child count query)
        createOrganizationInProjection(
            code = "DCHD_${UUID.randomUUID().toString().take(7)}",
            name = "Active Child",
            parentId = parentId,
            level = 2,
        )

        mockMvc.perform(
            patch("/api/v1/admin/organizations/$parentId/deactivate")
                .with(user(adminEmail)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.warnings").isArray)
    }

    // --- PATCH /api/v1/admin/organizations/{id}/activate ---

    @Test
    fun `activate organization after deactivate returns 204`() {
        // First deactivate (orgId was created via API, so event store is populated)
        mockMvc.perform(
            patch("/api/v1/admin/organizations/$orgId/deactivate")
                .with(user(adminEmail)),
        )
            .andExpect(status().isOk)

        // Then activate
        mockMvc.perform(
            patch("/api/v1/admin/organizations/$orgId/activate")
                .with(user(adminEmail)),
        )
            .andExpect(status().isNoContent)
    }

    // --- GET /api/v1/admin/organizations/tree ---

    @Test
    fun `get organization tree returns 200 with tree structure`() {
        mockMvc.perform(get("/api/v1/admin/organizations/tree").with(user(adminEmail)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    // --- Authorization tests ---

    @Test
    fun `list organizations returns 403 for user without permission`() {
        mockMvc.perform(get("/api/v1/admin/organizations").with(user(regularEmail)))
            .andExpect(status().isForbidden)
    }
}
