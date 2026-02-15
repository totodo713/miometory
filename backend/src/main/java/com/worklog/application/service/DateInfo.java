package com.worklog.application.service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Value object representing date information including fiscal year and monthly period.
 *
 * This is returned by DateInfoService when calculating fiscal year and monthly period
 * for a given date and organization.
 */
public record DateInfo(
        LocalDate date,
        int fiscalYear,
        LocalDate fiscalYearStart,
        LocalDate fiscalYearEnd,
        LocalDate monthlyPeriodStart,
        LocalDate monthlyPeriodEnd,
        UUID fiscalYearPatternId,
        UUID monthlyPeriodPatternId,
        UUID organizationId) {
    public DateInfo {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (fiscalYearStart == null) {
            throw new IllegalArgumentException("Fiscal year start cannot be null");
        }
        if (fiscalYearEnd == null) {
            throw new IllegalArgumentException("Fiscal year end cannot be null");
        }
        if (monthlyPeriodStart == null) {
            throw new IllegalArgumentException("Monthly period start cannot be null");
        }
        if (monthlyPeriodEnd == null) {
            throw new IllegalArgumentException("Monthly period end cannot be null");
        }
        if (fiscalYearPatternId == null) {
            throw new IllegalArgumentException("Fiscal year pattern ID cannot be null");
        }
        if (monthlyPeriodPatternId == null) {
            throw new IllegalArgumentException("Monthly period pattern ID cannot be null");
        }
        if (organizationId == null) {
            throw new IllegalArgumentException("Organization ID cannot be null");
        }
    }
}
