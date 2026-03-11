package com.worklog.domain.fiscalyear;

import com.worklog.domain.shared.EntityId;
import java.util.UUID;

/**
 * Strongly-typed identifier for FiscalYearRule entities.
 */
public record FiscalYearRuleId(UUID value) implements EntityId {

    public FiscalYearRuleId {
        if (value == null) {
            throw new IllegalArgumentException("FiscalYearRuleId value cannot be null");
        }
    }

    public static FiscalYearRuleId generate() {
        return new FiscalYearRuleId(UUID.randomUUID());
    }

    public static FiscalYearRuleId of(UUID value) {
        return new FiscalYearRuleId(value);
    }

    public static FiscalYearRuleId of(String value) {
        return new FiscalYearRuleId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
