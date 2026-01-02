package com.worklog.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for work log entry details.
 */
public record WorkLogEntryResponse(
    UUID id,
    UUID memberId,
    UUID projectId,
    LocalDate date,
    BigDecimal hours,
    String comment,
    String status,
    UUID enteredBy,
    Instant createdAt,
    Instant updatedAt,
    Long version
) {
}
