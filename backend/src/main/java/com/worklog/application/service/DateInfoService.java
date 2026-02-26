package com.worklog.application.service;

import com.worklog.domain.fiscalyear.FiscalYearPattern;
import com.worklog.domain.fiscalyear.FiscalYearPattern.Pair;
import com.worklog.domain.fiscalyear.FiscalYearPatternId;
import com.worklog.domain.monthlyperiod.MonthlyPeriod;
import com.worklog.domain.monthlyperiod.MonthlyPeriodPattern;
import com.worklog.domain.monthlyperiod.MonthlyPeriodPatternId;
import com.worklog.domain.organization.Organization;
import com.worklog.domain.organization.OrganizationId;
import com.worklog.domain.settings.SystemDefaultFiscalYearPattern;
import com.worklog.domain.settings.SystemDefaultMonthlyPeriodPattern;
import com.worklog.domain.tenant.Tenant;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.FiscalYearPatternRepository;
import com.worklog.infrastructure.repository.MonthlyPeriodPatternRepository;
import com.worklog.infrastructure.repository.OrganizationRepository;
import com.worklog.infrastructure.repository.SystemDefaultSettingsRepository;
import com.worklog.infrastructure.repository.TenantRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for calculating date information (fiscal year and monthly period)
 * for organizations.
 *
 * Resolution chain (3-tier inheritance):
 * 1. Organization hierarchy — walk up from current org to root
 * 2. Tenant default — tenant-level default pattern reference
 * 3. System default — system-wide default values (raw startMonth/startDay)
 */
@Service
public class DateInfoService {

    private final OrganizationRepository organizationRepository;
    private final FiscalYearPatternRepository fiscalYearPatternRepository;
    private final MonthlyPeriodPatternRepository monthlyPeriodPatternRepository;
    private final TenantRepository tenantRepository;
    private final SystemDefaultSettingsRepository systemDefaultSettingsRepository;

    public DateInfoService(
            OrganizationRepository organizationRepository,
            FiscalYearPatternRepository fiscalYearPatternRepository,
            MonthlyPeriodPatternRepository monthlyPeriodPatternRepository,
            TenantRepository tenantRepository,
            SystemDefaultSettingsRepository systemDefaultSettingsRepository) {
        this.organizationRepository = organizationRepository;
        this.fiscalYearPatternRepository = fiscalYearPatternRepository;
        this.monthlyPeriodPatternRepository = monthlyPeriodPatternRepository;
        this.tenantRepository = tenantRepository;
        this.systemDefaultSettingsRepository = systemDefaultSettingsRepository;
    }

