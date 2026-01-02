package com.worklog.domain.tenant;

import com.worklog.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event raised when a tenant is deactivated.
 */
public record TenantDeactivated(
    UUID eventId,
    Instant occurredAt,
    UUID aggregateId,
    String reason
) implements DomainEvent {
    
    public static TenantDeactivated create(UUID tenantId, String reason) {
        return new TenantDeactivated(
            UUID.randomUUID(),
            Instant.now(),
            tenantId,
            reason
        );
    }
    
    public static TenantDeactivated create(UUID tenantId) {
        return create(tenantId, null);
    }
    
    @Override
    public String eventType() {
        return "TenantDeactivated";
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
