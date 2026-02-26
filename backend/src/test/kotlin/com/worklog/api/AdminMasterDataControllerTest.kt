package com.worklog.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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

class AdminMasterDataControllerTest : AdminIntegrationTestBase() {

    private lateinit var adminEmail: String
    private lateinit var tenantAdminEmail: String
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        adminEmail = "sysadmin-md-$suffix@test.com"
        tenantAdminEmail = "tenantadmin-md-$suffix@test.com"
        createUser(adminEmail, SYSTEM_ADMIN_ROLE_ID, "System Admin")
        createUser(tenantAdminEmail, TENANT_ADMIN_ROLE_ID, "Tenant Admin")
        createMemberForUser(tenantAdminEmail)
    }

    private val basePath = "/api/v1/admin/master-data"

    @Nested
    inner class FiscalYearPresets {
        @Test
        fun `list returns 200 with seeded data for system admin`() {
            mockMvc.perform(get("$basePath/fiscal-year-patterns").with(user(adminEmail)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray)
                .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
        }

        @Test
        fun `list returns 403 for tenant admin`() {
            mockMvc.perform(get("$basePath/fiscal-year-patterns").with(user(tenantAdminEmail)))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `create returns 201`() {
            val name = "FY-${UUID.randomUUID().toString().take(8)}"
            mockMvc.perform(
                post("$basePath/fiscal-year-patterns")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name","description":"Test","startMonth":7,"startDay":1}"""),
            ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNotEmpty)
        }

        @Test
        fun `create duplicate name returns 409`() {
            val name = "DupFY-${UUID.randomUUID().toString().take(8)}"
            mockMvc.perform(
                post("$basePath/fiscal-year-patterns").with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name","description":"First","startMonth":4,"startDay":1}"""),
            ).andExpect(status().isCreated)

            mockMvc.perform(
                post("$basePath/fiscal-year-patterns").with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name","description":"Second","startMonth":1,"startDay":1}"""),
            ).andExpect(status().isConflict)
        }

        @Test
        fun `update returns 200`() {
            val name = "UpdFY-${UUID.randomUUID().toString().take(8)}"
            val result = mockMvc.perform(
                post("$basePath/fiscal-year-patterns").with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name","startMonth":4,"startDay":1}"""),
            ).andExpect(status().isCreated).andReturn()

            val id = objectMapper.readTree(result.response.contentAsString).get("id").asText()

            mockMvc.perform(
                put("$basePath/fiscal-year-patterns/$id").with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name-updated","description":"Updated","startMonth":10,"startDay":1}"""),
            ).andExpect(status().isOk)
        }

        @Test
        fun `deactivate and activate returns 200`() {
            val name = "DaFY-${UUID.randomUUID().toString().take(8)}"
            val result = mockMvc.perform(
                post("$basePath/fiscal-year-patterns").with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name","startMonth":4,"startDay":1}"""),
            ).andExpect(status().isCreated).andReturn()

            val id = objectMapper.readTree(result.response.contentAsString).get("id").asText()

            mockMvc.perform(patch("$basePath/fiscal-year-patterns/$id/deactivate").with(user(adminEmail)))
                .andExpect(status().isOk)
            mockMvc.perform(patch("$basePath/fiscal-year-patterns/$id/activate").with(user(adminEmail)))
                .andExpect(status().isOk)
        }

        @Test
        fun `search filters by name`() {
            val unique = UUID.randomUUID().toString().take(8)
            val name = "SearchFY-$unique"
            mockMvc.perform(
                post("$basePath/fiscal-year-patterns").with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name","startMonth":4,"startDay":1}"""),
            ).andExpect(status().isCreated)

            mockMvc.perform(
                get("$basePath/fiscal-year-patterns").with(user(adminEmail)).param("search", unique),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value(name))
        }
    }

    @Nested
    inner class MonthlyPeriodPresets {
        @Test
        fun `list returns 200 with seeded data`() {
            mockMvc.perform(get("$basePath/monthly-period-patterns").with(user(adminEmail)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray)
                .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)))
        }

        @Test
        fun `create returns 201`() {
            val name = "MP-${UUID.randomUUID().toString().take(8)}"
            mockMvc.perform(
                post("$basePath/monthly-period-patterns").with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name","description":"Test","startDay":11}"""),
            ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNotEmpty)
        }

        @Test
        fun `list returns 403 for tenant admin`() {
            mockMvc.perform(get("$basePath/monthly-period-patterns").with(user(tenantAdminEmail)))
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class HolidayCalendars {
        @Test
        fun `list returns 200 with seeded data`() {
            mockMvc.perform(get("$basePath/holiday-calendars").with(user(adminEmail)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray)
                .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
        }

        @Test
        fun `create calendar and add entry returns 201`() {
            val calName = "HC-${UUID.randomUUID().toString().take(8)}"
            val result = mockMvc.perform(
                post("$basePath/holiday-calendars").with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$calName","description":"Test","country":"US"}"""),
            ).andExpect(status().isCreated).andReturn()

            val calId = objectMapper.readTree(result.response.contentAsString).get("id").asText()

            // Add FIXED entry
            mockMvc.perform(
                post("$basePath/holiday-calendars/$calId/entries").with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Independence Day","entryType":"FIXED","month":7,"day":4}"""),
            ).andExpect(status().isCreated)

            // Add NTH_WEEKDAY entry
            val nthBody = """{"name":"Thanksgiving","entryType":"NTH_WEEKDAY",""" +
                """"month":11,"nthOccurrence":4,"dayOfWeek":4}"""
            mockMvc.perform(
                post("$basePath/holiday-calendars/$calId/entries").with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(nthBody),
            ).andExpect(status().isCreated)

            // Verify entries
            mockMvc.perform(get("$basePath/holiday-calendars/$calId/entries").with(user(adminEmail)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
        }

        @Test
        fun `list seeded Japan entries`() {
            // NOTE: Use actual valid UUID from V23 migration (bc not hc)
            mockMvc.perform(
                get("$basePath/holiday-calendars/00000000-0000-0000-0000-bc0000000001/entries")
                    .with(user(adminEmail)),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(16))
        }

        @Test
        fun `delete entry returns 200`() {
            val calName = "HC-Del-${UUID.randomUUID().toString().take(8)}"
            val calResult = mockMvc.perform(
                post("$basePath/holiday-calendars").with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$calName","country":"US"}"""),
            ).andExpect(status().isCreated).andReturn()

            val calId = objectMapper.readTree(calResult.response.contentAsString).get("id").asText()

            val entryResult = mockMvc.perform(
                post("$basePath/holiday-calendars/$calId/entries").with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Test Holiday","entryType":"FIXED","month":1,"day":1}"""),
            ).andExpect(status().isCreated).andReturn()

            val entryId = objectMapper.readTree(entryResult.response.contentAsString).get("id").asText()

            mockMvc.perform(
                delete("$basePath/holiday-calendars/$calId/entries/$entryId").with(user(adminEmail)),
            ).andExpect(status().isOk)
        }

        @Test
        fun `update entry returns 200`() {
            val calName = "HC-Upd-${UUID.randomUUID().toString().take(8)}"
            val calResult = mockMvc.perform(
                post("$basePath/holiday-calendars").with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$calName","country":"US"}"""),
            ).andExpect(status().isCreated).andReturn()

            val calId = objectMapper.readTree(calResult.response.contentAsString).get("id").asText()

            val entryResult = mockMvc.perform(
                post("$basePath/holiday-calendars/$calId/entries").with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"New Year","entryType":"FIXED","month":1,"day":1}"""),
            ).andExpect(status().isCreated).andReturn()

            val entryId = objectMapper.readTree(entryResult.response.contentAsString).get("id").asText()

            mockMvc.perform(
                put("$basePath/holiday-calendars/$calId/entries/$entryId").with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"New Year Updated","entryType":"FIXED","month":1,"day":2}"""),
            ).andExpect(status().isOk)
        }

        @Test
        fun `list returns 403 for tenant admin`() {
            mockMvc.perform(get("$basePath/holiday-calendars").with(user(tenantAdminEmail)))
                .andExpect(status().isForbidden)
        }
    }
}
