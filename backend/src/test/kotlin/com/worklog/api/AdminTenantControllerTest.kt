package com.worklog.api

import com.fasterxml.jackson.databind.ObjectMapper
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
 * Integration tests for AdminTenantController (T056a).
 *
 * Tests CRUD operations, duplicate code validation, and @PreAuthorize enforcement.
 */
class AdminTenantControllerTest : AdminIntegrationTestBase() {

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

    @Test
    fun `list tenants returns 200 for system admin`() {
        mockMvc.perform(
            get("/api/v1/admin/tenants")
                .with(user(adminEmail)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
    }

    @Test
    fun `list tenants with status filter returns 200`() {
        mockMvc.perform(
            get("/api/v1/admin/tenants")
                .with(user(adminEmail))
                .param("status", "ACTIVE"),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `create tenant returns 201`() {
        val code = "T${UUID.randomUUID().toString().take(6).replace("-", "")}"
        mockMvc.perform(
            post("/api/v1/admin/tenants")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code","name":"Test Tenant"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNotEmpty)
    }

    @Test
    fun `create duplicate tenant code returns 409`() {
        val code = "DUP${UUID.randomUUID().toString().take(4).replace("-", "")}"

        // First create succeeds
        mockMvc.perform(
            post("/api/v1/admin/tenants")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code","name":"Tenant One"}"""),
        )
            .andExpect(status().isCreated)

        // Duplicate fails with 409
        mockMvc.perform(
            post("/api/v1/admin/tenants")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code","name":"Tenant Two"}"""),
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `update tenant name returns 200`() {
        val code = "UP${UUID.randomUUID().toString().take(6).replace("-", "")}"
        val result = mockMvc.perform(
            post("/api/v1/admin/tenants")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code","name":"Original Name"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val id = objectMapper.readTree(result.response.contentAsString).get("id").asText()

        mockMvc.perform(
            put("/api/v1/admin/tenants/$id")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Updated Name"}"""),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `deactivate and activate tenant returns 200`() {
        val code = "DA${UUID.randomUUID().toString().take(6).replace("-", "")}"
        val result = mockMvc.perform(
            post("/api/v1/admin/tenants")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code","name":"To Toggle"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val id = objectMapper.readTree(result.response.contentAsString).get("id").asText()

        mockMvc.perform(patch("/api/v1/admin/tenants/$id/deactivate").with(user(adminEmail)))
            .andExpect(status().isOk)

        mockMvc.perform(patch("/api/v1/admin/tenants/$id/activate").with(user(adminEmail)))
            .andExpect(status().isOk)
    }

    @Test
    fun `list tenants returns 403 for user without permission`() {
        mockMvc.perform(
            get("/api/v1/admin/tenants")
                .with(user(regularEmail)),
        )
            .andExpect(status().isForbidden)
    }
}
