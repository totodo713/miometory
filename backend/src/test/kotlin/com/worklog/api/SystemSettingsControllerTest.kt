package com.worklog.api

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Integration tests for SystemSettingsController.
 *
 * Exercises the full chain: Controller → Service → Repository → DB.
 * This covers SystemDefaultSettingsRepository's read and update methods.
 */
class SystemSettingsControllerTest : AdminIntegrationTestBase() {

    private lateinit var adminEmail: String
    private lateinit var regularEmail: String

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        adminEmail = "sysadmin-ss-$suffix@test.com"
        regularEmail = "user-ss-$suffix@test.com"

        createUser(adminEmail, SYSTEM_ADMIN_ROLE_ID, "System Admin")
        createUser(regularEmail, USER_ROLE_ID, "Regular User")
    }

    @Test
    fun `get system settings returns 200 with defaults`() {
        mockMvc.perform(
            get("/api/v1/admin/system/settings/patterns")
                .with(user(adminEmail)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fiscalYearStartMonth").isNumber)
            .andExpect(jsonPath("$.fiscalYearStartDay").isNumber)
            .andExpect(jsonPath("$.monthlyPeriodStartDay").isNumber)
    }

    @Test
    fun `get system settings returns 403 for non-admin`() {
        mockMvc.perform(
            get("/api/v1/admin/system/settings/patterns")
                .with(user(regularEmail)),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `update system settings returns 204`() {
        mockMvc.perform(
            put("/api/v1/admin/system/settings/patterns")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fiscalYearStartMonth":10,"fiscalYearStartDay":1,"monthlyPeriodStartDay":15}"""),
        )
            .andExpect(status().isNoContent)

        // Verify the update was persisted
        mockMvc.perform(
            get("/api/v1/admin/system/settings/patterns")
                .with(user(adminEmail)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fiscalYearStartMonth").value(10))
            .andExpect(jsonPath("$.fiscalYearStartDay").value(1))
            .andExpect(jsonPath("$.monthlyPeriodStartDay").value(15))

        // Restore defaults for other tests
        mockMvc.perform(
            put("/api/v1/admin/system/settings/patterns")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fiscalYearStartMonth":4,"fiscalYearStartDay":1,"monthlyPeriodStartDay":1}"""),
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `update system settings returns 403 for non-admin`() {
        mockMvc.perform(
            put("/api/v1/admin/system/settings/patterns")
                .with(user(regularEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fiscalYearStartMonth":10,"fiscalYearStartDay":1,"monthlyPeriodStartDay":15}"""),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `update with invalid month returns 400`() {
        mockMvc.perform(
            put("/api/v1/admin/system/settings/patterns")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fiscalYearStartMonth":13,"fiscalYearStartDay":1,"monthlyPeriodStartDay":1}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `update with invalid monthly period start day returns 400`() {
        mockMvc.perform(
            put("/api/v1/admin/system/settings/patterns")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fiscalYearStartMonth":4,"fiscalYearStartDay":1,"monthlyPeriodStartDay":29}"""),
        )
            .andExpect(status().isBadRequest)
    }
}
