package com.worklog.infrastructure.csv;

import java.util.List;

/**
 * Result of processing and validating a member CSV file.
 *
 * @param validRows parsed rows that passed all validation
 * @param errors    validation errors found across all rows
 */
public record MemberCsvResult(List<MemberCsvRow> validRows, List<CsvValidationError> errors) {

    public MemberCsvResult {
        validRows = List.copyOf(validRows);
        errors = List.copyOf(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
