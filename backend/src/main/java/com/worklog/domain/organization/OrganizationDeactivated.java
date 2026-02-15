package com.worklog.domain.organization;

import com.worklog.domain.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when an organization is deactivated.
 */
public record OrganizationDeactivated(UUID eventId, Instant occurredAt, UUID aggregateId) implements DomainEvent {

    public static OrganizationDeactivated create(UUID organizationId) {
        return new OrganizationDeactivated(UUID.randomUUID(), Instant.now(), organizationId);
    }

    @Override
    public String eventType() {
        return "OrganizationDeactivated";
    }
}
