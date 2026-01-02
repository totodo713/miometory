package com.worklog.infrastructure.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklog.domain.monthlyperiod.MonthlyPeriodPattern;
import com.worklog.domain.monthlyperiod.MonthlyPeriodPatternCreated;
import com.worklog.domain.monthlyperiod.MonthlyPeriodPatternId;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.eventsourcing.EventStore;
import com.worklog.eventsourcing.StoredEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for MonthlyPeriodPattern aggregates.
 * 
 * Provides persistence operations using event sourcing.
 * Reconstructs aggregates by replaying events from the event store.
 */
@Repository
public class MonthlyPeriodPatternRepository {

    private final EventStore eventStore;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public MonthlyPeriodPatternRepository(
        EventStore eventStore,
        ObjectMapper objectMapper,
        JdbcTemplate jdbcTemplate
    ) {
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Save a monthly period pattern aggregate by appending its uncommitted events.
     */
    @Transactional
    public void save(MonthlyPeriodPattern pattern) {
        List<DomainEvent> events = pattern.getUncommittedEvents();
        if (events.isEmpty()) {
            return;
        }

        eventStore.append(
            pattern.getId().value(),
            pattern.getAggregateType(),
            events,
            pattern.getVersion()
        );

        // Update projection table for query performance
        updateProjection(pattern);

        pattern.clearUncommittedEvents();
        pattern.setVersion(pattern.getVersion() + events.size());
    }

    /**
     * Find a monthly period pattern by ID.
     * 
     * Reconstructs the aggregate from events in the event store.
     */
    public Optional<MonthlyPeriodPattern> findById(MonthlyPeriodPatternId id) {
        List<StoredEvent> storedEvents = eventStore.load(id.value());
        if (storedEvents.isEmpty()) {
            return Optional.empty();
        }

        // Create empty aggregate using reflection
        MonthlyPeriodPattern pattern = createEmptyPattern();

        // Replay all events to rebuild state
        for (StoredEvent storedEvent : storedEvents) {
            DomainEvent event = deserializeEvent(storedEvent);
            pattern.replay(event);
        }

        pattern.clearUncommittedEvents();
        return Optional.of(pattern);
    }

    /**
     * Find all monthly period patterns for a tenant.
     * Uses the projection table for performance.
     */
    public List<MonthlyPeriodPattern> findByTenantId(UUID tenantId) {
        List<UUID> patternIds = jdbcTemplate.query(
            "SELECT id FROM monthly_period_pattern WHERE tenant_id = ? ORDER BY name",
            (rs, rowNum) -> UUID.fromString(rs.getString("id")),
            tenantId
        );

        return patternIds.stream()
            .map(id -> findById(MonthlyPeriodPatternId.of(id)))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }

    /**
     * Check if a pattern exists by ID.
     */
    public boolean existsById(MonthlyPeriodPatternId id) {
        return eventStore.getCurrentVersion(id.value()) > 0;
    }

    /**
     * Updates the projection table for query performance.
     */
    private void updateProjection(MonthlyPeriodPattern pattern) {
        jdbcTemplate.update(
            "INSERT INTO monthly_period_pattern (id, tenant_id, organization_id, name, start_day, created_at) " +
            "VALUES (?, ?, ?, ?, ?, NOW()) " +
            "ON CONFLICT (id) DO UPDATE SET " +
            "name = EXCLUDED.name, " +
            "start_day = EXCLUDED.start_day",
            pattern.getId().value(),
            pattern.getTenantId().value(), // Extract UUID from TenantId
            null, // organization_id - not used in current implementation
            pattern.getName(),
            pattern.getStartDay()
        );
    }

    /**
     * Deserializes a stored event into a domain event.
     */
    private DomainEvent deserializeEvent(StoredEvent storedEvent) {
        try {
            return switch (storedEvent.eventType()) {
                case "MonthlyPeriodPatternCreated" -> 
                    objectMapper.readValue(storedEvent.payload(), MonthlyPeriodPatternCreated.class);
                default -> 
                    throw new IllegalArgumentException("Unknown event type: " + storedEvent.eventType());
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event: " + storedEvent.eventType(), e);
        }
    }

    /**
     * Creates an empty MonthlyPeriodPattern instance using reflection.
     * This is needed because the constructor is private.
     */
    private MonthlyPeriodPattern createEmptyPattern() {
        try {
            Constructor<MonthlyPeriodPattern> constructor = MonthlyPeriodPattern.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create empty MonthlyPeriodPattern instance", e);
        }
    }
}
