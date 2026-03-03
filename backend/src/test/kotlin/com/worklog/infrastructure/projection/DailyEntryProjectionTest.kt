package com.worklog.infrastructure.projection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Unit tests for DailyEntryProjection record.
 */
class DailyEntryProjectionTest {

    @Test
    fun `empty should return projection with zero hours and DRAFT status for weekday`() {
        val date = LocalDate.of(2025, 1, 6) // Monday

        val projection = DailyEntryProjection.empty(date, false)

        assertEquals(date, projection.date())
        assertEquals(BigDecimal.ZERO, projection.totalWorkHours())
        assertEquals(BigDecimal.ZERO, projection.totalAbsenceHours())
        assertEquals("DRAFT", projection.status())
        assertFalse(projection.isWeekend())
        assertFalse(projection.isHoliday())
        assertFalse(projection.hasProxyEntries())
        assertEquals(BigDecimal.ZERO, projection.standardDailyHours())
        assertEquals(BigDecimal.ZERO, projection.overtimeHours())
    }

    @Test
    fun `empty should return projection with isWeekend true for weekend`() {
        val date = LocalDate.of(2025, 1, 4) // Saturday

        val projection = DailyEntryProjection.empty(date, true)

        assertEquals(date, projection.date())
        assertTrue(projection.isWeekend())
        assertEquals(BigDecimal.ZERO, projection.standardDailyHours())
        assertEquals(BigDecimal.ZERO, projection.overtimeHours())
    }

    @Test
    fun `constructor should accept all fields including standardDailyHours and overtimeHours`() {
        val date = LocalDate.of(2025, 1, 6)
        val totalWorkHours = BigDecimal("10.50")
        val totalAbsenceHours = BigDecimal("0.00")
        val standardDailyHours = BigDecimal("8.00")
        val overtimeHours = BigDecimal("2.50")

        val projection = DailyEntryProjection(
            date,
            totalWorkHours,
            totalAbsenceHours,
            "SUBMITTED",
            false,
            false,
            true,
            standardDailyHours,
            overtimeHours,
        )

        assertEquals(date, projection.date())
        assertEquals(totalWorkHours, projection.totalWorkHours())
        assertEquals(totalAbsenceHours, projection.totalAbsenceHours())
        assertEquals("SUBMITTED", projection.status())
        assertFalse(projection.isWeekend())
        assertFalse(projection.isHoliday())
        assertTrue(projection.hasProxyEntries())
        assertEquals(standardDailyHours, projection.standardDailyHours())
        assertEquals(overtimeHours, projection.overtimeHours())
    }

    @Test
    fun `constructor should handle holiday entries`() {
        val date = LocalDate.of(2025, 1, 1) // New Year

        val projection = DailyEntryProjection(
            date,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "DRAFT",
            false,
            true,
            false,
            BigDecimal("8.00"),
            BigDecimal.ZERO,
        )

        assertTrue(projection.isHoliday())
        assertFalse(projection.isWeekend())
        assertEquals(BigDecimal("8.00"), projection.standardDailyHours())
    }
}
