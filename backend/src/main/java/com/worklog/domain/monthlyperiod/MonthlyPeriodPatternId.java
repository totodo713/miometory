package com.worklog.domain.monthlyperiod;

import com.worklog.domain.shared.EntityId;
import java.util.UUID;

/**
 * Strongly-typed identifier for MonthlyPeriodPattern entities.
 */
public record MonthlyPeriodPatternId(UUID value) implements EntityId {

    public MonthlyPeriodPatternId {
        if (value == null) {
            throw new IllegalArgumentException("MonthlyPeriodPatternId value cannot be null");
        }
    }

    public static MonthlyPeriodPatternId generate() {
        return new MonthlyPeriodPatternId(UUID.randomUUID());
    }

    public static MonthlyPeriodPatternId of(UUID value) {
        return new MonthlyPeriodPatternId(value);
    }

    public static MonthlyPeriodPatternId of(String value) {
        return new MonthlyPeriodPatternId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
