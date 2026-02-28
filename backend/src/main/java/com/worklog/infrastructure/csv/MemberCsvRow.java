package com.worklog.infrastructure.csv;

/**
 * Represents a single parsed row from a member CSV file.
 *
 * @param rowNumber 1-based row number (excluding header)
 * @param email     email address from CSV
 * @param displayName display name from CSV
 */
public record MemberCsvRow(int rowNumber, String email, String displayName) {}
