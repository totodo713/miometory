package com.worklog.application.approval;

import com.worklog.domain.approval.MonthlyApprovalId;
import com.worklog.domain.member.MemberId;

/**
 * Command to reject a submitted month's time entries with a reason.
 *
 * Triggered when a manager reviews a team member's submitted time entries
 * and rejects them with feedback. This will transition all associated work log
 * entries and absences back to DRAFT status (editable by the engineer again).
 */
public record RejectMonthCommand(MonthlyApprovalId approvalId, MemberId reviewedBy, String rejectionReason) {
    public RejectMonthCommand {
        if (approvalId == null) {
            throw new IllegalArgumentException("approvalId cannot be null");
        }
        if (reviewedBy == null) {
            throw new IllegalArgumentException("reviewedBy cannot be null");
        }
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("rejectionReason cannot be null or blank");
        }
        if (rejectionReason.length() > 1000) {
            throw new IllegalArgumentException("rejectionReason must not exceed 1000 characters");
        }
    }
}
