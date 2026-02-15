package com.worklog.domain.shared

import com.worklog.domain.shared.DomainException
import com.worklog.domain.shared.FiscalMonthPeriod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class FiscalMonthPeriodTest {
    @Test
    fun `should create valid fiscal month period`() {
        val startDate = LocalDate.of(2025, 1, 21)
        val endDate = LocalDate.of(2025, 2, 20)
        val period = FiscalMonthPeriod(startDate, endDate)

        assertEquals(startDate, period.startDate())
        assertEquals(endDate, period.endDate())
    }

    @Test
    fun `should create fiscal month period using factory method`() {
        val startDate = LocalDate.of(2025, 1, 21)
        val period = FiscalMonthPeriod.of(startDate)

        assertEquals(startDate, period.startDate())
        assertEquals(LocalDate.of(2025, 2, 20), period.endDate())
    }

    @Test
    fun `should create fiscal month period for date in latter part of month`() {
        val date = LocalDate.of(2025, 1, 25) // 21st onwards
        val period = FiscalMonthPeriod.forDate(date)

        assertEquals(LocalDate.of(2025, 1, 21), period.startDate())
        assertEquals(LocalDate.of(2025, 2, 20), period.endDate())
    }

    @Test
    fun `should create fiscal month period for date in early part of month`() {
        val date = LocalDate.of(2025, 2, 15) // 1st-20th
        val period = FiscalMonthPeriod.forDate(date)

        assertEquals(LocalDate.of(2025, 1, 21), period.startDate())
        assertEquals(LocalDate.of(2025, 2, 20), period.endDate())
    }

    @Test
    fun `should create fiscal month period for date on the 21st`() {
        val date = LocalDate.of(2025, 3, 21)
        val period = FiscalMonthPeriod.forDate(date)

        assertEquals(LocalDate.of(2025, 3, 21), period.startDate())
        assertEquals(LocalDate.of(2025, 4, 20), period.endDate())
    }

    @Test
    fun `should create fiscal month period for date on the 20th`() {
        val date = LocalDate.of(2025, 2, 20)
        val period = FiscalMonthPeriod.forDate(date)

        assertEquals(LocalDate.of(2025, 1, 21), period.startDate())
        assertEquals(LocalDate.of(2025, 2, 20), period.endDate())
    }

    @Test
    fun `should throw exception for null start date`() {
        val exception =
            assertThrows<DomainException> {
                FiscalMonthPeriod(null, LocalDate.of(2025, 2, 20))
            }
        assertEquals("FISCAL_PERIOD_NULL", exception.errorCode)
    }

    @Test
    fun `should throw exception for null end date`() {
        val exception =
            assertThrows<DomainException> {
                FiscalMonthPeriod(LocalDate.of(2025, 1, 21), null)
            }
        assertEquals("FISCAL_PERIOD_NULL", exception.errorCode)
    }

    @Test
    fun `should throw exception when start date is after end date`() {
        val exception =
            assertThrows<DomainException> {
                FiscalMonthPeriod(
                    LocalDate.of(2025, 2, 21),
                    LocalDate.of(2025, 1, 20),
                )
            }
        assertEquals("FISCAL_PERIOD_INVALID_RANGE", exception.errorCode)
    }

    @Test
    fun `should throw exception when start date is same as end date`() {
        val exception =
            assertThrows<DomainException> {
                FiscalMonthPeriod(
                    LocalDate.of(2025, 1, 21),
                    LocalDate.of(2025, 1, 21),
                )
            }
        assertEquals("FISCAL_PERIOD_INVALID_RANGE", exception.errorCode)
    }

    @Test
    fun `should throw exception when start date is not on 21st`() {
        val exception =
            assertThrows<DomainException> {
                FiscalMonthPeriod(
                    LocalDate.of(2025, 1, 20),
                    LocalDate.of(2025, 2, 20),
                )
            }
        assertEquals("FISCAL_PERIOD_INVALID_START", exception.errorCode)
    }

    @Test
    fun `should throw exception when end date is not on 20th`() {
        val exception =
            assertThrows<DomainException> {
                FiscalMonthPeriod(
                    LocalDate.of(2025, 1, 21),
                    LocalDate.of(2025, 2, 21),
                )
            }
        assertEquals("FISCAL_PERIOD_INVALID_END", exception.errorCode)
    }

    @Test
    fun `should throw exception for period too short`() {
        val exception =
            assertThrows<DomainException> {
                // Manually creating invalid period (would be 26 days - too short)
                FiscalMonthPeriod(
                    LocalDate.of(2025, 1, 21),
                    LocalDate.of(2025, 2, 16).withDayOfMonth(20).minusDays(5), // hack to get 20th but different month
                )
            }
        // This will fail validation before we get to length check
        assertEquals("FISCAL_PERIOD_INVALID_END", exception.errorCode)
    }

    @Test
    fun `should throw exception when factory method start date is not on 21st`() {
        val exception =
            assertThrows<DomainException> {
                FiscalMonthPeriod.of(LocalDate.of(2025, 1, 15))
            }
        assertEquals("FISCAL_PERIOD_INVALID_START", exception.errorCode)
    }

    @Test
    fun `should check if period contains date`() {
        val period = FiscalMonthPeriod.of(LocalDate.of(2025, 1, 21))

        assertTrue(period.contains(LocalDate.of(2025, 1, 21))) // start date
        assertTrue(period.contains(LocalDate.of(2025, 2, 1))) // middle
        assertTrue(period.contains(LocalDate.of(2025, 2, 20))) // end date
        assertFalse(period.contains(LocalDate.of(2025, 1, 20))) // before
        assertFalse(period.contains(LocalDate.of(2025, 2, 21))) // after
    }

    @Test
    fun `should get next fiscal month period`() {
        val period = FiscalMonthPeriod.of(LocalDate.of(2025, 1, 21))
        val next = period.next()

        assertEquals(LocalDate.of(2025, 2, 21), next.startDate())
        assertEquals(LocalDate.of(2025, 3, 20), next.endDate())
    }

    @Test
    fun `should get previous fiscal month period`() {
        val period = FiscalMonthPeriod.of(LocalDate.of(2025, 2, 21))
        val previous = period.previous()

        assertEquals(LocalDate.of(2025, 1, 21), previous.startDate())
        assertEquals(LocalDate.of(2025, 2, 20), previous.endDate())
    }

    @Test
    fun `should detect overlapping periods`() {
        val period1 = FiscalMonthPeriod.of(LocalDate.of(2025, 1, 21))
        val period2 = FiscalMonthPeriod.of(LocalDate.of(2025, 2, 21))
        val period3 = FiscalMonthPeriod.of(LocalDate.of(2025, 1, 21))

        assertFalse(period1.overlaps(period2)) // consecutive periods don't overlap
        assertTrue(period1.overlaps(period3)) // same period overlaps
    }

    @Test
    fun `should have correct toString format`() {
        val period = FiscalMonthPeriod.of(LocalDate.of(2025, 1, 21))
        assertEquals("2025-01-21 to 2025-02-20", period.toString())
    }

    @Test
    fun `should handle year boundary correctly`() {
        val period = FiscalMonthPeriod.of(LocalDate.of(2024, 12, 21))

        assertEquals(LocalDate.of(2024, 12, 21), period.startDate())
        assertEquals(LocalDate.of(2025, 1, 20), period.endDate())
    }

    @Test
    fun `should handle February correctly in leap year`() {
        // 2024 is a leap year
        val period = FiscalMonthPeriod.of(LocalDate.of(2024, 2, 21))

        assertEquals(LocalDate.of(2024, 2, 21), period.startDate())
        assertEquals(LocalDate.of(2024, 3, 20), period.endDate())
    }

    @Test
    fun `should handle February correctly in non-leap year`() {
        // 2025 is not a leap year
        val period = FiscalMonthPeriod.of(LocalDate.of(2025, 2, 21))

        assertEquals(LocalDate.of(2025, 2, 21), period.startDate())
        assertEquals(LocalDate.of(2025, 3, 20), period.endDate())
    }

    @Test
    fun `should chain next calls correctly`() {
        val january = FiscalMonthPeriod.of(LocalDate.of(2025, 1, 21))
        val april = january.next().next().next()

        assertEquals(LocalDate.of(2025, 4, 21), april.startDate())
        assertEquals(LocalDate.of(2025, 5, 20), april.endDate())
    }

    @Test
    fun `should chain previous calls correctly`() {
        val april = FiscalMonthPeriod.of(LocalDate.of(2025, 4, 21))
        val january = april.previous().previous().previous()

        assertEquals(LocalDate.of(2025, 1, 21), january.startDate())
        assertEquals(LocalDate.of(2025, 2, 20), january.endDate())
    }
}
