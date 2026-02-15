package com.worklog.application.approval;

import com.worklog.domain.approval.MonthlyApprovalId;
import com.worklog.domain.member.MemberId;

/**
 * Command to approve a submitted month's time entries.
 *
 * Triggered when a manager reviews a team member's submitted time entries
 * and approves them. This will transition all associated work log entries
 * and absences to APPROVED status (permanently read-only).
 */
public record ApproveMonthCommand(MonthlyApprovalId approvalId, MemberId reviewedBy) {
    public ApproveMonthCommand {
        if (approvalId == null) {
            throw new IllegalArgumentException("approvalId cannot be null");
        }
        if (reviewedBy == null) {
            throw new IllegalArgumentException("reviewedBy cannot be null");
        }
    }
}
