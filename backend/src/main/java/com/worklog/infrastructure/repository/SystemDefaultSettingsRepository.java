package com.worklog.infrastructure.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklog.domain.settings.SystemDefaultFiscalYearRule;
import com.worklog.domain.settings.SystemDefaultMonthlyPeriodRule;
import java.math.BigDecimal;
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
    private static final String KEY_STANDARD_DAILY_HOURS = "standard_daily_hours";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SystemDefaultSettingsRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SystemDefaultFiscalYearRule getDefaultFiscalYearRule() {
        String json = jdbcTemplate.queryForObject(
                "SELECT setting_value::text FROM system_default_settings WHERE setting_key = ?",
                String.class,
                KEY_FISCAL_YEAR);
        return parseFiscalYearRule(json);
    }

    @Transactional(readOnly = true)
    public SystemDefaultMonthlyPeriodRule getDefaultMonthlyPeriodRule() {
        String json = jdbcTemplate.queryForObject(
                "SELECT setting_value::text FROM system_default_settings WHERE setting_key = ?",
                String.class,
                KEY_MONTHLY_PERIOD);
        return parseMonthlyPeriodRule(json);
    }

    @Transactional
    public void updateDefaultFiscalYearRule(SystemDefaultFiscalYearRule pattern, UUID updatedBy) {
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
    public void updateDefaultMonthlyPeriodRule(SystemDefaultMonthlyPeriodRule pattern, UUID updatedBy) {
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

    @Transactional(readOnly = true)
    public BigDecimal getDefaultStandardDailyHours() {
        String json = jdbcTemplate.queryForObject(
                "SELECT setting_value::text FROM system_default_settings WHERE setting_key = ?",
                String.class,
                KEY_STANDARD_DAILY_HOURS);
        return parseStandardDailyHours(json);
    }

    private BigDecimal parseStandardDailyHours(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return BigDecimal.valueOf(node.get("hours").asDouble());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse default standard daily hours", e);
        }
    }

    private SystemDefaultFiscalYearRule parseFiscalYearRule(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return new SystemDefaultFiscalYearRule(
                    node.get("startMonth").asInt(), node.get("startDay").asInt());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse default fiscal year pattern", e);
        }
    }

    private SystemDefaultMonthlyPeriodRule parseMonthlyPeriodRule(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return new SystemDefaultMonthlyPeriodRule(node.get("startDay").asInt());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse default monthly period pattern", e);
        }
    }
}
