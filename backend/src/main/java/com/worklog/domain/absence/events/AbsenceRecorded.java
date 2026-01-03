package com.worklog.domain.absence.events;

import com.worklog.domain.absence.AbsenceId;
import com.worklog.domain.absence.AbsenceType;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.shared.TimeAmount;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Event raised when a new absence is recorded.
 */
public record AbsenceRecorded(
    UUID eventId,
    Instant occurredAt,
    UUID aggregateId,
    UUID memberId,
    LocalDate date,
    double hours,
    String absenceType,
    String reason,
    UUID recordedBy
) implements DomainEvent {
    
    public static AbsenceRecorded create(
        AbsenceId id,
        MemberId memberId,
        LocalDate date,
        TimeAmount hours,
        AbsenceType absenceType,
        String reason,
        MemberId recordedBy
    ) {
        return new AbsenceRecorded(
            UUID.randomUUID(),
            Instant.now(),
            id.value(),
            memberId.value(),
            date,
            hours.hours().doubleValue(),
            absenceType.name(),
            reason,
            recordedBy.value()
        );
    }
    
    @Override
    public String eventType() {
        return "AbsenceRecorded";
    }
}
