package com.worklog.api.dto;

import java.time.Instant;
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
        List<DailyCalendarEntry> entries,
        MonthlyApprovalSummary monthlyApproval) {

    /**
     * Backward-compatible constructor without monthlyApproval.
     */
    public MonthlyCalendarResponse(
            UUID memberId,
            String memberName,
            LocalDate periodStart,
            LocalDate periodEnd,
            List<DailyCalendarEntry> entries) {
        this(memberId, memberName, periodStart, periodEnd, entries, null);
    }

    /**
     * Summary of monthly approval status included in calendar response.
     */
    public record MonthlyApprovalSummary(
            UUID approvalId,
            String status,
            String rejectionReason,
            UUID reviewedBy,
            String reviewerName,
            Instant reviewedAt) {}
}
