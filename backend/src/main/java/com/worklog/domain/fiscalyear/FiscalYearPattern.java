package com.worklog.domain.fiscalyear;

import com.worklog.domain.shared.DomainException;

import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.UUID;

/**
 * FiscalYearPattern entity that defines how fiscal years are calculated.
 * 
 * Business Rules:
 * - startMonth must be 1-12 (January to December)
 * - startDay must be 1-31 and valid for the specified month
 * - The combination of startMonth and startDay must form a valid date
 */
public class FiscalYearPattern {
    
    private final FiscalYearPatternId id;
    private final UUID tenantId;
    private final String name;
    private final int startMonth;
    private final int startDay;
    
    private FiscalYearPattern(
        FiscalYearPatternId id,
        UUID tenantId,
        String name,
        int startMonth,
        int startDay
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.startMonth = startMonth;
        this.startDay = startDay;
    }
    
    /**
     * Creates a new FiscalYearPattern.
     * 
     * @param tenantId The tenant ID
     * @param name The pattern name
     * @param startMonth The starting month (1-12)
     * @param startDay The starting day (1-31, must be valid for the month)
     * @return A new FiscalYearPattern
     * @throws DomainException if validation fails
     */
    public static FiscalYearPattern create(
        UUID tenantId,
        String name,
        int startMonth,
        int startDay
    ) {
        return create(FiscalYearPatternId.generate(), tenantId, name, startMonth, startDay);
    }
    
    /**
     * Creates a new FiscalYearPattern with a specific ID.
     * Used for reconstitution from storage.
     */
    public static FiscalYearPattern create(
        FiscalYearPatternId id,
        UUID tenantId,
        String name,
        int startMonth,
        int startDay
    ) {
        validateName(name);
        validateStartMonth(startMonth);
        validateStartDay(startDay);
        validateDate(startMonth, startDay);
        
        String trimmedName = name.trim();
        
        return new FiscalYearPattern(id, tenantId, trimmedName, startMonth, startDay);
    }
    
    /**
     * Calculates the fiscal year for a given date.
     * 
     * @param date The date to calculate the fiscal year for
     * @return The fiscal year as an integer
     */
    public int getFiscalYear(LocalDate date) {
        LocalDate fiscalYearStart = LocalDate.of(date.getYear(), startMonth, startDay);
        
        if (date.isBefore(fiscalYearStart)) {
            return date.getYear() - 1;
        } else {
            return date.getYear();
        }
    }
    
    /**
     * Gets the start and end dates for a given fiscal year.
     * 
     * @param fiscalYear The fiscal year
     * @return A pair of (start date, end date)
     */
    public Pair<LocalDate, LocalDate> getFiscalYearRange(int fiscalYear) {
        LocalDate start = LocalDate.of(fiscalYear, startMonth, startDay);
        LocalDate end = start.plusYears(1).minusDays(1);
        return new Pair<>(start, end);
    }
    
    private static void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new DomainException("NAME_REQUIRED", "Fiscal year pattern name is required");
        }
        if (name.length() > 100) {
            throw new DomainException("NAME_TOO_LONG", "Fiscal year pattern name must not exceed 100 characters");
        }
    }
    
    private static void validateStartMonth(int startMonth) {
        if (startMonth < 1 || startMonth > 12) {
            throw new DomainException(
                "INVALID_START_MONTH",
                "Start month must be between 1 and 12, got: " + startMonth
            );
        }
    }
    
    private static void validateStartDay(int startDay) {
        if (startDay < 1 || startDay > 31) {
            throw new DomainException(
                "INVALID_START_DAY",
                "Start day must be between 1 and 31, got: " + startDay
            );
        }
    }
    
    private static void validateDate(int startMonth, int startDay) {
        try {
            // Use a leap year (2024) to validate February 29
            LocalDate.of(2024, startMonth, startDay);
        } catch (DateTimeException e) {
            throw new DomainException(
                "INVALID_DATE",
                String.format("Invalid date combination: month=%d, day=%d", startMonth, startDay)
            );
        }
    }
    
    // Getters
    public FiscalYearPatternId getId() {
        return id;
    }
    
    public UUID getTenantId() {
        return tenantId;
    }
    
    public String getName() {
        return name;
    }
    
    public int getStartMonth() {
        return startMonth;
    }
    
    public int getStartDay() {
        return startDay;
    }
    
    /**
     * Simple Pair class for returning two values.
     * Includes component methods for Kotlin destructuring.
     */
    public static record Pair<A, B>(A first, B second) {
        public A component1() {
            return first;
        }
        
        public B component2() {
            return second;
        }
    }
}
