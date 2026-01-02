package com.worklog.domain.fiscalyear;

import com.worklog.domain.shared.EntityId;

import java.util.UUID;

/**
 * Strongly-typed identifier for FiscalYearPattern entities.
 */
public record FiscalYearPatternId(UUID value) implements EntityId {
    
    public FiscalYearPatternId {
        if (value == null) {
            throw new IllegalArgumentException("FiscalYearPatternId value cannot be null");
        }
    }
    
    public static FiscalYearPatternId generate() {
        return new FiscalYearPatternId(UUID.randomUUID());
    }
    
    public static FiscalYearPatternId of(UUID value) {
        return new FiscalYearPatternId(value);
    }
    
    public static FiscalYearPatternId of(String value) {
        return new FiscalYearPatternId(UUID.fromString(value));
    }
    
    @Override
    public String toString() {
        return value.toString();
    }
}
