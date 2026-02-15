package com.worklog.application.approval;

import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.FiscalMonthPeriod;

/**
 * Command to submit a month's time entries for manager approval.
 *
 * Triggered when an engineer has completed their time entries for a fiscal month
 * and wants to submit them for manager review. This will transition all associated
 * work log entries and absences to SUBMITTED status (read-only for the engineer).
 */
public record SubmitMonthForApprovalCommand(MemberId memberId, FiscalMonthPeriod fiscalMonth, MemberId submittedBy) {
    public SubmitMonthForApprovalCommand {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId cannot be null");
        }
        if (fiscalMonth == null) {
            throw new IllegalArgumentException("fiscalMonth cannot be null");
        }
        if (submittedBy == null) {
            throw new IllegalArgumentException("submittedBy cannot be null");
        }
    }
}
