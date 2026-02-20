package com.worklog.infrastructure.repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for the daily_rejection_log projection table.
 *
 * This is a read/write projection (not event-sourced) used to track
 * individual daily rejections separately from monthly approval rejections.
 * Uses UPSERT semantics: re-rejection of the same member+date overwrites the previous record.
 */
@Repository
public class JdbcDailyRejectionLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDailyRejectionLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Save or update a daily rejection log entry.
     * Uses INSERT ... ON CONFLICT to implement UPSERT on (member_id, work_date).
     */
    @Transactional
    public void save(
            UUID memberId, LocalDate workDate, UUID rejectedBy, String rejectionReason, Set<UUID> affectedEntryIds) {
        UUID[] entryIdsArray = affectedEntryIds.toArray(new UUID[0]);

        String sql = """
                INSERT INTO daily_rejection_log (member_id, work_date, rejected_by, rejection_reason, affected_entry_ids, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (member_id, work_date)
                DO UPDATE SET rejected_by = EXCLUDED.rejected_by,
                              rejection_reason = EXCLUDED.rejection_reason,
                              affected_entry_ids = EXCLUDED.affected_entry_ids,
                              created_at = EXCLUDED.created_at
                """;

        jdbcTemplate.update(
                sql, memberId, workDate, rejectedBy, rejectionReason, entryIdsArray, Timestamp.from(Instant.now()));
    }

    /**
     * Check if a daily rejection log entry exists for a member on a specific date.
     * Used by recall logic to determine if entries went through a daily rejection cycle.
     */
    public boolean existsByMemberIdAndDate(UUID memberId, LocalDate workDate) {
        String sql = """
                SELECT COUNT(*) > 0
                FROM daily_rejection_log
                WHERE member_id = ?
                AND work_date = ?
                """;

        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, memberId, workDate));
    }

    /**
     * Find daily rejection log entries for a member within a date range.
     * Used to enrich calendar entries with daily rejection data.
     */
    public List<DailyRejectionRecord> findByMemberIdAndDateRange(
            UUID memberId, LocalDate startDate, LocalDate endDate) {
        String sql = """
                SELECT id, member_id, work_date, rejected_by, rejection_reason, affected_entry_ids, created_at
                FROM daily_rejection_log
                WHERE member_id = ?
                AND work_date BETWEEN ? AND ?
                ORDER BY work_date
                """;

        return jdbcTemplate.query(sql, this::mapRow, memberId, startDate, endDate);
    }

    private DailyRejectionRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        Array sqlArray = rs.getArray("affected_entry_ids");
        UUID[] uuidArray = sqlArray != null ? (UUID[]) sqlArray.getArray() : new UUID[0];
        Set<UUID> affectedEntryIds = Arrays.stream(uuidArray).collect(Collectors.toSet());

        return new DailyRejectionRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("member_id", UUID.class),
                rs.getDate("work_date").toLocalDate(),
                rs.getObject("rejected_by", UUID.class),
                rs.getString("rejection_reason"),
                affectedEntryIds,
                rs.getTimestamp("created_at").toInstant());
    }

    /**
     * Lightweight record representing a daily rejection log row.
     */
    public record DailyRejectionRecord(
            UUID id,
            UUID memberId,
            LocalDate workDate,
            UUID rejectedBy,
            String rejectionReason,
            Set<UUID> affectedEntryIds,
            Instant createdAt) {}
}
