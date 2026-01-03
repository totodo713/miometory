package com.worklog.api.dto;

import java.util.UUID;

/**
 * Request DTO for rejecting a submitted month.
 * Contains the reviewer ID and a mandatory rejection reason for feedback to the engineer.
 */
public record RejectMonthRequest(
    UUID reviewedBy,
    String rejectionReason
) {
    public RejectMonthRequest {
        if (reviewedBy == null) {
            throw new IllegalArgumentException("reviewedBy is required");
        }
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("rejectionReason is required");
        }
        if (rejectionReason.length() > 1000) {
            throw new IllegalArgumentException("rejectionReason must not exceed 1000 characters");
        }
    }
}
