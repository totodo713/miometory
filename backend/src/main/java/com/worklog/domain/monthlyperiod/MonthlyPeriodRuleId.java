package com.worklog.domain.monthlyperiod;

import com.worklog.domain.shared.EntityId;
import java.util.UUID;

/**
 * Strongly-typed identifier for MonthlyPeriodRule entities.
 */
public record MonthlyPeriodRuleId(UUID value) implements EntityId {

    public MonthlyPeriodRuleId {
        if (value == null) {
            throw new IllegalArgumentException("MonthlyPeriodRuleId value cannot be null");
        }
    }

    public static MonthlyPeriodRuleId generate() {
        return new MonthlyPeriodRuleId(UUID.randomUUID());
    }

    public static MonthlyPeriodRuleId of(UUID value) {
        return new MonthlyPeriodRuleId(value);
    }

    public static MonthlyPeriodRuleId of(String value) {
        return new MonthlyPeriodRuleId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
