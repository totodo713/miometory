package com.worklog.domain.shared

import com.worklog.domain.shared.DateRange
import com.worklog.domain.shared.DomainException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class DateRangeTest {

    @Test
    fun `should create valid date range`() {
        val startDate = LocalDate.of(2025, 1, 1)
        val endDate = LocalDate.of(2025, 1, 31)
        val range = DateRange(startDate, endDate)
        
        assertEquals(startDate, range.startDate())
        assertEquals(endDate, range.endDate())
    }

    @Test
    fun `should create date range using factory method`() {
        val startDate = LocalDate.of(2025, 1, 1)
        val endDate = LocalDate.of(2025, 1, 31)
        val range = DateRange.of(startDate, endDate)
        
        assertEquals(startDate, range.startDate())
        assertEquals(endDate, range.endDate())
    }

    @Test
    fun `should create single day range`() {
        val date = LocalDate.of(2025, 1, 15)
        val range = DateRange.singleDay(date)
        
        assertEquals(date, range.startDate())
        assertEquals(date, range.endDate())
        assertTrue(range.isSingleDay)
    }

    @Test
    fun `should allow same start and end date`() {
        val date = LocalDate.of(2025, 1, 15)
        val range = DateRange(date, date)
        
        assertEquals(date, range.startDate())
        assertEquals(date, range.endDate())
    }

    @Test
    fun `should throw exception for null start date`() {
        val exception = assertThrows<DomainException> {
            DateRange(null, LocalDate.of(2025, 1, 31))
        }
        assertEquals("DATE_RANGE_NULL", exception.errorCode)
    }

    @Test
    fun `should throw exception for null end date`() {
        val exception = assertThrows<DomainException> {
            DateRange(LocalDate.of(2025, 1, 1), null)
        }
        assertEquals("DATE_RANGE_NULL", exception.errorCode)
    }

    @Test
    fun `should throw exception when start date is after end date`() {
        val exception = assertThrows<DomainException> {
            DateRange(
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 1, 1)
            )
        }
        assertEquals("DATE_RANGE_INVALID", exception.errorCode)
    }

    @Test
    fun `should check if range contains date`() {
        val range = DateRange.of(
            LocalDate.of(2025, 1, 10),
            LocalDate.of(2025, 1, 20)
        )
        
        assertTrue(range.contains(LocalDate.of(2025, 1, 10)))  // start date
        assertTrue(range.contains(LocalDate.of(2025, 1, 15)))  // middle
        assertTrue(range.contains(LocalDate.of(2025, 1, 20)))  // end date
        assertFalse(range.contains(LocalDate.of(2025, 1, 9)))  // before
        assertFalse(range.contains(LocalDate.of(2025, 1, 21))) // after
    }

    @Test
    fun `should detect overlapping ranges`() {
        val range1 = DateRange.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 15))
        val range2 = DateRange.of(LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 20))
        val range3 = DateRange.of(LocalDate.of(2025, 1, 16), LocalDate.of(2025, 1, 31))
        val range4 = DateRange.of(LocalDate.of(2025, 1, 15), LocalDate.of(2025, 1, 15))
        
        assertTrue(range1.overlaps(range2))   // overlapping
        assertTrue(range2.overlaps(range1))   // symmetric
        assertFalse(range1.overlaps(range3))  // non-overlapping
        assertTrue(range1.overlaps(range4))   // edge case: single day at end
    }

    @Test
    fun `should detect adjacent but non-overlapping ranges`() {
        val range1 = DateRange.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 15))
        val range2 = DateRange.of(LocalDate.of(2025, 1, 16), LocalDate.of(2025, 1, 31))
        
        assertFalse(range1.overlaps(range2))
        assertFalse(range2.overlaps(range1))
    }

    @Test
    fun `should check if range contains another range`() {
        val outer = DateRange.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31))
        val inner = DateRange.of(LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 20))
        val partial = DateRange.of(LocalDate.of(2025, 1, 20), LocalDate.of(2025, 2, 5))
        
        assertTrue(outer.contains(inner))
        assertFalse(inner.contains(outer))
        assertFalse(outer.contains(partial))  // extends beyond
    }

    @Test
    fun `should calculate duration in days`() {
        val singleDay = DateRange.singleDay(LocalDate.of(2025, 1, 15))
        val week = DateRange.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 7))
        val month = DateRange.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31))
        
        assertEquals(1, singleDay.durationInDays())
        assertEquals(7, week.durationInDays())
        assertEquals(31, month.durationInDays())
    }

    @Test
    fun `should detect single day range`() {
        val singleDay = DateRange.singleDay(LocalDate.of(2025, 1, 15))
        val multiDay = DateRange.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2))
        
        assertTrue(singleDay.isSingleDay)
        assertFalse(multiDay.isSingleDay)
    }

    @Test
    fun `should extend range by days`() {
        val range = DateRange.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 15))
        val extended = range.extendBy(5)
        
        assertEquals(LocalDate.of(2025, 1, 1), extended.startDate())
        assertEquals(LocalDate.of(2025, 1, 20), extended.endDate())
    }

    @Test
    fun `should shorten range with negative days`() {
        val range = DateRange.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 15))
        val shortened = range.extendBy(-5)
        
        assertEquals(LocalDate.of(2025, 1, 1), shortened.startDate())
        assertEquals(LocalDate.of(2025, 1, 10), shortened.endDate())
    }

    @Test
    fun `should throw exception when extending creates invalid range`() {
        val range = DateRange.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 5))
        val exception = assertThrows<DomainException> {
            range.extendBy(-10) // would make end date before start date
        }
        assertEquals("DATE_RANGE_INVALID", exception.errorCode)
    }

    @Test
    fun `should start earlier by days`() {
        val range = DateRange.of(LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 20))
        val earlier = range.startEarlierBy(5)
        
        assertEquals(LocalDate.of(2025, 1, 5), earlier.startDate())
        assertEquals(LocalDate.of(2025, 1, 20), earlier.endDate())
    }

    @Test
    fun `should start later with negative days`() {
        val range = DateRange.of(LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 20))
        val later = range.startEarlierBy(-5)
        
        assertEquals(LocalDate.of(2025, 1, 15), later.startDate())
        assertEquals(LocalDate.of(2025, 1, 20), later.endDate())
    }

    @Test
    fun `should throw exception when startEarlierBy creates invalid range`() {
        val range = DateRange.of(LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 15))
        val exception = assertThrows<DomainException> {
            range.startEarlierBy(-10) // would make start date after end date
        }
        assertEquals("DATE_RANGE_INVALID", exception.errorCode)
    }

    @Test
    fun `should have correct toString for multi-day range`() {
        val range = DateRange.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31))
        assertEquals("2025-01-01 to 2025-01-31", range.toString())
    }

    @Test
    fun `should have correct toString for single-day range`() {
        val range = DateRange.singleDay(LocalDate.of(2025, 1, 15))
        assertEquals("2025-01-15", range.toString())
    }

    @Test
    fun `should handle year boundary correctly`() {
        val range = DateRange.of(LocalDate.of(2024, 12, 25), LocalDate.of(2025, 1, 5))
        
        assertEquals(12, range.durationInDays())
        assertTrue(range.contains(LocalDate.of(2024, 12, 31)))
        assertTrue(range.contains(LocalDate.of(2025, 1, 1)))
    }

    @Test
    fun `should handle leap year February correctly`() {
        // 2024 is a leap year
        val range = DateRange.of(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29))
        assertEquals(29, range.durationInDays())
    }
}
