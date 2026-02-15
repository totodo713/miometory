package com.worklog.domain.absence;

import com.worklog.domain.shared.EntityId;
import java.util.UUID;

/**
 * Strongly-typed identifier for Absence aggregates.
 *
 * Used to uniquely identify absence records (time away from work such as
 * vacation, sick leave, or special leave).
 */
public record AbsenceId(UUID value) implements EntityId {

    public AbsenceId {
        if (value == null) {
            throw new IllegalArgumentException("AbsenceId value cannot be null");
        }
    }

    /**
     * Generates a new random AbsenceId.
     */
    public static AbsenceId generate() {
        return new AbsenceId(UUID.randomUUID());
    }

    /**
     * Creates an AbsenceId from a UUID.
     */
    public static AbsenceId of(UUID value) {
        return new AbsenceId(value);
    }

    /**
     * Creates an AbsenceId from a string representation.
     */
    public static AbsenceId of(String value) {
        return new AbsenceId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
