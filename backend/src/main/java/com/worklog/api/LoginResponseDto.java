package com.worklog.api;

import java.time.Instant;

/**
 * Response DTO for user login endpoint.
 *
 * @param user User details (id, email, name, accountStatus)
 * @param sessionExpiresAt When the session will expire (ISO 8601 timestamp)
 * @param rememberMeToken Optional remember-me token (if user checked "Remember Me")
 * @param warning Optional warning message (e.g., account expiring soon)
 */
public record LoginResponseDto(UserDto user, Instant sessionExpiresAt, String rememberMeToken, String warning) {}
