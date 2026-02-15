package com.worklog.api.dto;

import java.math.BigDecimal;

/**
 * Request DTO for updating an absence record via PATCH.
 */
public record PatchAbsenceRequest(BigDecimal hours, String absenceType, String reason) {}
