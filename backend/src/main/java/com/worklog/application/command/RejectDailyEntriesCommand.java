package com.worklog.application.command;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Command to reject all SUBMITTED work log entries for a specific member and date.
 * Transitions entries from SUBMITTED to DRAFT status atomically.
 * Persists a daily rejection log record for feedback visibility.
 *
 * @param memberId Member whose entries to reject
 * @param date Date of the entries to reject
 * @param rejectedBy Manager performing the rejection
 * @param rejectionReason Reason for rejection (required, max 1000 chars)
 */
public record RejectDailyEntriesCommand(UUID memberId, LocalDate date, UUID rejectedBy, String rejectionReason) {
    public RejectDailyEntriesCommand {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId is required");
        }
        if (date == null) {
            throw new IllegalArgumentException("date is required");
        }
        if (rejectedBy == null) {
            throw new IllegalArgumentException("rejectedBy is required");
        }
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("rejectionReason is required");
        }
        if (rejectionReason.length() > 1000) {
            throw new IllegalArgumentException("rejectionReason must not exceed 1000 characters");
        }
    }
}
