package com.worklog.domain.monthlyperiod

import com.worklog.domain.shared.DomainException
import com.worklog.domain.tenant.TenantId
import com.worklog.fixtures.MonthlyPeriodPatternFixtures
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for MonthlyPeriodPattern entity.
 * Tests domain logic for monthly period calculation without infrastructure dependencies.
 */
class MonthlyPeriodPatternTest {
    private val tenantId = TenantId(UUID.randomUUID())

    @Test
    fun `create should generate valid pattern`() {
        val pattern =
            MonthlyPeriodPatternFixtures.createPattern(
                tenantId = tenantId,
                name = "21日締め",
                startDay = 21,
            )

        assertNotNull(pattern.id)
        assertEquals(tenantId.value(), pattern.tenantId.value())
        assertEquals("21日締め", pattern.name)
        assertEquals(21, pattern.startDay)
    }

    @Test
    fun `create should trim whitespace from name`() {
        val pattern =
            MonthlyPeriodPatternFixtures.createPattern(
                tenantId = tenantId,
                name = "  21st Day Close  ",
                startDay = 21,
            )

        assertEquals("21st Day Close", pattern.name)
    }

    // Validation tests for startDay
    @Test
    fun `create should fail with startDay less than 1`() {
        val exception =
            assertFailsWith<DomainException> {
                MonthlyPeriodPatternFixtures.createPattern(tenantId, "Invalid", 0)
            }
        assertEquals("INVALID_START_DAY", exception.errorCode)
    }

    @Test
    fun `create should fail with startDay greater than 28`() {
        val exception =
            assertFailsWith<DomainException> {
                MonthlyPeriodPatternFixtures.createPattern(tenantId, "Invalid", 29)
            }
        assertEquals("INVALID_START_DAY", exception.errorCode)
    }

    @Test
    fun `create should fail with negative startDay`() {
        val exception =
            assertFailsWith<DomainException> {
                MonthlyPeriodPatternFixtures.createPattern(tenantId, "Invalid", -1)
            }
        assertEquals("INVALID_START_DAY", exception.errorCode)
    }

    @Test
    fun `create should allow startDay of 28`() {
        val pattern = MonthlyPeriodPatternFixtures.createPattern(tenantId, "28日締め", 28)

        assertEquals(28, pattern.startDay)
    }

    // Validation tests for name
    @Test
    fun `create should fail with empty name`() {
        val exception =
            assertFailsWith<DomainException> {
                MonthlyPeriodPatternFixtures.createPattern(tenantId, "", 21)
            }
        assertEquals("NAME_REQUIRED", exception.errorCode)
    }

    @Test
    fun `create should fail with name too long`() {
        val longName = "a".repeat(101)
        val exception =
            assertFailsWith<DomainException> {
                MonthlyPeriodPatternFixtures.createPattern(tenantId, longName, 21)
            }
        assertEquals("NAME_TOO_LONG", exception.errorCode)
    }

    // Monthly period calculation tests - 21日締め pattern (startDay = 21)
    @Test
    fun `getMonthlyPeriod should return correct period for 21st day pattern - date within period`() {
        val pattern = MonthlyPeriodPatternFixtures.createPattern(tenantId, "21日締め", 21)
        val date = LocalDate.of(2025, 1, 15)
        val period = pattern.getMonthlyPeriod(date)

        assertEquals(LocalDate.of(2024, 12, 21), period.startDate)
        assertEquals(LocalDate.of(2025, 1, 20), period.endDate)
        assertEquals(1, period.displayMonth)
        assertEquals(2025, period.displayYear)
    }

    @Test
    fun `getMonthlyPeriod should return correct period for 21st day pattern - date at period start`() {
        val pattern = MonthlyPeriodPatternFixtures.createPattern(tenantId, "21日締め", 21)
        val date = LocalDate.of(2025, 1, 21)
        val period = pattern.getMonthlyPeriod(date)

        assertEquals(LocalDate.of(2025, 1, 21), period.startDate)
        assertEquals(LocalDate.of(2025, 2, 20), period.endDate)
        assertEquals(2, period.displayMonth)
        assertEquals(2025, period.displayYear)
    }

