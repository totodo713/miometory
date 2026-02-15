package com.worklog.domain.permission;

import com.worklog.domain.shared.EntityId;
import java.util.UUID;

/**
 * Strongly-typed identifier for Permission aggregates.
 *
 * Permissions define specific actions users can perform.
 */
public record PermissionId(UUID value) implements EntityId {

    public PermissionId {
        if (value == null) {
            throw new IllegalArgumentException("PermissionId value cannot be null");
        }
    }

    /**
     * Generates a new random PermissionId.
     */
    public static PermissionId generate() {
        return new PermissionId(UUID.randomUUID());
    }

    /**
     * Creates a PermissionId from a UUID.
     */
    public static PermissionId of(UUID value) {
        return new PermissionId(value);
    }

    /**
     * Creates a PermissionId from a string representation.
     */
    public static PermissionId of(String value) {
        return new PermissionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
