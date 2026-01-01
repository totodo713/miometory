package com.worklog.domain.monthlyperiod;

import com.worklog.domain.shared.DomainException;

import java.time.LocalDate;
import java.util.UUID;

/**
 * MonthlyPeriodPattern entity that defines how monthly periods are calculated.
 * 
 * Business Rules:
 * - startDay must be 1-28 (restricted to 28 to handle February in all years)
 * - Monthly periods are calculated based on the start day
 * - If current day >= startDay, period starts on startDay of current month
 * - If current day < startDay, period starts on startDay of previous month
 */
public class MonthlyPeriodPattern {
    
    private final MonthlyPeriodPatternId id;
    private final UUID tenantId;
    private final String name;
    private final int startDay;
    
    private MonthlyPeriodPattern(
        MonthlyPeriodPatternId id,
        UUID tenantId,
        String name,
        int startDay
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.startDay = startDay;
    }
    
    /**
     * Creates a new MonthlyPeriodPattern.
     * 
     * @param tenantId The tenant ID
     * @param name The pattern name
     * @param startDay The starting day (1-28)
     * @return A new MonthlyPeriodPattern
     * @throws DomainException if validation fails
     */
    public static MonthlyPeriodPattern create(
        UUID tenantId,
        String name,
        int startDay
    ) {
        return create(MonthlyPeriodPatternId.generate(), tenantId, name, startDay);
    }
    
    /**
     * Creates a new MonthlyPeriodPattern with a specific ID.
     * Used for reconstitution from storage.
     */
    public static MonthlyPeriodPattern create(
        MonthlyPeriodPatternId id,
        UUID tenantId,
        String name,
        int startDay
    ) {
        validateName(name);
        validateStartDay(startDay);
        
        String trimmedName = name.trim();
        
        return new MonthlyPeriodPattern(id, tenantId, trimmedName, startDay);
    }
    
    /**
     * Calculates the monthly period for a given date.
     * 
     * @param date The date to calculate the monthly period for
     * @return A MonthlyPeriod with start and end dates
     */
    public MonthlyPeriod getMonthlyPeriod(LocalDate date) {
        LocalDate periodStart;
        
        if (date.getDayOfMonth() >= startDay) {
            // Period starts in the current month
            periodStart = date.withDayOfMonth(startDay);
        } else {
            // Period starts in the previous month
            periodStart = date.minusMonths(1).withDayOfMonth(startDay);
        }
        
        LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);
        
        return new MonthlyPeriod(periodStart, periodEnd);
    }
    
    private static void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new DomainException("NAME_REQUIRED", "Monthly period pattern name is required");
        }
        if (name.length() > 100) {
            throw new DomainException("NAME_TOO_LONG", "Monthly period pattern name must not exceed 100 characters");
        }
    }
    
    private static void validateStartDay(int startDay) {
        if (startDay < 1 || startDay > 28) {
            throw new DomainException(
                "INVALID_START_DAY",
                "Start day must be between 1 and 28 (to handle February), got: " + startDay
            );
        }
    }
    
    // Getters
    public MonthlyPeriodPatternId getId() {
        return id;
    }
    
    public UUID getTenantId() {
        return tenantId;
    }
    
    public String getName() {
        return name;
    }
    
    public int getStartDay() {
        return startDay;
    }
}