    /**
     * Calculates date information for an organization and date.
     *
     * @param organizationId The organization ID
     * @param date The date to calculate for
     * @return DateInfo containing fiscal year, monthly period, and source information
     * @throws IllegalArgumentException if organization not found
     * @throws IllegalStateException if no patterns can be resolved
     */
    @Transactional(readOnly = true)
    public DateInfo getDateInfo(UUID organizationId, LocalDate date) {
        Organization org = organizationRepository
                .findById(new OrganizationId(organizationId))
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + organizationId));

        PatternResolution fyResolution = resolveFiscalYearPattern(org);
        PatternResolution mpResolution = resolveMonthlyPeriodPattern(org);

        // Calculate fiscal year
        FiscalYearPattern fyPattern = loadOrCreateFiscalYearPattern(fyResolution);
        int fiscalYear = fyPattern.getFiscalYear(date);
        Pair<LocalDate, LocalDate> fiscalYearRange = fyPattern.getFiscalYearRange(fiscalYear);

        // Calculate monthly period
        MonthlyPeriodPattern mpPattern = loadOrCreateMonthlyPeriodPattern(mpResolution);
        MonthlyPeriod monthlyPeriod = mpPattern.getMonthlyPeriod(date);

        return new DateInfo(
                date,
                fiscalYear,
                fiscalYearRange.first(),
                fiscalYearRange.second(),
                monthlyPeriod.start(),
                monthlyPeriod.end(),
                fyResolution.patternId,
                mpResolution.patternId,
                organizationId,
                fyResolution.source,
                mpResolution.source);
    }

    /**
     * Resolves the effective patterns for an organization (used by admin effective-patterns endpoint).
     */
    @Transactional(readOnly = true)
    public EffectivePatterns getEffectivePatterns(UUID organizationId) {
        Organization org = organizationRepository
                .findById(new OrganizationId(organizationId))
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + organizationId));

        PatternResolution fyResolution = resolveFiscalYearPattern(org);
        PatternResolution mpResolution = resolveMonthlyPeriodPattern(org);

        return new EffectivePatterns(
                fyResolution.patternId, fyResolution.source, mpResolution.patternId, mpResolution.source);
    }

    /**
     * Resolves fiscal year pattern from organization hierarchy, then tenant, then system.
     */
    private PatternResolution resolveFiscalYearPattern(Organization org) {
        // Step 1: Walk organization hierarchy
        Organization current = org;
        while (current != null) {
            if (current.getFiscalYearPatternId() != null) {
                return new PatternResolution(
                        current.getFiscalYearPatternId(),
                        "organization:" + current.getId().value());
            }
            if (current.getParentId() != null) {
                current = organizationRepository.findById(current.getParentId()).orElse(null);
            } else {
                current = null;
            }
        }

        // Step 2: Check tenant default
        Tenant tenant = tenantRepository.findById(org.getTenantId()).orElse(null);
        if (tenant != null && tenant.getDefaultFiscalYearPatternId() != null) {
            return new PatternResolution(tenant.getDefaultFiscalYearPatternId(), "tenant");
        }

        // Step 3: Fall back to system default
        return new PatternResolution(null, "system");
    }

    /**
     * Resolves monthly period pattern from organization hierarchy, then tenant, then system.
     */
    private PatternResolution resolveMonthlyPeriodPattern(Organization org) {
        // Step 1: Walk organization hierarchy
        Organization current = org;
        while (current != null) {
            if (current.getMonthlyPeriodPatternId() != null) {
                return new PatternResolution(
                        current.getMonthlyPeriodPatternId(),
                        "organization:" + current.getId().value());
            }
            if (current.getParentId() != null) {
                current = organizationRepository.findById(current.getParentId()).orElse(null);
            } else {
                current = null;
            }
        }

        // Step 2: Check tenant default
        Tenant tenant = tenantRepository.findById(org.getTenantId()).orElse(null);
        if (tenant != null && tenant.getDefaultMonthlyPeriodPatternId() != null) {
            return new PatternResolution(tenant.getDefaultMonthlyPeriodPatternId(), "tenant");
        }

        // Step 3: Fall back to system default
        return new PatternResolution(null, "system");
    }

    private FiscalYearPattern loadOrCreateFiscalYearPattern(PatternResolution resolution) {
        if (resolution.patternId != null) {
            return fiscalYearPatternRepository
                    .findById(FiscalYearPatternId.of(resolution.patternId))
                    .orElseThrow(
                            () -> new IllegalStateException("Fiscal year pattern not found: " + resolution.patternId));
        }
        // System default: create transient pattern for calculation
        SystemDefaultFiscalYearPattern systemDefault = systemDefaultSettingsRepository.getDefaultFiscalYearPattern();
        return FiscalYearPattern.createWithId(
                FiscalYearPatternId.generate(),
                TenantId.of(new UUID(0, 0)),
                "System Default",
                systemDefault.startMonth(),
                systemDefault.startDay());
    }

    private MonthlyPeriodPattern loadOrCreateMonthlyPeriodPattern(PatternResolution resolution) {
        if (resolution.patternId != null) {
            return monthlyPeriodPatternRepository
                    .findById(MonthlyPeriodPatternId.of(resolution.patternId))
                    .orElseThrow(() ->
                            new IllegalStateException("Monthly period pattern not found: " + resolution.patternId));
        }
        // System default: create transient pattern for calculation
        SystemDefaultMonthlyPeriodPattern systemDefault =
                systemDefaultSettingsRepository.getDefaultMonthlyPeriodPattern();
        return MonthlyPeriodPattern.createWithId(
                MonthlyPeriodPatternId.generate(),
                TenantId.of(new UUID(0, 0)),
                "System Default",
                systemDefault.startDay());
    }

    private record PatternResolution(UUID patternId, String source) {}

    public record EffectivePatterns(
            UUID fiscalYearPatternId,
            String fiscalYearSource,
            UUID monthlyPeriodPatternId,
            String monthlyPeriodSource) {}
}
