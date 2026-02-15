package com.worklog.domain.member;

import com.worklog.domain.shared.EntityId;
import java.util.UUID;

/**
 * Strongly-typed identifier for Member aggregates.
 *
 * Members represent employees/users in the organization who can log work hours.
 */
public record MemberId(UUID value) implements EntityId {

    public MemberId {
        if (value == null) {
            throw new IllegalArgumentException("MemberId value cannot be null");
        }
    }

    /**
     * Generates a new random MemberId.
     */
    public static MemberId generate() {
        return new MemberId(UUID.randomUUID());
    }

    /**
     * Creates a MemberId from a UUID.
     */
    public static MemberId of(UUID value) {
        return new MemberId(value);
    }

    /**
     * Creates a MemberId from a string representation.
     */
    public static MemberId of(String value) {
        return new MemberId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
