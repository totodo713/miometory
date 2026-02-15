package com.worklog.infrastructure.csv;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Service for validating CSV rows during import.
 * Validates date format, project code, hours, and notes according to business rules.
 */
@Service
public class CsvValidationService {

    private static final int MAX_PROJECT_CODE_LENGTH = 50;
    private static final int MAX_NOTES_LENGTH = 500;
    private static final BigDecimal HOURS_INCREMENT = new BigDecimal("0.25");
    private static final BigDecimal MIN_HOURS = BigDecimal.ZERO;
    private static final BigDecimal MAX_HOURS = new BigDecimal("24");

    /**
     * Validates a single CSV row.
     *
     * @param rowNumber The row number (1-based, excluding header)
     * @param date The date string (expected format: YYYY-MM-DD)
     * @param projectCode The project code
     * @param hours The hours string
     * @param notes The notes (optional)
     * @return ValidationResult containing any errors found
     */
    public ValidationResult validateRow(int rowNumber, String date, String projectCode, String hours, String notes) {
        List<String> errors = new ArrayList<>();

        // Validate date
        if (date == null || date.isBlank()) {
            errors.add("Date is required");
        } else {
            try {
                LocalDate.parse(date);
            } catch (DateTimeParseException e) {
                errors.add("Date must be in YYYY-MM-DD format");
            }
        }

        // Validate project code
        if (projectCode == null || projectCode.isBlank()) {
            errors.add("Project Code is required");
        } else if (projectCode.length() > MAX_PROJECT_CODE_LENGTH) {
            errors.add("Project Code must not exceed " + MAX_PROJECT_CODE_LENGTH + " characters");
        }

        // Validate hours
        if (hours == null || hours.isBlank()) {
            errors.add("Hours is required");
        } else {
            try {
                BigDecimal hoursValue = new BigDecimal(hours);

                if (hoursValue.compareTo(MIN_HOURS) < 0) {
                    errors.add("Hours must be 0 or greater");
                } else if (hoursValue.compareTo(MAX_HOURS) > 0) {
                    errors.add("Hours must not exceed 24");
                } else {
                    // Check if hours are in 0.25 increments
                    BigDecimal remainder = hoursValue.remainder(HOURS_INCREMENT);
                    if (remainder.compareTo(BigDecimal.ZERO) != 0) {
                        errors.add("Hours must be in 0.25 increments (e.g., 1.00, 1.25, 1.50, 1.75)");
                    }
                }
            } catch (NumberFormatException e) {
                errors.add("Hours must be a valid number");
            }
        }

        // Validate notes (optional, but has max length)
        if (notes != null && notes.length() > MAX_NOTES_LENGTH) {
            errors.add("Notes must not exceed " + MAX_NOTES_LENGTH + " characters");
        }

        return new ValidationResult(rowNumber, errors);
    }

    /**
     * Result of validating a CSV row.
     */
    public static class ValidationResult {
        private final int rowNumber;
        private final List<String> errors;

        public ValidationResult(int rowNumber, List<String> errors) {
            this.rowNumber = rowNumber;
            this.errors = errors;
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public int getRowNumber() {
            return rowNumber;
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getErrorMessage() {
            if (errors.isEmpty()) {
                return "";
            }
            return "Row " + rowNumber + ": " + String.join(", ", errors);
        }
    }
}
