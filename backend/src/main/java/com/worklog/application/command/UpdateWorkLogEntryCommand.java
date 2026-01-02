package com.worklog.application.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command to update an existing work log entry.
 * 
 * @param id Work log entry ID to update
 * @param hours New hours value (will be validated as 0.25h increments, max 24h)
 * @param comment New comment (max 500 characters)
 * @param updatedBy Who is updating the entry
 * @param version Current version for optimistic locking
 */
public record UpdateWorkLogEntryCommand(
    UUID id,
    BigDecimal hours,
    String comment,
    UUID updatedBy,
    Long version
) {
}
