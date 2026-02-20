package com.worklog.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for recalling all SUBMITTED work log entries for a specific date back to DRAFT.
 */
public record RecallDailyEntriesRequest(UUID memberId, LocalDate date, UUID recalledBy) {
    public RecallDailyEntriesRequest {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId is required");
        }
        if (date == null) {
            throw new IllegalArgumentException("date is required");
        }
        if (recalledBy == null) {
            throw new IllegalArgumentException("recalledBy is required");
        }
    }
}
