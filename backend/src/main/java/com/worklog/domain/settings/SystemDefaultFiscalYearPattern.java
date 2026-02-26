package com.worklog.domain.settings;

/**
 * Value object representing the system-wide default fiscal year pattern.
 * Stored as raw values (not a pattern ID) since system defaults are tenant-independent.
 */
public record SystemDefaultFiscalYearPattern(int startMonth, int startDay) {

    public SystemDefaultFiscalYearPattern {
        if (startMonth < 1 || startMonth > 12) {
            throw new IllegalArgumentException("startMonth must be between 1 and 12");
        }
        if (startDay < 1 || startDay > 31) {
            throw new IllegalArgumentException("startDay must be between 1 and 31");
        }
    }
}
