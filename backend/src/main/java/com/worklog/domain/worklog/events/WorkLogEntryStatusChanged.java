package com.worklog.domain.worklog.events;

import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.worklog.WorkLogEntryId;
import com.worklog.domain.worklog.WorkLogStatus;
import java.time.Instant;
import java.util.UUID;

public record WorkLogEntryStatusChanged(
        UUID eventId, Instant occurredAt, UUID aggregateId, String fromStatus, String toStatus, UUID changedBy)
        implements DomainEvent {

    public static WorkLogEntryStatusChanged create(
            WorkLogEntryId id, WorkLogStatus fromStatus, WorkLogStatus toStatus, MemberId changedBy) {
        return new WorkLogEntryStatusChanged(
                UUID.randomUUID(), Instant.now(), id.value(), fromStatus.name(), toStatus.name(), changedBy.value());
    }

    @Override
    public String eventType() {
        return "WorkLogEntryStatusChanged";
    }
}
