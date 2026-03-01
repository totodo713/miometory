package com.worklog.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Integration tests for AdminMemberCsvController.
 *
 * Tests template download, dry-run validation, import execution,
 * error handling, and full dry-run → import flow.
 */
class AdminMemberCsvControllerTest : AdminIntegrationTestBase() {

    private lateinit var adminEmail: String
    private lateinit var regularEmail: String

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        adminEmail = "csvadmin-$suffix@test.com"
        regularEmail = "csvuser-$suffix@test.com"

        createUser(adminEmail, TENANT_ADMIN_ROLE_ID, "CSV Admin")
        createUser(regularEmail, USER_ROLE_ID, "Regular User")
        createMemberForUser(adminEmail)
    }

    private fun csvFile(content: String, filename: String = "test.csv"): MockMultipartFile =
        MockMultipartFile("file", filename, "text/csv", content.toByteArray())

    private fun validCsvContent(vararg emails: String): String {
        val rows = emails.mapIndexed { i, email -> "$email,User ${i + 1}" }
        return "email,displayName\n${rows.joinToString("\n")}\n"
    }

    @Nested
    inner class Template {
        @Test
        fun `GET template returns 200 with CSV content`() {
            mockMvc.perform(
                get("/api/v1/admin/members/csv/template")
                    .with(user(adminEmail)),
            )
                .andExpect(status().isOk)
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(
                    header().string("Content-Disposition", """attachment; filename="member-import-template.csv""""),
                )
        }

        @Test
        fun `GET template returns 403 for user without permission`() {
            mockMvc.perform(
                get("/api/v1/admin/members/csv/template")
                    .with(user(regularEmail)),
            )
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class DryRun {
        @Test
        fun `POST dry-run with valid CSV returns 200 with session`() {
            val suffix = UUID.randomUUID().toString().take(8)
            val email = "new-$suffix@example.com"
            val csv = csvFile(validCsvContent(email))

            mockMvc.perform(
                multipart("/api/v1/admin/members/csv/dry-run")
                    .file(csv)
                    .param("organizationId", ADM_TEST_ORG_ID)
                    .with(user(adminEmail)),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.sessionId").isNotEmpty)
                .andExpect(jsonPath("$.totalRows").value(1))
                .andExpect(jsonPath("$.validRows").value(1))
                .andExpect(jsonPath("$.errorRows").value(0))
                .andExpect(jsonPath("$.rows[0].email").value(email))
                .andExpect(jsonPath("$.rows[0].status").value("VALID"))
        }

        @Test
        fun `POST dry-run with empty file returns 400`() {
            val csv = MockMultipartFile("file", "empty.csv", "text/csv", ByteArray(0))

            mockMvc.perform(
                multipart("/api/v1/admin/members/csv/dry-run")
                    .file(csv)
                    .param("organizationId", ADM_TEST_ORG_ID)
                    .with(user(adminEmail)),
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `POST dry-run with non-CSV file returns 400`() {
            val file = MockMultipartFile("file", "data.txt", "text/plain", "some data".toByteArray())

            mockMvc.perform(
                multipart("/api/v1/admin/members/csv/dry-run")
                    .file(file)
                    .param("organizationId", ADM_TEST_ORG_ID)
                    .with(user(adminEmail)),
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `POST dry-run with invalid headers returns 400`() {
            val csv = csvFile("wrong,headers\nfoo,bar\n")

            mockMvc.perform(
                multipart("/api/v1/admin/members/csv/dry-run")
                    .file(csv)
                    .param("organizationId", ADM_TEST_ORG_ID)
                    .with(user(adminEmail)),
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `POST dry-run with nonexistent organization returns 404`() {
            val csv = csvFile(validCsvContent("test@example.com"))
            val fakeOrgId = UUID.randomUUID().toString()

            mockMvc.perform(
                multipart("/api/v1/admin/members/csv/dry-run")
                    .file(csv)
                    .param("organizationId", fakeOrgId)
                    .with(user(adminEmail)),
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `POST dry-run detects duplicate emails in CSV`() {
            val suffix = UUID.randomUUID().toString().take(8)
            val email = "dup-$suffix@example.com"
            val csv = csvFile("email,displayName\n$email,User 1\n$email,User 2\n")

            mockMvc.perform(
                multipart("/api/v1/admin/members/csv/dry-run")
                    .file(csv)
                    .param("organizationId", ADM_TEST_ORG_ID)
                    .with(user(adminEmail)),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.validRows").value(1))
                .andExpect(jsonPath("$.errorRows").value(1))
        }

        @Test
        fun `POST dry-run detects existing DB emails`() {
            // adminEmail already exists in DB
            val csv = csvFile(validCsvContent(adminEmail))

            mockMvc.perform(
                multipart("/api/v1/admin/members/csv/dry-run")
                    .file(csv)
                    .param("organizationId", ADM_TEST_ORG_ID)
                    .with(user(adminEmail)),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.validRows").value(0))
                .andExpect(jsonPath("$.errorRows").value(1))
        }

        @Test
        fun `POST dry-run returns 403 for user without permission`() {
            val csv = csvFile(validCsvContent("test@example.com"))

            mockMvc.perform(
                multipart("/api/v1/admin/members/csv/dry-run")
                    .file(csv)
                    .param("organizationId", ADM_TEST_ORG_ID)
                    .with(user(regularEmail)),
            )
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class Import {
        @Test
        fun `POST import with valid session returns 201 with result CSV`() {
            val suffix = UUID.randomUUID().toString().take(8)
            val email = "import-$suffix@example.com"
            val csv = csvFile(validCsvContent(email))

            // Dry-run first
            val dryRunResult = mockMvc.perform(
                multipart("/api/v1/admin/members/csv/dry-run")
                    .file(csv)
                    .param("organizationId", ADM_TEST_ORG_ID)
                    .with(user(adminEmail)),
            )
                .andExpect(status().isOk)
                .andReturn()

            val sessionId = com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(dryRunResult.response.contentAsString)
                .get("sessionId").asText()

            // Execute import
            mockMvc.perform(
                post("/api/v1/admin/members/csv/import/$sessionId")
                    .with(user(adminEmail)),
            )
                .andExpect(status().isCreated)
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", """attachment; filename="import-result.csv""""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
        }

        @Test
        fun `POST import with nonexistent session returns 404`() {
            val fakeSessionId = UUID.randomUUID().toString()

            mockMvc.perform(
                post("/api/v1/admin/members/csv/import/$fakeSessionId")
                    .with(user(adminEmail)),
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `POST import with same session twice returns 404 on second attempt`() {
            val suffix = UUID.randomUUID().toString().take(8)
            val email = "reuse-$suffix@example.com"
            val csv = csvFile(validCsvContent(email))

            val dryRunResult = mockMvc.perform(
                multipart("/api/v1/admin/members/csv/dry-run")
                    .file(csv)
                    .param("organizationId", ADM_TEST_ORG_ID)
                    .with(user(adminEmail)),
            )
                .andExpect(status().isOk)
                .andReturn()

            val sessionId = com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(dryRunResult.response.contentAsString)
                .get("sessionId").asText()

            // First import succeeds
            mockMvc.perform(
                post("/api/v1/admin/members/csv/import/$sessionId")
                    .with(user(adminEmail)),
            )
                .andExpect(status().isCreated)

            // Second import fails (session removed after commit)
            mockMvc.perform(
                post("/api/v1/admin/members/csv/import/$sessionId")
                    .with(user(adminEmail)),
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `POST import returns 409 when re-validation finds new conflicts`() {
            val suffix = UUID.randomUUID().toString().take(8)
            val email = "conflict-$suffix@example.com"
            val csv = csvFile(validCsvContent(email))

            // Dry-run
            val dryRunResult = mockMvc.perform(
                multipart("/api/v1/admin/members/csv/dry-run")
                    .file(csv)
                    .param("organizationId", ADM_TEST_ORG_ID)
                    .with(user(adminEmail)),
            )
                .andExpect(status().isOk)
                .andReturn()

            val sessionId = com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(dryRunResult.response.contentAsString)
                .get("sessionId").asText()

            // Register the same email via direct invite (simulating concurrent admin)
            mockMvc.perform(
                post("/api/v1/admin/members")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"email":"$email","displayName":"Conflict User",""" +
                            """"organizationId":"$ADM_TEST_ORG_ID","managerId":null}""",
                    ),
            )
                .andExpect(status().isCreated)

            // Import should detect re-validation error
            mockMvc.perform(
                post("/api/v1/admin/members/csv/import/$sessionId")
                    .with(user(adminEmail)),
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.errorCode").value("IMPORT_VALIDATION_CHANGED"))
        }

        @Test
        fun `POST import returns 403 for user without permission`() {
            mockMvc.perform(
                post("/api/v1/admin/members/csv/import/${UUID.randomUUID()}")
                    .with(user(regularEmail)),
            )
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class FullFlow {
        @Test
        fun `dry-run then import creates users and members in DB`() {
            val suffix = UUID.randomUUID().toString().take(8)
            val email1 = "flow1-$suffix@example.com"
            val email2 = "flow2-$suffix@example.com"
            val csv = csvFile(validCsvContent(email1, email2))

            // Dry-run
            val dryRunResult = mockMvc.perform(
                multipart("/api/v1/admin/members/csv/dry-run")
                    .file(csv)
                    .param("organizationId", ADM_TEST_ORG_ID)
                    .with(user(adminEmail)),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.validRows").value(2))
                .andExpect(jsonPath("$.errorRows").value(0))
                .andReturn()

            val sessionId = com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(dryRunResult.response.contentAsString)
                .get("sessionId").asText()

            // Import
            val importResult = mockMvc.perform(
                post("/api/v1/admin/members/csv/import/$sessionId")
                    .with(user(adminEmail)),
            )
                .andExpect(status().isCreated)
                .andReturn()

            // Verify result CSV has BOM and content
            val resultBytes = importResult.response.contentAsByteArray
            assertEquals(0xEF.toByte(), resultBytes[0], "Result CSV should start with UTF-8 BOM")
            assertEquals(0xBB.toByte(), resultBytes[1], "Result CSV should have BOM byte 2")
            assertEquals(0xBF.toByte(), resultBytes[2], "Result CSV should have BOM byte 3")

            val resultCsv = String(resultBytes.drop(3).toByteArray())
            assertTrue(resultCsv.contains("temporaryPassword")) { "Result CSV should contain password header" }
            assertTrue(resultCsv.contains(email1)) { "Result CSV should contain first email" }
            assertTrue(resultCsv.contains(email2)) { "Result CSV should contain second email" }

            // Verify DB state: users created
            val userCount = baseJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email IN (?, ?)",
                Long::class.java,
                email1,
                email2,
            )
            assertEquals(2L, userCount, "Expected 2 users in DB, got $userCount")

            // Verify DB state: members created
            val memberCount = baseJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM members WHERE email IN (?, ?)",
                Long::class.java,
                email1,
                email2,
            )
            assertEquals(2L, memberCount, "Expected 2 members in DB, got $memberCount")
        }

        @Test
        fun `import marks existing user as EXISTING and new user as CREATED in result CSV`() {
            val suffix = UUID.randomUUID().toString().take(8)
            val existingEmail = "existing-$suffix@example.com"
            val newEmail = "new-$suffix@example.com"

            // Pre-create user (simulates user registered in another tenant)
            createUser(existingEmail, USER_ROLE_ID, "Existing User")

            val csv = csvFile(validCsvContent(existingEmail, newEmail))

            // Dry-run
            val dryRunResult = mockMvc.perform(
                multipart("/api/v1/admin/members/csv/dry-run")
                    .file(csv)
                    .param("organizationId", ADM_TEST_ORG_ID)
                    .with(user(adminEmail)),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.validRows").value(2))
                .andReturn()

            val sessionId = com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(dryRunResult.response.contentAsString)
                .get("sessionId").asText()

            // Import
            val importResult = mockMvc.perform(
                post("/api/v1/admin/members/csv/import/$sessionId")
                    .with(user(adminEmail)),
            )
                .andExpect(status().isCreated)
                .andReturn()

            val resultCsv = String(importResult.response.contentAsByteArray.drop(3).toByteArray())
            val lines = resultCsv.lines().filter { it.isNotBlank() }

            // Header + 2 data rows
            assertEquals(3, lines.size, "Expected header + 2 data rows")

            val existingLine = lines.first { it.contains(existingEmail) }
            val newLine = lines.first { it.contains(newEmail) }

            assertTrue(existingLine.contains("EXISTING")) { "Existing user should have EXISTING status" }
            assertTrue(newLine.contains("CREATED")) { "New user should have CREATED status" }

            // EXISTING user should have empty temporaryPassword (last field)
            assertTrue(existingLine.endsWith(",")) { "Existing user should have empty temporaryPassword" }
        }
    }
}
