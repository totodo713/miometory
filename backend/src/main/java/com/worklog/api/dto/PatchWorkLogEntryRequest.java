package com.worklog.api.dto;

import java.math.BigDecimal;

/**
 * Request DTO for updating a work log entry via PATCH.
 */
public record PatchWorkLogEntryRequest(
    BigDecimal hours,
    String comment
) {
}
