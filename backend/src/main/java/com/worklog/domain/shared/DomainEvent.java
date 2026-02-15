package com.worklog.domain.shared;

import java.time.Instant;
import java.util.UUID;

/**
 * Base interface for all domain events.
 *
 * Domain events capture something that happened in the domain that domain experts care about.
 * Events are immutable and represent facts that occurred at a specific point in time.
 */
public interface DomainEvent {

    /**
     * @return Unique identifier for this event instance
     */
    UUID eventId();

    /**
     * @return Type name of this event (e.g., "TenantCreated", "OrganizationUpdated")
     */
    String eventType();

    /**
     * @return Timestamp when this event occurred
     */
    Instant occurredAt();

    /**
     * @return ID of the aggregate that generated this event
     */
    UUID aggregateId();
}
