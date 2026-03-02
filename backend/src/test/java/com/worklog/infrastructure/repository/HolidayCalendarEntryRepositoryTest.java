package com.worklog.infrastructure.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.worklog.IntegrationTestBase;
import com.worklog.infrastructure.repository.HolidayCalendarEntryRepository.HolidayCalendarEntryRow;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("HolidayCalendarEntryRepository")
class HolidayCalendarEntryRepositoryTest extends IntegrationTestBase {

    @Autowired
    private HolidayCalendarEntryRepository repository;

    private UUID tenantId;
    private UUID calendarId;

    @BeforeEach
    void setUp() {
        // Use unique tenant per test run to isolate from other tests' data
        tenantId = UUID.randomUUID();
        calendarId = UUID.randomUUID();

        // Create tenant
        baseJdbcTemplate.update("""
                INSERT INTO tenant (id, code, name, status)
                VALUES (?, ?, 'Test Tenant', 'ACTIVE')
                """, tenantId, "t-" + tenantId.toString().substring(0, 8));

        // Create active holiday calendar
        baseJdbcTemplate.update("""
                INSERT INTO holiday_calendar (id, tenant_id, name, country, is_active)
                VALUES (?, ?, 'Test Calendar', 'JP', true)
                """, calendarId, tenantId);
    }

    @Test
    @DisplayName("should return FIXED entry with all fields mapped correctly")
    void shouldReturnFixedEntry() {
        baseJdbcTemplate.update("""
                INSERT INTO holiday_calendar_entry (holiday_calendar_id, name, name_ja, entry_type, month, day)
                VALUES (?, 'New Year''s Day', '元日', 'FIXED', 1, 1)
                """, calendarId);

        List<HolidayCalendarEntryRow> result = repository.findActiveEntriesByTenantId(tenantId);

        assertFalse(result.isEmpty());
        HolidayCalendarEntryRow entry = result.stream()
                .filter(e -> e.name().equals("New Year's Day"))
                .findFirst()
                .orElseThrow();
        assertEquals("元日", entry.nameJa());
        assertEquals("FIXED", entry.entryType());
        assertEquals(1, entry.month());
        assertEquals(1, entry.day());
        assertNull(entry.nthOccurrence());
        assertNull(entry.dayOfWeek());
        assertNull(entry.specificYear());
    }

    @Test
    @DisplayName("should return NTH_WEEKDAY entry with nullable fields mapped correctly")
    void shouldReturnNthWeekdayEntry() {
        baseJdbcTemplate.update("""
                INSERT INTO holiday_calendar_entry (holiday_calendar_id, name, name_ja, entry_type, month, nth_occurrence, day_of_week)
                VALUES (?, 'Coming of Age Day', '成人の日', 'NTH_WEEKDAY', 1, 2, 1)
                """, calendarId);

        List<HolidayCalendarEntryRow> result = repository.findActiveEntriesByTenantId(tenantId);

        HolidayCalendarEntryRow entry = result.stream()
                .filter(e -> e.name().equals("Coming of Age Day"))
                .findFirst()
                .orElseThrow();
        assertEquals("NTH_WEEKDAY", entry.entryType());
        assertEquals(1, entry.month());
        assertNull(entry.day());
        assertEquals(2, entry.nthOccurrence());
        assertEquals(1, entry.dayOfWeek());
    }

    @Test
    @DisplayName("should not return entries from inactive calendar")
    void shouldNotReturnEntriesFromInactiveCalendar() {
        UUID inactiveCalendarId = UUID.randomUUID();
        baseJdbcTemplate.update("""
                INSERT INTO holiday_calendar (id, tenant_id, name, country, is_active)
                VALUES (?, ?, 'Inactive Calendar', 'JP', false)
                """, inactiveCalendarId, tenantId);
        baseJdbcTemplate.update("""
                INSERT INTO holiday_calendar_entry (holiday_calendar_id, name, name_ja, entry_type, month, day)
                VALUES (?, 'Hidden Holiday', '非表示', 'FIXED', 3, 15)
                """, inactiveCalendarId);

        List<HolidayCalendarEntryRow> result = repository.findActiveEntriesByTenantId(tenantId);

        assertTrue(result.stream().noneMatch(e -> e.name().equals("Hidden Holiday")));
    }

    @Test
    @DisplayName("should return empty list for tenant with no calendars")
    void shouldReturnEmptyForUnknownTenant() {
        UUID unknownTenantId = UUID.randomUUID();

        List<HolidayCalendarEntryRow> result = repository.findActiveEntriesByTenantId(unknownTenantId);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("should return entries ordered by month, day, name")
    void shouldReturnEntriesOrdered() {
        baseJdbcTemplate.update("""
                INSERT INTO holiday_calendar_entry (holiday_calendar_id, name, name_ja, entry_type, month, day)
                VALUES (?, 'Foundation Day', '建国記念の日', 'FIXED', 2, 11)
                """, calendarId);
        baseJdbcTemplate.update("""
                INSERT INTO holiday_calendar_entry (holiday_calendar_id, name, name_ja, entry_type, month, day)
                VALUES (?, 'New Year''s Day', '元日', 'FIXED', 1, 1)
                """, calendarId);

        List<HolidayCalendarEntryRow> result = repository.findActiveEntriesByTenantId(tenantId);

        List<HolidayCalendarEntryRow> testEntries = result.stream()
                .filter(e -> e.name().equals("New Year's Day") || e.name().equals("Foundation Day"))
                .toList();
        assertEquals(2, testEntries.size());
        assertEquals("New Year's Day", testEntries.get(0).name());
        assertEquals("Foundation Day", testEntries.get(1).name());
    }

    @Test
    @DisplayName("should map specific_year when present")
    void shouldMapSpecificYear() {
        baseJdbcTemplate.update("""
                INSERT INTO holiday_calendar_entry (holiday_calendar_id, name, name_ja, entry_type, month, day, specific_year)
                VALUES (?, 'Olympic Day', 'オリンピック記念日', 'FIXED', 7, 24, 2020)
                """, calendarId);

        List<HolidayCalendarEntryRow> result = repository.findActiveEntriesByTenantId(tenantId);

        HolidayCalendarEntryRow entry = result.stream()
                .filter(e -> e.name().equals("Olympic Day"))
                .findFirst()
                .orElseThrow();
        assertEquals(2020, entry.specificYear());
    }
}
