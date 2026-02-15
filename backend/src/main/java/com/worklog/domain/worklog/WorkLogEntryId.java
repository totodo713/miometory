package com.worklog.domain.worklog;

import com.worklog.domain.shared.EntityId;
import java.util.UUID;

/**
 * Strongly-typed identifier for WorkLogEntry aggregates.
 *
 * Used to uniquely identify work log entries (hours worked by a member
 * on a specific project on a specific date).
 */
public record WorkLogEntryId(UUID value) implements EntityId {

    public WorkLogEntryId {
        if (value == null) {
            throw new IllegalArgumentException("WorkLogEntryId value cannot be null");
        }
    }

    /**
     * Generates a new random WorkLogEntryId.
     */
    public static WorkLogEntryId generate() {
        return new WorkLogEntryId(UUID.randomUUID());
    }

    /**
     * Creates a WorkLogEntryId from a UUID.
     */
    public static WorkLogEntryId of(UUID value) {
        return new WorkLogEntryId(value);
    }

    /**
     * Creates a WorkLogEntryId from a string representation.
     */
    public static WorkLogEntryId of(String value) {
        return new WorkLogEntryId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
