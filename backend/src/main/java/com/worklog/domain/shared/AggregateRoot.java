package com.worklog.domain.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Base class for aggregate roots in the domain model.
 * 
 * Aggregate roots are the main entry points into the domain model.
 * They maintain consistency boundaries and track domain events that occur
 * during their lifecycle.
 * 
 * Features:
 * - Event collection for event sourcing
 * - Version tracking for optimistic locking
 * - Abstract methods for subclasses to implement
 */
public abstract class AggregateRoot<ID extends EntityId> {
    
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();
    private long version = 0;
    
    /**
     * @return The unique identifier of this aggregate
     */
    public abstract ID getId();
    
    /**
     * @return The type name of this aggregate (e.g., "Tenant", "Organization")
     */
    public abstract String getAggregateType();
    
    /**
     * @return Current version of this aggregate (for optimistic locking)
     */
    public long getVersion() {
        return version;
    }
    
    /**
     * Sets the version after loading from the event store.
     * This should only be called by the repository when reconstituting the aggregate.
     * 
     * @param version The version to set
     */
    public void setVersion(long version) {
        this.version = version;
    }
    
    /**
     * @return List of uncommitted events that haven't been persisted yet
     */
    public List<DomainEvent> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }
    
    /**
     * Clears all uncommitted events.
     * This should be called by the repository after successfully persisting the events.
     */
    public void clearUncommittedEvents() {
        uncommittedEvents.clear();
    }
    
    /**
     * Registers a domain event that occurred during an operation on this aggregate.
     * 
     * @param event The domain event to register
     */
    protected void registerEvent(DomainEvent event) {
        uncommittedEvents.add(event);
    }
    
    /**
     * Applies an event to update the aggregate's state.
     * This method should be called both when raising new events and when replaying
     * events from the event store.
     * 
     * @param event The event to apply
     */
    protected abstract void apply(DomainEvent event);
    
    /**
     * Raises a new domain event, applies it to update state, and registers it.
     * 
     * @param event The event to raise
     */
    protected void raiseEvent(DomainEvent event) {
        apply(event);
        registerEvent(event);
    }
    
    /**
     * Replays an event from the event store to rebuild the aggregate's state.
     * This doesn't register the event since it's already persisted.
     * 
     * @param event The event to replay
     */
    public void replay(DomainEvent event) {
        apply(event);
        version++;
    }
}
