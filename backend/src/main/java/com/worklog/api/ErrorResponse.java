package com.worklog.api;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error response format for all API exceptions.
 * Provides consistent error structure across the application.
 *
 * @param error     Error code (e.g., "INVALID_START_MONTH", "ENTITY_NOT_FOUND")
 * @param message   Human-readable error message
 * @param timestamp When the error occurred
 * @param details   Additional context about the error (optional)
 */
public record ErrorResponse(
    String error,
    String message,
    Instant timestamp,
    Map<String, Object> details
) {
    /**
     * Creates an error response with the current timestamp.
     *
     * @param error   Error code
     * @param message Human-readable error message
     * @param details Additional context
     * @return ErrorResponse instance
     */
    public static ErrorResponse of(String error, String message, Map<String, Object> details) {
        return new ErrorResponse(error, message, Instant.now(), details);
    }

    /**
     * Creates an error response without additional details.
     *
     * @param error   Error code
     * @param message Human-readable error message
     * @return ErrorResponse instance
     */
    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, Instant.now(), Map.of());
    }
}
