package com.worklog.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for rejecting all SUBMITTED work log entries for a specific date.
 * Manager specifies the member, date, and a mandatory rejection reason.
 */
public record RejectDailyEntriesRequest(UUID memberId, LocalDate date, UUID rejectedBy, String rejectionReason) {
    public RejectDailyEntriesRequest {
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
