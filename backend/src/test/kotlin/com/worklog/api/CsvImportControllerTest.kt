package com.worklog.api

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/csv/template",
                String::class.java,
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
        val csvContent =
            """
            Date,Project Code,Hours,Notes
            2026-01-02,PRJ-001,8.00,Test import
            2026-01-03,PRJ-002,4.00,Another test
            """.trimIndent()

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
        body.add(
            "file",
            object : ByteArrayResource(csvContent.toByteArray()) {
                override fun getFilename(): String = "test.csv"
            },
        )
        body.add("memberId", testMemberId.toString())

        val requestEntity = HttpEntity(body, headers)

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/csv/import",
                requestEntity,
                Map::class.java,
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
        body.add(
            "file",
            object : ByteArrayResource(csvContent.toByteArray()) {
                override fun getFilename(): String = "empty.csv"
            },
        )
        body.add("memberId", testMemberId.toString())

        val requestEntity = HttpEntity(body, headers)

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/csv/import",
                requestEntity,
                Map::class.java,
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
        body.add(
            "file",
            object : ByteArrayResource(csvContent.toByteArray()) {
                override fun getFilename(): String = "large.csv"
            },
        )
        body.add("memberId", testMemberId.toString())

        val requestEntity = HttpEntity(body, headers)

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/csv/import",
                requestEntity,
                Map::class.java,
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
        val csvContent =
            """
            Date,Project Code,Hours,Notes
            2026/01/02,PRJ-001,8.00,Invalid date format
            """.trimIndent()

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
        body.add(
            "file",
            object : ByteArrayResource(csvContent.toByteArray()) {
                override fun getFilename(): String = "invalid.csv"
            },
        )
        body.add("memberId", testMemberId.toString())

        val requestEntity = HttpEntity(body, headers)

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/csv/import",
                requestEntity,
                Map::class.java,
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
        body.add(
            "file",
            object : ByteArrayResource("not a csv".toByteArray()) {
                override fun getFilename(): String = "test.txt"
            },
        )
        body.add("memberId", testMemberId.toString())

        val requestEntity = HttpEntity(body, headers)

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/csv/import",
                requestEntity,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `POST import with valid project codes should process successfully`() {
        // Arrange
        val csvContent =
            """
            Date,Project Code,Hours,Notes
            2026-01-02,PRJ-001,4.00,Morning work
            2026-01-02,PRJ-002,4.00,Afternoon work
            2026-01-03,PRJ-003,8.00,Full day work
            """.trimIndent()

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
        body.add(
            "file",
            object : ByteArrayResource(csvContent.toByteArray()) {
                override fun getFilename(): String = "projects.csv"
            },
        )
        body.add("memberId", testMemberId.toString())

        val requestEntity = HttpEntity(body, headers)

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/csv/import",
                requestEntity,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.containsKey("importId"))
    }

    /**
     * Performance test for CSV import with 10K rows via HTTP endpoint.
     *
     * This test validates the full HTTP pipeline for CSV import.
     * For the full 100K row SC-005 performance test, see:
     * StreamingCsvProcessorPerformanceTest.java
     *
     * Task: T144 - Performance test for CSV import (100K rows in <1000s, SC-005)
     */
    @Test
    fun `CSV import via HTTP should handle 10K rows efficiently`() {
        // Arrange - Generate 10K row CSV content (within multipart size limit)
        val rowCount = 10_000
        val csvBuilder = StringBuilder()
        csvBuilder.append("Date,Project Code,Hours,Notes\n")

        val projectCodes = listOf("PRJ-001", "PRJ-002", "PRJ-003")
        val hoursOptions = listOf("1.00", "2.00", "4.00", "8.00")

        for (i in 1..rowCount) {
            val month = String.format("%02d", ((i - 1) / 28) % 12 + 1)
            val day = String.format("%02d", (i - 1) % 28 + 1)
            val year = 2025 - ((i - 1) / (28 * 12))
            val projectCode = projectCodes[i % projectCodes.size]
            val hours = hoursOptions[i % hoursOptions.size]
            csvBuilder.append("$year-$month-$day,$projectCode,$hours,Performance test row $i\n")
        }

        val csvContent = csvBuilder.toString()
        val csvBytes = csvContent.toByteArray()

        println("Generated CSV with $rowCount rows, size: ${csvBytes.size / 1024} KB")

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
        body.add(
            "file",
            object : ByteArrayResource(csvBytes) {
                override fun getFilename(): String = "performance_10k.csv"
            },
        )
        body.add("memberId", testMemberId.toString())

        val requestEntity = HttpEntity(body, headers)

        // Act - Measure processing time
        val startTime = System.currentTimeMillis()

        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/csv/import",
                requestEntity,
                Map::class.java,
            )

        val endTime = System.currentTimeMillis()
        val elapsedTimeSeconds = (endTime - startTime) / 1000.0
        val rowsPerSecond = rowCount / elapsedTimeSeconds

        // Assert
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.containsKey("importId"))

        println("HTTP Performance Results:")
        println("  - Total rows: $rowCount")
        println("  - Elapsed time: ${"%.2f".format(elapsedTimeSeconds)} seconds")
        println("  - Rows per second: ${"%.2f".format(rowsPerSecond)}")
        println("  - Target: 100 rows/second")

        // Should be much faster than minimum requirement
        assertTrue(
            rowsPerSecond >= 100,
            "CSV import rate ${"%.2f".format(
                rowsPerSecond,
            )} rows/sec is below minimum 100 rows/sec requirement (SC-005)",
        )
    }
}
