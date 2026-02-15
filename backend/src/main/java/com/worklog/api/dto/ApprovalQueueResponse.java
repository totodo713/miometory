package com.worklog.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for the manager's approval queue.
 * Contains a list of pending approvals awaiting review.
 */
public record ApprovalQueueResponse(List<PendingApproval> pendingApprovals, int totalCount) {
    /**
     * Individual pending approval in the queue.
     */
    public record PendingApproval(
            String approvalId,
            String memberId,
            String memberName,
            LocalDate fiscalMonthStart,
            LocalDate fiscalMonthEnd,
            BigDecimal totalWorkHours,
            BigDecimal totalAbsenceHours,
            Instant submittedAt,
            String submittedByName) {}
}
