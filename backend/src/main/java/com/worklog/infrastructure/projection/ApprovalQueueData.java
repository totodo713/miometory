package com.worklog.infrastructure.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Data transfer object for approval queue view.
 *
 * Contains the list of pending monthly approvals that a manager needs to review.
 * Each item represents a team member's submitted time entries for a fiscal month.
 */
public record ApprovalQueueData(List<PendingApproval> pendingApprovals) {
    /**
     * Individual pending approval item.
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
