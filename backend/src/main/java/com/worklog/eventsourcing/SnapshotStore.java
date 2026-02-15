package com.worklog.eventsourcing;

import java.util.Optional;
import java.util.UUID;

/**
 * Stores and retrieves aggregate snapshots for performance optimization.
 *
 * Snapshots capture the current state of an aggregate at a specific version,
 * allowing faster reconstitution by replaying only events after the snapshot.
 */
public interface SnapshotStore {

    /**
     * Saves a snapshot of an aggregate's state.
     *
     * @param aggregateId The ID of the aggregate
     * @param aggregateType The type name of the aggregate
     * @param version The version at which the snapshot was taken
     * @param state The serialized state of the aggregate
     */
    void save(UUID aggregateId, String aggregateType, long version, String state);

    /**
     * Loads the latest snapshot for an aggregate.
     *
     * @param aggregateId The ID of the aggregate
     * @return Optional containing the snapshot if one exists
     */
    Optional<Snapshot> load(UUID aggregateId);

    /**
     * Represents a stored snapshot.
     */
    record Snapshot(UUID aggregateId, String aggregateType, long version, String state) {}
}
