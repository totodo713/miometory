package com.worklog.application.service;

import com.worklog.domain.fiscalyear.FiscalYearPattern;
import com.worklog.domain.monthlyperiod.MonthlyPeriodPattern;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.FiscalYearPatternRepository;
import com.worklog.infrastructure.repository.MonthlyPeriodPatternRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminMasterDataService {

    private final JdbcTemplate jdbcTemplate;
    private final FiscalYearPatternRepository fiscalYearPatternRepository;
    private final MonthlyPeriodPatternRepository monthlyPeriodPatternRepository;

    public AdminMasterDataService(
            JdbcTemplate jdbcTemplate,
            FiscalYearPatternRepository fiscalYearPatternRepository,
            MonthlyPeriodPatternRepository monthlyPeriodPatternRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.fiscalYearPatternRepository = fiscalYearPatternRepository;
        this.monthlyPeriodPatternRepository = monthlyPeriodPatternRepository;
    }

    // ========================================================================
    // Fiscal Year Pattern Presets
    // ========================================================================

    @Transactional(readOnly = true)
    public PresetPage<FiscalYearPresetRow> listFiscalYearPresets(String search, Boolean isActive, int page, int size) {
        var sb = new StringBuilder(
                "SELECT id, name, description, start_month, start_day, is_active FROM fiscal_year_pattern_preset WHERE 1=1");
        var countSb = new StringBuilder("SELECT COUNT(*) FROM fiscal_year_pattern_preset WHERE 1=1");
        var params = new java.util.ArrayList<Object>();
        var countParams = new java.util.ArrayList<Object>();

        if (search != null && !search.isBlank()) {
            String clause = " AND (LOWER(name) LIKE ? ESCAPE '\\' OR LOWER(description) LIKE ? ESCAPE '\\')";
            sb.append(clause);
            countSb.append(clause);
            String pattern = "%" + escapeLike(search).toLowerCase() + "%";
            params.add(pattern);
            params.add(pattern);
            countParams.add(pattern);
            countParams.add(pattern);
        }
        if (isActive != null) {
            String clause = " AND is_active = ?";
            sb.append(clause);
            countSb.append(clause);
            params.add(isActive);
            countParams.add(isActive);
        }

        Long total = jdbcTemplate.queryForObject(countSb.toString(), Long.class, countParams.toArray());
        long totalElements = total != null ? total : 0;

        sb.append(" ORDER BY name LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        List<FiscalYearPresetRow> content = jdbcTemplate.query(
                sb.toString(),
                (rs, rowNum) -> new FiscalYearPresetRow(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("start_month"),
                        rs.getInt("start_day"),
                        rs.getBoolean("is_active")),
                params.toArray());

        int totalPages = (int) Math.ceil((double) totalElements / size);
        return new PresetPage<>(content, totalElements, totalPages, page);
    }

    public UUID createFiscalYearPreset(String name, String description, int startMonth, int startDay) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO fiscal_year_pattern_preset (id, name, description, start_month, start_day, is_active, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, true, ?, ?)
                    """, id, name, description, startMonth, startDay, Timestamp.from(now), Timestamp.from(now));
        } catch (DataIntegrityViolationException ex) {
            throw new DomainException("DUPLICATE_NAME", "A fiscal year preset with this name already exists");
        }
        return id;
    }

    public void updateFiscalYearPreset(UUID id, String name, String description, int startMonth, int startDay) {
        try {
            int rows = jdbcTemplate.update(
                    """
                    UPDATE fiscal_year_pattern_preset SET name = ?, description = ?, start_month = ?, start_day = ?, updated_at = ?
                    WHERE id = ?
                    """, name, description, startMonth, startDay, Timestamp.from(Instant.now()), id);
            if (rows == 0) {
                throw new DomainException("PRESET_NOT_FOUND", "Fiscal year preset not found");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new DomainException("DUPLICATE_NAME", "A fiscal year preset with this name already exists");
        }
    }

    public void deactivateFiscalYearPreset(UUID id) {
        int rows = jdbcTemplate.update(
                "UPDATE fiscal_year_pattern_preset SET is_active = false, updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                id);
        if (rows == 0) {
            throw new DomainException("PRESET_NOT_FOUND", "Fiscal year preset not found");
        }
    }

    public void activateFiscalYearPreset(UUID id) {
        int rows = jdbcTemplate.update(
                "UPDATE fiscal_year_pattern_preset SET is_active = true, updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                id);
        if (rows == 0) {
            throw new DomainException("PRESET_NOT_FOUND", "Fiscal year preset not found");
        }
    }

    // ========================================================================
    // Monthly Period Pattern Presets
    // ========================================================================

    @Transactional(readOnly = true)
    public PresetPage<MonthlyPeriodPresetRow> listMonthlyPeriodPresets(
            String search, Boolean isActive, int page, int size) {
        var sb = new StringBuilder(
                "SELECT id, name, description, start_day, is_active FROM monthly_period_pattern_preset WHERE 1=1");
        var countSb = new StringBuilder("SELECT COUNT(*) FROM monthly_period_pattern_preset WHERE 1=1");
        var params = new java.util.ArrayList<Object>();
        var countParams = new java.util.ArrayList<Object>();

        if (search != null && !search.isBlank()) {
            String clause = " AND (LOWER(name) LIKE ? ESCAPE '\\' OR LOWER(description) LIKE ? ESCAPE '\\')";
            sb.append(clause);
            countSb.append(clause);
            String pattern = "%" + escapeLike(search).toLowerCase() + "%";
            params.add(pattern);
            params.add(pattern);
            countParams.add(pattern);
            countParams.add(pattern);
        }
        if (isActive != null) {
            String clause = " AND is_active = ?";
            sb.append(clause);
            countSb.append(clause);
            params.add(isActive);
            countParams.add(isActive);
        }

        Long total = jdbcTemplate.queryForObject(countSb.toString(), Long.class, countParams.toArray());
        long totalElements = total != null ? total : 0;

        sb.append(" ORDER BY name LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        List<MonthlyPeriodPresetRow> content = jdbcTemplate.query(
                sb.toString(),
                (rs, rowNum) -> new MonthlyPeriodPresetRow(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("start_day"),
                        rs.getBoolean("is_active")),
                params.toArray());

        int totalPages = (int) Math.ceil((double) totalElements / size);
        return new PresetPage<>(content, totalElements, totalPages, page);
    }

    public UUID createMonthlyPeriodPreset(String name, String description, int startDay) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbcTemplate.update("""
                    INSERT INTO monthly_period_pattern_preset (id, name, description, start_day, is_active, created_at, updated_at)
                    VALUES (?, ?, ?, ?, true, ?, ?)
                    """, id, name, description, startDay, Timestamp.from(now), Timestamp.from(now));
        } catch (DataIntegrityViolationException ex) {
            throw new DomainException("DUPLICATE_NAME", "A monthly period preset with this name already exists");
        }
        return id;
    }

    public void updateMonthlyPeriodPreset(UUID id, String name, String description, int startDay) {
        try {
            int rows = jdbcTemplate.update("""
                    UPDATE monthly_period_pattern_preset SET name = ?, description = ?, start_day = ?, updated_at = ?
                    WHERE id = ?
                    """, name, description, startDay, Timestamp.from(Instant.now()), id);
            if (rows == 0) {
                throw new DomainException("PRESET_NOT_FOUND", "Monthly period preset not found");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new DomainException("DUPLICATE_NAME", "A monthly period preset with this name already exists");
        }
    }

    public void deactivateMonthlyPeriodPreset(UUID id) {
        int rows = jdbcTemplate.update(
                "UPDATE monthly_period_pattern_preset SET is_active = false, updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                id);
        if (rows == 0) {
            throw new DomainException("PRESET_NOT_FOUND", "Monthly period preset not found");
        }
    }

    public void activateMonthlyPeriodPreset(UUID id) {
        int rows = jdbcTemplate.update(
                "UPDATE monthly_period_pattern_preset SET is_active = true, updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                id);
        if (rows == 0) {
            throw new DomainException("PRESET_NOT_FOUND", "Monthly period preset not found");
        }
    }

    // ========================================================================
    // Holiday Calendar Presets
    // ========================================================================

    @Transactional(readOnly = true)
    public PresetPage<HolidayCalendarPresetRow> listHolidayCalendarPresets(
            String search, Boolean isActive, int page, int size) {
        var sb = new StringBuilder("""
            SELECT h.id, h.name, h.description, h.country, h.is_active,
                   COALESCE(ec.cnt, 0) AS entry_count
            FROM holiday_calendar_preset h
            LEFT JOIN (
                SELECT holiday_calendar_id, COUNT(*) AS cnt
                FROM holiday_calendar_entry_preset
                GROUP BY holiday_calendar_id
            ) ec ON ec.holiday_calendar_id = h.id
            WHERE 1=1
            """);
        var countSb = new StringBuilder("SELECT COUNT(*) FROM holiday_calendar_preset h WHERE 1=1");
        var params = new java.util.ArrayList<Object>();
        var countParams = new java.util.ArrayList<Object>();

        if (search != null && !search.isBlank()) {
            String clause = " AND (LOWER(h.name) LIKE ? ESCAPE '\\' OR LOWER(h.description) LIKE ? ESCAPE '\\')";
            sb.append(clause);
            countSb.append(clause);
            String pattern = "%" + escapeLike(search).toLowerCase() + "%";
            params.add(pattern);
            params.add(pattern);
            countParams.add(pattern);
            countParams.add(pattern);
        }
        if (isActive != null) {
            String clause = " AND h.is_active = ?";
            sb.append(clause);
            countSb.append(clause);
            params.add(isActive);
            countParams.add(isActive);
        }

        Long total = jdbcTemplate.queryForObject(countSb.toString(), Long.class, countParams.toArray());
        long totalElements = total != null ? total : 0;

        sb.append(" ORDER BY h.name LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        List<HolidayCalendarPresetRow> content = jdbcTemplate.query(
                sb.toString(),
                (rs, rowNum) -> new HolidayCalendarPresetRow(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("country"),
                        rs.getBoolean("is_active"),
                        rs.getInt("entry_count")),
                params.toArray());

        int totalPages = (int) Math.ceil((double) totalElements / size);
        return new PresetPage<>(content, totalElements, totalPages, page);
    }

    public UUID createHolidayCalendarPreset(String name, String description, String country) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbcTemplate.update("""
                    INSERT INTO holiday_calendar_preset (id, name, description, country, is_active, created_at, updated_at)
                    VALUES (?, ?, ?, ?, true, ?, ?)
                    """, id, name, description, country, Timestamp.from(now), Timestamp.from(now));
        } catch (DataIntegrityViolationException ex) {
            throw new DomainException("DUPLICATE_NAME", "A holiday calendar with this name already exists");
        }
        return id;
    }

    public void updateHolidayCalendarPreset(UUID id, String name, String description, String country) {
        try {
            int rows = jdbcTemplate.update("""
                    UPDATE holiday_calendar_preset SET name = ?, description = ?, country = ?, updated_at = ?
                    WHERE id = ?
                    """, name, description, country, Timestamp.from(Instant.now()), id);
            if (rows == 0) {
                throw new DomainException("PRESET_NOT_FOUND", "Holiday calendar not found");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new DomainException("DUPLICATE_NAME", "A holiday calendar with this name already exists");
        }
    }

    public void deactivateHolidayCalendarPreset(UUID id) {
        int rows = jdbcTemplate.update(
                "UPDATE holiday_calendar_preset SET is_active = false, updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                id);
        if (rows == 0) {
            throw new DomainException("PRESET_NOT_FOUND", "Holiday calendar not found");
        }
    }

    public void activateHolidayCalendarPreset(UUID id) {
        int rows = jdbcTemplate.update(
                "UPDATE holiday_calendar_preset SET is_active = true, updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                id);
        if (rows == 0) {
            throw new DomainException("PRESET_NOT_FOUND", "Holiday calendar not found");
        }
    }

    // ========================================================================
    // Holiday Calendar Entries
    // ========================================================================

    @Transactional(readOnly = true)
    public List<HolidayEntryRow> listHolidayEntries(UUID calendarId) {
        // Verify calendar exists
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM holiday_calendar_preset WHERE id = ?", Long.class, calendarId);
        if (count == null || count == 0) {
            throw new DomainException("PRESET_NOT_FOUND", "Holiday calendar not found");
        }

        return jdbcTemplate.query(
                """
                SELECT id, name, name_ja, entry_type, month, day, nth_occurrence, day_of_week, specific_year
                FROM holiday_calendar_entry_preset
                WHERE holiday_calendar_id = ?
                ORDER BY month, day NULLS LAST, name
                """,
                (rs, rowNum) -> new HolidayEntryRow(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("name"),
                        rs.getString("name_ja"),
                        rs.getString("entry_type"),
                        rs.getInt("month"),
                        rs.getObject("day") != null ? rs.getInt("day") : null,
                        rs.getObject("nth_occurrence") != null ? rs.getInt("nth_occurrence") : null,
                        rs.getObject("day_of_week") != null ? rs.getInt("day_of_week") : null,
                        rs.getObject("specific_year") != null ? rs.getInt("specific_year") : null),
                calendarId);
    }

    public UUID addHolidayEntry(
            UUID calendarId,
            String name,
            String nameJa,
            String entryType,
            int month,
            Integer day,
            Integer nthOccurrence,
            Integer dayOfWeek,
            Integer specificYear) {
        validateEntryType(entryType, day, nthOccurrence, dayOfWeek);

        // Verify calendar exists
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM holiday_calendar_preset WHERE id = ?", Long.class, calendarId);
        if (count == null || count == 0) {
            throw new DomainException("PRESET_NOT_FOUND", "Holiday calendar not found");
        }

        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO holiday_calendar_entry_preset (id, holiday_calendar_id, name, name_ja, entry_type, month, day, nth_occurrence, day_of_week, specific_year)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, calendarId, name, nameJa, entryType, month, day, nthOccurrence, dayOfWeek, specificYear);
        return id;
    }

    public void updateHolidayEntry(
            UUID calendarId,
            UUID entryId,
            String name,
            String nameJa,
            String entryType,
            int month,
            Integer day,
            Integer nthOccurrence,
            Integer dayOfWeek,
            Integer specificYear) {
        validateEntryType(entryType, day, nthOccurrence, dayOfWeek);

        int rows = jdbcTemplate.update(
                """
                UPDATE holiday_calendar_entry_preset
                SET name = ?, name_ja = ?, entry_type = ?, month = ?, day = ?, nth_occurrence = ?, day_of_week = ?, specific_year = ?
                WHERE id = ? AND holiday_calendar_id = ?
                """, name, nameJa, entryType, month, day, nthOccurrence, dayOfWeek, specificYear, entryId, calendarId);
        if (rows == 0) {
            throw new DomainException("ENTRY_NOT_FOUND", "Holiday entry not found");
        }
    }

    public void removeHolidayEntry(UUID calendarId, UUID entryId) {
        int rows = jdbcTemplate.update(
                "DELETE FROM holiday_calendar_entry_preset WHERE id = ? AND holiday_calendar_id = ?",
                entryId,
                calendarId);
        if (rows == 0) {
            throw new DomainException("ENTRY_NOT_FOUND", "Holiday entry not found");
        }
    }

    // ========================================================================
    // Tenant Bootstrap: Copy Active Presets to Tenant
    // ========================================================================

    /**
     * Copies all active presets to a tenant as initial master data.
     * Called during tenant bootstrap to provide default fiscal year patterns,
     * monthly period patterns, and holiday calendars.
     *
     * <p>Fiscal year and monthly period patterns use event-sourced domain models.
     * Holiday calendars use direct JDBC inserts (no event sourcing).
     */
    @Transactional
    public CopiedPresets copyPresetsToTenant(UUID tenantId) {
        TenantId tid = TenantId.of(tenantId);

        // 1. Copy active fiscal year presets → fiscal_year_pattern (event-sourced)
        List<CopiedPattern> fyPatterns = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT id, name, start_month, start_day FROM fiscal_year_pattern_preset WHERE is_active = true ORDER BY name",
                (rs) -> {
                    UUID presetId = rs.getObject("id", UUID.class);
                    String name = rs.getString("name");
                    int startMonth = rs.getInt("start_month");
                    int startDay = rs.getInt("start_day");

                    FiscalYearPattern pattern = FiscalYearPattern.create(tid, name, startMonth, startDay);
                    fiscalYearPatternRepository.save(pattern);
                    fyPatterns.add(new CopiedPattern(presetId, pattern.getId().value(), name));
                });

        // 2. Copy active monthly period presets → monthly_period_pattern (event-sourced)
        List<CopiedPattern> mpPatterns = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT id, name, start_day FROM monthly_period_pattern_preset WHERE is_active = true ORDER BY name",
                (rs) -> {
                    UUID presetId = rs.getObject("id", UUID.class);
                    String name = rs.getString("name");
                    int startDay = rs.getInt("start_day");

                    MonthlyPeriodPattern pattern = MonthlyPeriodPattern.create(tid, name, startDay);
                    monthlyPeriodPatternRepository.save(pattern);
                    mpPatterns.add(new CopiedPattern(presetId, pattern.getId().value(), name));
                });

        // 3. Copy active holiday calendars + entries → holiday_calendar + holiday_calendar_entry (direct JDBC)
        List<CopiedCalendar> hcCalendars = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT id, name, description, country FROM holiday_calendar_preset WHERE is_active = true ORDER BY name",
                (rs) -> {
                    UUID presetCalendarId = rs.getObject("id", UUID.class);
                    String name = rs.getString("name");
                    String description = rs.getString("description");
                    String country = rs.getString("country");

                    UUID tenantCalendarId = UUID.randomUUID();
                    Instant now = Instant.now();
                    jdbcTemplate.update(
                            """
                            INSERT INTO holiday_calendar (id, tenant_id, name, description, country, is_active, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, true, ?, ?)
                            """,
                            tenantCalendarId,
                            tenantId,
                            name,
                            description,
                            country,
                            Timestamp.from(now),
                            Timestamp.from(now));

                    // Copy all entries from the preset calendar
                    jdbcTemplate.query(
                            "SELECT name, name_ja, entry_type, month, day, nth_occurrence, day_of_week, specific_year "
                                    + "FROM holiday_calendar_entry_preset WHERE holiday_calendar_id = ?",
                            (entryRs) -> {
                                UUID entryId = UUID.randomUUID();
                                jdbcTemplate.update(
                                        """
                                        INSERT INTO holiday_calendar_entry (id, holiday_calendar_id, name, name_ja, entry_type, month, day, nth_occurrence, day_of_week, specific_year)
                                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                        """,
                                        entryId,
                                        tenantCalendarId,
                                        entryRs.getString("name"),
                                        entryRs.getString("name_ja"),
                                        entryRs.getString("entry_type"),
                                        entryRs.getInt("month"),
                                        entryRs.getObject("day") != null ? entryRs.getInt("day") : null,
                                        entryRs.getObject("nth_occurrence") != null
                                                ? entryRs.getInt("nth_occurrence")
                                                : null,
                                        entryRs.getObject("day_of_week") != null ? entryRs.getInt("day_of_week") : null,
                                        entryRs.getObject("specific_year") != null
                                                ? entryRs.getInt("specific_year")
                                                : null);
                            },
                            presetCalendarId);

                    hcCalendars.add(new CopiedCalendar(presetCalendarId, tenantCalendarId, name));
                });

        return new CopiedPresets(fyPatterns, mpPatterns, hcCalendars);
    }

    // ========================================================================
    // Helper
    // ========================================================================

    private static void validateEntryType(String entryType, Integer day, Integer nthOccurrence, Integer dayOfWeek) {
        if (!"FIXED".equals(entryType) && !"NTH_WEEKDAY".equals(entryType)) {
            throw new DomainException("INVALID_ENTRY_TYPE", "Entry type must be FIXED or NTH_WEEKDAY");
        }
        if ("FIXED".equals(entryType) && day == null) {
            throw new DomainException("VALIDATION_ERROR", "Day is required for FIXED entries");
        }
        if ("NTH_WEEKDAY".equals(entryType) && (nthOccurrence == null || dayOfWeek == null)) {
            throw new DomainException(
                    "VALIDATION_ERROR", "nthOccurrence and dayOfWeek are required for NTH_WEEKDAY entries");
        }
    }

    private static String escapeLike(String input) {
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // ========================================================================
    // DTOs
    // ========================================================================

    public record PresetPage<T>(List<T> content, long totalElements, int totalPages, int number) {}

    public record FiscalYearPresetRow(
            String id, String name, String description, int startMonth, int startDay, boolean isActive) {}

    public record MonthlyPeriodPresetRow(String id, String name, String description, int startDay, boolean isActive) {}

    public record HolidayCalendarPresetRow(
            String id, String name, String description, String country, boolean isActive, int entryCount) {}

    public record HolidayEntryRow(
            String id,
            String name,
            String nameJa,
            String entryType,
            int month,
            Integer day,
            Integer nthOccurrence,
            Integer dayOfWeek,
            Integer specificYear) {}

    // Tenant bootstrap copy result DTOs

    public record CopiedPresets(
            List<CopiedPattern> fiscalYearPatterns,
            List<CopiedPattern> monthlyPeriodPatterns,
            List<CopiedCalendar> holidayCalendars) {}

    public record CopiedPattern(UUID presetId, UUID tenantPatternId, String name) {}

    public record CopiedCalendar(UUID presetId, UUID tenantCalendarId, String name) {}
}
