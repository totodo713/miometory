package com.worklog.eventsourcing;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC implementation of the SnapshotStore interface.
 *
 * Uses PostgreSQL with JSONB for state storage.
 * Stores only the latest snapshot per aggregate (upsert behavior).
 */
@Repository
public class JdbcSnapshotStore implements SnapshotStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSnapshotStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(UUID aggregateId, String aggregateType, long version, String state) {
        // Use upsert (INSERT ON CONFLICT) to always keep only the latest snapshot
        jdbcTemplate.update("""
            INSERT INTO snapshot_store (aggregate_id, aggregate_type, version, state, created_at)
            VALUES (?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP)
            ON CONFLICT (aggregate_id) DO UPDATE
            SET aggregate_type = EXCLUDED.aggregate_type,
                version = EXCLUDED.version,
                state = EXCLUDED.state,
                created_at = CURRENT_TIMESTAMP
            """, aggregateId, aggregateType, version, state);
    }

    @Override
    public Optional<Snapshot> load(UUID aggregateId) {
        List<Snapshot> snapshots = jdbcTemplate.query("""
            SELECT aggregate_id, aggregate_type, version, state::text
            FROM snapshot_store
            WHERE aggregate_id = ?
            """, new SnapshotRowMapper(), aggregateId);

        return snapshots.isEmpty() ? Optional.empty() : Optional.of(snapshots.get(0));
    }

    private static class SnapshotRowMapper implements RowMapper<Snapshot> {
        @Override
        public Snapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Snapshot(
                    UUID.fromString(rs.getString("aggregate_id")),
                    rs.getString("aggregate_type"),
                    rs.getLong("version"),
                    rs.getString("state"));
        }
    }
}
