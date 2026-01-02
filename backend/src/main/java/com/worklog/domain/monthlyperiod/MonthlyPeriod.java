package com.worklog.domain.monthlyperiod;

import java.time.LocalDate;

/**
 * Value object representing a monthly period with start and end dates.
 */
public record MonthlyPeriod(
    LocalDate startDate, 
    LocalDate endDate,
    int displayMonth,
    int displayYear
) {
    
    public MonthlyPeriod {
        if (startDate == null) {
            throw new IllegalArgumentException("Start date cannot be null");
        }
        if (endDate == null) {
            throw new IllegalArgumentException("End date cannot be null");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be after or equal to start date");
        }
    }
    
    // Legacy aliases for backward compatibility
    public LocalDate start() {
        return startDate;
    }
    
    public LocalDate end() {
        return endDate;
    }
    
    // Kotlin destructuring support
    public LocalDate component1() {
        return startDate;
    }
    
    public LocalDate component2() {
        return endDate;
    }
    
    public int component3() {
        return displayMonth;
    }
    
    public int component4() {
        return displayYear;
    }
}
