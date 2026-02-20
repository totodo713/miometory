package com.worklog.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for submitting all DRAFT work log entries for a specific date.
 */
public record SubmitDailyEntriesRequest(UUID memberId, LocalDate date, UUID submittedBy) {
    public SubmitDailyEntriesRequest {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId is required");
        }
        if (date == null) {
            throw new IllegalArgumentException("date is required");
        }
        if (submittedBy == null) {
            throw new IllegalArgumentException("submittedBy is required");
        }
    }
}
