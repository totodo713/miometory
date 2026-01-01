package com.worklog.domain.monthlyperiod;

import java.time.LocalDate;

/**
 * Value object representing a monthly period with start and end dates.
 */
public record MonthlyPeriod(LocalDate start, LocalDate end) {
    
    public MonthlyPeriod {
        if (start == null) {
            throw new IllegalArgumentException("Start date cannot be null");
        }
        if (end == null) {
            throw new IllegalArgumentException("End date cannot be null");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End date must be after or equal to start date");
        }
    }
    
    // Kotlin destructuring support
    public LocalDate component1() {
        return start;
    }
    
    public LocalDate component2() {
        return end;
    }
}
