package com.worklog.infrastructure.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.shared.TimeAmount;
import com.worklog.domain.worklog.WorkLogEntry;
import com.worklog.domain.worklog.WorkLogEntryId;
import com.worklog.domain.worklog.events.WorkLogEntryCreated;
import com.worklog.domain.worklog.events.WorkLogEntryDeleted;
import com.worklog.domain.worklog.events.WorkLogEntryStatusChanged;
import com.worklog.domain.worklog.events.WorkLogEntryUpdated;
import com.worklog.eventsourcing.EventStore;
import com.worklog.eventsourcing.StoredEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for WorkLogEntry aggregates.
 * 
 * Provides persistence operations using event sourcing.
 * Reconstructs aggregates by replaying events from the event store.
 */
@Repository
public class JdbcWorkLogRepository {

    private final EventStore eventStore;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkLogRepository(EventStore eventStore, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Save a work log entry aggregate by appending its uncommitted events.
     */
    @Transactional
    public void save(WorkLogEntry entry) {
        List<DomainEvent> events = entry.getUncommittedEvents();
        if (events.isEmpty()) {
            return;
        }

        eventStore.append(
                entry.getId().value(),
                entry.getAggregateType(),
                events,
                entry.getVersion()
        );

        entry.clearUncommittedEvents();
        entry.setVersion(entry.getVersion() + events.size());
    }

    /**
     * Find a work log entry by ID.
     * 
     * Reconstructs the aggregate from events in the event store.
     */
    public Optional<WorkLogEntry> findById(WorkLogEntryId entryId) {
        List<StoredEvent> storedEvents = eventStore.load(entryId.value());
        if (storedEvents.isEmpty()) {
            return Optional.empty();
        }

        // Create empty aggregate using reflection
        WorkLogEntry entry = createEmptyEntry();

        // Replay all events to rebuild state
        for (StoredEvent storedEvent : storedEvents) {
            DomainEvent event = deserializeEvent(storedEvent);
            entry.replay(event);
        }

        entry.clearUncommittedEvents();
        return Optional.of(entry);
    }

    /**
     * Calculate total hours for a member on a specific date across all projects.
     * This is used for the 24-hour daily limit validation.
     * 
     * @param memberId Member ID
     * @param date Date to check
     * @param excludeEntryId Optional entry ID to exclude (for updates)
     * @return Total hours as BigDecimal
     */
    public BigDecimal getTotalHoursForDate(UUID memberId, LocalDate date, UUID excludeEntryId) {
        String sql = """
            SELECT COALESCE(SUM(CAST(payload->>'hours' AS DECIMAL)), 0) as total
            FROM event_store
            WHERE aggregate_type = 'WorkLogEntry'
            AND CAST(payload->>'memberId' AS UUID) = ?
            AND CAST(payload->>'date' AS DATE) = ?
            AND event_type = 'WorkLogEntryCreated'
            AND aggregate_id NOT IN (
                SELECT aggregate_id 
                FROM event_store 
                WHERE aggregate_type = 'WorkLogEntry'
                AND event_type = 'WorkLogEntryDeleted'
            )
            AND aggregate_id != COALESCE(?, '00000000-0000-0000-0000-000000000000'::UUID)
            """;

        return jdbcTemplate.queryForObject(sql, BigDecimal.class, memberId, date, excludeEntryId);
    }

    /**
     * Deserializes a stored event into a domain event.
     */
    private DomainEvent deserializeEvent(StoredEvent storedEvent) {
        try {
            return switch (storedEvent.eventType()) {
                case "WorkLogEntryCreated" -> objectMapper.readValue(storedEvent.payload(), WorkLogEntryCreated.class);
                case "WorkLogEntryUpdated" -> objectMapper.readValue(storedEvent.payload(), WorkLogEntryUpdated.class);
                case "WorkLogEntryDeleted" -> objectMapper.readValue(storedEvent.payload(), WorkLogEntryDeleted.class);
                case "WorkLogEntryStatusChanged" -> objectMapper.readValue(storedEvent.payload(), WorkLogEntryStatusChanged.class);
                default -> throw new IllegalArgumentException("Unknown event type: " + storedEvent.eventType());
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event: " + storedEvent.eventType(), e);
        }
    }

    /**
     * Creates an empty WorkLogEntry instance using reflection.
     * This is needed because the constructor is private.
     */
    private WorkLogEntry createEmptyEntry() {
        try {
            Constructor<WorkLogEntry> constructor = WorkLogEntry.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create empty WorkLogEntry instance", e);
        }
    }

    /**
     * Check if a work log entry exists by ID.
     */
    public boolean existsById(WorkLogEntryId entryId) {
        return eventStore.getCurrentVersion(entryId.value()) > 0;
    }
}
