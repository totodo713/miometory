package com.worklog.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for daily rejection operation.
 * Returns the count, date, reason, and status of affected entries.
 */
public record RejectDailyEntriesResponse(
        int rejectedCount, LocalDate date, String rejectionReason, List<RejectedEntryItem> entries) {

    public record RejectedEntryItem(UUID id, UUID projectId, BigDecimal hours, String status, long version) {}
}
