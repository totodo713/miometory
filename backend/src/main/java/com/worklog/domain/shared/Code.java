package com.worklog.domain.shared;

import java.util.regex.Pattern;

/**
 * Value object representing a unique code identifier.
 *
 * Codes are alphanumeric strings with underscores, used for human-readable
 * identification of entities like tenants and organizations.
 *
 * Constraints:
 * - 1-32 characters
 * - Alphanumeric and underscores only
 * - Case-insensitive for uniqueness
 */
public record Code(String value) {

    private static final Pattern VALID_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 32;

    public Code {
        if (value == null || value.isBlank()) {
            throw new DomainException("CODE_REQUIRED", "Code cannot be null or blank");
        }
        value = value.trim();
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new DomainException(
                    "CODE_LENGTH", String.format("Code must be between %d and %d characters", MIN_LENGTH, MAX_LENGTH));
        }
        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new DomainException("CODE_FORMAT", "Code can only contain alphanumeric characters and underscores");
        }
    }

    public static Code of(String value) {
        return new Code(value);
    }

    /**
     * Compares codes case-insensitively.
     */
    public boolean equalsIgnoreCase(Code other) {
        if (other == null) return false;
        return this.value.equalsIgnoreCase(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
