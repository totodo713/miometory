package com.worklog.infrastructure.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklog.domain.settings.SystemDefaultFiscalYearPattern;
import com.worklog.domain.settings.SystemDefaultMonthlyPeriodPattern;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for system-wide default settings.
 * Uses direct JDBC access (not event sourcing) since these are admin configuration values.
 */
@Repository
public class SystemDefaultSettingsRepository {

    private static final String KEY_FISCAL_YEAR = "default_fiscal_year_pattern";
    private static final String KEY_MONTHLY_PERIOD = "default_monthly_period_pattern";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SystemDefaultSettingsRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SystemDefaultFiscalYearPattern getDefaultFiscalYearPattern() {
        String json = jdbcTemplate.queryForObject(
                "SELECT setting_value::text FROM system_default_settings WHERE setting_key = ?",
                String.class,
                KEY_FISCAL_YEAR);
        return parseFiscalYearPattern(json);
    }

    @Transactional(readOnly = true)
    public SystemDefaultMonthlyPeriodPattern getDefaultMonthlyPeriodPattern() {
        String json = jdbcTemplate.queryForObject(
                "SELECT setting_value::text FROM system_default_settings WHERE setting_key = ?",
                String.class,
                KEY_MONTHLY_PERIOD);
        return parseMonthlyPeriodPattern(json);
    }

    @Transactional
    public void updateDefaultFiscalYearPattern(SystemDefaultFiscalYearPattern pattern, UUID updatedBy) {
        String json = String.format("{\"startMonth\": %d, \"startDay\": %d}", pattern.startMonth(), pattern.startDay());
        jdbcTemplate.update(
                "INSERT INTO system_default_settings (setting_key, setting_value, updated_by, updated_at) "
                        + "VALUES (?, ?::jsonb, ?, NOW()) "
                        + "ON CONFLICT (setting_key) DO UPDATE SET "
                        + "setting_value = EXCLUDED.setting_value, "
                        + "updated_by = EXCLUDED.updated_by, "
                        + "updated_at = NOW()",
                KEY_FISCAL_YEAR,
                json,
                updatedBy);
    }

    @Transactional
    public void updateDefaultMonthlyPeriodPattern(SystemDefaultMonthlyPeriodPattern pattern, UUID updatedBy) {
        String json = String.format("{\"startDay\": %d}", pattern.startDay());
        jdbcTemplate.update(
                "INSERT INTO system_default_settings (setting_key, setting_value, updated_by, updated_at) "
                        + "VALUES (?, ?::jsonb, ?, NOW()) "
                        + "ON CONFLICT (setting_key) DO UPDATE SET "
                        + "setting_value = EXCLUDED.setting_value, "
                        + "updated_by = EXCLUDED.updated_by, "
                        + "updated_at = NOW()",
                KEY_MONTHLY_PERIOD,
                json,
                updatedBy);
    }

    private SystemDefaultFiscalYearPattern parseFiscalYearPattern(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return new SystemDefaultFiscalYearPattern(
                    node.get("startMonth").asInt(), node.get("startDay").asInt());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse default fiscal year pattern", e);
        }
    }

    private SystemDefaultMonthlyPeriodPattern parseMonthlyPeriodPattern(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return new SystemDefaultMonthlyPeriodPattern(node.get("startDay").asInt());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse default monthly period pattern", e);
        }
    }
}
