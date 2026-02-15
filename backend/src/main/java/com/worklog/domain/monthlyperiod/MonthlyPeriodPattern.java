package com.worklog.domain.monthlyperiod;

import com.worklog.domain.shared.AggregateRoot;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import java.time.LocalDate;

/**
 * MonthlyPeriodPattern aggregate root that defines how monthly periods are calculated.
 *
 * Business Rules:
 * - startDay must be 1-28 (to handle February in non-leap years)
 * - Periods always span exactly one calendar month
 *
 * This is an event-sourced aggregate that tracks changes via domain events.
 */
public class MonthlyPeriodPattern extends AggregateRoot<MonthlyPeriodPatternId> {

    private MonthlyPeriodPatternId id;
    private TenantId tenantId;
    private String name;
    private int startDay;

    // Private constructor for factory methods
    private MonthlyPeriodPattern() {}

    /**
     * Creates a new MonthlyPeriodPattern.
     *
     * @param tenantId The tenant ID
     * @param name The pattern name
     * @param startDay The starting day (1-28)
     * @return A new MonthlyPeriodPattern with MonthlyPeriodPatternCreated event
     * @throws DomainException if validation fails
     */
    public static MonthlyPeriodPattern create(TenantId tenantId, String name, int startDay) {
        validateName(name);
        validateStartDay(startDay);

        MonthlyPeriodPattern pattern = new MonthlyPeriodPattern();
        MonthlyPeriodPatternId patternId = MonthlyPeriodPatternId.generate();

        MonthlyPeriodPatternCreated event =
                MonthlyPeriodPatternCreated.create(patternId.value(), tenantId.value(), name.trim(), startDay);
        pattern.raiseEvent(event);

        return pattern;
    }

    /**
     * Creates a new MonthlyPeriodPattern with a specific ID.
     * Used for reconstitution from event store.
     */
    public static MonthlyPeriodPattern createWithId(
            MonthlyPeriodPatternId id, TenantId tenantId, String name, int startDay) {
        validateName(name);
        validateStartDay(startDay);

        MonthlyPeriodPattern pattern = new MonthlyPeriodPattern();

        MonthlyPeriodPatternCreated event =
                MonthlyPeriodPatternCreated.create(id.value(), tenantId.value(), name.trim(), startDay);
        pattern.raiseEvent(event);

        return pattern;
    }

    /**
     * Calculates the monthly period for a given date.
     *
     * @param date The date to calculate the monthly period for
     * @return A MonthlyPeriod with start and end dates, and display month/year
     */
    public MonthlyPeriod getMonthlyPeriod(LocalDate date) {
        LocalDate periodStart;

        if (date.getDayOfMonth() >= startDay) {
            // Period starts in the current month
            periodStart = date.withDayOfMonth(startDay);
        } else {
            // Period starts in the previous month
            periodStart = date.minusMonths(1).withDayOfMonth(startDay);
        }

        LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);

        // Display month/year is based on the end date (when the period closes)
        int displayMonth = periodEnd.getMonthValue();
        int displayYear = periodEnd.getYear();

        return new MonthlyPeriod(periodStart, periodEnd, displayMonth, displayYear);
    }

    @Override
    protected void apply(DomainEvent event) {
        if (event instanceof MonthlyPeriodPatternCreated e) {
            this.id = MonthlyPeriodPatternId.of(e.aggregateId());
            this.tenantId = TenantId.of(e.tenantId());
            this.name = e.name();
            this.startDay = e.startDay();
        } else {
            throw new IllegalArgumentException(
                    "Unknown event type: " + event.getClass().getName());
        }
    }

    private static void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new DomainException("NAME_REQUIRED", "Monthly period pattern name is required");
        }
        if (name.length() > 100) {
            throw new DomainException("NAME_TOO_LONG", "Monthly period pattern name must not exceed 100 characters");
        }
    }

    private static void validateStartDay(int startDay) {
        if (startDay < 1 || startDay > 28) {
            throw new DomainException(
                    "INVALID_START_DAY", "Start day must be between 1 and 28 (to handle February), got: " + startDay);
        }
    }

    // Getters

    @Override
    public MonthlyPeriodPatternId getId() {
        return id;
    }

    @Override
    public String getAggregateType() {
        return "MonthlyPeriodPattern";
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public int getStartDay() {
        return startDay;
    }
}
