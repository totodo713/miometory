package com.worklog.domain.user;

import com.worklog.domain.shared.EntityId;
import java.util.UUID;

/**
 * Strongly-typed identifier for User aggregates.
 *
 * Users are authenticated individuals who can access the system.
 */
public record UserId(UUID value) implements EntityId {

    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("UserId value cannot be null");
        }
    }

    /**
     * Generates a new random UserId.
     */
    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    /**
     * Creates a UserId from a UUID.
     */
    public static UserId of(UUID value) {
        return new UserId(value);
    }

    /**
     * Creates a UserId from a string representation.
     */
    public static UserId of(String value) {
        return new UserId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
