package com.worklog.domain.organization;

import com.worklog.domain.shared.DomainEvent;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Domain event emitted when default attendance times are assigned to an organization.
 */
public record OrganizationDefaultAttendanceTimesAssigned(
        UUID eventId, Instant occurredAt, UUID aggregateId, LocalTime defaultStartTime, LocalTime defaultEndTime)
        implements DomainEvent {

    public static OrganizationDefaultAttendanceTimesAssigned create(
            UUID organizationId, LocalTime defaultStartTime, LocalTime defaultEndTime) {
        return new OrganizationDefaultAttendanceTimesAssigned(
                UUID.randomUUID(), Instant.now(), organizationId, defaultStartTime, defaultEndTime);
    }

    @Override
    public String eventType() {
        return "OrganizationDefaultAttendanceTimesAssigned";
    }
}
