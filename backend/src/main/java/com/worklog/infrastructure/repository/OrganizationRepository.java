package com.worklog.infrastructure.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklog.domain.organization.*;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.eventsourcing.EventStore;
import com.worklog.eventsourcing.StoredEvent;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
    private final JdbcTemplate jdbcTemplate;

    public OrganizationRepository(EventStore eventStore, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
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
                organization.getId().value(), organization.getAggregateType(), events, organization.getVersion());

        // Bump aggregate version to reflect appended events before updating projection
        organization.setVersion(organization.getVersion() + events.size());

        // Update projection table for query performance
        updateProjection(organization);

        organization.clearUncommittedEvents();
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
     * Updates the projection table for query performance.
     * Keeps the organization read model in sync with the event store
     * within the same transactional boundary.
     */
    private void updateProjection(Organization organization) {
        jdbcTemplate.update(
                "INSERT INTO organization "
                        + "(id, tenant_id, parent_id, code, name, level, status, version, "
                        + "fiscal_year_pattern_id, monthly_period_pattern_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW()) "
                        + "ON CONFLICT (id) DO UPDATE SET "
                        + "name = EXCLUDED.name, "
                        + "status = EXCLUDED.status, "
                        + "version = EXCLUDED.version, "
                        + "fiscal_year_pattern_id = EXCLUDED.fiscal_year_pattern_id, "
                        + "monthly_period_pattern_id = EXCLUDED.monthly_period_pattern_id, "
                        + "updated_at = NOW()",
                organization.getId().value(),
                organization.getTenantId().value(),
                organization.getParentId() != null ? organization.getParentId().value() : null,
                organization.getCode().value(),
                organization.getName(),
                organization.getLevel(),
                organization.getStatus().name(),
                organization.getVersion(),
                organization.getFiscalYearPatternId(),
                organization.getMonthlyPeriodPatternId());
    }

    /**
     * Deserializes a stored event into a domain event.
     */
    private DomainEvent deserializeEvent(StoredEvent storedEvent) {
        try {
            return switch (storedEvent.eventType()) {
                case "OrganizationCreated" -> objectMapper.readValue(storedEvent.payload(), OrganizationCreated.class);
                case "OrganizationUpdated" -> objectMapper.readValue(storedEvent.payload(), OrganizationUpdated.class);
                case "OrganizationDeactivated" ->
                    objectMapper.readValue(storedEvent.payload(), OrganizationDeactivated.class);
                case "OrganizationActivated" ->
                    objectMapper.readValue(storedEvent.payload(), OrganizationActivated.class);
                case "OrganizationPatternAssigned" ->
                    objectMapper.readValue(storedEvent.payload(), OrganizationPatternAssigned.class);
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
