package com.worklog.domain.organization;

import com.worklog.domain.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when fiscal year and monthly period rules are assigned to an organization.
 */
public record OrganizationRulesAssigned(
        UUID eventId, Instant occurredAt, UUID aggregateId, UUID fiscalYearRuleId, UUID monthlyPeriodRuleId)
        implements DomainEvent {

    public static OrganizationRulesAssigned create(
            UUID organizationId, UUID fiscalYearRuleId, UUID monthlyPeriodRuleId) {
        return new OrganizationRulesAssigned(
                UUID.randomUUID(), Instant.now(), organizationId, fiscalYearRuleId, monthlyPeriodRuleId);
    }

    @Override
    public String eventType() {
        return "OrganizationRulesAssigned";
    }
}
