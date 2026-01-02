package com.worklog.domain.shared;

import java.util.UUID;

/**
 * Base class for strongly-typed entity identifiers.
 * 
 * Provides type-safety for IDs to prevent accidentally mixing up different entity IDs.
 * 
 * Usage:
 * <pre>
 * public record TenantId(UUID value) implements EntityId {}
 * </pre>
 */
public interface EntityId {
    
    /**
     * @return The underlying UUID value
     */
    UUID value();
    
    /**
     * Creates a new random ID for subclasses to use in factory methods.
     * @return A new random UUID
     */
    static UUID randomUUID() {
        return UUID.randomUUID();
    }
}
