package com.worklog.infrastructure.repository;

import com.worklog.domain.monthlyperiod.MonthlyPeriodPattern;
import com.worklog.domain.monthlyperiod.MonthlyPeriodPatternId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for MonthlyPeriodPattern entities.
 * 
 * Uses simple CRUD operations (not event sourced).
 */
@Repository
public class MonthlyPeriodPatternRepository {

    private final JdbcTemplate jdbcTemplate;

    public MonthlyPeriodPatternRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Save a monthly period pattern.
     */
    public void save(MonthlyPeriodPattern pattern) {
        jdbcTemplate.update(
            "INSERT INTO monthly_period_pattern (id, tenant_id, name, start_day, created_at) " +
            "VALUES (?, ?, ?, ?, NOW()) " +
            "ON CONFLICT (id) DO UPDATE SET " +
            "name = EXCLUDED.name, " +
            "start_day = EXCLUDED.start_day",
            pattern.getId().value(),
            pattern.getTenantId(),
            pattern.getName(),
            pattern.getStartDay()
        );
    }

    /**
     * Find a monthly period pattern by ID.
     */
    public Optional<MonthlyPeriodPattern> findById(MonthlyPeriodPatternId id) {
        List<MonthlyPeriodPattern> results = jdbcTemplate.query(
            "SELECT id, tenant_id, name, start_day FROM monthly_period_pattern WHERE id = ?",
            new MonthlyPeriodPatternRowMapper(),
            id.value()
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Find all monthly period patterns for a tenant.
     */
    public List<MonthlyPeriodPattern> findByTenantId(UUID tenantId) {
        return jdbcTemplate.query(
            "SELECT id, tenant_id, name, start_day FROM monthly_period_pattern " +
            "WHERE tenant_id = ? ORDER BY name",
            new MonthlyPeriodPatternRowMapper(),
            tenantId
        );
    }

    /**
     * Check if a pattern exists by ID.
     */
    public boolean existsById(MonthlyPeriodPatternId id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM monthly_period_pattern WHERE id = ?",
            Integer.class,
            id.value()
        );
        return count != null && count > 0;
    }

    /**
     * RowMapper for MonthlyPeriodPattern.
     */
    private static class MonthlyPeriodPatternRowMapper implements RowMapper<MonthlyPeriodPattern> {
        @Override
        public MonthlyPeriodPattern mapRow(ResultSet rs, int rowNum) throws SQLException {
            return MonthlyPeriodPattern.create(
                MonthlyPeriodPatternId.of(UUID.fromString(rs.getString("id"))),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("name"),
                rs.getInt("start_day")
            );
        }
    }
}
