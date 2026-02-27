package com.worklog.infrastructure.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklog.domain.member.Member;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.worklog.WorkLogEntry;
import com.worklog.domain.worklog.WorkLogEntryId;
import com.worklog.domain.worklog.events.WorkLogEntryCreated;
import com.worklog.domain.worklog.events.WorkLogEntryDeleted;
import com.worklog.domain.worklog.events.WorkLogEntryStatusChanged;
import com.worklog.domain.worklog.events.WorkLogEntryUpdated;
import com.worklog.eventsourcing.EventStore;
import com.worklog.eventsourcing.StoredEvent;
import com.worklog.infrastructure.projection.MonthlyCalendarProjection;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for WorkLogEntry aggregates.
 *
 * Provides persistence operations using event sourcing.
 * Reconstructs aggregates by replaying events from the event store.
 */
@Repository
public class JdbcWorkLogRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcWorkLogRepository.class);

    private final EventStore eventStore;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final JdbcMemberRepository memberRepository;
    private final MonthlyCalendarProjection calendarProjection;

    public JdbcWorkLogRepository(
            EventStore eventStore,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            JdbcMemberRepository memberRepository,
            MonthlyCalendarProjection calendarProjection) {
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.memberRepository = memberRepository;
        this.calendarProjection = calendarProjection;
    }

    /**
     * Save a work log entry aggregate by appending its uncommitted events.
     * Also updates the projection table synchronously within the same transaction.
     */
    @Transactional
    public void save(WorkLogEntry entry) {
        List<DomainEvent> events = entry.getUncommittedEvents();
        if (events.isEmpty()) {
            return;
        }

        eventStore.append(entry.getId().value(), entry.getAggregateType(), events, entry.getVersion());
        updateProjection(entry, events);

        entry.clearUncommittedEvents();
        entry.setVersion(entry.getVersion() + events.size());

        evictCalendarCache(entry.getMemberId().value());
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

        // Return empty if the aggregate is marked as deleted
        if (entry.isDeleted()) {
            return Optional.empty();
        }

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
     * Check if a non-deleted work log entry exists for a given member, project, and date.
     * Uses a direct SQL query against the event store to avoid full aggregate reconstruction.
     *
     * @param memberId Member ID
     * @param projectId Project ID
     * @param date Date to check
     * @return true if a non-deleted entry exists
     */
    public boolean existsByMemberProjectAndDate(UUID memberId, UUID projectId, LocalDate date) {
        String sql = """
            SELECT COUNT(*) > 0
            FROM event_store
            WHERE aggregate_type = 'WorkLogEntry'
            AND event_type = 'WorkLogEntryCreated'
            AND CAST(payload->>'memberId' AS UUID) = ?
            AND CAST(payload->>'projectId' AS UUID) = ?
            AND CAST(payload->>'date' AS DATE) = ?
            AND aggregate_id NOT IN (
                SELECT aggregate_id
                FROM event_store
                WHERE aggregate_type = 'WorkLogEntry'
                AND event_type = 'WorkLogEntryDeleted'
            )
            """;

        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, memberId, projectId, date));
    }

    /**
     * Find work log entries by date range with optional status filter.
     * Reconstructs aggregates from events.
     *
     * @param memberId Member ID to filter by
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @param status Optional status filter
     * @return List of work log entries matching criteria
     */
    public List<WorkLogEntry> findByDateRange(
            UUID memberId, LocalDate startDate, LocalDate endDate, com.worklog.domain.worklog.WorkLogStatus status) {
        // Query for aggregate IDs matching criteria
        String sql = """
            SELECT DISTINCT e.aggregate_id, CAST(e.payload->>'date' AS DATE) as entry_date
            FROM event_store e
            WHERE e.aggregate_type = 'WorkLogEntry'
            AND e.event_type = 'WorkLogEntryCreated'
            AND CAST(e.payload->>'memberId' AS UUID) = ?
            AND CAST(e.payload->>'date' AS DATE) BETWEEN ? AND ?
            AND e.aggregate_id NOT IN (
                SELECT aggregate_id
                FROM event_store
                WHERE aggregate_type = 'WorkLogEntry'
                AND event_type = 'WorkLogEntryDeleted'
            )
            ORDER BY entry_date DESC
            """;

        List<UUID> aggregateIds = jdbcTemplate.query(
                sql, (rs, rowNum) -> UUID.fromString(rs.getString("aggregate_id")), memberId, startDate, endDate);

        // Reconstruct each aggregate from events
        List<WorkLogEntry> entries = new java.util.ArrayList<>();
        for (UUID aggregateId : aggregateIds) {
            Optional<WorkLogEntry> entry = findById(WorkLogEntryId.of(aggregateId));

            // Apply status filter if specified
            if (entry.isPresent()) {
                if (status == null || entry.get().getStatus() == status) {
                    entries.add(entry.get());
                }
            }
        }

        return entries;
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
                case "WorkLogEntryStatusChanged" ->
                    objectMapper.readValue(storedEvent.payload(), WorkLogEntryStatusChanged.class);
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
     * Updates the projection table based on domain events.
     * SQL failures propagate to the caller, rolling back the enclosing transaction
     * so that the event store and projection stay consistent.
     */
    private void updateProjection(WorkLogEntry entry, List<DomainEvent> events) {
        for (DomainEvent event : events) {
            switch (event) {
                case WorkLogEntryCreated e -> {
                    Member member = findMemberForProjection(MemberId.of(e.memberId()))
                            .orElseThrow(() -> {
                                logger.warn(
                                        "Cannot create projection for entry {}: member {} not found in members table",
                                        e.aggregateId(),
                                        e.memberId());
                                return new IllegalStateException("Cannot create projection for entry "
                                        + e.aggregateId() + ": member " + e.memberId()
                                        + " not found in members table");
                            });
                    if (!member.hasOrganization()) {
                        logger.warn(
                                "Cannot create projection for entry {}: member {} has no organization assigned",
                                e.aggregateId(),
                                e.memberId());
                        throw new IllegalStateException("Cannot create projection for entry " + e.aggregateId()
                                + ": member " + e.memberId() + " has no organization assigned");
                    }
                    UUID organizationId = member.getOrganizationId().value();
                    jdbcTemplate.update(
                            """
                            INSERT INTO work_log_entries_projection
                                (id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by)
                            VALUES (?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?)
                            ON CONFLICT (member_id, project_id, work_date) DO UPDATE SET
                                id = EXCLUDED.id,
                                hours = EXCLUDED.hours,
                                notes = EXCLUDED.notes,
                                status = work_log_entries_projection.status,
                                entered_by = EXCLUDED.entered_by,
                                organization_id = EXCLUDED.organization_id,
                                updated_at = CURRENT_TIMESTAMP
                            """,
                            e.aggregateId(),
                            e.memberId(),
                            organizationId,
                            e.projectId(),
                            e.date(),
                            BigDecimal.valueOf(e.hours()),
                            e.comment(),
                            e.enteredBy());
                }
                case WorkLogEntryUpdated e -> {
                    jdbcTemplate.update("""
                            UPDATE work_log_entries_projection
                            SET hours = ?, notes = ?, updated_at = CURRENT_TIMESTAMP
                            WHERE id = ?
                            """, BigDecimal.valueOf(e.hours()), e.comment(), e.aggregateId());
                }
                case WorkLogEntryStatusChanged e -> {
                    jdbcTemplate.update("""
                            UPDATE work_log_entries_projection
                            SET status = ?, updated_at = CURRENT_TIMESTAMP
                            WHERE id = ?
                            """, e.toStatus(), e.aggregateId());
                }
                case WorkLogEntryDeleted e -> {
                    jdbcTemplate.update("DELETE FROM work_log_entries_projection WHERE id = ?", e.aggregateId());
                }
                default -> {
                    // Unknown event type - skip projection update
                }
            }
        }
    }

    private Optional<Member> findMemberForProjection(MemberId memberId) {
        return memberRepository.findById(memberId);
    }

    private void evictCalendarCache(UUID memberId) {
        try {
            calendarProjection.evictMemberCache(memberId);
        } catch (Exception e) {
            logger.warn("Failed to evict calendar cache for member {}: {}", memberId, e.getMessage());
        }
    }

    /**
     * Check if a work log entry exists by ID.
     */
    public boolean existsById(WorkLogEntryId entryId) {
        return eventStore.getCurrentVersion(entryId.value()) > 0;
    }

    /**
     * Find unique project IDs for a member within a date range.
     * This is used for the "Copy from Previous Month" feature.
     *
     * @param memberId Member ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of unique project IDs
     */
    public List<UUID> findUniqueProjectIdsByDateRange(UUID memberId, LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT DISTINCT CAST(e.payload->>'projectId' AS UUID) as project_id
            FROM event_store e
            WHERE e.aggregate_type = 'WorkLogEntry'
            AND e.event_type = 'WorkLogEntryCreated'
            AND CAST(e.payload->>'memberId' AS UUID) = ?
            AND CAST(e.payload->>'date' AS DATE) BETWEEN ? AND ?
            AND e.aggregate_id NOT IN (
                SELECT aggregate_id
                FROM event_store
                WHERE aggregate_type = 'WorkLogEntry'
                AND event_type = 'WorkLogEntryDeleted'
            )
            ORDER BY project_id
            """;

        return jdbcTemplate.query(
                sql, (rs, rowNum) -> UUID.fromString(rs.getString("project_id")), memberId, startDate, endDate);
    }
}
