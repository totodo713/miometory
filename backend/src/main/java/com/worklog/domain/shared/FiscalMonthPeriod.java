package com.worklog.domain.shared;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Value object representing a fiscal month period.
 * 
 * A fiscal month runs from the 21st of one calendar month to the 20th of the next.
 * This differs from standard calendar months and is used for billing and approval cycles.
 * 
 * Constraints:
 * - Start date must be on the 21st of a month
 * - End date must be on the 20th of a month
 * - Start date must be before end date
 * - Period must be approximately one calendar month (27-30 days)
 * 
 * Example: 2025-01-21 to 2025-02-20 is a valid fiscal month period.
 */
public record FiscalMonthPeriod(LocalDate startDate, LocalDate endDate) {
    
    private static final int FISCAL_START_DAY = 21;
    private static final int FISCAL_END_DAY = 20;
    private static final long MIN_DAYS = 27;
    private static final long MAX_DAYS = 30;
    
    public FiscalMonthPeriod {
        if (startDate == null || endDate == null) {
            throw new DomainException("FISCAL_PERIOD_NULL", 
                "Start date and end date cannot be null");
        }
        
        if (!startDate.isBefore(endDate)) {
            throw new DomainException("FISCAL_PERIOD_INVALID_RANGE", 
                "Start date must be before end date");
        }
        
        if (startDate.getDayOfMonth() != FISCAL_START_DAY) {
            throw new DomainException("FISCAL_PERIOD_INVALID_START", 
                "Fiscal month must start on the 21st");
        }
        
        if (endDate.getDayOfMonth() != FISCAL_END_DAY) {
            throw new DomainException("FISCAL_PERIOD_INVALID_END", 
                "Fiscal month must end on the 20th");
        }
        
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        if (daysBetween < MIN_DAYS || daysBetween > MAX_DAYS) {
            throw new DomainException("FISCAL_PERIOD_INVALID_LENGTH", 
                String.format("Fiscal month must be approximately 1 calendar month (27-30 days), but was %d days", 
                    daysBetween));
        }
    }
    
    /**
     * Factory method to create a fiscal month period from a start date.
     * Automatically calculates the end date.
     * 
     * @param startDate must be on the 21st of a month
     */
    public static FiscalMonthPeriod of(LocalDate startDate) {
        if (startDate.getDayOfMonth() != FISCAL_START_DAY) {
            throw new DomainException("FISCAL_PERIOD_INVALID_START", 
                "Fiscal month must start on the 21st");
        }
        LocalDate endDate = startDate.plusMonths(1).withDayOfMonth(FISCAL_END_DAY);
        return new FiscalMonthPeriod(startDate, endDate);
    }
    
    /**
     * Factory method to create a fiscal month period containing the given date.
     * 
     * @param date any date within the fiscal month
     */
    public static FiscalMonthPeriod forDate(LocalDate date) {
        LocalDate start;
        if (date.getDayOfMonth() >= FISCAL_START_DAY) {
            // Date is in the latter part of the month (21st onwards)
            start = date.withDayOfMonth(FISCAL_START_DAY);
        } else {
            // Date is in the early part of the month (1st-20th)
            start = date.minusMonths(1).withDayOfMonth(FISCAL_START_DAY);
        }
        return FiscalMonthPeriod.of(start);
    }
    
    /**
     * Checks if this fiscal month period contains the given date.
     */
    public boolean contains(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
    
    /**
     * Returns the next fiscal month period.
     */
    public FiscalMonthPeriod next() {
        return FiscalMonthPeriod.of(startDate.plusMonths(1));
    }
    
    /**
     * Returns the previous fiscal month period.
     */
    public FiscalMonthPeriod previous() {
        return FiscalMonthPeriod.of(startDate.minusMonths(1));
    }
    
    /**
     * Checks if this fiscal month period overlaps with another.
     */
    public boolean overlaps(FiscalMonthPeriod other) {
        return !endDate.isBefore(other.startDate) && !other.endDate.isBefore(startDate);
    }
    
    /**
     * Returns a human-readable representation.
     * Example: "2025-01-21 to 2025-02-20"
     */
    @Override
    public String toString() {
        return String.format("%s to %s", startDate, endDate);
    }
}
