package com.worklog.api

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for TimesheetController.
 *
 * Tests the timesheet view and daily attendance CRUD endpoints.
 */
class TimesheetControllerTest : IntegrationTestBase() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private val testMemberId = UUID.randomUUID()
    private val testProjectId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        createTestMember(testMemberId)
        createTestProject(testProjectId)
        // Insert assignment linking member to project
        executeInNewTransaction {
            baseJdbcTemplate.update(
                """INSERT INTO member_project_assignments
                   (id, tenant_id, member_id, project_id, assigned_at, is_active)
                   VALUES (?, ?::uuid, ?, ?, CURRENT_TIMESTAMP, true)
                   ON CONFLICT ON CONSTRAINT uk_member_project_assignment DO NOTHING""",
                UUID.randomUUID(),
                TEST_TENANT_ID,
                testMemberId,
                testProjectId,
            )
        }
        // Clean up any attendance records for this member to prevent test interference
        executeInNewTransaction {
            baseJdbcTemplate.update(
                "DELETE FROM daily_attendance WHERE member_id = ?",
                testMemberId,
            )
        }
    }

    @Test
    fun `GET timesheet should return 200 with calendar period`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/timesheet/2026/3?memberId=$testMemberId&projectId=$testProjectId&periodType=calendar",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("2026-03-01", body["periodStart"])
        assertEquals("2026-03-31", body["periodEnd"])

        @Suppress("UNCHECKED_CAST")
        val rows = body["rows"] as List<Map<String, Any?>>
        assertNotNull(rows)
        assertEquals(31, rows.size, "March has 31 days")

        assertNotNull(body["summary"])
    }

    @Test
    fun `GET timesheet should return 200 with fiscal period`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/timesheet/2026/3?memberId=$testMemberId&projectId=$testProjectId&periodType=fiscal",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("2026-02-21", body["periodStart"])
        assertEquals("2026-03-20", body["periodEnd"])
    }

    @Test
    fun `PUT attendance should save and return id`() {
        // Arrange
        val requestBody =
            mapOf(
                "date" to "2026-03-02",
                "startTime" to "09:00:00",
                "endTime" to "18:00:00",
                "remarks" to "test",
                "version" to null,
            )
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        // Act
        val response =
            restTemplate.exchange(
                "/api/v1/worklog/timesheet/attendance?memberId=$testMemberId",
                HttpMethod.PUT,
                HttpEntity(requestBody, headers),
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>
        assertNotNull(body["id"], "Response should contain an attendance record id")
    }

    @Test
    fun `DELETE attendance should return 204`() {
        // Arrange - First save an attendance record
        val requestBody =
            mapOf(
                "date" to "2026-03-02",
                "startTime" to "09:00:00",
                "endTime" to "18:00:00",
                "remarks" to "to be deleted",
                "version" to null,
            )
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        restTemplate.exchange(
            "/api/v1/worklog/timesheet/attendance?memberId=$testMemberId",
            HttpMethod.PUT,
            HttpEntity(requestBody, headers),
            Map::class.java,
        )

        // Act - Delete the attendance record
        val response =
            restTemplate.exchange(
                "/api/v1/worklog/timesheet/attendance/2026-03-02?memberId=$testMemberId",
                HttpMethod.DELETE,
                null,
                Void::class.java,
            )

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    @Test
    fun `GET timesheet should return 400 for year below 2020`() {
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/timesheet/2019/3?memberId=$testMemberId&projectId=$testProjectId",
                Map::class.java,
            )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `GET timesheet should return 400 for year above 2100`() {
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/timesheet/2101/3?memberId=$testMemberId&projectId=$testProjectId",
                Map::class.java,
            )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `GET timesheet should return 400 for month below 1`() {
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/timesheet/2026/0?memberId=$testMemberId&projectId=$testProjectId",
                Map::class.java,
            )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `GET timesheet should return 400 for month above 12`() {
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/timesheet/2026/13?memberId=$testMemberId&projectId=$testProjectId",
                Map::class.java,
            )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `GET timesheet should accept boundary year 2020`() {
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/timesheet/2020/1?memberId=$testMemberId&projectId=$testProjectId",
                Map::class.java,
            )

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `GET timesheet should accept boundary year 2100`() {
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/timesheet/2100/12?memberId=$testMemberId&projectId=$testProjectId",
                Map::class.java,
            )

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `GET timesheet should return 404 for non-existent member`() {
        val fakeMemberId = UUID.randomUUID()
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/timesheet/2026/3?memberId=$fakeMemberId&projectId=$testProjectId",
                Map::class.java,
            )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `GET timesheet should return 404 for non-existent project`() {
        val fakeProjectId = UUID.randomUUID()
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/timesheet/2026/3?memberId=$testMemberId&projectId=$fakeProjectId",
                Map::class.java,
            )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `GET timesheet should work without assignment`() {
        // Create a separate member with no assignment to this project
        val unassignedMemberId = UUID.randomUUID()
        createTestMember(unassignedMemberId)

        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/timesheet/2026/3?memberId=$unassignedMemberId&projectId=$testProjectId",
                Map::class.java,
            )

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>

        @Suppress("UNCHECKED_CAST")
        val rows = body["rows"] as List<Map<String, Any?>>
        // defaultStartTime/defaultEndTime should fall back to system defaults (09:00/18:00)
        val firstRow = rows.first()
        assertEquals("09:00", firstRow["defaultStartTime"])
        assertEquals("18:00", firstRow["defaultEndTime"])
    }

    @Test
    fun `GET timesheet should return default times from assignment`() {
        // Create a member and project with default times in assignment
        val memberWithDefaults = UUID.randomUUID()
        val projectWithDefaults = UUID.randomUUID()
        createTestMember(memberWithDefaults)
        createTestProject(projectWithDefaults)
        executeInNewTransaction {
            baseJdbcTemplate.update(
                """INSERT INTO member_project_assignments
                   (id, tenant_id, member_id, project_id, assigned_at, is_active, default_start_time, default_end_time)
                   VALUES (?, ?::uuid, ?, ?, CURRENT_TIMESTAMP, true, '09:00:00', '18:00:00')
                   ON CONFLICT ON CONSTRAINT uk_member_project_assignment DO NOTHING""",
                UUID.randomUUID(),
                TEST_TENANT_ID,
                memberWithDefaults,
                projectWithDefaults,
            )
        }

        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/timesheet/2026/3?memberId=$memberWithDefaults&projectId=$projectWithDefaults",
                Map::class.java,
            )

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>

        @Suppress("UNCHECKED_CAST")
        val rows = body["rows"] as List<Map<String, Any?>>
        val firstRow = rows.first()
        assertNotNull(firstRow["defaultStartTime"])
        assertNotNull(firstRow["defaultEndTime"])
    }

    @Test
    fun `GET timesheet summary should count business days correctly`() {
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/timesheet/2026/3?memberId=$testMemberId&projectId=$testProjectId&periodType=calendar",
                Map::class.java,
            )

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>

        @Suppress("UNCHECKED_CAST")
        val summary = body["summary"] as Map<String, Any?>
        val businessDays = (summary["totalBusinessDays"] as Number).toInt()
        assertTrue(businessDays in 20..23, "March 2026 should have 20-23 business days, got $businessDays")
        assertEquals(0, (summary["totalWorkingDays"] as Number).toInt())
    }

    @Test
    fun `GET timesheet should include saved attendance data`() {
        // Arrange - Save attendance for 2026-03-02
        val requestBody =
            mapOf(
                "date" to "2026-03-02",
                "startTime" to "09:00:00",
                "endTime" to "18:00:00",
                "remarks" to "meeting",
                "version" to null,
            )
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        restTemplate.exchange(
            "/api/v1/worklog/timesheet/attendance?memberId=$testMemberId",
            HttpMethod.PUT,
            HttpEntity(requestBody, headers),
            Map::class.java,
        )

        // Act - Get timesheet for March 2026
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/timesheet/2026/3?memberId=$testMemberId&projectId=$testProjectId&periodType=calendar",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>

        @Suppress("UNCHECKED_CAST")
        val rows = body["rows"] as List<Map<String, Any?>>

        val march2 = rows.find { it["date"] == "2026-03-02" }
        assertNotNull(march2, "Expected to find row for 2026-03-02")
        assertEquals(true, march2["hasAttendanceRecord"])
        assertTrue(
            march2["startTime"].toString().contains("09:00"),
            "startTime should contain 09:00",
        )
        assertEquals("meeting", march2["remarks"])
    }
}
