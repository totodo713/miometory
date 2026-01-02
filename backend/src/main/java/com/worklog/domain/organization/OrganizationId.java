package com.worklog.domain.organization;

import com.worklog.domain.shared.EntityId;

import java.util.UUID;

/**
 * Strongly-typed identifier for Organization aggregates.
 */
public record OrganizationId(UUID value) implements EntityId {
    
    public OrganizationId {
        if (value == null) {
            throw new IllegalArgumentException("OrganizationId value cannot be null");
        }
    }
    
    public static OrganizationId generate() {
        return new OrganizationId(UUID.randomUUID());
    }
    
    public static OrganizationId of(UUID value) {
        return new OrganizationId(value);
    }
    
    public static OrganizationId of(String value) {
        return new OrganizationId(UUID.fromString(value));
    }
    
    @Override
    public String toString() {
        return value.toString();
    }
}
