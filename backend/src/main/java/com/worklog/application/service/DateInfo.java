package com.worklog.application.service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Value object representing date information including fiscal year and monthly period.
 *
 * This is returned by DateInfoService when calculating fiscal year and monthly period
 * for a given date and organization.
 *
 * Rule IDs are nullable when the system default is used (no actual rule entity exists).
 * The source fields indicate where the pattern was resolved from:
 * - "organization:&lt;id&gt;" — from a specific organization in the hierarchy
 * - "tenant" — from the tenant's default pattern
 * - "system" — from the system-wide default
 */
public record DateInfo(
        LocalDate date,
        int fiscalYear,
        LocalDate fiscalYearStart,
        LocalDate fiscalYearEnd,
        LocalDate monthlyPeriodStart,
        LocalDate monthlyPeriodEnd,
        UUID fiscalYearRuleId,
        UUID monthlyPeriodRuleId,
        UUID organizationId,
        String fiscalYearSource,
        String monthlyPeriodSource) {
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
        if (organizationId == null) {
            throw new IllegalArgumentException("Organization ID cannot be null");
        }
        if (fiscalYearSource == null) {
            throw new IllegalArgumentException("Fiscal year source cannot be null");
        }
        if (monthlyPeriodSource == null) {
            throw new IllegalArgumentException("Monthly period source cannot be null");
        }
    }
}
