package com.worklog.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO for a single day in the calendar view.
 *
 * @param rejectionSource "monthly" if rejected via monthly approval, "daily" if rejected via daily rejection, null otherwise
 * @param rejectionReason The rejection reason (from monthly approval or daily rejection log), null if not rejected
 */
public record DailyCalendarEntry(
        LocalDate date,
        BigDecimal totalWorkHours,
        BigDecimal totalAbsenceHours,
        String status,
        boolean isWeekend,
        boolean isHoliday,
        boolean hasProxyEntries,
        String rejectionSource,
        String rejectionReason) {

    /**
     * Backward-compatible constructor without rejection fields.
     */
    public DailyCalendarEntry(
            LocalDate date,
            BigDecimal totalWorkHours,
            BigDecimal totalAbsenceHours,
            String status,
            boolean isWeekend,
            boolean isHoliday,
            boolean hasProxyEntries) {
        this(date, totalWorkHours, totalAbsenceHours, status, isWeekend, isHoliday, hasProxyEntries, null, null);
    }
}
