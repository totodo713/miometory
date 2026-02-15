package com.worklog.application.command;

import java.util.UUID;

/**
 * Command to copy projects from the previous fiscal month.
 *
 * This retrieves the unique list of projects a member worked on
 * in the previous fiscal month, which can be used as a template
 * for the current month.
 *
 * @param memberId Member whose previous month data to retrieve
 * @param targetYear The target fiscal year (current month's year)
 * @param targetMonth The target fiscal month (current month, 1-12)
 */
public record CopyFromPreviousMonthCommand(UUID memberId, int targetYear, int targetMonth) {
    public CopyFromPreviousMonthCommand {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId cannot be null");
        }
        if (targetMonth < 1 || targetMonth > 12) {
            throw new IllegalArgumentException("targetMonth must be between 1 and 12");
        }
        if (targetYear < 2000 || targetYear > 2100) {
            throw new IllegalArgumentException("targetYear must be between 2000 and 2100");
        }
    }
}
