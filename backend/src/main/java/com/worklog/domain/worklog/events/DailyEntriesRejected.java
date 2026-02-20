package com.worklog.domain.worklog.events;

import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Domain event emitted when a manager rejects daily-submitted entries for a specific date.
 * All SUBMITTED entries for the member on that date are transitioned to DRAFT.
 *
 * This event is emitted by the service layer (not from a single aggregate) since it
 * coordinates multiple WorkLogEntry aggregates. The daily_rejection_log projection
 * is updated synchronously.
 */
public record DailyEntriesRejected(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID memberId,
        LocalDate workDate,
        UUID rejectedBy,
        String rejectionReason,
        Set<UUID> affectedEntryIds)
        implements DomainEvent {

    public static DailyEntriesRejected create(
            MemberId memberId,
            LocalDate workDate,
            MemberId rejectedBy,
            String rejectionReason,
            Set<UUID> affectedEntryIds) {
        return new DailyEntriesRejected(
                UUID.randomUUID(),
                Instant.now(),
                memberId.value(),
                memberId.value(),
                workDate,
                rejectedBy.value(),
                rejectionReason,
                affectedEntryIds);
    }

    @Override
    public String eventType() {
        return "DailyEntriesRejected";
    }
}
