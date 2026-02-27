package com.worklog.infrastructure.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklog.domain.absence.Absence;
import com.worklog.domain.absence.AbsenceId;
import com.worklog.domain.absence.events.AbsenceDeleted;
import com.worklog.domain.absence.events.AbsenceRecorded;
import com.worklog.domain.absence.events.AbsenceStatusChanged;
import com.worklog.domain.absence.events.AbsenceUpdated;
import com.worklog.domain.member.Member;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainEvent;
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
 * Repository for Absence aggregates.
 *
 * Provides persistence operations using event sourcing.
 * Reconstructs aggregates by replaying events from the event store.
 */
@Repository
public class JdbcAbsenceRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcAbsenceRepository.class);

    private final EventStore eventStore;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final JdbcMemberRepository memberRepository;
    private final MonthlyCalendarProjection calendarProjection;

    public JdbcAbsenceRepository(
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
     * Save an absence aggregate by appending its uncommitted events.
     * Also updates the projection table synchronously within the same transaction.
     */
    @Transactional
    public void save(Absence absence) {
        List<DomainEvent> events = absence.getUncommittedEvents();
        if (events.isEmpty()) {
            return;
        }

        eventStore.append(absence.getId().value(), absence.getAggregateType(), events, absence.getVersion());
        updateProjection(absence, events);

        absence.clearUncommittedEvents();
        absence.setVersion(absence.getVersion() + events.size());

        evictCalendarCache(absence.getMemberId().value());
    }

    /**
     * Find an absence by ID.
     *
     * Reconstructs the aggregate from events in the event store.
     */
    public Optional<Absence> findById(AbsenceId absenceId) {
        List<StoredEvent> storedEvents = eventStore.load(absenceId.value());
        if (storedEvents.isEmpty()) {
            return Optional.empty();
        }

        // Create empty aggregate using reflection
        Absence absence = createEmptyAbsence();

        // Replay all events to rebuild state
        for (StoredEvent storedEvent : storedEvents) {
            DomainEvent event = deserializeEvent(storedEvent);
            absence.replay(event);
        }

        absence.clearUncommittedEvents();

        // Return empty if the aggregate is marked as deleted
        if (absence.isDeleted()) {
            return Optional.empty();
        }

        return Optional.of(absence);
    }

    /**
     * Calculate total absence hours for a member on a specific date.
     * This is used for the 24-hour daily limit validation (work + absence).
     *
     * @param memberId Member ID
     * @param date Date to check
     * @param excludeAbsenceId Optional absence ID to exclude (for updates)
     * @return Total absence hours as BigDecimal
     */
    public BigDecimal getTotalHoursForDate(UUID memberId, LocalDate date, UUID excludeAbsenceId) {
        String sql = """
            SELECT COALESCE(SUM(CAST(payload->>'hours' AS DECIMAL)), 0) as total
            FROM event_store
            WHERE aggregate_type = 'Absence'
            AND CAST(payload->>'memberId' AS UUID) = ?
            AND CAST(payload->>'date' AS DATE) = ?
            AND event_type = 'AbsenceRecorded'
            AND aggregate_id NOT IN (
                SELECT aggregate_id
                FROM event_store
                WHERE aggregate_type = 'Absence'
                AND event_type = 'AbsenceDeleted'
            )
            AND aggregate_id != COALESCE(?, '00000000-0000-0000-0000-000000000000'::UUID)
            """;

        return jdbcTemplate.queryForObject(sql, BigDecimal.class, memberId, date, excludeAbsenceId);
    }

    /**
     * Find absences by date range with optional status filter.
     * Reconstructs aggregates from events.
     *
     * @param memberId Member ID to filter by
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @param status Optional status filter
     * @return List of absences matching criteria
     */
    public List<Absence> findByDateRange(
            UUID memberId, LocalDate startDate, LocalDate endDate, com.worklog.domain.absence.AbsenceStatus status) {
        // Query for aggregate IDs matching criteria
        String sql = """
            SELECT DISTINCT e.aggregate_id, CAST(e.payload->>'date' AS DATE) as absence_date
            FROM event_store e
            WHERE e.aggregate_type = 'Absence'
            AND e.event_type = 'AbsenceRecorded'
            AND CAST(e.payload->>'memberId' AS UUID) = ?
            AND CAST(e.payload->>'date' AS DATE) BETWEEN ? AND ?
            AND e.aggregate_id NOT IN (
                SELECT aggregate_id
                FROM event_store
                WHERE aggregate_type = 'Absence'
                AND event_type = 'AbsenceDeleted'
            )
            ORDER BY absence_date DESC
            """;

        List<UUID> aggregateIds = jdbcTemplate.query(
                sql, (rs, rowNum) -> UUID.fromString(rs.getString("aggregate_id")), memberId, startDate, endDate);

        // Reconstruct each aggregate from events
        List<Absence> absences = new java.util.ArrayList<>();
        for (UUID aggregateId : aggregateIds) {
            Optional<Absence> absence = findById(AbsenceId.of(aggregateId));

            // Apply status filter if specified
            if (absence.isPresent()) {
                if (status == null || absence.get().getStatus() == status) {
                    absences.add(absence.get());
                }
            }
        }

        return absences;
    }

    /**
     * Deserializes a stored event into a domain event.
     */
    private DomainEvent deserializeEvent(StoredEvent storedEvent) {
        try {
            return switch (storedEvent.eventType()) {
                case "AbsenceRecorded" -> objectMapper.readValue(storedEvent.payload(), AbsenceRecorded.class);
                case "AbsenceUpdated" -> objectMapper.readValue(storedEvent.payload(), AbsenceUpdated.class);
                case "AbsenceDeleted" -> objectMapper.readValue(storedEvent.payload(), AbsenceDeleted.class);
                case "AbsenceStatusChanged" ->
                    objectMapper.readValue(storedEvent.payload(), AbsenceStatusChanged.class);
                default -> throw new IllegalArgumentException("Unknown event type: " + storedEvent.eventType());
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event: " + storedEvent.eventType(), e);
        }
    }

    /**
     * Creates an empty Absence instance using reflection.
     * This is needed because the constructor is private.
     */
    private Absence createEmptyAbsence() {
        try {
            Constructor<Absence> constructor = Absence.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create empty Absence instance", e);
        }
    }

    /**
     * Updates the projection table based on domain events.
     * SQL failures propagate to the caller, rolling back the enclosing transaction
     * so that the event store and projection stay consistent.
     */
    private void updateProjection(Absence absence, List<DomainEvent> events) {
        for (DomainEvent event : events) {
            switch (event) {
                case AbsenceRecorded e -> {
                    Member member = findMemberForProjection(MemberId.of(e.memberId()))
                            .orElseThrow(() -> {
                                logger.warn(
                                        "Cannot create projection for absence {}: member {} not found in members table",
                                        e.aggregateId(),
                                        e.memberId());
                                return new IllegalStateException("Cannot create projection for absence "
                                        + e.aggregateId() + ": member " + e.memberId()
                                        + " not found in members table");
                            });
                    if (!member.hasOrganization()) {
                        logger.warn(
                                "Cannot create projection for absence {}: member {} has no organization assigned",
                                e.aggregateId(),
                                e.memberId());
                        throw new IllegalStateException("Cannot create projection for absence " + e.aggregateId()
                                + ": member " + e.memberId() + " has no organization assigned");
                    }
                    UUID organizationId = member.getOrganizationId().value();
                    jdbcTemplate.update(
                            """
                            INSERT INTO absences_projection
                                (id, member_id, organization_id, absence_type, start_date, end_date,
                                 hours_per_day, notes, status)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT')
                            """,
                            e.aggregateId(),
                            e.memberId(),
                            organizationId,
                            e.absenceType(),
                            e.date(),
                            e.date(),
                            BigDecimal.valueOf(e.hours()),
                            e.reason());
                }
                case AbsenceUpdated e -> {
                    jdbcTemplate.update(
                            """
                            UPDATE absences_projection
                            SET hours_per_day = ?, absence_type = ?, notes = ?, updated_at = CURRENT_TIMESTAMP
                            WHERE id = ?
                            """, BigDecimal.valueOf(e.hours()), e.absenceType(), e.reason(), e.aggregateId());
                }
                case AbsenceStatusChanged e -> {
                    jdbcTemplate.update("""
                            UPDATE absences_projection
                            SET status = ?, updated_at = CURRENT_TIMESTAMP
                            WHERE id = ?
                            """, e.toStatus(), e.aggregateId());
                }
                case AbsenceDeleted e -> {
                    jdbcTemplate.update("DELETE FROM absences_projection WHERE id = ?", e.aggregateId());
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
     * Check if an absence exists by ID.
     */
    public boolean existsById(AbsenceId absenceId) {
        return eventStore.getCurrentVersion(absenceId.value()) > 0;
    }
}
