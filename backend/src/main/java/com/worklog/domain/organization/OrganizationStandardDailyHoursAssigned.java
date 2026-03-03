package com.worklog.domain.organization;

import com.worklog.domain.shared.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when standard daily hours are assigned to an organization.
 */
public record OrganizationStandardDailyHoursAssigned(
        UUID eventId, Instant occurredAt, UUID aggregateId, BigDecimal standardDailyHours) implements DomainEvent {

    public static OrganizationStandardDailyHoursAssigned create(UUID organizationId, BigDecimal standardDailyHours) {
        return new OrganizationStandardDailyHoursAssigned(
                UUID.randomUUID(), Instant.now(), organizationId, standardDailyHours);
    }

    @Override
    public String eventType() {
        return "OrganizationStandardDailyHoursAssigned";
    }
}
