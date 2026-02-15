package com.worklog.infrastructure.projection;

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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
     * Results are cached with key: memberId:startDate:endDate
     *
     * @param memberId Member ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Map of date to total work hours
     */
    @Cacheable(
            cacheNames = "calendar:daily-totals",
            key = "#memberId.toString() + ':' + #startDate.toString() + ':' + #endDate.toString()",
            condition = "@environment.getProperty('worklog.cache.enabled', 'false') == 'true'")
    public Map<LocalDate, BigDecimal> getDailyTotals(UUID memberId, LocalDate startDate, LocalDate endDate) {
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

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, memberId, startDate, endDate);

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
     * Results are cached with key: memberId:startDate:endDate
     *
     * @param memberId Member ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Map of date to total absence hours
     */
    @Cacheable(
            cacheNames = "calendar:absence-totals",
            key = "#memberId.toString() + ':' + #startDate.toString() + ':' + #endDate.toString()",
            condition = "@environment.getProperty('worklog.cache.enabled', 'false') == 'true'")
    public Map<LocalDate, BigDecimal> getAbsenceTotals(UUID memberId, LocalDate startDate, LocalDate endDate) {
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

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, memberId, endDate, startDate);

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
     * Results are cached with key: memberId:startDate:endDate
     *
     * @param memberId Member ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Set of dates that have at least one proxy entry
     */
    @Cacheable(
            cacheNames = "calendar:proxy-dates",
            key = "#memberId.toString() + ':' + #startDate.toString() + ':' + #endDate.toString()",
            condition = "@environment.getProperty('worklog.cache.enabled', 'false') == 'true'")
    public Set<LocalDate> getProxyEntryDates(UUID memberId, LocalDate startDate, LocalDate endDate) {
        // Uses idx_work_log_entries_entered_by and idx_work_log_entries_member_date
        String sql = """
            SELECT DISTINCT work_date
            FROM work_log_entries_projection
            WHERE member_id = ?
            AND work_date BETWEEN ? AND ?
            AND entered_by IS NOT NULL
            AND entered_by != member_id
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, memberId, startDate, endDate);

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
     * Results are cached with key: memberId:startDate:endDate
     *
     * @param memberId Member ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of daily entry projections
     */
    @Cacheable(
            cacheNames = "calendar:daily-entries",
            key = "#memberId.toString() + ':' + #startDate.toString() + ':' + #endDate.toString()",
            condition = "@environment.getProperty('worklog.cache.enabled', 'false') == 'true'")
    public List<DailyEntryProjection> getDailyEntries(UUID memberId, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, BigDecimal> dailyTotals = getDailyTotals(memberId, startDate, endDate);
        Map<LocalDate, BigDecimal> absenceTotals = getAbsenceTotals(memberId, startDate, endDate);
        Map<LocalDate, String> dailyStatuses = getDailyStatuses(memberId, startDate, endDate);
        Set<LocalDate> proxyDates = getProxyEntryDates(memberId, startDate, endDate);

        List<DailyEntryProjection> entries = new ArrayList<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            boolean isWeekend =
                    current.getDayOfWeek() == DayOfWeek.SATURDAY || current.getDayOfWeek() == DayOfWeek.SUNDAY;

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
                    hasProxyEntries));

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
    private Map<LocalDate, String> getDailyStatuses(UUID memberId, LocalDate startDate, LocalDate endDate) {
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

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, memberId, startDate, endDate);

        Map<LocalDate, String> dailyStatuses = new HashMap<>();
        for (Map<String, Object> row : results) {
            LocalDate date = ((java.sql.Date) row.get("work_date")).toLocalDate();
            String status = (String) row.get("status");
            dailyStatuses.put(date, status);
        }

        return dailyStatuses;
    }

    /**
     * Evicts all cache entries when a member's data is modified.
     *
     * Should be called when a member's work log entries or absences are modified.
     *
     * Note: Due to Spring Cache limitations, this evicts ALL cache entries rather than
     * just the specific member's entries. The cache keys include date ranges
     * (memberId:startDate:endDate), making pattern-based eviction impossible without
     * a custom CacheManager implementation.
     *
     * For high-volume scenarios, consider implementing a custom eviction strategy.
     *
     * @param memberId Member ID whose data was modified (currently unused due to Spring Cache limitations)
     */
    @Caching(
            evict = {
                @CacheEvict(cacheNames = "calendar:daily-totals", allEntries = true),
                @CacheEvict(cacheNames = "calendar:absence-totals", allEntries = true),
                @CacheEvict(cacheNames = "calendar:proxy-dates", allEntries = true),
                @CacheEvict(cacheNames = "calendar:daily-entries", allEntries = true)
            })
    public void evictMemberCache(@SuppressWarnings("unused") UUID memberId) {
        // Cache eviction is handled by the annotations
        // This method is intentionally empty - the @CacheEvict annotations do the work
        // TODO: For better performance, implement custom CacheManager with pattern-based eviction
    }

    /**
     * Evicts all projection caches.
     *
     * Should be called during bulk operations or data migrations.
     */
    @Caching(
            evict = {
                @CacheEvict(cacheNames = "calendar:daily-totals", allEntries = true),
                @CacheEvict(cacheNames = "calendar:absence-totals", allEntries = true),
                @CacheEvict(cacheNames = "calendar:proxy-dates", allEntries = true),
                @CacheEvict(cacheNames = "calendar:daily-entries", allEntries = true)
            })
    public void evictAllCaches() {
        // Cache eviction is handled by the annotations
    }
}
