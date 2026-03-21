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

    public List<HolidayCalendarRuleRow> findActiveRulesByTenantId(UUID tenantId) {
        return jdbcTemplate.query(
                """
                SELECT r.name, r.name_ja, r.rule_type, r.month, r.day,
                       r.nth_occurrence, r.day_of_week, r.specific_year
                FROM holiday_calendar_rules r
                JOIN holiday_calendars c ON r.holiday_calendar_id = c.id
                WHERE c.tenant_id = ? AND c.is_active = true
                ORDER BY r.month, r.day NULLS LAST, r.name
                """,
                (rs, rowNum) -> new HolidayCalendarRuleRow(
                        rs.getString("name"),
                        rs.getString("name_ja"),
                        rs.getString("rule_type"),
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
            String ruleType,
            int month,
            Integer day,
            Integer nthOccurrence,
            Integer dayOfWeek,
            Integer specificYear) {}
}
