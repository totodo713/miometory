package com.worklog.application.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command to update an existing absence record.
 * 
 * @param id Absence ID to update
 * @param hours New hours value (will be validated as 0.25h increments, > 0 and <= 24h)
 * @param absenceType New absence type (PAID_LEAVE, SICK_LEAVE, SPECIAL_LEAVE, OTHER)
 * @param reason New reason/comment (max 500 characters)
 * @param updatedBy Who is updating the absence
 * @param version Current version for optimistic locking
 */
public record UpdateAbsenceCommand(
    UUID id,
    BigDecimal hours,
    String absenceType,
    String reason,
    UUID updatedBy,
    Long version
) {
}
