package com.worklog.infrastructure.projection;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Projection for monthly calendar view.
 * 
 * Queries the event store to build a read model showing daily totals
 * for work log entries within a fiscal month period.
 */
@Component
public class MonthlyCalendarProjection {

    private final JdbcTemplate jdbcTemplate;

    public MonthlyCalendarProjection(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Gets daily totals for a member within a date range.
     * 
     * This method aggregates work hours from WorkLogEntryCreated events,
     * excluding deleted entries. It returns a map of date to total hours.
     * 
     * @param memberId Member ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Map of date to total work hours
     */
    public Map<LocalDate, BigDecimal> getDailyTotals(
        UUID memberId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        String sql = """
            SELECT 
                CAST(payload->>'date' AS DATE) as entry_date,
                SUM(CAST(payload->>'hours' AS DECIMAL)) as total_hours
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
            GROUP BY entry_date
            ORDER BY entry_date
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
            sql,
            memberId,
            startDate,
            endDate
        );

        Map<LocalDate, BigDecimal> dailyTotals = new HashMap<>();
        for (Map<String, Object> row : results) {
            LocalDate date = ((java.sql.Date) row.get("entry_date")).toLocalDate();
            BigDecimal hours = (BigDecimal) row.get("total_hours");
            dailyTotals.put(date, hours);
        }

        return dailyTotals;
    }

    /**
     * Gets daily calendar entries for a member within a date range.
     * 
     * This includes days with no entries (showing zero hours).
     * 
     * @param memberId Member ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of daily entry projections
     */
    public List<DailyEntryProjection> getDailyEntries(
        UUID memberId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        Map<LocalDate, BigDecimal> dailyTotals = getDailyTotals(memberId, startDate, endDate);
        Map<LocalDate, String> dailyStatuses = getDailyStatuses(memberId, startDate, endDate);

        List<DailyEntryProjection> entries = new ArrayList<>();
        LocalDate current = startDate;
        
        while (!current.isAfter(endDate)) {
            boolean isWeekend = current.getDayOfWeek() == DayOfWeek.SATURDAY 
                             || current.getDayOfWeek() == DayOfWeek.SUNDAY;
            
            BigDecimal totalHours = dailyTotals.getOrDefault(current, BigDecimal.ZERO);
            String status = dailyStatuses.getOrDefault(current, "DRAFT");
            
            entries.add(new DailyEntryProjection(
                current,
                totalHours,
                BigDecimal.ZERO, // Absence hours - not implemented yet
                status,
                isWeekend,
                false // Holiday detection - not implemented yet
            ));
            
            current = current.plusDays(1);
        }

        return entries;
    }

    /**
     * Gets the status for each day within a date range.
     * 
     * If a day has entries with mixed statuses, returns "MIXED".
     * If a day has no entries, returns "DRAFT".
     * Otherwise returns the common status.
     * 
     * @param memberId Member ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Map of date to status
     */
    private Map<LocalDate, String> getDailyStatuses(
        UUID memberId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        String sql = """
            WITH latest_status AS (
                SELECT 
                    e1.aggregate_id,
                    CAST(e1.payload->>'date' AS DATE) as entry_date,
                    COALESCE(
                        (SELECT e2.payload->>'toStatus'
                         FROM event_store e2
                         WHERE e2.aggregate_id = e1.aggregate_id
                         AND e2.event_type = 'WorkLogEntryStatusChanged'
                         ORDER BY e2.sequence_number DESC
                         LIMIT 1),
                        'DRAFT'
                    ) as status
                FROM event_store e1
                WHERE e1.aggregate_type = 'WorkLogEntry'
                AND e1.event_type = 'WorkLogEntryCreated'
                AND CAST(e1.payload->>'memberId' AS UUID) = ?
                AND CAST(e1.payload->>'date' AS DATE) BETWEEN ? AND ?
                AND e1.aggregate_id NOT IN (
                    SELECT aggregate_id 
                    FROM event_store 
                    WHERE aggregate_type = 'WorkLogEntry'
                    AND event_type = 'WorkLogEntryDeleted'
                )
            )
            SELECT 
                entry_date,
                CASE 
                    WHEN COUNT(DISTINCT status) > 1 THEN 'MIXED'
                    ELSE MAX(status)
                END as status
            FROM latest_status
            GROUP BY entry_date
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
            sql,
            memberId,
            startDate,
            endDate
        );

        Map<LocalDate, String> dailyStatuses = new HashMap<>();
        for (Map<String, Object> row : results) {
            LocalDate date = ((java.sql.Date) row.get("entry_date")).toLocalDate();
            String status = (String) row.get("status");
            dailyStatuses.put(date, status);
        }

        return dailyStatuses;
    }
}
