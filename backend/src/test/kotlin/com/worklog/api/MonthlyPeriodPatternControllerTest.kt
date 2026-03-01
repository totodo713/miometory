package com.worklog.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Integration tests for MonthlyPeriodPatternController CRUD operations.
 *
 * Uses system admin auth (required by @PreAuthorize + TenantAccessValidator).
 */
class MonthlyPeriodPatternControllerTest : AdminIntegrationTestBase() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var systemAdminEmail: String

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        systemAdminEmail = "sysadmin-mpc-$suffix@test.com"
        createUser(systemAdminEmail, SYSTEM_ADMIN_ROLE_ID, "System Admin")
    }

    @Test
    fun `POST should create monthly period pattern and return 201`() {
        val tenantId = createTenantViaApi()

        val result = mockMvc.perform(
            post("/api/v1/tenants/$tenantId/monthly-period-patterns")
                .with(user(systemAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"21st Cutoff","startDay":21}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
            .andExpect(jsonPath("$.name").value("21st Cutoff"))
            .andExpect(jsonPath("$.startDay").value(21))
            .andReturn()

        val body = objectMapper.readTree(result.response.contentAsString)
        assertEquals(tenantId.toString(), body["tenantId"].asText())
    }

    @Test
    fun `POST should reject invalid start day below 1`() {
        val tenantId = createTenantViaApi()

        mockMvc.perform(
            post("/api/v1/tenants/$tenantId/monthly-period-patterns")
                .with(user(systemAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Invalid Pattern","startDay":0}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST should reject invalid start day above 28`() {
        val tenantId = createTenantViaApi()

        mockMvc.perform(
            post("/api/v1/tenants/$tenantId/monthly-period-patterns")
                .with(user(systemAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Invalid Date","startDay":29}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET by ID should return pattern when found`() {
        val tenantId = createTenantViaApi()

        // Create pattern
        val createResult = mockMvc.perform(
            post("/api/v1/tenants/$tenantId/monthly-period-patterns")
                .with(user(systemAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"15th Cutoff","startDay":15}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val patternId = objectMapper.readTree(createResult.response.contentAsString)["id"].asText()

        // Get pattern by ID
        mockMvc.perform(
            get("/api/v1/tenants/$tenantId/monthly-period-patterns/$patternId")
                .with(user(systemAdminEmail)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(patternId))
            .andExpect(jsonPath("$.name").value("15th Cutoff"))
            .andExpect(jsonPath("$.startDay").value(15))
    }

    @Test
    fun `GET by ID should return 404 when not found`() {
        val tenantId = createTenantViaApi()
        val nonExistentId = UUID.randomUUID()

        mockMvc.perform(
            get("/api/v1/tenants/$tenantId/monthly-period-patterns/$nonExistentId")
                .with(user(systemAdminEmail)),
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET list should return empty list when no patterns exist`() {
        val tenantId = createTenantViaApi()

        mockMvc.perform(
            get("/api/v1/tenants/$tenantId/monthly-period-patterns")
                .with(user(systemAdminEmail)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `GET list should return all patterns for tenant`() {
        val tenantId = createTenantViaApi()

        // Create two patterns
        mockMvc.perform(
            post("/api/v1/tenants/$tenantId/monthly-period-patterns")
                .with(user(systemAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"1st Cutoff","startDay":1}"""),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/tenants/$tenantId/monthly-period-patterns")
                .with(user(systemAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"21st Cutoff","startDay":21}"""),
        ).andExpect(status().isCreated)

        // Get list
        mockMvc.perform(
            get("/api/v1/tenants/$tenantId/monthly-period-patterns")
                .with(user(systemAdminEmail)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `GET list should not return patterns from other tenants`() {
        val tenant1Id = createTenantViaApi()
        val tenant2Id = createTenantViaApi()

        // Create pattern for tenant1
        mockMvc.perform(
            post("/api/v1/tenants/$tenant1Id/monthly-period-patterns")
                .with(user(systemAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Tenant1 Pattern","startDay":1}"""),
        ).andExpect(status().isCreated)

        // Create pattern for tenant2
        mockMvc.perform(
            post("/api/v1/tenants/$tenant2Id/monthly-period-patterns")
                .with(user(systemAdminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Tenant2 Pattern","startDay":15}"""),
        ).andExpect(status().isCreated)

        // Get list for tenant1
        mockMvc.perform(
            get("/api/v1/tenants/$tenant1Id/monthly-period-patterns")
                .with(user(systemAdminEmail)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Tenant1 Pattern"))
    }

    private fun createTenantViaApi(): UUID {
        val shortCode = "T${System.nanoTime()}"
        val result = mockMvc.perform(
            post("/api/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$shortCode","name":"Test Tenant"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val tenantId = UUID.fromString(objectMapper.readTree(result.response.contentAsString)["id"].asText())

        // Create tenant projection (event store → projection gap)
        baseJdbcTemplate.update(
            """INSERT INTO tenant (id, code, name, status, created_at)
               VALUES (?, ?, ?, 'ACTIVE', NOW()) ON CONFLICT (id) DO NOTHING""",
            tenantId,
            shortCode,
            "Test Tenant",
        )

        return tenantId
    }
}
