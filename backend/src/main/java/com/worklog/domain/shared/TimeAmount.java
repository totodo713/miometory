package com.worklog.domain.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Value object representing an amount of time in hours.
 * 
 * TimeAmount enforces business rules for time tracking:
 * - Hours must be non-negative
 * - Hours cannot exceed 24 (max hours in a day)
 * - Hours must be in 0.25 hour increments (15-minute intervals)
 * 
 * Used for WorkLogEntry and Absence aggregates to ensure consistent
 * time representation across the domain.
 */
public record TimeAmount(BigDecimal hours) {
    
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal MAX_HOURS = BigDecimal.valueOf(24);
    private static final BigDecimal INCREMENT = BigDecimal.valueOf(0.25);
    
    public TimeAmount {
        if (hours == null) {
            throw new DomainException("TIME_AMOUNT_NULL", "Hours cannot be null");
        }
        
        // Scale to 2 decimal places for consistent comparison
        hours = hours.setScale(2, RoundingMode.HALF_UP);
        
        if (hours.compareTo(ZERO) < 0) {
            throw new DomainException("TIME_AMOUNT_NEGATIVE", "Hours cannot be negative");
        }
        
        if (hours.compareTo(MAX_HOURS) > 0) {
            throw new DomainException("TIME_AMOUNT_EXCEEDS_MAX", 
                "Hours cannot exceed 24");
        }
        
        // Check if hours is a multiple of 0.25
        BigDecimal remainder = hours.remainder(INCREMENT);
        if (remainder.compareTo(ZERO) != 0) {
            throw new DomainException("TIME_AMOUNT_INVALID_INCREMENT", 
                "Hours must be in 0.25 hour increments (15-minute intervals)");
        }
    }
    
    /**
     * Factory method to create TimeAmount from hours.
     */
    public static TimeAmount of(BigDecimal hours) {
        return new TimeAmount(hours);
    }
    
    /**
     * Factory method to create TimeAmount from double value.
     */
    public static TimeAmount of(double hours) {
        return new TimeAmount(BigDecimal.valueOf(hours));
    }
    
    /**
     * Factory method to create zero hours.
     */
    public static TimeAmount zero() {
        return new TimeAmount(ZERO);
    }
    
    /**
     * Adds another TimeAmount to this one.
     * 
     * @throws DomainException if the result exceeds 24 hours
     */
    public TimeAmount plus(TimeAmount other) {
        return new TimeAmount(this.hours.add(other.hours));
    }
    
    /**
     * Subtracts another TimeAmount from this one.
     * 
     * @throws DomainException if the result is negative
     */
    public TimeAmount minus(TimeAmount other) {
        return new TimeAmount(this.hours.subtract(other.hours));
    }
    
    /**
     * Checks if this TimeAmount is greater than another.
     */
    public boolean isGreaterThan(TimeAmount other) {
        return this.hours.compareTo(other.hours) > 0;
    }
    
    /**
     * Checks if this TimeAmount is less than another.
     */
    public boolean isLessThan(TimeAmount other) {
        return this.hours.compareTo(other.hours) < 0;
    }
    
    /**
     * Checks if this TimeAmount is zero.
     */
    public boolean isZero() {
        return this.hours.compareTo(ZERO) == 0;
    }
    
    @Override
    public String toString() {
        return hours.toString() + "h";
    }
}
