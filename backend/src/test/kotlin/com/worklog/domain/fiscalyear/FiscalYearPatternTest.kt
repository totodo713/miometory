package com.worklog.domain.fiscalyear

import com.worklog.domain.shared.DomainException
import com.worklog.domain.tenant.TenantId
import com.worklog.fixtures.FiscalYearPatternFixtures
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for FiscalYearPattern entity.
 * Tests domain logic for fiscal year calculation without infrastructure dependencies.
 */
class FiscalYearPatternTest {

    private val tenantId = TenantId(UUID.randomUUID())

    @Test
    fun `create should generate valid pattern with FiscalYearPatternCreated event`() {
        val pattern = FiscalYearPatternFixtures.createPattern(
            tenantId = tenantId,
            name = "4月開始",
            startMonth = 4,
            startDay = 1
        )

        assertNotNull(pattern.id)
        assertEquals(tenantId.value(), pattern.tenantId.value())
        assertEquals("4月開始", pattern.name)
        assertEquals(4, pattern.startMonth)
        assertEquals(1, pattern.startDay)

        val events = pattern.uncommittedEvents
        assertEquals(1, events.size)
        assertTrue(events[0] is FiscalYearPatternCreated)

        val event = events[0] as FiscalYearPatternCreated
        assertEquals(pattern.id.value(), event.aggregateId())
        assertEquals("4月開始", event.name)
        assertEquals(4, event.startMonth)
        assertEquals(1, event.startDay)
    }

    @Test
    fun `create should trim whitespace from name`() {
        val pattern = FiscalYearPatternFixtures.createPattern(
            tenantId = tenantId,
            name = "  April Start  ",
            startMonth = 4,
            startDay = 1
        )

        assertEquals("April Start", pattern.name)
    }

    // Validation tests for startMonth
    @Test
    fun `create should fail with startMonth less than 1`() {
        val exception = assertFailsWith<DomainException> {
            FiscalYearPatternFixtures.createPattern(tenantId, "Invalid", 0, 1)
        }
        assertEquals("INVALID_START_MONTH", exception.errorCode)
    }

    @Test
    fun `create should fail with startMonth greater than 12`() {
        val exception = assertFailsWith<DomainException> {
            FiscalYearPatternFixtures.createPattern(tenantId, "Invalid", 13, 1)
        }
        assertEquals("INVALID_START_MONTH", exception.errorCode)
    }

    @Test
    fun `create should fail with negative startMonth`() {
        val exception = assertFailsWith<DomainException> {
            FiscalYearPatternFixtures.createPattern(tenantId, "Invalid", -1, 1)
        }
        assertEquals("INVALID_START_MONTH", exception.errorCode)
    }

    // Validation tests for startDay
    @Test
    fun `create should fail with startDay less than 1`() {
        val exception = assertFailsWith<DomainException> {
            FiscalYearPatternFixtures.createPattern(tenantId, "Invalid", 4, 0)
        }
        assertEquals("INVALID_START_DAY", exception.errorCode)
    }

    @Test
    fun `create should fail with startDay greater than 31`() {
        val exception = assertFailsWith<DomainException> {
            FiscalYearPatternFixtures.createPattern(tenantId, "Invalid", 4, 32)
        }
        assertEquals("INVALID_START_DAY", exception.errorCode)
    }

    @Test
    fun `create should fail with negative startDay`() {
        val exception = assertFailsWith<DomainException> {
            FiscalYearPatternFixtures.createPattern(tenantId, "Invalid", 4, -1)
        }
        assertEquals("INVALID_START_DAY", exception.errorCode)
    }

    // Validation tests for name
    @Test
    fun `create should fail with empty name`() {
        val exception = assertFailsWith<DomainException> {
            FiscalYearPatternFixtures.createPattern(tenantId, "", 4, 1)
        }
        assertEquals("NAME_REQUIRED", exception.errorCode)
    }

    @Test
    fun `create should fail with name too long`() {
        val longName = "a".repeat(101)
        val exception = assertFailsWith<DomainException> {
            FiscalYearPatternFixtures.createPattern(tenantId, longName, 4, 1)
        }
        assertEquals("NAME_TOO_LONG", exception.errorCode)
    }

    // Fiscal year calculation tests - April start pattern (4月開始)
    @Test
    fun `getFiscalYear should return correct year for April start pattern - before fiscal year start`() {
        val pattern = FiscalYearPatternFixtures.createPattern(tenantId, "4月開始", 4, 1)
        val date = LocalDate.of(2025, 3, 31)
        
        assertEquals(2024, pattern.getFiscalYear(date))
    }

    @Test
    fun `getFiscalYear should return correct year for April start pattern - on fiscal year start`() {
        val pattern = FiscalYearPatternFixtures.createPattern(tenantId, "4月開始", 4, 1)
        val date = LocalDate.of(2025, 4, 1)
        
        assertEquals(2025, pattern.getFiscalYear(date))
    }

    @Test
    fun `getFiscalYear should return correct year for April start pattern - after fiscal year start`() {
        val pattern = FiscalYearPatternFixtures.createPattern(tenantId, "4月開始", 4, 1)
        val date = LocalDate.of(2025, 4, 2)
        
        assertEquals(2025, pattern.getFiscalYear(date))
    }

    @Test
    fun `getFiscalYear should return correct year for April start pattern - end of calendar year`() {
        val pattern = FiscalYearPatternFixtures.createPattern(tenantId, "4月開始", 4, 1)
        val date = LocalDate.of(2025, 12, 31)
        
        assertEquals(2025, pattern.getFiscalYear(date))
    }

