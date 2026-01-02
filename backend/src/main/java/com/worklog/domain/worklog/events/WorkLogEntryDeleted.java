package com.worklog.domain.worklog.events;

import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.worklog.WorkLogEntryId;
import java.time.Instant;
import java.util.UUID;

public record WorkLogEntryDeleted(
    UUID eventId,
    Instant occurredAt,
    UUID aggregateId,
    UUID deletedBy
) implements DomainEvent {
    
    public static WorkLogEntryDeleted create(
        WorkLogEntryId id,
        MemberId deletedBy
    ) {
        return new WorkLogEntryDeleted(
            UUID.randomUUID(),
            Instant.now(),
            id.value(),
            deletedBy.value()
        );
    }
    
    @Override
    public String eventType() {
        return "WorkLogEntryDeleted";
    }
}
