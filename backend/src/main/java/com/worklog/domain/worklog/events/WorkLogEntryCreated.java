package com.worklog.domain.worklog.events;

import com.worklog.domain.member.MemberId;
import com.worklog.domain.project.ProjectId;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.shared.TimeAmount;
import com.worklog.domain.worklog.WorkLogEntryId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Event raised when a new work log entry is created.
 */
public record WorkLogEntryCreated(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID memberId,
        UUID projectId,
        LocalDate date,
        double hours,
        String comment,
        UUID enteredBy)
        implements DomainEvent {

    public static WorkLogEntryCreated create(
            WorkLogEntryId id,
            MemberId memberId,
            ProjectId projectId,
            LocalDate date,
            TimeAmount hours,
            String comment,
            MemberId enteredBy) {
        return new WorkLogEntryCreated(
                UUID.randomUUID(),
                Instant.now(),
                id.value(),
                memberId.value(),
                projectId.value(),
                date,
                hours.hours().doubleValue(),
                comment,
                enteredBy.value());
    }

    @Override
    public String eventType() {
        return "WorkLogEntryCreated";
    }
}
