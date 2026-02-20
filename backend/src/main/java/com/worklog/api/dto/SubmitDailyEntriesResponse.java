package com.worklog.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for daily entry submission results.
 *
 * @param submittedCount Number of entries that were transitioned to SUBMITTED
 * @param date The date of the submitted entries
 * @param entries List of entry status details after submission
 */
public record SubmitDailyEntriesResponse(int submittedCount, LocalDate date, List<EntryStatusItem> entries) {

    /**
     * Represents the status of a single entry after a submit or recall operation.
     */
    public record EntryStatusItem(UUID id, UUID projectId, BigDecimal hours, String status, long version) {}
}
