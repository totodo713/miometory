package com.worklog.domain.absence.events;

import com.worklog.domain.absence.AbsenceId;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event raised when an absence is deleted.
 */
public record AbsenceDeleted(
    UUID eventId,
    Instant occurredAt,
    UUID aggregateId,
    UUID deletedBy
) implements DomainEvent {
    
    public static AbsenceDeleted create(
        AbsenceId id,
        MemberId deletedBy
    ) {
        return new AbsenceDeleted(
            UUID.randomUUID(),
            Instant.now(),
            id.value(),
            deletedBy.value()
        );
    }
    
    @Override
    public String eventType() {
        return "AbsenceDeleted";
    }
}
