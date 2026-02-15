package com.worklog.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for creating a new work log entry.
 */
public record CreateWorkLogEntryRequest(
        UUID memberId, UUID projectId, LocalDate date, BigDecimal hours, String comment, UUID enteredBy) {}
