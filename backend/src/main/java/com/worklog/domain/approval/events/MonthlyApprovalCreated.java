package com.worklog.domain.approval.events;

import com.worklog.domain.approval.MonthlyApprovalId;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.shared.FiscalMonthPeriod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain event indicating a monthly approval record was created.
 * This event is published when a new fiscal month period is initialized
 * for a member's time tracking approval workflow.
 */
public record MonthlyApprovalCreated(
    UUID eventId,
    Instant occurredAt,
    UUID aggregateId,
    UUID memberId,
    LocalDate fiscalMonthStart,
    LocalDate fiscalMonthEnd
) implements DomainEvent {
    
    public static MonthlyApprovalCreated create(
        MonthlyApprovalId id,
        MemberId memberId,
        FiscalMonthPeriod fiscalMonth
    ) {
        return new MonthlyApprovalCreated(
            UUID.randomUUID(),
            Instant.now(),
            id.value(),
            memberId.value(),
            fiscalMonth.startDate(),
            fiscalMonth.endDate()
        );
    }
    
    @Override
    public String eventType() {
        return "MonthlyApprovalCreated";
    }
}
