package com.worklog.application.service;

import com.worklog.domain.fiscalyear.FiscalYearPattern;
import com.worklog.domain.fiscalyear.FiscalYearPattern.Pair;
import com.worklog.domain.fiscalyear.FiscalYearPatternId;
import com.worklog.domain.monthlyperiod.MonthlyPeriod;
import com.worklog.domain.monthlyperiod.MonthlyPeriodPattern;
import com.worklog.domain.monthlyperiod.MonthlyPeriodPatternId;
import com.worklog.domain.organization.Organization;
import com.worklog.domain.organization.OrganizationId;
import com.worklog.infrastructure.repository.FiscalYearPatternRepository;
import com.worklog.infrastructure.repository.MonthlyPeriodPatternRepository;
import com.worklog.infrastructure.repository.OrganizationRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for calculating date information (fiscal year and monthly period)
 * for organizations.
 *
 * This service:
 * - Resolves fiscal year and monthly period patterns from organization hierarchy
 * - Child organizations inherit patterns from parents if not set
 * - Root organizations must have both patterns set
 * - Calculates fiscal year and monthly period for a given date
 */
@Service
public class DateInfoService {

    private final OrganizationRepository organizationRepository;
    private final FiscalYearPatternRepository fiscalYearPatternRepository;
    private final MonthlyPeriodPatternRepository monthlyPeriodPatternRepository;

    public DateInfoService(
            OrganizationRepository organizationRepository,
            FiscalYearPatternRepository fiscalYearPatternRepository,
            MonthlyPeriodPatternRepository monthlyPeriodPatternRepository) {
        this.organizationRepository = organizationRepository;
        this.fiscalYearPatternRepository = fiscalYearPatternRepository;
        this.monthlyPeriodPatternRepository = monthlyPeriodPatternRepository;
    }

    /**
     * Calculates date information for an organization and date.
     *
     * @param organizationId The organization ID
     * @param date The date to calculate for
     * @return DateInfo containing fiscal year and monthly period information
     * @throws IllegalArgumentException if organization not found
     * @throws IllegalStateException if no patterns can be resolved
     */
    @Transactional(readOnly = true)
    public DateInfo getDateInfo(UUID organizationId, LocalDate date) {
        // Load organization
        Organization org = organizationRepository
                .findById(new OrganizationId(organizationId))
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + organizationId));

        // Resolve patterns from hierarchy
        UUID fiscalYearPatternId = resolveFiscalYearPattern(org);
        UUID monthlyPeriodPatternId = resolveMonthlyPeriodPattern(org);

        if (fiscalYearPatternId == null) {
            throw new IllegalStateException("No fiscal year pattern found for organization: " + organizationId);
        }

        if (monthlyPeriodPatternId == null) {
            throw new IllegalStateException("No monthly period pattern found for organization: " + organizationId);
        }

        // Load patterns
        FiscalYearPattern fiscalYearPattern = loadFiscalYearPattern(fiscalYearPatternId);
        MonthlyPeriodPattern monthlyPeriodPattern = loadMonthlyPeriodPattern(monthlyPeriodPatternId);

        // Calculate fiscal year
        int fiscalYear = fiscalYearPattern.getFiscalYear(date);
        Pair<LocalDate, LocalDate> fiscalYearRange = fiscalYearPattern.getFiscalYearRange(fiscalYear);

        // Calculate monthly period
        MonthlyPeriod monthlyPeriod = monthlyPeriodPattern.getMonthlyPeriod(date);

        return new DateInfo(
                date,
                fiscalYear,
                fiscalYearRange.first(),
                fiscalYearRange.second(),
                monthlyPeriod.start(),
                monthlyPeriod.end(),
                fiscalYearPatternId,
                monthlyPeriodPatternId,
                organizationId);
    }

    /**
     * Resolves fiscal year pattern ID from organization hierarchy.
     * Walks up the hierarchy until a pattern is found.
     *
     * @param org The organization to start from
     * @return The fiscal year pattern ID, or null if not found
     */
    private UUID resolveFiscalYearPattern(Organization org) {
        Organization current = org;

        while (current != null) {
            if (current.getFiscalYearPatternId() != null) {
                return current.getFiscalYearPatternId();
            }

            // Walk up to parent
            if (current.getParentId() != null) {
                current = organizationRepository.findById(current.getParentId()).orElse(null);
            } else {
                // Reached root without finding pattern
                current = null;
            }
        }

        return null;
    }

    /**
     * Resolves monthly period pattern ID from organization hierarchy.
     * Walks up the hierarchy until a pattern is found.
     *
     * @param org The organization to start from
     * @return The monthly period pattern ID, or null if not found
     */
    private UUID resolveMonthlyPeriodPattern(Organization org) {
        Organization current = org;

        while (current != null) {
            if (current.getMonthlyPeriodPatternId() != null) {
                return current.getMonthlyPeriodPatternId();
            }

            // Walk up to parent
            if (current.getParentId() != null) {
                current = organizationRepository.findById(current.getParentId()).orElse(null);
            } else {
                // Reached root without finding pattern
                current = null;
            }
        }

        return null;
    }

    /**
     * Loads fiscal year pattern from event store via repository.
     */
    private FiscalYearPattern loadFiscalYearPattern(UUID patternId) {
        return fiscalYearPatternRepository
                .findById(FiscalYearPatternId.of(patternId))
                .orElseThrow(() -> new IllegalStateException("Fiscal year pattern not found: " + patternId));
    }

    /**
     * Loads monthly period pattern from event store via repository.
     */
    private MonthlyPeriodPattern loadMonthlyPeriodPattern(UUID patternId) {
        return monthlyPeriodPatternRepository
                .findById(MonthlyPeriodPatternId.of(patternId))
                .orElseThrow(() -> new IllegalStateException("Monthly period pattern not found: " + patternId));
    }
}
