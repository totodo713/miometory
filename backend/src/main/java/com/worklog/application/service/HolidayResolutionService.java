package com.worklog.application.service;

import com.worklog.domain.model.HolidayInfo;
import com.worklog.infrastructure.repository.HolidayCalendarEntryRepository;
import com.worklog.infrastructure.repository.HolidayCalendarEntryRepository.HolidayCalendarEntryRow;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HolidayResolutionService {

    private static final Logger log = LoggerFactory.getLogger(HolidayResolutionService.class);

    private final HolidayCalendarEntryRepository repository;

    public HolidayResolutionService(HolidayCalendarEntryRepository repository) {
        this.repository = repository;
    }

    /**
     * Resolves holiday calendar entries for a tenant into actual dates within the given range.
     *
     * @param tenantId the tenant whose holiday calendar to resolve
     * @param start the start date (inclusive)
     * @param end the end date (inclusive)
     * @return map of resolved holiday dates to their info, ordered by date
     */
    @Cacheable(
            cacheNames = "calendar:holidays",
            key = "#tenantId.toString() + ':' + #start.toString() + ':' + #end.toString()",
            condition = "@environment.getProperty('worklog.cache.enabled', 'false') == 'true'")
    @Transactional(readOnly = true)
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
                        default -> {
                            log.warn(
                                    "Unknown holiday entry_type '{}' for entry '{}', skipping",
                                    entry.entryType(),
                                    entry.name());
                            yield null;
                        }
                    };

            if (resolved != null && !resolved.isBefore(start) && !resolved.isAfter(end)) {
                holidays.put(resolved, new HolidayInfo(entry.name(), entry.nameJa(), resolved));
            }
        }
    }

    private LocalDate resolveFixed(int year, HolidayCalendarEntryRow entry) {
        try {
            return LocalDate.of(year, entry.month(), entry.day());
        } catch (DateTimeException e) {
            log.warn(
                    "Invalid FIXED holiday date: {}/{}/{} for '{}', skipping",
                    year,
                    entry.month(),
                    entry.day(),
                    entry.name());
            return null;
        }
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
