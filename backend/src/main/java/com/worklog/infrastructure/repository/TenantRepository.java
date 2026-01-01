package com.worklog.infrastructure.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.tenant.*;
import com.worklog.eventsourcing.EventStore;
import com.worklog.eventsourcing.StoredEvent;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Tenant aggregates.
 * 
 * Provides persistence operations using event sourcing.
 * Reconstructs aggregates by replaying events from the event store.
 */
@Repository
public class TenantRepository {

    private final EventStore eventStore;
    private final ObjectMapper objectMapper;

    public TenantRepository(EventStore eventStore, ObjectMapper objectMapper) {
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
    }

    /**
     * Save a tenant aggregate by appending its uncommitted events.
     */
    @Transactional
    public void save(Tenant tenant) {
        List<DomainEvent> events = tenant.getUncommittedEvents();
        if (events.isEmpty()) {
            return;
        }

        eventStore.append(
                tenant.getId().value(),
                tenant.getAggregateType(),
                events,
                tenant.getVersion()
        );

        tenant.clearUncommittedEvents();
        tenant.setVersion(tenant.getVersion() + events.size());
    }

    /**
     * Find a tenant by ID.
     * 
     * Reconstructs the aggregate from events in the event store.
     */
    public Optional<Tenant> findById(TenantId tenantId) {
        List<StoredEvent> storedEvents = eventStore.load(tenantId.value());
        if (storedEvents.isEmpty()) {
            return Optional.empty();
        }

        // Create empty aggregate using reflection
        Tenant tenant = createEmptyTenant();

        // Replay all events to rebuild state
        for (StoredEvent storedEvent : storedEvents) {
            DomainEvent event = deserializeEvent(storedEvent);
            tenant.replay(event);
        }

        tenant.clearUncommittedEvents();
        return Optional.of(tenant);
    }

    /**
     * Deserializes a stored event into a domain event.
     */
    private DomainEvent deserializeEvent(StoredEvent storedEvent) {
        try {
            return switch (storedEvent.eventType()) {
                case "TenantCreated" -> objectMapper.readValue(storedEvent.payload(), TenantCreated.class);
                case "TenantUpdated" -> objectMapper.readValue(storedEvent.payload(), TenantUpdated.class);
                case "TenantDeactivated" -> objectMapper.readValue(storedEvent.payload(), TenantDeactivated.class);
                case "TenantActivated" -> objectMapper.readValue(storedEvent.payload(), TenantActivated.class);
                default -> throw new IllegalArgumentException("Unknown event type: " + storedEvent.eventType());
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event: " + storedEvent.eventType(), e);
        }
    }

    /**
     * Creates an empty Tenant instance using reflection.
     * This is needed because the constructor is private.
     */
    private Tenant createEmptyTenant() {
        try {
            Constructor<Tenant> constructor = Tenant.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create empty Tenant instance", e);
        }
    }

    /**
     * Check if a tenant exists by ID.
     */
    public boolean existsById(TenantId tenantId) {
        return eventStore.getCurrentVersion(tenantId.value()) > 0;
    }
}
