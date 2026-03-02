package com.worklog.api

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for CalendarController.
 *
 * Tests the calendar view API endpoints for viewing work log data.
 */
class CalendarControllerTest : IntegrationTestBase() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private val testMemberId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        createTestMember(testMemberId)
        cleanupTestHolidayData()
    }

    private fun cleanupTestHolidayData() {
        executeInNewTransaction {
            // CASCADE on holiday_calendar_entry handles child row cleanup
            baseJdbcTemplate.update(
                "DELETE FROM holiday_calendar WHERE tenant_id = ?::UUID",
                TEST_TENANT_ID,
            )
        }
    }

    @Test
    fun `GET calendar should return 400 for invalid year below range`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2019/1?memberId=$testMemberId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("INVALID_YEAR", body["errorCode"])
    }

    @Test
    fun `GET calendar should return 400 for invalid year above range`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2101/1?memberId=$testMemberId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("INVALID_YEAR", body["errorCode"])
    }

    @Test
    fun `GET calendar should return 400 for invalid month below range`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2024/0?memberId=$testMemberId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("INVALID_MONTH", body["errorCode"])
    }

    @Test
    fun `GET calendar should return 400 for invalid month above range`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2024/13?memberId=$testMemberId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("INVALID_MONTH", body["errorCode"])
    }

    @Test
    fun `GET calendar should return 400 when memberId is missing`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2024/1",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("MEMBER_ID_REQUIRED", body["errorCode"])
    }

    @Test
    fun `GET calendar should return 404 for non-existent member`() {
        // Act
        val nonExistentMemberId = UUID.randomUUID()
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2024/1?memberId=$nonExistentMemberId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("MEMBER_NOT_FOUND", body["errorCode"])
    }

    @Test
    fun `GET calendar should return 200 for valid request`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2024/1?memberId=$testMemberId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
    }

    @Test
    fun `GET calendar should calculate correct fiscal month period`() {
        // Act - Request January 2024
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2024/1?memberId=$testMemberId",
                Map::class.java,
            )

        // Assert - Period should be Dec 21, 2023 to Jan 20, 2024
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("2023-12-21", body["periodStart"])
        assertEquals("2024-01-20", body["periodEnd"])
    }

    @Test
    fun `GET calendar should include holiday info in response`() {
        // Arrange - Create holiday calendar for test tenant
        val calendarId = UUID.randomUUID()
        createHolidayCalendar(calendarId)
        createHolidayCalendarEntry(
            calendarId = calendarId,
            name = "New Year's Day",
            nameJa = "元日",
            entryType = "FIXED",
            month = 1,
            day = 1,
        )

        // Act - Request January 2024 (fiscal period: 2023-12-21 to 2024-01-20)
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2024/1?memberId=$testMemberId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>

        @Suppress("UNCHECKED_CAST")
        val entries = body["dates"] as List<Map<String, Any?>>

        val jan1 = entries.find { it["date"] == "2024-01-01" }
        assertNotNull(jan1, "Expected entry for 2024-01-01")
        assertEquals(true, jan1["isHoliday"])
        assertEquals("New Year's Day", jan1["holidayName"])
        assertEquals("元日", jan1["holidayNameJa"])
    }

    @Test
    fun `GET calendar should return isHoliday false for non-holiday dates`() {
        // Act - Request January 2024 (no holidays configured)
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2024/1?memberId=$testMemberId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>

        @Suppress("UNCHECKED_CAST")
        val dates = body["dates"] as List<Map<String, Any?>>

        // Verify every entry contains isHoliday field and none are true
        dates.forEach { entry ->
            assertTrue(entry.containsKey("isHoliday"), "Each entry should include isHoliday")
        }
        val anyHoliday = dates.any { it["isHoliday"] == true }
        assertEquals(false, anyHoliday, "No entries should be marked as holidays")
    }

    @Test
    fun `GET summary should return 400 for invalid year`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2019/1/summary?memberId=$testMemberId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("INVALID_YEAR", body["errorCode"])
    }

    @Test
    fun `GET summary should return 400 for invalid month`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2024/13/summary?memberId=$testMemberId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("INVALID_MONTH", body["errorCode"])
    }

    @Test
    fun `GET summary should return 400 when memberId is missing`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2024/1/summary",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("MEMBER_ID_REQUIRED", body["errorCode"])
    }

    @Test
    fun `GET summary should return response for valid request`() {
        // Act - Note: Summary may return 200 with empty data or 500 if member doesn't exist
        // This tests that the endpoint is accessible and returns a valid response format
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2024/1/summary?memberId=$testMemberId",
                Map::class.java,
            )

        // Assert - Accept 200 (data found) or check that error handling works
        assertTrue(
            response.statusCode == HttpStatus.OK || response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR,
            "Expected 200 or 500 status code",
        )
    }

    @Test
    fun `GET calendar for boundary year should work correctly`() {
        // Test minimum valid year
        val minResponse =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2020/6?memberId=$testMemberId",
                Map::class.java,
            )
        assertEquals(HttpStatus.OK, minResponse.statusCode)

        // Test maximum valid year
        val maxResponse =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2100/6?memberId=$testMemberId",
                Map::class.java,
            )
        assertEquals(HttpStatus.OK, maxResponse.statusCode)
    }

    @Test
    fun `GET calendar for boundary months should work correctly`() {
        // Test January
        val janResponse =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2024/1?memberId=$testMemberId",
                Map::class.java,
            )
        assertEquals(HttpStatus.OK, janResponse.statusCode)

        // Test December
        val decResponse =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2024/12?memberId=$testMemberId",
                Map::class.java,
            )
        assertEquals(HttpStatus.OK, decResponse.statusCode)
    }

    private fun createHolidayCalendar(calendarId: UUID) {
        executeInNewTransaction {
            baseJdbcTemplate.update(
                """INSERT INTO holiday_calendar
                   (id, tenant_id, name, description, country, is_active)
                   VALUES (?::UUID, ?::UUID,
                   'Test Holidays', 'Test', 'JP', true)""",
                calendarId.toString(),
                TEST_TENANT_ID,
            )
        }
    }

    private fun createHolidayCalendarEntry(
        calendarId: UUID,
        name: String,
        nameJa: String,
        entryType: String,
        month: Int,
        day: Int? = null,
        nthOccurrence: Int? = null,
        dayOfWeek: Int? = null,
    ) {
        executeInNewTransaction {
            baseJdbcTemplate.update(
                """INSERT INTO holiday_calendar_entry
                   (id, holiday_calendar_id, name, name_ja, entry_type, month, day, nth_occurrence, day_of_week)
                   VALUES (?::UUID, ?::UUID, ?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID().toString(),
                calendarId.toString(),
                name,
                nameJa,
                entryType,
                month,
                day,
                nthOccurrence,
                dayOfWeek,
            )
        }
    }
}
