package com.worklog.domain.tenant;

import com.worklog.domain.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Event raised when default patterns are assigned to a tenant.
 * Either or both pattern IDs may be null (clearing the tenant-level default).
 */
public record TenantDefaultPatternsAssigned(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID defaultFiscalYearPatternId,
        UUID defaultMonthlyPeriodPatternId)
        implements DomainEvent {

    public static TenantDefaultPatternsAssigned create(
            UUID tenantId, UUID defaultFiscalYearPatternId, UUID defaultMonthlyPeriodPatternId) {
        return new TenantDefaultPatternsAssigned(
                UUID.randomUUID(), Instant.now(), tenantId, defaultFiscalYearPatternId, defaultMonthlyPeriodPatternId);
    }

    @Override
    public String eventType() {
        return "TenantDefaultPatternsAssigned";
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
