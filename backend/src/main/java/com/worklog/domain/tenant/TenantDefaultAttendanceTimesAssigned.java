package com.worklog.domain.tenant;

import com.worklog.domain.shared.DomainEvent;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Domain event emitted when default attendance times are assigned to a tenant.
 */
public record TenantDefaultAttendanceTimesAssigned(
        UUID eventId, Instant occurredAt, UUID aggregateId, LocalTime defaultStartTime, LocalTime defaultEndTime)
        implements DomainEvent {

    public static TenantDefaultAttendanceTimesAssigned create(
            UUID tenantId, LocalTime defaultStartTime, LocalTime defaultEndTime) {
        return new TenantDefaultAttendanceTimesAssigned(
                UUID.randomUUID(), Instant.now(), tenantId, defaultStartTime, defaultEndTime);
    }

    @Override
    public String eventType() {
        return "TenantDefaultAttendanceTimesAssigned";
    }
}
