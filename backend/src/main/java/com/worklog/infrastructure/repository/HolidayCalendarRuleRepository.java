package com.worklog.infrastructure.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HolidayCalendarRuleRepository {

    private final JdbcTemplate jdbcTemplate;

    public HolidayCalendarRuleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<HolidayCalendarRuleRow> findActiveEntriesByTenantId(UUID tenantId) {
        return jdbcTemplate.query(
                """
                SELECT e.name, e.name_ja, e.entry_type, e.month, e.day,
                       e.nth_occurrence, e.day_of_week, e.specific_year
                FROM holiday_calendar_rules e
                JOIN holiday_calendars c ON e.holiday_calendar_id = c.id
                WHERE c.tenant_id = ? AND c.is_active = true
                ORDER BY e.month, e.day NULLS LAST, e.name
                """,
                (rs, rowNum) -> new HolidayCalendarRuleRow(
                        rs.getString("name"),
                        rs.getString("name_ja"),
                        rs.getString("entry_type"),
                        rs.getInt("month"),
                        rs.getObject("day") != null ? rs.getInt("day") : null,
                        rs.getObject("nth_occurrence") != null ? rs.getInt("nth_occurrence") : null,
                        rs.getObject("day_of_week") != null ? rs.getInt("day_of_week") : null,
                        rs.getObject("specific_year") != null ? rs.getInt("specific_year") : null),
                tenantId);
    }

    public record HolidayCalendarRuleRow(
            String name,
            String nameJa,
            String entryType,
            int month,
            Integer day,
            Integer nthOccurrence,
            Integer dayOfWeek,
            Integer specificYear) {}
}
