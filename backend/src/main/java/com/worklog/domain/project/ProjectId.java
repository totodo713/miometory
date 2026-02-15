package com.worklog.domain.project;

import com.worklog.domain.shared.EntityId;
import java.util.UUID;

/**
 * Strongly-typed identifier for Project aggregates.
 *
 * Projects represent work assignments that members can log hours against.
 */
public record ProjectId(UUID value) implements EntityId {

    public ProjectId {
        if (value == null) {
            throw new IllegalArgumentException("ProjectId value cannot be null");
        }
    }

    /**
     * Generates a new random ProjectId.
     */
    public static ProjectId generate() {
        return new ProjectId(UUID.randomUUID());
    }

    /**
     * Creates a ProjectId from a UUID.
     */
    public static ProjectId of(UUID value) {
        return new ProjectId(value);
    }

    /**
     * Creates a ProjectId from a string representation.
     */
    public static ProjectId of(String value) {
        return new ProjectId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
