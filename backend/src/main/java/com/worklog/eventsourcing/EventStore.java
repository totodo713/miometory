package com.worklog.eventsourcing;

import com.worklog.domain.shared.DomainEvent;

import java.util.List;
import java.util.UUID;

/**
 * Repository for persisting and retrieving domain events.
 * 
 * The event store is the primary persistence mechanism for event-sourced aggregates.
 * It provides append-only storage for events and supports optimistic locking
 * through version checking.
 */
public interface EventStore {
    
    /**
     * Appends new events for an aggregate to the event store.
     * 
     * @param aggregateId The ID of the aggregate
     * @param aggregateType The type name of the aggregate
     * @param events The domain events to append
     * @param expectedVersion The expected current version of the aggregate (for optimistic locking)
     * @throws com.worklog.domain.shared.OptimisticLockException if the expected version doesn't match
     */
    void append(UUID aggregateId, String aggregateType, List<DomainEvent> events, long expectedVersion);
    
    /**
     * Loads all stored events for an aggregate.
     * 
     * @param aggregateId The ID of the aggregate
     * @return List of stored events in order of version
     */
    List<StoredEvent> load(UUID aggregateId);
    
    /**
     * Loads stored events for an aggregate starting from a specific version.
     * 
     * @param aggregateId The ID of the aggregate
     * @param fromVersion The version to start loading from (inclusive)
     * @return List of stored events in order of version
     */
    List<StoredEvent> loadFromVersion(UUID aggregateId, long fromVersion);
    
    /**
     * Gets the current version of an aggregate.
     * 
     * @param aggregateId The ID of the aggregate
     * @return The current version, or 0 if the aggregate doesn't exist
     */
    long getCurrentVersion(UUID aggregateId);
}
