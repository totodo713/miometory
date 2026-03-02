package com.worklog.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO for a single day in the calendar view.
 *
 * @param rejectionSource "monthly" if rejected via monthly approval, "daily" if rejected via daily rejection, null otherwise
 * @param rejectionReason The rejection reason (from monthly approval or daily rejection log), null if not rejected
 * @param holidayName The holiday name in English (nullable, only set when isHoliday is true)
 * @param holidayNameJa The holiday name in Japanese (nullable, only set when isHoliday is true)
 */
public record DailyCalendarEntry(
        LocalDate date,
        BigDecimal totalWorkHours,
        BigDecimal totalAbsenceHours,
        String status,
        boolean isWeekend,
        boolean isHoliday,
        String holidayName,
        String holidayNameJa,
        boolean hasProxyEntries,
        String rejectionSource,
        String rejectionReason) {

    /**
     * Backward-compatible constructor without holiday name fields.
     */
    public DailyCalendarEntry(
            LocalDate date,
            BigDecimal totalWorkHours,
            BigDecimal totalAbsenceHours,
            String status,
            boolean isWeekend,
            boolean isHoliday,
            boolean hasProxyEntries,
            String rejectionSource,
            String rejectionReason) {
        this(
                date,
                totalWorkHours,
                totalAbsenceHours,
                status,
                isWeekend,
                isHoliday,
                null,
                null,
                hasProxyEntries,
                rejectionSource,
                rejectionReason);
    }

    /**
     * Backward-compatible constructor without rejection and holiday name fields.
     */
    public DailyCalendarEntry(
            LocalDate date,
            BigDecimal totalWorkHours,
            BigDecimal totalAbsenceHours,
            String status,
            boolean isWeekend,
            boolean isHoliday,
            boolean hasProxyEntries) {
        this(
                date,
                totalWorkHours,
                totalAbsenceHours,
                status,
                isWeekend,
                isHoliday,
                null,
                null,
                hasProxyEntries,
                null,
                null);
    }
}
