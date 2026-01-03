package com.worklog.infrastructure.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklog.domain.approval.MonthlyApproval;
import com.worklog.domain.approval.MonthlyApprovalId;
import com.worklog.domain.approval.events.MonthApproved;
import com.worklog.domain.approval.events.MonthRejected;
import com.worklog.domain.approval.events.MonthSubmittedForApproval;
import com.worklog.domain.approval.events.MonthlyApprovalCreated;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.shared.FiscalMonthPeriod;
import com.worklog.eventsourcing.EventStore;
import com.worklog.eventsourcing.StoredEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for MonthlyApproval aggregates.
 * 
 * Provides persistence operations using event sourcing.
 * Reconstructs aggregates by replaying events from the event store.
 */
@Repository
public class JdbcApprovalRepository {

    private final EventStore eventStore;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public JdbcApprovalRepository(EventStore eventStore, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Save a monthly approval aggregate by appending its uncommitted events.
     */
    @Transactional
    public void save(MonthlyApproval approval) {
        List<DomainEvent> events = approval.getUncommittedEvents();
        if (events.isEmpty()) {
            return;
        }

        eventStore.append(
                approval.getId().value(),
                approval.getAggregateType(),
                events,
                approval.getVersion()
        );

        approval.clearUncommittedEvents();
        approval.setVersion(approval.getVersion() + events.size());
    }

    /**
     * Find a monthly approval by ID.
     * 
     * Reconstructs the aggregate from events in the event store.
     */
    public Optional<MonthlyApproval> findById(MonthlyApprovalId approvalId) {
        List<StoredEvent> storedEvents = eventStore.load(approvalId.value());
        if (storedEvents.isEmpty()) {
            return Optional.empty();
        }

        // Create empty aggregate using reflection
        MonthlyApproval approval = createEmptyApproval();

        // Replay all events to rebuild state
        for (StoredEvent storedEvent : storedEvents) {
            DomainEvent event = deserializeEvent(storedEvent);
            approval.replay(event);
        }

        approval.clearUncommittedEvents();
        return Optional.of(approval);
    }

    /**
     * Find a monthly approval by member and fiscal month.
     * Returns the approval record if it exists, otherwise empty.
     * 
     * @param memberId Member ID
     * @param fiscalMonth Fiscal month period
     * @return Optional containing the approval if found
     */
    public Optional<MonthlyApproval> findByMemberAndFiscalMonth(MemberId memberId, FiscalMonthPeriod fiscalMonth) {
        // Query for aggregate ID matching member and fiscal month
        String sql = """
            SELECT DISTINCT aggregate_id
            FROM event_store
            WHERE aggregate_type = 'MonthlyApproval'
            AND event_type = 'MonthlyApprovalCreated'
            AND CAST(payload->>'memberId' AS UUID) = ?
            AND CAST(payload->>'fiscalMonthStart' AS DATE) = ?
            AND CAST(payload->>'fiscalMonthEnd' AS DATE) = ?
            LIMIT 1
            """;

        List<UUID> aggregateIds = jdbcTemplate.queryForList(
            sql,
            UUID.class,
            memberId.value(),
            fiscalMonth.startDate(),
            fiscalMonth.endDate()
        );

        if (aggregateIds.isEmpty()) {
            return Optional.empty();
        }

        return findById(MonthlyApprovalId.of(aggregateIds.get(0)));
    }

    /**
     * Find all work log entry IDs for a member within a fiscal month.
     * Used when submitting a month for approval.
     * 
     * @param memberId Member ID
     * @param startDate Fiscal month start date
     * @param endDate Fiscal month end date
     * @return List of work log entry UUIDs
     */
    public List<UUID> findWorkLogEntryIds(UUID memberId, LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT DISTINCT aggregate_id
            FROM event_store
            WHERE aggregate_type = 'WorkLogEntry'
            AND event_type = 'WorkLogEntryCreated'
            AND CAST(payload->>'memberId' AS UUID) = ?
            AND CAST(payload->>'date' AS DATE) BETWEEN ? AND ?
            AND aggregate_id NOT IN (
                SELECT aggregate_id 
                FROM event_store 
                WHERE aggregate_type = 'WorkLogEntry'
                AND event_type = 'WorkLogEntryDeleted'
            )
            ORDER BY aggregate_id
            """;

        return jdbcTemplate.queryForList(sql, UUID.class, memberId, startDate, endDate);
    }

    /**
     * Find all absence IDs for a member within a fiscal month.
     * Used when submitting a month for approval.
     * 
     * @param memberId Member ID
     * @param startDate Fiscal month start date
     * @param endDate Fiscal month end date
     * @return List of absence UUIDs
     */
    public List<UUID> findAbsenceIds(UUID memberId, LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT DISTINCT aggregate_id
            FROM event_store
            WHERE aggregate_type = 'Absence'
            AND event_type = 'AbsenceRecorded'
            AND CAST(payload->>'memberId' AS UUID) = ?
            AND CAST(payload->>'date' AS DATE) BETWEEN ? AND ?
            AND aggregate_id NOT IN (
                SELECT aggregate_id 
                FROM event_store 
                WHERE aggregate_type = 'Absence'
                AND event_type = 'AbsenceDeleted'
            )
            ORDER BY aggregate_id
            """;

        return jdbcTemplate.queryForList(sql, UUID.class, memberId, startDate, endDate);
    }

    /**
     * Create an empty MonthlyApproval instance using reflection.
     * This is needed to replay events on a blank aggregate.
     */
    private MonthlyApproval createEmptyApproval() {
        try {
            Constructor<MonthlyApproval> constructor = MonthlyApproval.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create empty MonthlyApproval instance", e);
        }
    }

    /**
     * Deserialize a stored event into its domain event type.
     */
    private DomainEvent deserializeEvent(StoredEvent storedEvent) {
        try {
            return switch (storedEvent.eventType()) {
                case "MonthlyApprovalCreated" -> objectMapper.readValue(
                    storedEvent.payload(), 
                    MonthlyApprovalCreated.class
                );
                case "MonthSubmittedForApproval" -> objectMapper.readValue(
                    storedEvent.payload(), 
                    MonthSubmittedForApproval.class
                );
                case "MonthApproved" -> objectMapper.readValue(
                    storedEvent.payload(), 
                    MonthApproved.class
                );
                case "MonthRejected" -> objectMapper.readValue(
                    storedEvent.payload(), 
                    MonthRejected.class
                );
                default -> throw new IllegalArgumentException("Unknown event type: " + storedEvent.eventType());
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event: " + storedEvent.eventType(), e);
        }
    }
}