    // Fiscal year calculation tests - November start pattern (11月開始 - year boundary crossing)
    @Test
    fun `getFiscalYear should return correct year for November start pattern - before fiscal year start`() {
        val pattern = FiscalYearPatternFixtures.createPattern(tenantId, "11月開始", 11, 1)
        val date = LocalDate.of(2025, 10, 31)
        
        assertEquals(2024, pattern.getFiscalYear(date))
    }

    @Test
    fun `getFiscalYear should return correct year for November start pattern - on fiscal year start`() {
        val pattern = FiscalYearPatternFixtures.createPattern(tenantId, "11月開始", 11, 1)
        val date = LocalDate.of(2025, 11, 1)
        
        assertEquals(2025, pattern.getFiscalYear(date))
    }

    @Test
    fun `getFiscalYear should return correct year for November start pattern - after fiscal year start`() {
        val pattern = FiscalYearPatternFixtures.createPattern(tenantId, "11月開始", 11, 1)
        val date = LocalDate.of(2025, 11, 2)
        
        assertEquals(2025, pattern.getFiscalYear(date))
    }

    @Test
    fun `getFiscalYear should return correct year for November start pattern - next calendar year`() {
        val pattern = FiscalYearPatternFixtures.createPattern(tenantId, "11月開始", 11, 1)
        val date = LocalDate.of(2026, 1, 15)
        
        assertEquals(2025, pattern.getFiscalYear(date))
    }

    // Fiscal year calculation tests - January start pattern (1月開始 - calendar year)
    @Test
    fun `getFiscalYear should return correct year for January start pattern - end of fiscal year`() {
        val pattern = FiscalYearPatternFixtures.createPattern(tenantId, "1月開始", 1, 1)
        val date = LocalDate.of(2024, 12, 31)
        
        assertEquals(2024, pattern.getFiscalYear(date))
    }

    @Test
    fun `getFiscalYear should return correct year for January start pattern - on fiscal year start`() {
        val pattern = FiscalYearPatternFixtures.createPattern(tenantId, "1月開始", 1, 1)
        val date = LocalDate.of(2025, 1, 1)
        
        assertEquals(2025, pattern.getFiscalYear(date))
    }

    @Test
    fun `getFiscalYear should return correct year for January start pattern - end of calendar year`() {
        val pattern = FiscalYearPatternFixtures.createPattern(tenantId, "1月開始", 1, 1)
        val date = LocalDate.of(2025, 12, 31)
        
        assertEquals(2025, pattern.getFiscalYear(date))
    }

    // Fiscal year range calculation tests
    @Test
    fun `getFiscalYearRange should return correct range for April start pattern`() {
        val pattern = FiscalYearPatternFixtures.createPattern(tenantId, "4月開始", 4, 1)
        val range = pattern.getFiscalYearRange(2025)
        
        assertEquals(LocalDate.of(2025, 4, 1), range.first)
        assertEquals(LocalDate.of(2026, 3, 31), range.second)
    }

    @Test
    fun `getFiscalYearRange should return correct range for November start pattern`() {
        val pattern = FiscalYearPatternFixtures.createPattern(tenantId, "11月開始", 11, 1)
        val range = pattern.getFiscalYearRange(2025)
        
        assertEquals(LocalDate.of(2025, 11, 1), range.first)
        assertEquals(LocalDate.of(2026, 10, 31), range.second)
    }

    @Test
    fun `getFiscalYearRange should return correct range for January start pattern`() {
        val pattern = FiscalYearPatternFixtures.createPattern(tenantId, "1月開始", 1, 1)
        val range = pattern.getFiscalYearRange(2025)
        
        assertEquals(LocalDate.of(2025, 1, 1), range.first)
        assertEquals(LocalDate.of(2025, 12, 31), range.second)
    }

    // Edge case: leap year handling
    @Test
    fun `getFiscalYear should handle leap year correctly`() {
        val pattern = FiscalYearPatternFixtures.createPattern(tenantId, "2月開始", 2, 29)
        // 2024 is a leap year, 2025 is not
        val leapYearDate = LocalDate.of(2024, 2, 29)
        
        assertEquals(2024, pattern.getFiscalYear(leapYearDate))
    }

    // Comprehensive test using fixtures
    @Test
    fun `getFiscalYear should match all test cases from fixtures`() {
        FiscalYearPatternFixtures.fiscalYearCalculationTestCases.forEach { (patternData, dateStr, expectedYear) ->
            val pattern = FiscalYearPatternFixtures.createPattern(
                tenantId = tenantId,
                name = "Test Pattern",
                startMonth = patternData["startMonth"] as Int,
                startDay = patternData["startDay"] as Int
            )
            val date = LocalDate.parse(dateStr)
            
            assertEquals(
                expectedYear,
                pattern.getFiscalYear(date),
                "Failed for pattern ${patternData} with date $dateStr"
            )
        }
    }

    @Test
    fun `getFiscalYearRange should match all test cases from fixtures`() {
        FiscalYearPatternFixtures.fiscalYearRangeTestCases.forEach { (patternData, fiscalYear, expectedRange) ->
            val pattern = FiscalYearPatternFixtures.createPattern(
                tenantId = tenantId,
                name = "Test Pattern",
                startMonth = patternData["startMonth"] as Int,
                startDay = patternData["startDay"] as Int
            )
            val range = pattern.getFiscalYearRange(fiscalYear)
            
            assertEquals(
                LocalDate.parse(expectedRange.first),
                range.first,
                "Failed start date for pattern ${patternData} with fiscal year $fiscalYear"
            )
            assertEquals(
                LocalDate.parse(expectedRange.second),
                range.second,
                "Failed end date for pattern ${patternData} with fiscal year $fiscalYear"
            )
        }
    }
}
