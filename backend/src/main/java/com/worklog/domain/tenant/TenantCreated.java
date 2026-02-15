package com.worklog.domain.tenant;

import com.worklog.domain.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Event raised when a new tenant is created.
 */
public record TenantCreated(UUID eventId, Instant occurredAt, UUID aggregateId, String code, String name)
        implements DomainEvent {

    public static TenantCreated create(UUID tenantId, String code, String name) {
        return new TenantCreated(UUID.randomUUID(), Instant.now(), tenantId, code, name);
    }

    @Override
    public String eventType() {
        return "TenantCreated";
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
