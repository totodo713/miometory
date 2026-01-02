package com.worklog.api;

import com.worklog.domain.shared.DomainException;
import com.worklog.domain.shared.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST API controllers.
 * Translates domain and framework exceptions into standardized error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles domain-level business rule violations.
     * Returns 400 Bad Request with error code and message.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(
            DomainException ex,
            WebRequest request
    ) {
        logger.warn("Domain exception: {} - {}", ex.getErrorCode(), ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
            ex.getErrorCode(),
            ex.getMessage(),
            Map.of("path", getRequestPath(request))
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }

    /**
     * Handles optimistic locking conflicts.
     * Returns 409 Conflict with detailed version information.
     */
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockException(
            OptimisticLockException ex,
            WebRequest request
    ) {
        logger.warn("Optimistic lock conflict: {} [{}]",
                ex.getAggregateType(), ex.getAggregateId());

        Map<String, Object> details = new HashMap<>();
        details.put("aggregateType", ex.getAggregateType());
        details.put("aggregateId", ex.getAggregateId());
        details.put("expectedVersion", ex.getExpectedVersion());
        details.put("actualVersion", ex.getActualVersion());
        details.put("path", getRequestPath(request));

        ErrorResponse error = ErrorResponse.of(
            ex.getErrorCode(),
            ex.getMessage(),
            details
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error);
    }

    /**
     * Handles validation errors from @Valid annotations.
     * Returns 400 Bad Request with field-level validation errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request
    ) {
        logger.warn("Validation failed: {} errors", ex.getBindingResult().getErrorCount());

        Map<String, Object> details = new HashMap<>();
        details.put("path", getRequestPath(request));

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        details.put("fieldErrors", fieldErrors);

        ErrorResponse error = ErrorResponse.of(
            "VALIDATION_FAILED",
            "Request validation failed",
            details
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }

    /**
     * Handles type mismatch errors (e.g., invalid UUID format).
     * Returns 400 Bad Request with type information.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatchException(
            MethodArgumentTypeMismatchException ex,
            WebRequest request
    ) {
        logger.warn("Type mismatch: parameter '{}' has invalid value '{}'",
                ex.getName(), ex.getValue());

        String expectedType = ex.getRequiredType() != null
                ? ex.getRequiredType().getSimpleName()
                : "unknown";

        Map<String, Object> details = new HashMap<>();
        details.put("parameter", ex.getName());
        details.put("value", ex.getValue());
        details.put("expectedType", expectedType);
        details.put("path", getRequestPath(request));

        ErrorResponse error = ErrorResponse.of(
            "INVALID_PARAMETER_TYPE",
            String.format("Invalid value for parameter '%s': expected %s",
                    ex.getName(), expectedType),
            details
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }

    /**
     * Handles IllegalArgumentException (common for invalid inputs).
     * Returns 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request
    ) {
        logger.warn("Illegal argument: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
            "INVALID_ARGUMENT",
            ex.getMessage(),
            Map.of("path", getRequestPath(request))
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }

    /**
     * Handles all other unexpected exceptions.
     * Returns 500 Internal Server Error with generic message.
     * Logs full stack trace for debugging.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            WebRequest request
    ) {
        logger.error("Unexpected error occurred", ex);

        ErrorResponse error = ErrorResponse.of(
            "INTERNAL_SERVER_ERROR",
            "An unexpected error occurred. Please contact support.",
            Map.of("path", getRequestPath(request))
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }

    /**
     * Extracts the request path from WebRequest for error details.
     */
    private String getRequestPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
