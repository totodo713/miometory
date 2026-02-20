package com.worklog.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for retrieving daily rejection log entries.
 * Used for calendar display and rejection reason visibility.
 */
public record DailyRejectionResponse(List<DailyRejectionItem> rejections) {

    public record DailyRejectionItem(
            LocalDate date, String rejectionReason, UUID rejectedBy, String rejectedByName, Instant rejectedAt) {}
}
