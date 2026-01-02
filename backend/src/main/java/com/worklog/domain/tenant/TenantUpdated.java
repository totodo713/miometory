package com.worklog.domain.tenant;

import com.worklog.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event raised when a tenant is updated.
 */
public record TenantUpdated(
    UUID eventId,
    Instant occurredAt,
    UUID aggregateId,
    String name
) implements DomainEvent {
    
    public static TenantUpdated create(UUID tenantId, String name) {
        return new TenantUpdated(
            UUID.randomUUID(),
            Instant.now(),
            tenantId,
            name
        );
    }
    
    @Override
    public String eventType() {
        return "TenantUpdated";
    }
    
    @Override
    public UUID eventId() {
        return eventId;
    }
    
    @Override
    public Instant occurredAt() {
        return occurredAt;
    }
    
    @Override
    public UUID aggregateId() {
        return aggregateId;
    }
}
