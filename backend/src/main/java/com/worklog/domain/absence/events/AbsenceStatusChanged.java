package com.worklog.domain.absence.events;

import com.worklog.domain.absence.AbsenceId;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event raised when an absence status changes.
 */
public record AbsenceStatusChanged(
    UUID eventId,
    Instant occurredAt,
    UUID aggregateId,
    String fromStatus,
    String toStatus,
    UUID changedBy
) implements DomainEvent {
    
    public static AbsenceStatusChanged create(
        AbsenceId id,
        String fromStatus,
        String toStatus,
        MemberId changedBy
    ) {
        return new AbsenceStatusChanged(
            UUID.randomUUID(),
            Instant.now(),
            id.value(),
            fromStatus,
            toStatus,
            changedBy.value()
        );
    }
    
    @Override
    public String eventType() {
        return "AbsenceStatusChanged";
    }
}
