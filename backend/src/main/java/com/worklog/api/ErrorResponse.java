package com.worklog.api;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error response format for all API exceptions.
 * Provides consistent error structure across the application.
 *
 * @param errorCode Error code (e.g., "INVALID_START_MONTH", "ENTITY_NOT_FOUND")
 * @param message   Human-readable error message
 * @param timestamp When the error occurred
 * @param details   Additional context about the error (optional)
 */
public record ErrorResponse(String errorCode, String message, Instant timestamp, Map<String, Object> details) {
    /**
     * Creates an error response with the current timestamp.
     *
     * @param errorCode Error code
     * @param message Human-readable error message
     * @param details Additional context
     * @return ErrorResponse instance
     */
    public static ErrorResponse of(String errorCode, String message, Map<String, Object> details) {
        return new ErrorResponse(errorCode, message, Instant.now(), details);
    }

    /**
     * Creates an error response without additional details.
     *
     * @param errorCode Error code
     * @param message Human-readable error message
     * @return ErrorResponse instance
     */
    public static ErrorResponse of(String errorCode, String message) {
        return new ErrorResponse(errorCode, message, Instant.now(), Map.of());
    }
}
