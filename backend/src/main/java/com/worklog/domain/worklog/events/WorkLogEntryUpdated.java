package com.worklog.domain.worklog.events;

import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.shared.TimeAmount;
import com.worklog.domain.worklog.WorkLogEntryId;

import java.time.Instant;
import java.util.UUID;

/**
 * Event raised when a work log entry is updated.
 */
public record WorkLogEntryUpdated(
    UUID eventId,
    Instant occurredAt,
    UUID aggregateId,
    double hours,
    String comment,
    UUID updatedBy
) implements DomainEvent {
    
    public static WorkLogEntryUpdated create(
        WorkLogEntryId id,
        TimeAmount hours,
        String comment,
        MemberId updatedBy
    ) {
        return new WorkLogEntryUpdated(
            UUID.randomUUID(),
            Instant.now(),
            id.value(),
            hours.hours().doubleValue(),
            comment,
            updatedBy.value()
        );
    }
    
    @Override
    public String eventType() {
        return "WorkLogEntryUpdated";
    }
}
