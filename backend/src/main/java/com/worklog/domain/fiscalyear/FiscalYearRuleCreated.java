package com.worklog.domain.fiscalyear;

import com.worklog.domain.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event fired when a fiscal year pattern is created.
 *
 * This event captures the initial state of a fiscal year pattern,
 * including the pattern definition (start month/day) and tenant ownership.
 */
public record FiscalYearRuleCreated(
        UUID eventId, Instant occurredAt, UUID aggregateId, UUID tenantId, String name, int startMonth, int startDay)
        implements DomainEvent {

    public static FiscalYearRuleCreated create(
            UUID fiscalYearRuleId, UUID tenantId, String name, int startMonth, int startDay) {
        return new FiscalYearRuleCreated(
                UUID.randomUUID(), Instant.now(), fiscalYearRuleId, tenantId, name, startMonth, startDay);
    }

    @Override
    public String eventType() {
        return "FiscalYearRuleCreated";
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
