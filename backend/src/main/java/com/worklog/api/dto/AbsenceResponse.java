package com.worklog.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for absence record details.
 */
public record AbsenceResponse(
    UUID id,
    UUID memberId,
    LocalDate date,
    BigDecimal hours,
    String absenceType,
    String reason,
    String status,
    UUID recordedBy,
    Instant createdAt,
    Instant updatedAt,
    Long version
) {
}
