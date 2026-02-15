package com.worklog.application.command;

import java.util.UUID;

/**
 * Command to delete an absence record.
 *
 * @param id Absence ID to delete
 * @param deletedBy Who is deleting the absence
 */
public record DeleteAbsenceCommand(UUID id, UUID deletedBy) {}
