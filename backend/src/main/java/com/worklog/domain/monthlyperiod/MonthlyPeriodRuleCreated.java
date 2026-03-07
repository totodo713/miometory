package com.worklog.domain.monthlyperiod;

import com.worklog.domain.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event fired when a monthly period pattern is created.
 *
 * This event captures the initial state of a monthly period pattern,
 * including the pattern definition (start day) and tenant ownership.
 */
public record MonthlyPeriodRuleCreated(
        UUID eventId, Instant occurredAt, UUID aggregateId, UUID tenantId, String name, int startDay)
        implements DomainEvent {

    public static MonthlyPeriodRuleCreated create(
            UUID monthlyPeriodRuleId, UUID tenantId, String name, int startDay) {
        return new MonthlyPeriodRuleCreated(
                UUID.randomUUID(), Instant.now(), monthlyPeriodRuleId, tenantId, name, startDay);
    }

    @Override
    public String eventType() {
        return "MonthlyPeriodRuleCreated";
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
