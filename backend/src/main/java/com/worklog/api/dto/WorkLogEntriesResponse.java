package com.worklog.api.dto;

import java.util.List;

/**
 * Response DTO for a list of work log entries.
 */
public record WorkLogEntriesResponse(
    List<WorkLogEntryResponse> entries,
    int total
) {
}
