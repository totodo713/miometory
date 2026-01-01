package com.worklog.eventsourcing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.shared.OptimisticLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JDBC implementation of the EventStore interface.
 * 
 * Uses PostgreSQL with JSONB for event payload storage.
 * Implements optimistic locking through version checking.
 */
@Repository
public class JdbcEventStore implements EventStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcEventStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void append(UUID aggregateId, String aggregateType, List<DomainEvent> events, long expectedVersion) {
        // Check current version for optimistic locking
        long currentVersion = getCurrentVersion(aggregateId);
        if (currentVersion != expectedVersion) {
            throw new OptimisticLockException(aggregateType, aggregateId.toString(), expectedVersion, currentVersion);
        }

        // Append each event with incrementing version
        long version = expectedVersion;
        for (DomainEvent event : events) {
            version++;
            insertEvent(aggregateId, aggregateType, event, version);
        }
    }

    private void insertEvent(UUID aggregateId, String aggregateType, DomainEvent event, long version) {
        String payload = serializeEvent(event);
        
        jdbcTemplate.update(
            """
            INSERT INTO event_store (id, aggregate_type, aggregate_id, event_type, payload, version, created_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
            """,
            event.eventId(),
            aggregateType,
            aggregateId,
            event.eventType(),
            payload,
            version,
            Timestamp.from(event.occurredAt())
        );
    }

    private String serializeEvent(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event: " + event.eventType(), e);
        }
    }

    @Override
    public List<StoredEvent> load(UUID aggregateId) {
        return jdbcTemplate.query(
            """
            SELECT id, aggregate_type, aggregate_id, event_type, payload::text, version, created_at
            FROM event_store
            WHERE aggregate_id = ?
            ORDER BY version ASC
            """,
            new StoredEventRowMapper(),
            aggregateId
        );
    }

    @Override
    public List<StoredEvent> loadFromVersion(UUID aggregateId, long fromVersion) {
        return jdbcTemplate.query(
            """
            SELECT id, aggregate_type, aggregate_id, event_type, payload::text, version, created_at
            FROM event_store
            WHERE aggregate_id = ? AND version >= ?
            ORDER BY version ASC
            """,
            new StoredEventRowMapper(),
            aggregateId,
            fromVersion
        );
    }

    @Override
    public long getCurrentVersion(UUID aggregateId) {
        Long version = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(MAX(version), 0)
            FROM event_store
            WHERE aggregate_id = ?
            """,
            Long.class,
            aggregateId
        );
        return version != null ? version : 0L;
    }

    private static class StoredEventRowMapper implements RowMapper<StoredEvent> {
        @Override
        public StoredEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new StoredEvent(
                UUID.fromString(rs.getString("id")),
                rs.getString("aggregate_type"),
                UUID.fromString(rs.getString("aggregate_id")),
                rs.getString("event_type"),
                rs.getString("payload"),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant()
            );
        }
    }
}
