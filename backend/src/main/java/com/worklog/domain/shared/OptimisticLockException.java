package com.worklog.domain.shared;

/**
 * Exception thrown when an optimistic locking conflict occurs.
 *
 * This happens when trying to save an aggregate that has been modified
 * by another transaction since it was loaded. The expected version
 * doesn't match the actual version in the database.
 */
public class OptimisticLockException extends DomainException {

    private final String aggregateType;
    private final String aggregateId;
    private final long expectedVersion;
    private final long actualVersion;

    public OptimisticLockException(String aggregateType, String aggregateId, long expectedVersion, long actualVersion) {
        super(
                "OPTIMISTIC_LOCK_CONFLICT",
                String.format(
                        "Optimistic lock conflict for %s[%s]: expected version %d but was %d",
                        aggregateType, aggregateId, expectedVersion, actualVersion));
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public long getExpectedVersion() {
        return expectedVersion;
    }

    public long getActualVersion() {
        return actualVersion;
    }
}
