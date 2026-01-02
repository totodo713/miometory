package com.worklog.domain.fiscalyear;

import com.worklog.domain.shared.AggregateRoot;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;

import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.UUID;

/**
 * FiscalYearPattern aggregate root that defines how fiscal years are calculated.
 * 
 * Business Rules:
 * - startMonth must be 1-12 (January to December)
 * - startDay must be 1-31 and valid for the specified month
 * - The combination of startMonth and startDay must form a valid date
 * 
 * This is an event-sourced aggregate that tracks changes via domain events.
 */
public class FiscalYearPattern extends AggregateRoot<FiscalYearPatternId> {
    
    private FiscalYearPatternId id;
    private TenantId tenantId;
    private String name;
    private int startMonth;
    private int startDay;
    
    // Package-private constructor for factory methods (accessible from Kotlin in same package)
    FiscalYearPattern() {
    }
    
    /**
     * Creates a new FiscalYearPattern.
     * 
     * @param tenantId The tenant ID
     * @param name The pattern name
     * @param startMonth The starting month (1-12)
     * @param startDay The starting day (1-31, must be valid for the month)
     * @return A new FiscalYearPattern with FiscalYearPatternCreated event
     * @throws DomainException if validation fails
     */
    public static FiscalYearPattern create(
        TenantId tenantId,
        String name,
        int startMonth,
        int startDay
    ) {
        validateName(name);
        validateStartMonth(startMonth);
        validateStartDay(startDay);
        validateDate(startMonth, startDay);
        
        FiscalYearPattern pattern = new FiscalYearPattern();
        FiscalYearPatternId patternId = FiscalYearPatternId.generate();
        
        FiscalYearPatternCreated event = FiscalYearPatternCreated.create(
            patternId.value(),
            tenantId.value(),
            name.trim(),
            startMonth,
            startDay
        );
        pattern.raiseEvent(event);
        
        return pattern;
    }
    
    /**
     * Creates a new FiscalYearPattern with a specific ID.
     * Used for reconstitution from event store.
     */
    public static FiscalYearPattern createWithId(
        FiscalYearPatternId id,
        TenantId tenantId,
        String name,
        int startMonth,
        int startDay
    ) {
        validateName(name);
        validateStartMonth(startMonth);
        validateStartDay(startDay);
        validateDate(startMonth, startDay);
        
        FiscalYearPattern pattern = new FiscalYearPattern();
        
        FiscalYearPatternCreated event = FiscalYearPatternCreated.create(
            id.value(),
            tenantId.value(),
            name.trim(),
            startMonth,
            startDay
        );
        pattern.raiseEvent(event);
        
        return pattern;
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
    
    @Override
    protected void apply(DomainEvent event) {
        if (event instanceof FiscalYearPatternCreated e) {
            this.id = FiscalYearPatternId.of(e.aggregateId());
            this.tenantId = TenantId.of(e.tenantId());
            this.name = e.name();
            this.startMonth = e.startMonth();
            this.startDay = e.startDay();
        } else {
            throw new IllegalArgumentException("Unknown event type: " + event.getClass().getName());
        }
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
    
    @Override
    public FiscalYearPatternId getId() {
        return id;
    }
    
    @Override
    public String getAggregateType() {
        return "FiscalYearPattern";
    }
    
    public TenantId getTenantId() {
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
