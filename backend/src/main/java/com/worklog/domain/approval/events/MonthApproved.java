package com.worklog.domain.approval.events;

import com.worklog.domain.approval.MonthlyApprovalId;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event indicating a manager has approved a member's submitted monthly time.
 * This event triggers all associated work log entries and absences to transition
 * to APPROVED status and become permanently read-only.
 */
public record MonthApproved(UUID eventId, Instant occurredAt, UUID aggregateId, Instant reviewedAt, UUID reviewedBy)
        implements DomainEvent {

    public static MonthApproved create(MonthlyApprovalId id, MemberId reviewedBy) {
        Instant now = Instant.now();
        return new MonthApproved(UUID.randomUUID(), now, id.value(), now, reviewedBy.value());
    }

    @Override
    public String eventType() {
        return "MonthApproved";
    }
}
