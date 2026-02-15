package com.worklog.domain.approval.events;

import com.worklog.domain.approval.MonthlyApprovalId;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainEvent;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Domain event indicating a member has submitted their monthly time for approval.
 * This event triggers the workflow where all associated work log entries and
 * absences transition to SUBMITTED status and become read-only for the member.
 */
public record MonthSubmittedForApproval(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        Instant submittedAt,
        UUID submittedBy,
        Set<UUID> workLogEntryIds,
        Set<UUID> absenceIds)
        implements DomainEvent {

    public static MonthSubmittedForApproval create(
            MonthlyApprovalId id, MemberId submittedBy, Set<UUID> workLogEntryIds, Set<UUID> absenceIds) {
        Instant now = Instant.now();
        return new MonthSubmittedForApproval(
                UUID.randomUUID(),
                now,
                id.value(),
                now,
                submittedBy.value(),
                Set.copyOf(workLogEntryIds),
                Set.copyOf(absenceIds));
    }

    @Override
    public String eventType() {
        return "MonthSubmittedForApproval";
    }
}
