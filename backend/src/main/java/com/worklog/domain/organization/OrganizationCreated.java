package com.worklog.domain.organization;

import com.worklog.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a new organization is created.
 */
public record OrganizationCreated(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID tenantId,
        UUID parentId,
        String code,
        String name,
        int level
) implements DomainEvent {
    
    public static OrganizationCreated create(
            UUID organizationId,
            UUID tenantId,
            UUID parentId,
            String code,
            String name,
            int level
    ) {
        return new OrganizationCreated(
                UUID.randomUUID(),
                Instant.now(),
                organizationId,
                tenantId,
                parentId,
                code,
                name,
                level
        );
    }
    
    @Override
    public String eventType() {
        return "OrganizationCreated";
    }
}
