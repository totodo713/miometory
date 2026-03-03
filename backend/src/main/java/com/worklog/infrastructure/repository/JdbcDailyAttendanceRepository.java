package com.worklog.infrastructure.repository;

import com.worklog.domain.attendance.DailyAttendance;
import com.worklog.domain.attendance.DailyAttendanceId;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.tenant.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC-based repository for DailyAttendance entity.
 *
 * Provides CRUD operations for daily attendance records
 * using plain JdbcTemplate with positional parameters.
 */
@Repository
public class JdbcDailyAttendanceRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDailyAttendanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Saves a daily attendance record using UPSERT.
     *
     * Uses ON CONFLICT (member_id, attendance_date) to handle duplicate entries.
     * On conflict, updates start_time, end_time, remarks, increments version, and sets updated_at.
     *
     * @param attendance The attendance record to save
     */
    public void save(DailyAttendance attendance) {
        String sql = """
            INSERT INTO daily_attendance
                (id, tenant_id, member_id, attendance_date, start_time, end_time, remarks, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (member_id, attendance_date) DO UPDATE SET
                start_time = EXCLUDED.start_time,
                end_time = EXCLUDED.end_time,
                remarks = EXCLUDED.remarks,
                version = daily_attendance.version + 1,
                updated_at = CURRENT_TIMESTAMP
            """;

        jdbcTemplate.update(
                sql,
                attendance.getId().value(),
                attendance.getTenantId().value(),
                attendance.getMemberId().value(),
                attendance.getAttendanceDate(),
                attendance.getStartTime() != null ? Time.valueOf(attendance.getStartTime()) : null,
                attendance.getEndTime() != null ? Time.valueOf(attendance.getEndTime()) : null,
                attendance.getRemarks(),
                attendance.getVersion());
    }

    /**
     * Finds a daily attendance record by member and date.
     *
     * @param memberId The member ID
     * @param date The attendance date
     * @return The attendance record, or null if not found
     */
    public DailyAttendance findByMemberAndDate(MemberId memberId, LocalDate date) {
        String sql = """
            SELECT id, tenant_id, member_id, attendance_date, start_time, end_time, remarks, version
            FROM daily_attendance
            WHERE member_id = ? AND attendance_date = ?
            """;

        List<DailyAttendance> results = jdbcTemplate.query(sql, new DailyAttendanceRowMapper(), memberId.value(), date);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Finds daily attendance records for a member within a date range (inclusive).
     *
     * @param memberId The member ID
     * @param startDate The start date (inclusive)
     * @param endDate The end date (inclusive)
     * @return List of attendance records ordered by attendance_date
     */
    public List<DailyAttendance> findByMemberAndDateRange(MemberId memberId, LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT id, tenant_id, member_id, attendance_date, start_time, end_time, remarks, version
            FROM daily_attendance
            WHERE member_id = ? AND attendance_date >= ? AND attendance_date <= ?
            ORDER BY attendance_date
            """;

        return jdbcTemplate.query(sql, new DailyAttendanceRowMapper(), memberId.value(), startDate, endDate);
    }

    /**
     * Deletes a daily attendance record by member and date.
     *
     * @param memberId The member ID
     * @param date The attendance date
     */
    public void deleteByMemberAndDate(MemberId memberId, LocalDate date) {
        String sql = "DELETE FROM daily_attendance WHERE member_id = ? AND attendance_date = ?";
        jdbcTemplate.update(sql, memberId.value(), date);
    }

    /**
     * Row mapper for DailyAttendance entity.
     */
    private static class DailyAttendanceRowMapper implements RowMapper<DailyAttendance> {
        @Override
        public DailyAttendance mapRow(ResultSet rs, int rowNum) throws SQLException {
            Time startTime = rs.getTime("start_time");
            Time endTime = rs.getTime("end_time");

            return new DailyAttendance(
                    DailyAttendanceId.of(rs.getObject("id", UUID.class)),
                    TenantId.of(rs.getObject("tenant_id", UUID.class)),
                    MemberId.of(rs.getObject("member_id", UUID.class)),
                    rs.getDate("attendance_date").toLocalDate(),
                    startTime != null ? startTime.toLocalTime() : null,
                    endTime != null ? endTime.toLocalTime() : null,
                    rs.getString("remarks"),
                    rs.getInt("version"));
        }
    }
}
