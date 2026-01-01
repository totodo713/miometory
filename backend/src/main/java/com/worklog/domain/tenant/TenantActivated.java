package com.worklog.domain.tenant;

import com.worklog.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event raised when a tenant is reactivated.
 */
public record TenantActivated(
    UUID eventId,
    Instant occurredAt,
    UUID aggregateId
) implements DomainEvent {
    
    public static TenantActivated create(UUID tenantId) {
        return new TenantActivated(
            UUID.randomUUID(),
            Instant.now(),
            tenantId
        );
    }
    
    @Override
    public String eventType() {
        return "TenantActivated";
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
