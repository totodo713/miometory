package com.worklog.infrastructure.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklog.domain.organization.*;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.eventsourcing.EventStore;
import com.worklog.eventsourcing.StoredEvent;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Organization aggregates.
 * 
 * Provides persistence operations using event sourcing.
 * Reconstructs aggregates by replaying events from the event store.
 */
@Repository
public class OrganizationRepository {

    private final EventStore eventStore;
    private final ObjectMapper objectMapper;

    public OrganizationRepository(EventStore eventStore, ObjectMapper objectMapper) {
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
    }

    /**
     * Save an organization aggregate by appending its uncommitted events.
     */
    @Transactional
    public void save(Organization organization) {
        List<DomainEvent> events = organization.getUncommittedEvents();
        if (events.isEmpty()) {
            return;
        }

        eventStore.append(
                organization.getId().value(),
                organization.getAggregateType(),
                events,
                organization.getVersion()
        );

        organization.clearUncommittedEvents();
        organization.setVersion(organization.getVersion() + events.size());
    }

    /**
     * Find an organization by ID.
     * 
     * Reconstructs the aggregate from events in the event store.
     */
    public Optional<Organization> findById(OrganizationId organizationId) {
        List<StoredEvent> storedEvents = eventStore.load(organizationId.value());
        if (storedEvents.isEmpty()) {
            return Optional.empty();
        }

        // Create empty aggregate using reflection
        Organization organization = createEmptyOrganization();

        // Replay all events to rebuild state
        for (StoredEvent storedEvent : storedEvents) {
            DomainEvent event = deserializeEvent(storedEvent);
            organization.replay(event);
        }

        organization.clearUncommittedEvents();
        return Optional.of(organization);
    }

    /**
     * Deserializes a stored event into a domain event.
     */
    private DomainEvent deserializeEvent(StoredEvent storedEvent) {
        try {
            return switch (storedEvent.eventType()) {
                case "OrganizationCreated" -> objectMapper.readValue(storedEvent.payload(), OrganizationCreated.class);
                case "OrganizationUpdated" -> objectMapper.readValue(storedEvent.payload(), OrganizationUpdated.class);
                case "OrganizationDeactivated" -> objectMapper.readValue(storedEvent.payload(), OrganizationDeactivated.class);
                case "OrganizationActivated" -> objectMapper.readValue(storedEvent.payload(), OrganizationActivated.class);
                case "OrganizationPatternAssigned" -> objectMapper.readValue(storedEvent.payload(), OrganizationPatternAssigned.class);
                default -> throw new IllegalArgumentException("Unknown event type: " + storedEvent.eventType());
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event: " + storedEvent.eventType(), e);
        }
    }

    /**
     * Creates an empty Organization instance using reflection.
     * This is needed because the constructor is private.
     */
    private Organization createEmptyOrganization() {
        try {
            Constructor<Organization> constructor = Organization.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create empty Organization instance", e);
        }
    }

    /**
     * Check if an organization exists by ID.
     */
    public boolean existsById(OrganizationId organizationId) {
        return eventStore.getCurrentVersion(organizationId.value()) > 0;
    }
}
