package com.worklog.api

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.*
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for CSV Import Controller.
 * 
 * Tests CSV import functionality including:
 * - Template download
 * - CSV file upload
 * - Import progress tracking
 * - Validation error handling
 * 
 * Task: T143 - CSV import integration tests
 */
class CsvImportControllerTest : IntegrationTestBase() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private val testMemberId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `GET template should return CSV template file`() {
        // Act
        val response = restTemplate.getForEntity(
            "/api/v1/worklog/csv/template",
            String::class.java
        )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.startsWith("Date,Project Code,Hours,Notes"))
        assertTrue(response.body!!.contains("PRJ-001"))
    }

    @Test
    fun `POST import with valid CSV should return import ID and accept status`() {
        // Arrange
        val csvContent = """
            Date,Project Code,Hours,Notes
            2026-01-02,PRJ-001,8.00,Test import
            2026-01-03,PRJ-002,4.00,Another test
        """.trimIndent()

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
        body.add("file", object : ByteArrayResource(csvContent.toByteArray()) {
            override fun getFilename(): String = "test.csv"
        })
        body.add("memberId", testMemberId.toString())

        val requestEntity = HttpEntity(body, headers)

        // Act
        val response = restTemplate.postForEntity(
            "/api/v1/worklog/csv/import",
            requestEntity,
            Map::class.java
        )

        // Assert
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.containsKey("importId"))
        val importId = response.body!!["importId"] as String
        assertTrue(importId.isNotEmpty())
    }

    @Test
    fun `POST import with empty file should return bad request`() {
        // Arrange
        val csvContent = ""

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
        body.add("file", object : ByteArrayResource(csvContent.toByteArray()) {
            override fun getFilename(): String = "empty.csv"
        })
        body.add("memberId", testMemberId.toString())

        val requestEntity = HttpEntity(body, headers)

        // Act
        val response = restTemplate.postForEntity(
            "/api/v1/worklog/csv/import",
            requestEntity,
            Map::class.java
        )

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `POST import with 100 rows should process successfully`() {
        // Arrange - Create CSV with 100 rows
        val csvLines = mutableListOf("Date,Project Code,Hours,Notes")
        for (i in 1..100) {
            val day = String.format("%02d", (i % 28) + 1)
            csvLines.add("2025-12-$day,PRJ-001,8.00,Row $i")
        }
        val csvContent = csvLines.joinToString("\n")

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
        body.add("file", object : ByteArrayResource(csvContent.toByteArray()) {
            override fun getFilename(): String = "large.csv"
        })
        body.add("memberId", testMemberId.toString())

        val requestEntity = HttpEntity(body, headers)

        // Act
        val response = restTemplate.postForEntity(
            "/api/v1/worklog/csv/import",
            requestEntity,
            Map::class.java
        )

        // Assert
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.containsKey("importId"))
        
        // Note: We don't test SSE progress here as it requires special handling
        // The SSE endpoint is tested manually and in E2E tests
    }

    @Test
    fun `POST import with invalid date format should be accepted but fail validation`() {
        // Arrange
        val csvContent = """
            Date,Project Code,Hours,Notes
            2026/01/02,PRJ-001,8.00,Invalid date format
        """.trimIndent()

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
        body.add("file", object : ByteArrayResource(csvContent.toByteArray()) {
            override fun getFilename(): String = "invalid.csv"
        })
        body.add("memberId", testMemberId.toString())

        val requestEntity = HttpEntity(body, headers)

        // Act
        val response = restTemplate.postForEntity(
            "/api/v1/worklog/csv/import",
            requestEntity,
            Map::class.java
        )

        // Assert - Import is accepted, validation happens asynchronously
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
    }

    @Test
    fun `POST import with non-CSV file should return bad request`() {
        // Arrange
        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
        body.add("file", object : ByteArrayResource("not a csv".toByteArray()) {
            override fun getFilename(): String = "test.txt"
        })
        body.add("memberId", testMemberId.toString())

        val requestEntity = HttpEntity(body, headers)

        // Act
        val response = restTemplate.postForEntity(
            "/api/v1/worklog/csv/import",
            requestEntity,
            Map::class.java
        )

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `POST import with valid project codes should process successfully`() {
        // Arrange
        val csvContent = """
            Date,Project Code,Hours,Notes
            2026-01-02,PRJ-001,4.00,Morning work
            2026-01-02,PRJ-002,4.00,Afternoon work
            2026-01-03,PRJ-003,8.00,Full day work
        """.trimIndent()

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
        body.add("file", object : ByteArrayResource(csvContent.toByteArray()) {
            override fun getFilename(): String = "projects.csv"
        })
        body.add("memberId", testMemberId.toString())

        val requestEntity = HttpEntity(body, headers)

        // Act
        val response = restTemplate.postForEntity(
            "/api/v1/worklog/csv/import",
            requestEntity,
            Map::class.java
        )

        // Assert
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.containsKey("importId"))
    }
}
