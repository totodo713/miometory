package com.worklog.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.worklog.domain.model.HolidayInfo;
import com.worklog.infrastructure.repository.HolidayCalendarEntryRepository;
import com.worklog.infrastructure.repository.HolidayCalendarEntryRepository.HolidayCalendarEntryRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("HolidayResolutionService")
class HolidayResolutionServiceTest {

    @Mock
    private HolidayCalendarEntryRepository repository;

    private HolidayResolutionService service;

    @BeforeEach
    void setUp() {
        service = new HolidayResolutionService(repository);
    }

    @Nested
    @DisplayName("resolveHolidays")
    class ResolveHolidays {

        private final UUID tenantId = UUID.randomUUID();

        @Test
        @DisplayName("should resolve FIXED holiday to exact date")
        void shouldResolveFixedHoliday() {
            var entry = new HolidayCalendarEntryRow("New Year's Day", "\u5143\u65e5", "FIXED", 1, 1, null, null, null);
            when(repository.findActiveEntriesByTenantId(tenantId)).thenReturn(List.of(entry));

            Map<LocalDate, HolidayInfo> result =
                    service.resolveHolidays(tenantId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

            assertEquals(1, result.size());
            HolidayInfo holiday = result.get(LocalDate.of(2026, 1, 1));
            assertNotNull(holiday);
            assertEquals("New Year's Day", holiday.name());
            assertEquals("\u5143\u65e5", holiday.nameJa());
        }

        @Test
        @DisplayName("should exclude FIXED holiday before date range")
        void shouldExcludeFixedHolidayBeforeRange() {
            var entry = new HolidayCalendarEntryRow("New Year's Day", "\u5143\u65e5", "FIXED", 1, 1, null, null, null);
            when(repository.findActiveEntriesByTenantId(tenantId)).thenReturn(List.of(entry));

            Map<LocalDate, HolidayInfo> result =
                    service.resolveHolidays(tenantId, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should exclude FIXED holiday after date range")
        void shouldExcludeFixedHolidayAfterRange() {
            var entry = new HolidayCalendarEntryRow(
                    "Christmas", "\u30af\u30ea\u30b9\u30de\u30b9", "FIXED", 12, 25, null, null, null);
            when(repository.findActiveEntriesByTenantId(tenantId)).thenReturn(List.of(entry));

            Map<LocalDate, HolidayInfo> result =
                    service.resolveHolidays(tenantId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should resolve NTH_WEEKDAY holiday (Coming of Age Day = 2nd Monday in January)")
        void shouldResolveNthWeekdayHoliday() {
            // \u6210\u4eba\u306e\u65e5: 1\u6708\u7b2c2\u6708\u66dc\u65e5 \u2192 2026\u5e74\u306f1\u670812\u65e5
            var entry = new HolidayCalendarEntryRow(
                    "Coming of Age Day", "\u6210\u4eba\u306e\u65e5", "NTH_WEEKDAY", 1, null, 2, 1, null);
            when(repository.findActiveEntriesByTenantId(tenantId)).thenReturn(List.of(entry));

            Map<LocalDate, HolidayInfo> result =
                    service.resolveHolidays(tenantId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

            assertEquals(1, result.size());
            HolidayInfo holiday = result.get(LocalDate.of(2026, 1, 12));
            assertNotNull(holiday);
            assertEquals("Coming of Age Day", holiday.name());
            assertEquals("\u6210\u4eba\u306e\u65e5", holiday.nameJa());
        }

        @Test
        @DisplayName("should skip NTH_WEEKDAY when nth occurrence falls outside month")
        void shouldSkipNthWeekdayOutsideMonth() {
            var entry = new HolidayCalendarEntryRow(
                    "Fifth Monday", "\u7b2c5\u6708\u66dc", "NTH_WEEKDAY", 2, null, 5, 1, null);
            when(repository.findActiveEntriesByTenantId(tenantId)).thenReturn(List.of(entry));

            Map<LocalDate, HolidayInfo> result =
                    service.resolveHolidays(tenantId, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should resolve holidays spanning multiple months")
        void shouldResolveHolidaysSpanningMultipleMonths() {
            var newYear =
                    new HolidayCalendarEntryRow("New Year's Day", "\u5143\u65e5", "FIXED", 1, 1, null, null, null);
            var foundationDay = new HolidayCalendarEntryRow(
                    "Foundation Day", "\u5efa\u56fd\u8a18\u5ff5\u306e\u65e5", "FIXED", 2, 11, null, null, null);
            when(repository.findActiveEntriesByTenantId(tenantId)).thenReturn(List.of(newYear, foundationDay));

            Map<LocalDate, HolidayInfo> result =
                    service.resolveHolidays(tenantId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

            assertEquals(2, result.size());
            assertNotNull(result.get(LocalDate.of(2026, 1, 1)));
            assertNotNull(result.get(LocalDate.of(2026, 2, 11)));
        }

        @Test
        @DisplayName("should resolve holidays spanning multiple years")
        void shouldResolveHolidaysSpanningMultipleYears() {
            var newYear =
                    new HolidayCalendarEntryRow("New Year's Day", "\u5143\u65e5", "FIXED", 1, 1, null, null, null);
            when(repository.findActiveEntriesByTenantId(tenantId)).thenReturn(List.of(newYear));

            Map<LocalDate, HolidayInfo> result =
                    service.resolveHolidays(tenantId, LocalDate.of(2025, 12, 1), LocalDate.of(2026, 2, 28));

            // 2025\u5e741\u67081\u65e5\u306f\u671f\u9593\u5916\u30012026\u5e741\u67081\u65e5\u306f\u671f\u9593\u5185
            assertEquals(1, result.size());
            assertNotNull(result.get(LocalDate.of(2026, 1, 1)));
        }

        @Test
        @DisplayName("should filter by specific_year")
        void shouldFilterBySpecificYear() {
            var entry = new HolidayCalendarEntryRow(
                    "Olympic Day",
                    "\u30aa\u30ea\u30f3\u30d4\u30c3\u30af\u8a18\u5ff5\u65e5",
                    "FIXED",
                    7,
                    24,
                    null,
                    null,
                    2020);
            when(repository.findActiveEntriesByTenantId(tenantId)).thenReturn(List.of(entry));

            Map<LocalDate, HolidayInfo> result2020 =
                    service.resolveHolidays(tenantId, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31));
            assertEquals(1, result2020.size());

            Map<LocalDate, HolidayInfo> result2026 =
                    service.resolveHolidays(tenantId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
            assertTrue(result2026.isEmpty());
        }

        @Test
        @DisplayName("should return empty map when no entries exist")
        void shouldReturnEmptyMapWhenNoEntries() {
            when(repository.findActiveEntriesByTenantId(tenantId)).thenReturn(List.of());

            Map<LocalDate, HolidayInfo> result =
                    service.resolveHolidays(tenantId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should skip entry with unknown entry_type")
        void shouldSkipUnknownEntryType() {
            var entry = new HolidayCalendarEntryRow("Unknown", "不明", "CUSTOM", 1, 1, null, null, null);
            when(repository.findActiveEntriesByTenantId(tenantId)).thenReturn(List.of(entry));

            Map<LocalDate, HolidayInfo> result =
                    service.resolveHolidays(tenantId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should skip FIXED entry with invalid date (e.g. Feb 30)")
        void shouldSkipFixedEntryWithInvalidDate() {
            var entry = new HolidayCalendarEntryRow("Invalid", "無効", "FIXED", 2, 30, null, null, null);
            when(repository.findActiveEntriesByTenantId(tenantId)).thenReturn(List.of(entry));

            Map<LocalDate, HolidayInfo> result =
                    service.resolveHolidays(tenantId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should keep last entry when duplicate holidays resolve to same date")
        void shouldKeepLastEntryWhenDuplicateHolidays() {
            var entry1 = new HolidayCalendarEntryRow("Holiday A", "祝日A", "FIXED", 1, 1, null, null, null);
            var entry2 = new HolidayCalendarEntryRow("Holiday B", "祝日B", "FIXED", 1, 1, null, null, null);
            when(repository.findActiveEntriesByTenantId(tenantId)).thenReturn(List.of(entry1, entry2));

            Map<LocalDate, HolidayInfo> result =
                    service.resolveHolidays(tenantId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

            assertEquals(1, result.size());
            HolidayInfo holiday = result.get(LocalDate.of(2026, 1, 1));
            assertNotNull(holiday);
            assertEquals("Holiday B", holiday.name());
        }

        @Test
        @DisplayName("should return holidays ordered by date using TreeMap")
        void shouldReturnHolidaysOrderedByDate() {
            var feb = new HolidayCalendarEntryRow("Foundation Day", "建国記念の日", "FIXED", 2, 11, null, null, null);
            var jan = new HolidayCalendarEntryRow("New Year's Day", "元日", "FIXED", 1, 1, null, null, null);
            // Entries ordered: Feb first, Jan second (reversed chronological order)
            when(repository.findActiveEntriesByTenantId(tenantId)).thenReturn(List.of(feb, jan));

            Map<LocalDate, HolidayInfo> result =
                    service.resolveHolidays(tenantId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

            // TreeMap ensures date-ordered iteration regardless of insertion order
            var dates = result.keySet().stream().toList();
            assertEquals(LocalDate.of(2026, 1, 1), dates.get(0));
            assertEquals(LocalDate.of(2026, 2, 11), dates.get(1));
        }
    }
}
