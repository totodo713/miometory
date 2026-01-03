package com.worklog.domain.approval.events;

import com.worklog.domain.approval.MonthlyApprovalId;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event indicating a manager has rejected a member's submitted monthly time.
 * This event triggers all associated work log entries and absences to transition
 * back to DRAFT status, allowing the member to make corrections and resubmit.
 */
public record MonthRejected(
    UUID eventId,
    Instant occurredAt,
    UUID aggregateId,
    Instant reviewedAt,
    UUID reviewedBy,
    String rejectionReason
) implements DomainEvent {
    
    public MonthRejected {
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("rejectionReason must not be blank");
        }
        if (rejectionReason.length() > 1000) {
            throw new IllegalArgumentException("rejectionReason must not exceed 1000 characters");
        }
    }
    
    public static MonthRejected create(
        MonthlyApprovalId id,
        MemberId reviewedBy,
        String rejectionReason
    ) {
        Instant now = Instant.now();
        return new MonthRejected(
            UUID.randomUUID(),
            now,
            id.value(),
            now,
            reviewedBy.value(),
            rejectionReason
        );
    }
    
    @Override
    public String eventType() {
        return "MonthRejected";
    }
}
