package com.worklog.application.command;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Command to submit all DRAFT work log entries for a specific member and date.
 * Transitions entries from DRAFT to SUBMITTED status atomically.
 *
 * @param memberId Member whose entries to submit
 * @param date Date of the entries to submit
 * @param submittedBy Who is performing the submission (must equal memberId — no proxy submission)
 */
public record SubmitDailyEntriesCommand(UUID memberId, LocalDate date, UUID submittedBy) {
    public SubmitDailyEntriesCommand {
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
