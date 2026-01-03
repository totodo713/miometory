package com.worklog.application.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Command to create a new absence record.
 * 
 * @param memberId Member who is absent
 * @param date Date of absence
 * @param hours Hours of absence (will be validated as 0.25h increments, > 0 and <= 24h)
 * @param absenceType Type of absence (PAID_LEAVE, SICK_LEAVE, SPECIAL_LEAVE, OTHER)
 * @param reason Optional reason/comment (max 500 characters)
 * @param recordedBy Who is recording the absence (for proxy entries)
 */
public record CreateAbsenceCommand(
    UUID memberId,
    LocalDate date,
    BigDecimal hours,
    String absenceType,
    String reason,
    UUID recordedBy
) {
}
