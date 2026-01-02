package com.worklog.domain.organization;

import com.worklog.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when fiscal year and monthly period patterns are assigned to an organization.
 */
public record OrganizationPatternAssigned(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID fiscalYearPatternId,
        UUID monthlyPeriodPatternId
) implements DomainEvent {
    
    public static OrganizationPatternAssigned create(
            UUID organizationId,
            UUID fiscalYearPatternId,
            UUID monthlyPeriodPatternId
    ) {
        return new OrganizationPatternAssigned(
                UUID.randomUUID(),
                Instant.now(),
                organizationId,
                fiscalYearPatternId,
                monthlyPeriodPatternId
        );
    }
    
    @Override
    public String eventType() {
        return "OrganizationPatternAssigned";
    }
}
