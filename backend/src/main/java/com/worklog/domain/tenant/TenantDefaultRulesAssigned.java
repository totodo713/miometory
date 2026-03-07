package com.worklog.domain.tenant;

import com.worklog.domain.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Event raised when default patterns are assigned to a tenant.
 * Either or both pattern IDs may be null (clearing the tenant-level default).
 */
public record TenantDefaultRulesAssigned(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID defaultFiscalYearRuleId,
        UUID defaultMonthlyPeriodRuleId)
        implements DomainEvent {

    public static TenantDefaultRulesAssigned create(
            UUID tenantId, UUID defaultFiscalYearRuleId, UUID defaultMonthlyPeriodRuleId) {
        return new TenantDefaultRulesAssigned(
                UUID.randomUUID(), Instant.now(), tenantId, defaultFiscalYearRuleId, defaultMonthlyPeriodRuleId);
    }

    @Override
    public String eventType() {
        return "TenantDefaultRulesAssigned";
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
