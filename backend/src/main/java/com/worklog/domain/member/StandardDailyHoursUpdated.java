package com.worklog.domain.member;

import com.worklog.domain.shared.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a member's standard daily hours are updated.
 */
public record StandardDailyHoursUpdated(
        UUID eventId, Instant occurredAt, UUID aggregateId, BigDecimal standardDailyHours) implements DomainEvent {

    public static StandardDailyHoursUpdated create(UUID memberId, BigDecimal standardDailyHours) {
        return new StandardDailyHoursUpdated(UUID.randomUUID(), Instant.now(), memberId, standardDailyHours);
    }

    @Override
    public String eventType() {
        return "StandardDailyHoursUpdated";
    }
}
