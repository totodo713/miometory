package com.worklog.domain.organization;

import com.worklog.domain.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when an organization is updated.
 */
public record OrganizationUpdated(UUID eventId, Instant occurredAt, UUID aggregateId, String name)
        implements DomainEvent {

    public static OrganizationUpdated create(UUID organizationId, String name) {
        return new OrganizationUpdated(UUID.randomUUID(), Instant.now(), organizationId, name);
    }

    @Override
    public String eventType() {
        return "OrganizationUpdated";
    }
}
