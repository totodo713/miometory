package com.worklog.eventsourcing;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a domain event as stored in the event store.
 *
 * This is the persistent representation of a domain event, containing
 * all the metadata needed for storage and retrieval.
 */
public record StoredEvent(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String payload,
        long version,
        Instant createdAt) {

    /**
     * Creates a new StoredEvent with a generated ID and current timestamp.
     */
    public static StoredEvent create(
            String aggregateType, UUID aggregateId, String eventType, String payload, long version) {
        return new StoredEvent(
                UUID.randomUUID(), aggregateType, aggregateId, eventType, payload, version, Instant.now());
    }
}
