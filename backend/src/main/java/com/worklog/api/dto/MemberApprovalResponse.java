package com.worklog.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for member-facing approval status with rejection reason.
 * Used by the member UI (not the manager approval queue) to display
 * approval status and rejection feedback.
 */
public record MemberApprovalResponse(
        UUID approvalId,
        UUID memberId,
        LocalDate fiscalMonthStart,
        LocalDate fiscalMonthEnd,
        String status,
        Instant submittedAt,
        Instant reviewedAt,
        UUID reviewedBy,
        String reviewerName,
        String rejectionReason) {}
