package com.worklog.api;

import com.worklog.api.dto.DailyCalendarEntry;
import com.worklog.api.dto.MonthlyCalendarResponse;
import com.worklog.domain.approval.MonthlyApproval;
import com.worklog.domain.member.Member;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.shared.FiscalMonthPeriod;
import com.worklog.infrastructure.projection.DailyEntryProjection;
import com.worklog.infrastructure.projection.MonthlyCalendarProjection;
import com.worklog.infrastructure.projection.MonthlySummaryData;
import com.worklog.infrastructure.projection.MonthlySummaryProjection;
import com.worklog.infrastructure.repository.JdbcApprovalRepository;
import com.worklog.infrastructure.repository.JdbcDailyRejectionLogRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private final JdbcApprovalRepository approvalRepository;
    private final JdbcDailyRejectionLogRepository dailyRejectionLogRepository;
    private final JdbcMemberRepository memberRepository;

    public CalendarController(
            MonthlyCalendarProjection calendarProjection,
            MonthlySummaryProjection summaryProjection,
            JdbcApprovalRepository approvalRepository,
            JdbcDailyRejectionLogRepository dailyRejectionLogRepository,
            JdbcMemberRepository memberRepository) {
        this.calendarProjection = calendarProjection;
        this.summaryProjection = summaryProjection;
        this.approvalRepository = approvalRepository;
        this.dailyRejectionLogRepository = dailyRejectionLogRepository;
        this.memberRepository = memberRepository;
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
            @PathVariable int year, @PathVariable int month, @RequestParam(required = false) UUID memberId) {
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
        LocalDate periodStart =
                YearMonth.of(year, month).atDay(1).minusMonths(1).withDayOfMonth(21);
        LocalDate periodEnd = YearMonth.of(year, month).atDay(20);

        // Get daily entries from projection
        List<DailyEntryProjection> projections = calendarProjection.getDailyEntries(memberId, periodStart, periodEnd);

        // Get monthly approval status
        FiscalMonthPeriod fiscalMonth = new FiscalMonthPeriod(periodStart, periodEnd);
        Optional<MonthlyApproval> approval =
                approvalRepository.findByMemberAndFiscalMonth(MemberId.of(memberId), fiscalMonth);

        MonthlyCalendarResponse.MonthlyApprovalSummary approvalSummary = approval.map(
                        a -> new MonthlyCalendarResponse.MonthlyApprovalSummary(
                                a.getId().value(),
                                a.getStatus().toString(),
                                a.getRejectionReason(),
                                a.getReviewedBy() != null ? a.getReviewedBy().value() : null,
                                a.getReviewedBy() != null
                                        ? memberRepository
                                                .findById(a.getReviewedBy())
                                                .map(Member::getDisplayName)
                                                .orElse(null)
                                        : null,
                                a.getReviewedAt()))
                .orElse(null);

        // Get daily rejection log entries for enrichment
        Map<LocalDate, JdbcDailyRejectionLogRepository.DailyRejectionRecord> dailyRejections =
                dailyRejectionLogRepository.findByMemberIdAndDateRange(memberId, periodStart, periodEnd).stream()
                        .collect(Collectors.toMap(
                                JdbcDailyRejectionLogRepository.DailyRejectionRecord::workDate, Function.identity()));

        // Determine if the month is currently in REJECTED status
        boolean isMonthlyRejected = approvalSummary != null && "REJECTED".equals(approvalSummary.status());

        // Convert to response DTOs with rejection enrichment
        List<DailyCalendarEntry> entries = projections.stream()
                .map(p -> {
                    String rejectionSource = null;
                    String rejectionReason = null;

                    // Daily rejection takes precedence over monthly
                    JdbcDailyRejectionLogRepository.DailyRejectionRecord dailyRejection = dailyRejections.get(p.date());
                    if (dailyRejection != null && "DRAFT".equals(p.status())) {
                        rejectionSource = "daily";
                        rejectionReason = dailyRejection.rejectionReason();
                    } else if (isMonthlyRejected && "DRAFT".equals(p.status())) {
                        rejectionSource = "monthly";
                        rejectionReason = approvalSummary.rejectionReason();
                    }

                    return new DailyCalendarEntry(
                            p.date(),
                            p.totalWorkHours(),
                            p.totalAbsenceHours(),
                            p.status(),
                            p.isWeekend(),
                            p.isHoliday(),
                            p.hasProxyEntries(),
                            rejectionSource,
                            rejectionReason);
                })
                .collect(Collectors.toList());

        MonthlyCalendarResponse response = new MonthlyCalendarResponse(
                memberId,
                memberRepository
                        .findById(MemberId.of(memberId))
                        .map(Member::getDisplayName)
                        .orElse(null),
                periodStart,
                periodEnd,
                entries,
                approvalSummary);

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
            @PathVariable int year, @PathVariable int month, @RequestParam(required = false) UUID memberId) {
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
