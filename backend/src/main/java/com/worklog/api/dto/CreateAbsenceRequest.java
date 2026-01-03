package com.worklog.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for creating a new absence record.
 */
public record CreateAbsenceRequest(
    UUID memberId,
    LocalDate date,
    BigDecimal hours,
    String absenceType,
    String reason,
    UUID recordedBy
) {
}
