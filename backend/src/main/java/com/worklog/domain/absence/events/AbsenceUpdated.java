package com.worklog.domain.absence.events;

import com.worklog.domain.absence.AbsenceId;
import com.worklog.domain.absence.AbsenceType;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.shared.TimeAmount;

import java.time.Instant;
import java.util.UUID;

/**
 * Event raised when an absence is updated.
 */
public record AbsenceUpdated(
    UUID eventId,
    Instant occurredAt,
    UUID aggregateId,
    double hours,
    String absenceType,
    String reason,
    UUID updatedBy
) implements DomainEvent {
    
    public static AbsenceUpdated create(
        AbsenceId id,
        TimeAmount hours,
        AbsenceType absenceType,
        String reason,
        MemberId updatedBy
    ) {
        return new AbsenceUpdated(
            UUID.randomUUID(),
            Instant.now(),
            id.value(),
            hours.hours().doubleValue(),
            absenceType.name(),
            reason,
            updatedBy.value()
        );
    }
    
    @Override
    public String eventType() {
        return "AbsenceUpdated";
    }
}
