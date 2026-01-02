package com.worklog.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO for a single day in the calendar view.
 */
public record DailyCalendarEntry(
    LocalDate date,
    BigDecimal totalWorkHours,
    BigDecimal totalAbsenceHours,
    String status,
    boolean isWeekend,
    boolean isHoliday
) {
}
