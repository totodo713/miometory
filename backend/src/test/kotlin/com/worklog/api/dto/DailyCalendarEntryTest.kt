package com.worklog.api.dto

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailyCalendarEntryTest {
    private val date = LocalDate.of(2024, 1, 1)
    private val workHours = BigDecimal("8.0")
    private val absenceHours = BigDecimal.ZERO

    @Test
    fun `canonical constructor should set all fields`() {
        val entry = DailyCalendarEntry(
            date, workHours, absenceHours, "SUBMITTED",
            false, true, "New Year's Day", "元日",
            false, "daily", "Incorrect hours",
            BigDecimal("8.0"), BigDecimal("0.0"),
        )

        assertEquals(date, entry.date())
        assertEquals(workHours, entry.totalWorkHours())
        assertEquals(absenceHours, entry.totalAbsenceHours())
        assertEquals("SUBMITTED", entry.status())
        assertEquals(false, entry.isWeekend())
        assertEquals(true, entry.isHoliday())
        assertEquals("New Year's Day", entry.holidayName())
        assertEquals("元日", entry.holidayNameJa())
        assertEquals(false, entry.hasProxyEntries())
        assertEquals("daily", entry.rejectionSource())
        assertEquals("Incorrect hours", entry.rejectionReason())
        assertEquals(BigDecimal("8.0"), entry.standardDailyHours())
        assertEquals(BigDecimal("0.0"), entry.overtimeHours())
    }

    @Test
    fun `9-arg constructor should default holiday names and overtime to null`() {
        val entry = DailyCalendarEntry(
            date, workHours, absenceHours, "DRAFT",
            false, true,
            false, "monthly", "Needs revision",
        )

        assertEquals(true, entry.isHoliday())
        assertNull(entry.holidayName())
        assertNull(entry.holidayNameJa())
        assertEquals("monthly", entry.rejectionSource())
        assertEquals("Needs revision", entry.rejectionReason())
        assertNull(entry.standardDailyHours())
        assertNull(entry.overtimeHours())
    }

    @Test
    fun `7-arg constructor should default holiday names rejection and overtime to null`() {
        val entry = DailyCalendarEntry(
            date,
            workHours,
            absenceHours,
            "DRAFT",
            true,
            false,
            false,
        )

        assertEquals(false, entry.isHoliday())
        assertNull(entry.holidayName())
        assertNull(entry.holidayNameJa())
        assertNull(entry.rejectionSource())
        assertNull(entry.rejectionReason())
        assertNull(entry.standardDailyHours())
        assertNull(entry.overtimeHours())
    }
}
