package com.worklog.infrastructure.projection;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Projection for monthly calendar view.
 * 
 * Queries the read model tables (work_log_entries_projection, absences_projection)
 * to build a calendar view showing daily totals within a fiscal month period.
 * 
 * Optimized for performance using projection tables instead of event replay.
 * Uses the covering index idx_work_log_entries_calendar_covering for efficient queries.
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
     * This method aggregates work hours from the work_log_entries_projection table.
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
        // Uses idx_work_log_entries_calendar_covering for efficient query
        String sql = """
            SELECT 
                work_date,
                SUM(hours) as total_hours
            FROM work_log_entries_projection
            WHERE member_id = ?
            AND work_date BETWEEN ? AND ?
            GROUP BY work_date
            ORDER BY work_date
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
            sql,
            memberId,
            startDate,
            endDate
        );

        Map<LocalDate, BigDecimal> dailyTotals = new HashMap<>();
        for (Map<String, Object> row : results) {
            LocalDate date = ((java.sql.Date) row.get("work_date")).toLocalDate();
            BigDecimal hours = (BigDecimal) row.get("total_hours");
            dailyTotals.put(date, hours);
        }

        return dailyTotals;
    }

    /**
     * Gets daily absence totals for a member within a date range.
     * 
     * This method aggregates absence hours from the absences_projection table.
     * Since absences span date ranges, we need to expand them into daily hours.
     * 
     * @param memberId Member ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Map of date to total absence hours
     */
    public Map<LocalDate, BigDecimal> getAbsenceTotals(
        UUID memberId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        // Uses idx_absences_overlap for efficient overlap detection
        String sql = """
            SELECT 
                id,
                start_date,
                end_date,
                hours_per_day
            FROM absences_projection
            WHERE member_id = ?
            AND start_date <= ?
            AND end_date >= ?
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
            sql,
            memberId,
            endDate,
            startDate
        );

        Map<LocalDate, BigDecimal> absenceTotals = new HashMap<>();
        
        for (Map<String, Object> row : results) {
            LocalDate absenceStart = ((java.sql.Date) row.get("start_date")).toLocalDate();
            LocalDate absenceEnd = ((java.sql.Date) row.get("end_date")).toLocalDate();
            BigDecimal hoursPerDay = (BigDecimal) row.get("hours_per_day");
            
            // Calculate intersection with requested date range
            LocalDate effectiveStart = absenceStart.isBefore(startDate) ? startDate : absenceStart;
            LocalDate effectiveEnd = absenceEnd.isAfter(endDate) ? endDate : absenceEnd;
            
            // Add hours for each day in the effective range
            LocalDate current = effectiveStart;
            while (!current.isAfter(effectiveEnd)) {
                absenceTotals.merge(current, hoursPerDay, BigDecimal::add);
                current = current.plusDays(1);
            }
        }

        return absenceTotals;
    }

    /**
     * Gets dates that have proxy entries (where entered_by != member_id) for a member.
     * 
     * @param memberId Member ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Set of dates that have at least one proxy entry
     */
    public Set<LocalDate> getProxyEntryDates(
        UUID memberId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        // Uses idx_work_log_entries_entered_by and idx_work_log_entries_member_date
        String sql = """
            SELECT DISTINCT work_date
            FROM work_log_entries_projection
            WHERE member_id = ?
            AND work_date BETWEEN ? AND ?
            AND entered_by IS NOT NULL
            AND entered_by != member_id
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
            sql,
            memberId,
            startDate,
            endDate
        );

        Set<LocalDate> proxyDates = new HashSet<>();
        for (Map<String, Object> row : results) {
            LocalDate date = ((java.sql.Date) row.get("work_date")).toLocalDate();
            proxyDates.add(date);
        }

        return proxyDates;
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
        Map<LocalDate, BigDecimal> absenceTotals = getAbsenceTotals(memberId, startDate, endDate);
        Map<LocalDate, String> dailyStatuses = getDailyStatuses(memberId, startDate, endDate);
        Set<LocalDate> proxyDates = getProxyEntryDates(memberId, startDate, endDate);

        List<DailyEntryProjection> entries = new ArrayList<>();
        LocalDate current = startDate;
        
        while (!current.isAfter(endDate)) {
            boolean isWeekend = current.getDayOfWeek() == DayOfWeek.SATURDAY 
                             || current.getDayOfWeek() == DayOfWeek.SUNDAY;
            
            BigDecimal totalHours = dailyTotals.getOrDefault(current, BigDecimal.ZERO);
            BigDecimal absenceHours = absenceTotals.getOrDefault(current, BigDecimal.ZERO);
            String status = dailyStatuses.getOrDefault(current, "DRAFT");
            boolean hasProxyEntries = proxyDates.contains(current);
            
            entries.add(new DailyEntryProjection(
                current,
                totalHours,
                absenceHours,
                status,
                isWeekend,
                false, // Holiday detection - not implemented yet
                hasProxyEntries
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
        // Uses idx_work_log_entries_calendar_covering which includes status
        String sql = """
            SELECT 
                work_date,
                CASE 
                    WHEN COUNT(DISTINCT status) > 1 THEN 'MIXED'
                    ELSE MAX(status)
                END as status
            FROM work_log_entries_projection
            WHERE member_id = ?
            AND work_date BETWEEN ? AND ?
            GROUP BY work_date
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
            sql,
            memberId,
            startDate,
            endDate
        );

        Map<LocalDate, String> dailyStatuses = new HashMap<>();
        for (Map<String, Object> row : results) {
            LocalDate date = ((java.sql.Date) row.get("work_date")).toLocalDate();
            String status = (String) row.get("status");
            dailyStatuses.put(date, status);
        }

        return dailyStatuses;
    }
}
