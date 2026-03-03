package com.worklog.domain.tenant;

import com.worklog.domain.shared.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when standard daily hours are assigned to a tenant.
 */
public record TenantStandardDailyHoursAssigned(
        UUID eventId, Instant occurredAt, UUID aggregateId, BigDecimal standardDailyHours) implements DomainEvent {

    public static TenantStandardDailyHoursAssigned create(UUID tenantId, BigDecimal standardDailyHours) {
        return new TenantStandardDailyHoursAssigned(UUID.randomUUID(), Instant.now(), tenantId, standardDailyHours);
    }

    @Override
    public String eventType() {
        return "TenantStandardDailyHoursAssigned";
    }
}
