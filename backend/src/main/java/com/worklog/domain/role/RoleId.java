package com.worklog.domain.role;

import com.worklog.domain.shared.EntityId;
import java.util.UUID;

/**
 * Strongly-typed identifier for Role aggregates.
 *
 * Roles define sets of permissions for authorization.
 */
public record RoleId(UUID value) implements EntityId {

    public RoleId {
        if (value == null) {
            throw new IllegalArgumentException("RoleId value cannot be null");
        }
    }

    /**
     * Generates a new random RoleId.
     */
    public static RoleId generate() {
        return new RoleId(UUID.randomUUID());
    }

    /**
     * Creates a RoleId from a UUID.
     */
    public static RoleId of(UUID value) {
        return new RoleId(value);
    }

    /**
     * Creates a RoleId from a string representation.
     */
    public static RoleId of(String value) {
        return new RoleId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
