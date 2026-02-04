package com.worklog.api;

import java.util.UUID;

/**
 * Response DTO for user signup endpoint.
 * 
 * @param id User's unique identifier
 * @param email User's email address
 * @param name User's display name
 * @param accountStatus Current account status (e.g., "UNVERIFIED", "ACTIVE")
 * @param message Human-readable confirmation message
 */
public record SignupResponse(
    UUID id,
    String email,
    String name,
    String accountStatus,
    String message
) {}