    @Test
    fun `getMonthlyPeriod should return correct period for 21st day pattern - date after period start`() {
        val pattern = MonthlyPeriodPatternFixtures.createPattern(tenantId, "21日締め", 21)
        val date = LocalDate.of(2025, 1, 25)
        val period = pattern.getMonthlyPeriod(date)

        assertEquals(LocalDate.of(2025, 1, 21), period.startDate)
        assertEquals(LocalDate.of(2025, 2, 20), period.endDate)
        assertEquals(2, period.displayMonth)
        assertEquals(2025, period.displayYear)
    }

    @Test
    fun `getMonthlyPeriod should handle year boundary crossing for 21st day pattern`() {
        val pattern = MonthlyPeriodPatternFixtures.createPattern(tenantId, "21日締め", 21)
        val date = LocalDate.of(2025, 12, 25)
        val period = pattern.getMonthlyPeriod(date)

        assertEquals(LocalDate.of(2025, 12, 21), period.startDate)
        assertEquals(LocalDate.of(2026, 1, 20), period.endDate)
        assertEquals(1, period.displayMonth)
        assertEquals(2026, period.displayYear)
    }

    @Test
    fun `getMonthlyPeriod should handle February correctly for 21st day pattern`() {
        val pattern = MonthlyPeriodPatternFixtures.createPattern(tenantId, "21日締め", 21)
        val date = LocalDate.of(2025, 2, 15)
        val period = pattern.getMonthlyPeriod(date)

        assertEquals(LocalDate.of(2025, 1, 21), period.startDate)
        assertEquals(LocalDate.of(2025, 2, 20), period.endDate)
        assertEquals(2, period.displayMonth)
        assertEquals(2025, period.displayYear)
    }

    // Monthly period calculation tests - 1日締め pattern (startDay = 1) - month-end close
    @Test
    fun `getMonthlyPeriod should return correct period for 1st day pattern - date at month start`() {
        val pattern = MonthlyPeriodPatternFixtures.createPattern(tenantId, "1日締め", 1)
        val date = LocalDate.of(2025, 1, 1)
        val period = pattern.getMonthlyPeriod(date)

        assertEquals(LocalDate.of(2025, 1, 1), period.startDate)
        assertEquals(LocalDate.of(2025, 1, 31), period.endDate)
        assertEquals(1, period.displayMonth)
        assertEquals(2025, period.displayYear)
    }

    @Test
    fun `getMonthlyPeriod should return correct period for 1st day pattern - date in middle of month`() {
        val pattern = MonthlyPeriodPatternFixtures.createPattern(tenantId, "1日締め", 1)
        val date = LocalDate.of(2025, 1, 15)
        val period = pattern.getMonthlyPeriod(date)

        assertEquals(LocalDate.of(2025, 1, 1), period.startDate)
        assertEquals(LocalDate.of(2025, 1, 31), period.endDate)
        assertEquals(1, period.displayMonth)
        assertEquals(2025, period.displayYear)
    }

    @Test
    fun `getMonthlyPeriod should return correct period for 1st day pattern - date at month end`() {
        val pattern = MonthlyPeriodPatternFixtures.createPattern(tenantId, "1日締め", 1)
        val date = LocalDate.of(2025, 1, 31)
        val period = pattern.getMonthlyPeriod(date)

        assertEquals(LocalDate.of(2025, 1, 1), period.startDate)
        assertEquals(LocalDate.of(2025, 1, 31), period.endDate)
        assertEquals(1, period.displayMonth)
        assertEquals(2025, period.displayYear)
    }

