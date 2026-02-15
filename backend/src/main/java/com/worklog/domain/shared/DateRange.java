package com.worklog.domain.shared;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Value object representing a date range with start and end dates.
 *
 * Used for project validity periods and absence date ranges.
 * Allows start date to equal end date (single-day range).
 *
 * Constraints:
 * - Start date cannot be after end date
 * - Both dates must be non-null
 */
public record DateRange(LocalDate startDate, LocalDate endDate) {

    public DateRange {
        if (startDate == null || endDate == null) {
            throw new DomainException("DATE_RANGE_NULL", "Start date and end date cannot be null");
        }

        if (startDate.isAfter(endDate)) {
            throw new DomainException("DATE_RANGE_INVALID", "Start date cannot be after end date");
        }
    }

    /**
     * Factory method to create a date range.
     */
    public static DateRange of(LocalDate startDate, LocalDate endDate) {
        return new DateRange(startDate, endDate);
    }

    /**
     * Factory method to create a single-day date range.
     */
    public static DateRange singleDay(LocalDate date) {
        return new DateRange(date, date);
    }

    /**
     * Checks if this date range contains the given date (inclusive).
     */
    public boolean contains(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /**
     * Checks if this date range overlaps with another date range.
     * Returns true if there is any overlap (inclusive).
     */
    public boolean overlaps(DateRange other) {
        return !endDate.isBefore(other.startDate) && !other.endDate.isBefore(startDate);
    }

    /**
     * Checks if this date range completely contains another date range.
     */
    public boolean contains(DateRange other) {
        return !other.startDate.isBefore(startDate) && !other.endDate.isAfter(endDate);
    }

    /**
     * Returns the number of days in this date range (inclusive).
     * A single-day range returns 1.
     */
    public long durationInDays() {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    /**
     * Checks if this is a single-day range.
     */
    public boolean isSingleDay() {
        return startDate.equals(endDate);
    }

    /**
     * Returns a new DateRange extended by the given number of days.
     * Negative days will shorten the range.
     *
     * @throws DomainException if the result would be invalid
     */
    public DateRange extendBy(long days) {
        return new DateRange(startDate, endDate.plusDays(days));
    }

    /**
     * Returns a new DateRange starting from the given number of days earlier.
     * Negative days will move the start date forward.
     *
     * @throws DomainException if the result would be invalid
     */
    public DateRange startEarlierBy(long days) {
        return new DateRange(startDate.minusDays(days), endDate);
    }

    /**
     * Returns a human-readable representation.
     * Example: "2025-01-01 to 2025-01-31" or "2025-01-15" for single day
     */
    @Override
    public String toString() {
        if (isSingleDay()) {
            return startDate.toString();
        }
        return String.format("%s to %s", startDate, endDate);
    }
}
