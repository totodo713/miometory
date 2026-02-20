package com.worklog.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for daily entry recall results.
 *
 * @param recalledCount Number of entries that were transitioned back to DRAFT
 * @param date The date of the recalled entries
 * @param entries List of entry status details after recall
 */
public record RecallDailyEntriesResponse(int recalledCount, LocalDate date, List<EntryStatusItem> entries) {

    /**
     * Represents the status of a single entry after a recall operation.
     */
    public record EntryStatusItem(UUID id, UUID projectId, BigDecimal hours, String status, long version) {}
}
