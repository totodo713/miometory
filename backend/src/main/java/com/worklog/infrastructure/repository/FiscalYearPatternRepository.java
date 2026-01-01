package com.worklog.infrastructure.repository;

import com.worklog.domain.fiscalyear.FiscalYearPattern;
import com.worklog.domain.fiscalyear.FiscalYearPatternId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for FiscalYearPattern entities.
 * 
 * Uses simple CRUD operations (not event sourced).
 */
@Repository
public class FiscalYearPatternRepository {

    private final JdbcTemplate jdbcTemplate;

    public FiscalYearPatternRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Save a fiscal year pattern.
     */
    public void save(FiscalYearPattern pattern) {
        jdbcTemplate.update(
            "INSERT INTO fiscal_year_pattern (id, tenant_id, name, start_month, start_day, created_at) " +
            "VALUES (?, ?, ?, ?, ?, NOW()) " +
            "ON CONFLICT (id) DO UPDATE SET " +
            "name = EXCLUDED.name, " +
            "start_month = EXCLUDED.start_month, " +
            "start_day = EXCLUDED.start_day",
            pattern.getId().value(),
            pattern.getTenantId(),
            pattern.getName(),
            pattern.getStartMonth(),
            pattern.getStartDay()
        );
    }

    /**
     * Find a fiscal year pattern by ID.
     */
    public Optional<FiscalYearPattern> findById(FiscalYearPatternId id) {
        List<FiscalYearPattern> results = jdbcTemplate.query(
            "SELECT id, tenant_id, name, start_month, start_day FROM fiscal_year_pattern WHERE id = ?",
            new FiscalYearPatternRowMapper(),
            id.value()
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Find all fiscal year patterns for a tenant.
     */
    public List<FiscalYearPattern> findByTenantId(UUID tenantId) {
        return jdbcTemplate.query(
            "SELECT id, tenant_id, name, start_month, start_day FROM fiscal_year_pattern " +
            "WHERE tenant_id = ? ORDER BY name",
            new FiscalYearPatternRowMapper(),
            tenantId
        );
    }

    /**
     * Check if a pattern exists by ID.
     */
    public boolean existsById(FiscalYearPatternId id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM fiscal_year_pattern WHERE id = ?",
            Integer.class,
            id.value()
        );
        return count != null && count > 0;
    }

    /**
     * RowMapper for FiscalYearPattern.
     */
    private static class FiscalYearPatternRowMapper implements RowMapper<FiscalYearPattern> {
        @Override
        public FiscalYearPattern mapRow(ResultSet rs, int rowNum) throws SQLException {
            return FiscalYearPattern.create(
                FiscalYearPatternId.of(UUID.fromString(rs.getString("id"))),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("name"),
                rs.getInt("start_month"),
                rs.getInt("start_day")
            );
        }
    }
}
