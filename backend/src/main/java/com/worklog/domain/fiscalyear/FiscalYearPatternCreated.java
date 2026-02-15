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
public record FiscalYearPatternCreated(
        UUID eventId, Instant occurredAt, UUID aggregateId, UUID tenantId, String name, int startMonth, int startDay)
        implements DomainEvent {

    public static FiscalYearPatternCreated create(
            UUID fiscalYearPatternId, UUID tenantId, String name, int startMonth, int startDay) {
        return new FiscalYearPatternCreated(
                UUID.randomUUID(), Instant.now(), fiscalYearPatternId, tenantId, name, startMonth, startDay);
    }

    @Override
    public String eventType() {
        return "FiscalYearPatternCreated";
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
