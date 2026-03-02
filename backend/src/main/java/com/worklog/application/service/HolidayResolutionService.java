package com.worklog.application.service;

import com.worklog.domain.model.HolidayInfo;
import com.worklog.infrastructure.repository.HolidayCalendarEntryRepository;
import com.worklog.infrastructure.repository.HolidayCalendarEntryRepository.HolidayCalendarEntryRow;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class HolidayResolutionService {

    private final HolidayCalendarEntryRepository repository;

    public HolidayResolutionService(HolidayCalendarEntryRepository repository) {
        this.repository = repository;
    }

    @Cacheable(
            cacheNames = "calendar:holidays",
            key = "#tenantId.toString() + ':' + #start.toString() + ':' + #end.toString()",
            condition = "@environment.getProperty('worklog.cache.enabled', 'false') == 'true'")
    public Map<LocalDate, HolidayInfo> resolveHolidays(UUID tenantId, LocalDate start, LocalDate end) {
        var entries = repository.findActiveEntriesByTenantId(tenantId);
        Map<LocalDate, HolidayInfo> holidays = new LinkedHashMap<>();

        for (var entry : entries) {
            resolveEntry(entry, start, end, holidays);
        }

        return holidays;
    }

    private void resolveEntry(
            HolidayCalendarEntryRow entry, LocalDate start, LocalDate end, Map<LocalDate, HolidayInfo> holidays) {
        int startYear = start.getYear();
        int endYear = end.getYear();

        for (int year = startYear; year <= endYear; year++) {
            if (entry.specificYear() != null && entry.specificYear() != year) {
                continue;
            }

            LocalDate resolved =
                    switch (entry.entryType()) {
                        case "FIXED" -> resolveFixed(year, entry);
                        case "NTH_WEEKDAY" -> resolveNthWeekday(year, entry);
                        default -> null;
                    };

            if (resolved != null && !resolved.isBefore(start) && !resolved.isAfter(end)) {
                holidays.put(resolved, new HolidayInfo(entry.name(), entry.nameJa(), resolved));
            }
        }
    }

    private LocalDate resolveFixed(int year, HolidayCalendarEntryRow entry) {
        return LocalDate.of(year, entry.month(), entry.day());
    }

    private LocalDate resolveNthWeekday(int year, HolidayCalendarEntryRow entry) {
        DayOfWeek dayOfWeek = DayOfWeek.of(entry.dayOfWeek());
        LocalDate firstOfMonth = LocalDate.of(year, entry.month(), 1);
        LocalDate firstOccurrence = firstOfMonth.with(TemporalAdjusters.firstInMonth(dayOfWeek));
        LocalDate nthOccurrence = firstOccurrence.plusWeeks(entry.nthOccurrence() - 1);

        if (nthOccurrence.getMonthValue() != entry.month()) {
            return null;
        }
        return nthOccurrence;
    }
}
