package com.worklog.infrastructure.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Projection record for a single day's calendar entry.
 * Contains aggregated work hours and status information.
 */
public record DailyEntryProjection(
    LocalDate date,
    BigDecimal totalWorkHours,
    BigDecimal totalAbsenceHours,
    String status,
    boolean isWeekend,
    boolean isHoliday
) {
    /**
     * Creates a projection with default values.
     */
    public static DailyEntryProjection empty(LocalDate date, boolean isWeekend) {
        return new DailyEntryProjection(
            date,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "DRAFT",
            isWeekend,
            false
        );
    }
}
