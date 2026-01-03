package com.worklog.api;

import com.worklog.api.dto.DailyCalendarEntry;
import com.worklog.api.dto.MonthlyCalendarResponse;
import com.worklog.domain.shared.DomainException;
import com.worklog.infrastructure.projection.DailyEntryProjection;
import com.worklog.infrastructure.projection.MonthlyCalendarProjection;
import com.worklog.infrastructure.projection.MonthlySummaryData;
import com.worklog.infrastructure.projection.MonthlySummaryProjection;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for calendar view operations.
 * 
 * Provides read-only endpoints for viewing work log data in calendar format.
 */
@RestController
@RequestMapping("/api/v1/worklog/calendar")
public class CalendarController {

    private final MonthlyCalendarProjection calendarProjection;
    private final MonthlySummaryProjection summaryProjection;

    public CalendarController(
        MonthlyCalendarProjection calendarProjection,
        MonthlySummaryProjection summaryProjection
    ) {
        this.calendarProjection = calendarProjection;
        this.summaryProjection = summaryProjection;
    }

    /**
     * Get monthly calendar view for a member.
     * 
     * GET /api/v1/worklog/calendar/{year}/{month}?memberId=...
     * 
     * The month parameter represents the fiscal month, where the period runs
     * from the 21st of the previous month to the 20th of the specified month.
     * 
     * For example, calendar/2026/01 returns the period 2025-12-21 to 2026-01-20.
     * 
     * @param year Year (2020-2100)
     * @param month Month (1-12)
     * @param memberId Member ID (required for now, will default to authenticated user)
     * @return Monthly calendar with daily entries
     */
    @GetMapping("/{year}/{month}")
    public ResponseEntity<MonthlyCalendarResponse> getMonthlyCalendar(
        @PathVariable int year,
        @PathVariable int month,
        @RequestParam(required = false) UUID memberId
    ) {
        // Validate year and month
        if (year < 2020 || year > 2100) {
            throw new DomainException("INVALID_YEAR", "Year must be between 2020 and 2100");
        }
        if (month < 1 || month > 12) {
            throw new DomainException("INVALID_MONTH", "Month must be between 1 and 12");
        }

        // For now, require memberId. In production, get from SecurityContext
        if (memberId == null) {
            throw new DomainException("MEMBER_ID_REQUIRED", "memberId parameter is required");
        }

        // Calculate fiscal month period (21st of previous month to 20th of current month)
        LocalDate periodStart = YearMonth.of(year, month).atDay(1).minusMonths(1).withDayOfMonth(21);
        LocalDate periodEnd = YearMonth.of(year, month).atDay(20);

        // Get daily entries from projection
        List<DailyEntryProjection> projections = calendarProjection.getDailyEntries(
            memberId,
            periodStart,
            periodEnd
        );

        // Convert to response DTOs
        List<DailyCalendarEntry> entries = projections.stream()
            .map(p -> new DailyCalendarEntry(
                p.date(),
                p.totalWorkHours(),
                p.totalAbsenceHours(),
                p.status(),
                p.isWeekend(),
                p.isHoliday()
            ))
            .collect(Collectors.toList());

        MonthlyCalendarResponse response = new MonthlyCalendarResponse(
            memberId,
            "Member Name", // TODO: Fetch from member repository
            periodStart,
            periodEnd,
            entries
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get monthly summary for a member.
     * 
     * GET /api/v1/worklog/calendar/{year}/{month}/summary?memberId=...
     * 
     * Returns aggregated statistics including project breakdown with hours and percentages.
     * 
     * @param year Year (2020-2100)
     * @param month Month (1-12)
     * @param memberId Member ID (required for now, will default to authenticated user)
     * @return Monthly summary with project breakdown
     */
    @GetMapping("/{year}/{month}/summary")
    public ResponseEntity<MonthlySummaryData> getMonthlySummary(
        @PathVariable int year,
        @PathVariable int month,
        @RequestParam(required = false) UUID memberId
    ) {
        // Validate year and month
        if (year < 2020 || year > 2100) {
            throw new DomainException("INVALID_YEAR", "Year must be between 2020 and 2100");
        }
        if (month < 1 || month > 12) {
            throw new DomainException("INVALID_MONTH", "Month must be between 1 and 12");
        }

        // For now, require memberId. In production, get from SecurityContext
        if (memberId == null) {
            throw new DomainException("MEMBER_ID_REQUIRED", "memberId parameter is required");
        }

        // Get summary data from projection
        MonthlySummaryData summary = summaryProjection.getMonthlySummary(memberId, year, month);

        return ResponseEntity.ok(summary);
    }
}
