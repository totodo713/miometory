package com.worklog.application.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Command to create a new work log entry.
 *
 * @param memberId Member who worked (or attributed member for proxy entries)
 * @param projectId Project worked on
 * @param date Date of work
 * @param hours Hours worked (will be validated as 0.25h increments, max 24h)
 * @param comment Optional comment (max 500 characters)
 * @param enteredBy Who actually entered the data (for proxy entries)
 */
public record CreateWorkLogEntryCommand(
        UUID memberId, UUID projectId, LocalDate date, BigDecimal hours, String comment, UUID enteredBy) {}
