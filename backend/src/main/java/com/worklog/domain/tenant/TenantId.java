package com.worklog.domain.tenant;

import com.worklog.domain.shared.EntityId;

import java.util.UUID;

/**
 * Strongly-typed identifier for Tenant aggregates.
 */
public record TenantId(UUID value) implements EntityId {
    
    public TenantId {
        if (value == null) {
            throw new IllegalArgumentException("TenantId value cannot be null");
        }
    }
    
    public static TenantId generate() {
        return new TenantId(UUID.randomUUID());
    }
    
    public static TenantId of(UUID value) {
        return new TenantId(value);
    }
    
    public static TenantId of(String value) {
        return new TenantId(UUID.fromString(value));
    }
    
    @Override
    public String toString() {
        return value.toString();
    }
}
