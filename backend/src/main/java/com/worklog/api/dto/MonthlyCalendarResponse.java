package com.worklog.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for monthly calendar view.
 */
public record MonthlyCalendarResponse(
    UUID memberId,
    String memberName,
    LocalDate periodStart,
    LocalDate periodEnd,
    List<DailyCalendarEntry> dates
) {
}
