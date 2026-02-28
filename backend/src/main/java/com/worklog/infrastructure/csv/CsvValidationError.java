package com.worklog.infrastructure.csv;

/**
 * Represents a validation error found in a CSV row.
 *
 * @param rowNumber 1-based row number (excluding header)
 * @param field     the field name that failed validation
 * @param message   human-readable error description
 */
public record CsvValidationError(int rowNumber, String field, String message) {}
