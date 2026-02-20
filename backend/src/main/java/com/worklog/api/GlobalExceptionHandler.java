package com.worklog.api;

import com.worklog.domain.shared.DomainException;
import com.worklog.domain.shared.OptimisticLockException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Global exception handler for REST API controllers.
 * Translates domain and framework exceptions into standardized error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles domain-level business rule violations.
     * Returns 400 Bad Request for validation errors, 422 Unprocessable Entity for state violations.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException ex, WebRequest request) {
        logger.warn("Domain exception: {} - {}", ex.getErrorCode(), ex.getMessage());

        ErrorResponse error =
                ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), Map.of("path", getRequestPath(request)));

        // State violations return 422 UNPROCESSABLE_ENTITY
        // These are cases where the request is valid but the current state doesn't allow the operation
        HttpStatus status =
                isStateViolation(ex.getErrorCode()) ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status).body(error);
    }

    /**
     * Determines if an error code represents a state violation (422) vs validation error (400).
     */
    private boolean isStateViolation(String errorCode) {
        return errorCode != null
                && (errorCode.contains("ALREADY_SUBMITTED")
                        || errorCode.contains("ALREADY_APPROVED")
                        || errorCode.contains("ALREADY_REJECTED")
                        || errorCode.contains("NOT_SUBMITTED")
                        || errorCode.contains("NOT_PENDING")
                        || errorCode.contains("INVALID_STATUS_TRANSITION")
                        || errorCode.contains("CANNOT_MODIFY")
                        || errorCode.contains("CANNOT_SUBMIT")
                        || errorCode.contains("CANNOT_APPROVE")
                        || errorCode.contains("CANNOT_REJECT")
                        || errorCode.contains("RECALL_BLOCKED_BY_APPROVAL")
                        || errorCode.contains("REJECT_BLOCKED_BY_APPROVAL"));
    }

    /**
     * Handles optimistic locking conflicts.
     * Returns 409 Conflict with detailed version information.
     */
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockException(OptimisticLockException ex, WebRequest request) {
        logger.warn("Optimistic lock conflict: {} [{}]", ex.getAggregateType(), ex.getAggregateId());

        Map<String, Object> details = new HashMap<>();
        details.put("aggregateType", ex.getAggregateType());
        details.put("aggregateId", ex.getAggregateId());
        details.put("expectedVersion", ex.getExpectedVersion());
        details.put("actualVersion", ex.getActualVersion());
        details.put("path", getRequestPath(request));

        ErrorResponse error = ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), details);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handles validation errors from @Valid annotations.
     * Returns 400 Bad Request with field-level validation errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {
        logger.warn("Validation failed: {} errors", ex.getBindingResult().getErrorCount());

        Map<String, Object> details = new HashMap<>();
        details.put("path", getRequestPath(request));

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));
        details.put("fieldErrors", fieldErrors);

        ErrorResponse error = ErrorResponse.of("VALIDATION_FAILED", "Request validation failed", details);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles type mismatch errors (e.g., invalid UUID format).
     * Returns 400 Bad Request with type information.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatchException(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        logger.warn("Type mismatch: parameter '{}' has invalid value '{}'", ex.getName(), ex.getValue());

        String expectedType =
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";

        Map<String, Object> details = new HashMap<>();
        details.put("parameter", ex.getName());
        details.put("value", ex.getValue());
        details.put("expectedType", expectedType);
        details.put("path", getRequestPath(request));

        ErrorResponse error = ErrorResponse.of(
                "INVALID_PARAMETER_TYPE",
                String.format("Invalid value for parameter '%s': expected %s", ex.getName(), expectedType),
                details);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles HttpMessageNotReadableException (JSON parsing errors).
     * This includes validation errors thrown in record constructors.
     * Returns 400 Bad Request.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex, WebRequest request) {
        logger.warn("JSON parsing failed: {}", ex.getMessage());

        // Extract root cause for better error message
        Throwable rootCause = ex.getRootCause();
        String message = rootCause != null ? rootCause.getMessage() : ex.getMessage();

        // Try to extract error code from validation message
        String errorCode = "INVALID_REQUEST_BODY";
        if (message != null) {
            if (message.contains("memberId is required")) {
                errorCode = "MEMBER_ID_REQUIRED";
            } else if (message.contains("fiscalMonthStart is required")
                    || message.contains("fiscalMonthEnd is required")) {
                errorCode = "FISCAL_MONTH_REQUIRED";
            } else if (message.contains("reviewedBy is required")) {
                errorCode = "REVIEWED_BY_REQUIRED";
            } else if (message.contains("rejectionReason is required")) {
                errorCode = "REJECTION_REASON_REQUIRED";
            } else if (message.contains("rejectionReason must not exceed")) {
                errorCode = "REJECTION_REASON_TOO_LONG";
            } else if (message.contains("rejectedBy is required")) {
                errorCode = "REJECTED_BY_REQUIRED";
            }
        }

        ErrorResponse error = ErrorResponse.of(
                errorCode, message != null ? message : "Invalid request body", Map.of("path", getRequestPath(request)));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles IllegalArgumentException (common for invalid inputs).
     * Returns 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        logger.warn("Illegal argument: {}", ex.getMessage());

        ErrorResponse error =
                ErrorResponse.of("INVALID_ARGUMENT", ex.getMessage(), Map.of("path", getRequestPath(request)));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles all other unexpected exceptions.
     * Returns 500 Internal Server Error with generic message.
     * Logs full stack trace for debugging.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        logger.error("Unexpected error occurred", ex);

        ErrorResponse error = ErrorResponse.of(
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please contact support.",
                Map.of("path", getRequestPath(request)));

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Extracts the request path from WebRequest for error details.
     */
    private String getRequestPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
