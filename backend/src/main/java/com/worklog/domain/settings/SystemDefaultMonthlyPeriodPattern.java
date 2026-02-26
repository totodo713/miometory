package com.worklog.domain.settings;

/**
 * Value object representing the system-wide default monthly period pattern.
 * Stored as a raw value (not a pattern ID) since system defaults are tenant-independent.
 */
public record SystemDefaultMonthlyPeriodPattern(int startDay) {

    public SystemDefaultMonthlyPeriodPattern {
        if (startDay < 1 || startDay > 28) {
            throw new IllegalArgumentException("startDay must be between 1 and 28");
        }
    }
}