    @Test
    fun `getMonthlyPeriod should handle February with 28 days for 1st day pattern`() {
        val pattern = MonthlyPeriodPatternFixtures.createPattern(tenantId, "1日締め", 1)
        val date = LocalDate.of(2025, 2, 15)
        val period = pattern.getMonthlyPeriod(date)

        assertEquals(LocalDate.of(2025, 2, 1), period.startDate)
        assertEquals(LocalDate.of(2025, 2, 28), period.endDate)
        assertEquals(2, period.displayMonth)
        assertEquals(2025, period.displayYear)
    }

    @Test
    fun `getMonthlyPeriod should handle February with 29 days in leap year for 1st day pattern`() {
        val pattern = MonthlyPeriodPatternFixtures.createPattern(tenantId, "1日締め", 1)
        val date = LocalDate.of(2024, 2, 15)
        val period = pattern.getMonthlyPeriod(date)

        assertEquals(LocalDate.of(2024, 2, 1), period.startDate)
        assertEquals(LocalDate.of(2024, 2, 29), period.endDate)
        assertEquals(2, period.displayMonth)
        assertEquals(2024, period.displayYear)
    }

    // Monthly period calculation tests - 15日締め pattern (startDay = 15)
    @Test
    fun `getMonthlyPeriod should return correct period for 15th day pattern - before period start`() {
        val pattern = MonthlyPeriodPatternFixtures.createPattern(tenantId, "15日締め", 15)
        val date = LocalDate.of(2025, 1, 10)
        val period = pattern.getMonthlyPeriod(date)

        assertEquals(LocalDate.of(2024, 12, 15), period.startDate)
        assertEquals(LocalDate.of(2025, 1, 14), period.endDate)
        assertEquals(1, period.displayMonth)
        assertEquals(2025, period.displayYear)
    }

    @Test
    fun `getMonthlyPeriod should return correct period for 15th day pattern - at period start`() {
        val pattern = MonthlyPeriodPatternFixtures.createPattern(tenantId, "15日締め", 15)
        val date = LocalDate.of(2025, 1, 15)
        val period = pattern.getMonthlyPeriod(date)

        assertEquals(LocalDate.of(2025, 1, 15), period.startDate)
        assertEquals(LocalDate.of(2025, 2, 14), period.endDate)
        assertEquals(2, period.displayMonth)
        assertEquals(2025, period.displayYear)
    }

    @Test
    fun `getMonthlyPeriod should return correct period for 15th day pattern - after period start`() {
        val pattern = MonthlyPeriodPatternFixtures.createPattern(tenantId, "15日締め", 15)
        val date = LocalDate.of(2025, 1, 20)
        val period = pattern.getMonthlyPeriod(date)

        assertEquals(LocalDate.of(2025, 1, 15), period.startDate)
        assertEquals(LocalDate.of(2025, 2, 14), period.endDate)
        assertEquals(2, period.displayMonth)
        assertEquals(2025, period.displayYear)
    }

    // Comprehensive test using fixtures
    @Test
    fun `getMonthlyPeriod should match all test cases from fixtures`() {
        MonthlyPeriodPatternFixtures.monthlyPeriodCalculationTestCases.forEach { testCase ->
            val pattern =
                MonthlyPeriodPatternFixtures.createPattern(
                    tenantId = tenantId,
                    name = "Test Pattern",
                    startDay = testCase.first,
                )
            val date = LocalDate.parse(testCase.second)
            val period = pattern.getMonthlyPeriod(date)

            assertEquals(
                LocalDate.parse(testCase.third),
                period.startDate,
                "Failed start date for startDay=${testCase.first} with date ${testCase.second}",
            )
            assertEquals(
                LocalDate.parse(testCase.fourth),
                period.endDate,
                "Failed end date for startDay=${testCase.first} with date ${testCase.second}",
            )
            assertEquals(
                testCase.fifth,
                period.displayMonth,
                "Failed display month for startDay=${testCase.first} with date ${testCase.second}",
            )
            assertEquals(
                testCase.sixth,
                period.displayYear,
                "Failed display year for startDay=${testCase.first} with date ${testCase.second}",
            )
        }
    }
}
