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
 * @param memberId Associated member ID (null if user has no member record)
 */
public record UserDto(UUID id, String email, String name, String accountStatus, UUID memberId) {}
