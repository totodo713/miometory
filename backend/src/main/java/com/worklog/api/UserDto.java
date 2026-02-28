package com.worklog.api;

import java.util.UUID;

/**
 * User details DTO for API responses.
 * Excludes sensitive fields like password hash.
 *
 * @param id User's unique identifier
 * @param email User's email address
 * @param name User's display name
 * @param accountStatus Current account status (e.g., "ACTIVE", "LOCKED")
 * @param preferredLocale User's preferred locale ("en" or "ja")
 * @param memberId Associated member ID, set only when auto-selected (single tenant); null otherwise
 */
public record UserDto(
        UUID id, String email, String name, String accountStatus, String preferredLocale, UUID memberId) {}
