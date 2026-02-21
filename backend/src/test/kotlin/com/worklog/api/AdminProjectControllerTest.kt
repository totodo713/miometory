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
 * Integration tests for AdminProjectController (T025a).
 *
 * Tests CRUD, duplicate code validation, deactivate/activate,
 * and @PreAuthorize enforcement with project.* permissions.
 */
class AdminProjectControllerTest : AdminIntegrationTestBase() {

    private lateinit var adminEmail: String
    private lateinit var regularEmail: String
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        adminEmail = "tadmin-$suffix@test.com"
        regularEmail = "user-$suffix@test.com"

        createUser(adminEmail, TENANT_ADMIN_ROLE_ID, "Tenant Admin")
        createUser(regularEmail, USER_ROLE_ID, "Regular User")

        // Admin needs a member record for tenant resolution
        createMemberForUser(adminEmail)
    }

    @Test
    fun `list projects returns 200 for tenant admin`() {
        mockMvc.perform(get("/api/v1/admin/projects").with(user(adminEmail)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
    }

    @Test
    fun `create project returns 201`() {
        val code = "PRJ-${UUID.randomUUID().toString().take(6)}"
        mockMvc.perform(
            post("/api/v1/admin/projects")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code","name":"Test Project","validFrom":null,"validUntil":null}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNotEmpty)
    }

    @Test
    fun `create duplicate project code returns 409`() {
        val code = "DUP-${UUID.randomUUID().toString().take(4)}"

        mockMvc.perform(
            post("/api/v1/admin/projects")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code","name":"Project One","validFrom":null,"validUntil":null}"""),
        )
            .andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/admin/projects")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code","name":"Project Two","validFrom":null,"validUntil":null}"""),
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `update project returns 200`() {
        val code = "UP-${UUID.randomUUID().toString().take(6)}"
        val result = mockMvc.perform(
            post("/api/v1/admin/projects")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code","name":"Original","validFrom":null,"validUntil":null}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val id = objectMapper.readTree(result.response.contentAsString).get("id").asText()

        mockMvc.perform(
            put("/api/v1/admin/projects/$id")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Updated","validFrom":"2026-01-01","validUntil":"2026-12-31"}"""),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `deactivate and activate project returns 200`() {
        val code = "DA-${UUID.randomUUID().toString().take(6)}"
        val result = mockMvc.perform(
            post("/api/v1/admin/projects")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code","name":"To Toggle","validFrom":null,"validUntil":null}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val id = objectMapper.readTree(result.response.contentAsString).get("id").asText()

        mockMvc.perform(patch("/api/v1/admin/projects/$id/deactivate").with(user(adminEmail)))
            .andExpect(status().isOk)

        mockMvc.perform(patch("/api/v1/admin/projects/$id/activate").with(user(adminEmail)))
            .andExpect(status().isOk)
    }

    @Test
    fun `list projects returns 403 for user without permission`() {
        mockMvc.perform(get("/api/v1/admin/projects").with(user(regularEmail)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `create project returns 403 for user without permission`() {
        mockMvc.perform(
            post("/api/v1/admin/projects")
                .with(user(regularEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"NOPE","name":"No Permission","validFrom":null,"validUntil":null}"""),
        )
            .andExpect(status().isForbidden)
    }
}
