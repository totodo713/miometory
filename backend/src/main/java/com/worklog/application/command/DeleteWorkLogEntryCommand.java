package com.worklog.application.command;

import java.util.UUID;

/**
 * Command to delete a work log entry.
 * 
 * @param id Work log entry ID to delete
 * @param deletedBy Who is deleting the entry
 */
public record DeleteWorkLogEntryCommand(
    UUID id,
    UUID deletedBy
) {
}
