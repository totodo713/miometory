package com.worklog.application.command;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Command to recall all SUBMITTED work log entries for a specific member and date.
 * Transitions entries from SUBMITTED back to DRAFT status atomically.
 *
 * @param memberId Member whose entries to recall
 * @param date Date of the entries to recall
 * @param recalledBy Who is performing the recall (must equal memberId — no proxy recall)
 */
public record RecallDailyEntriesCommand(UUID memberId, LocalDate date, UUID recalledBy) {
    public RecallDailyEntriesCommand {
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
