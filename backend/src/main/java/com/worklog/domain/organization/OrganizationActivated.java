package com.worklog.domain.organization;

import com.worklog.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when an organization is activated.
 */
public record OrganizationActivated(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId
) implements DomainEvent {
    
    public static OrganizationActivated create(UUID organizationId) {
        return new OrganizationActivated(
                UUID.randomUUID(),
                Instant.now(),
                organizationId
        );
    }
    
    @Override
    public String eventType() {
        return "OrganizationActivated";
    }
}
