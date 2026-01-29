package com.worklog.api.dto;

import java.util.UUID;

/**
 * Response DTO for member information.
 * Used in subordinates list and member lookup endpoints.
 */
public record MemberResponse(
    UUID id,
    String email,
    String displayName,
    UUID managerId,
    boolean isActive
) {}
