package com.worklog.application.service;

import com.worklog.domain.fiscalyear.FiscalYearRule;
import com.worklog.domain.fiscalyear.FiscalYearRule.Pair;
import com.worklog.domain.fiscalyear.FiscalYearRuleId;
import com.worklog.domain.monthlyperiod.MonthlyPeriod;
import com.worklog.domain.monthlyperiod.MonthlyPeriodRule;
import com.worklog.domain.monthlyperiod.MonthlyPeriodRuleId;
import com.worklog.domain.organization.Organization;
import com.worklog.domain.organization.OrganizationId;
import com.worklog.domain.settings.SystemDefaultFiscalYearRule;
import com.worklog.domain.settings.SystemDefaultMonthlyPeriodRule;
import com.worklog.domain.tenant.Tenant;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.FiscalYearRuleRepository;
import com.worklog.infrastructure.repository.MonthlyPeriodRuleRepository;
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
 * 2. Tenant default — tenant-level default rule reference
 * 3. System default — system-wide default values (raw startMonth/startDay)
 */
@Service
public class DateInfoService {

    private final OrganizationRepository organizationRepository;
    private final FiscalYearRuleRepository fiscalYearRuleRepository;
    private final MonthlyPeriodRuleRepository monthlyPeriodRuleRepository;
    private final TenantRepository tenantRepository;
    private final SystemDefaultSettingsRepository systemDefaultSettingsRepository;

    public DateInfoService(
            OrganizationRepository organizationRepository,
            FiscalYearRuleRepository fiscalYearRuleRepository,
            MonthlyPeriodRuleRepository monthlyPeriodRuleRepository,
            TenantRepository tenantRepository,
            SystemDefaultSettingsRepository systemDefaultSettingsRepository) {
        this.organizationRepository = organizationRepository;
        this.fiscalYearRuleRepository = fiscalYearRuleRepository;
        this.monthlyPeriodRuleRepository = monthlyPeriodRuleRepository;
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
     * @throws IllegalStateException if system default settings are missing or an internal error prevents resolving rules
     */
    @Transactional(readOnly = true)
    public DateInfo getDateInfo(UUID organizationId, LocalDate date) {
        Organization org = organizationRepository
                .findById(new OrganizationId(organizationId))
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + organizationId));

        RuleResolution fyResolution = resolveFiscalYearRule(org);
        RuleResolution mpResolution = resolveMonthlyPeriodRule(org);

        // Calculate fiscal year
        FiscalYearRule fyRule = loadOrCreateFiscalYearRule(fyResolution);
        int fiscalYear = fyRule.getFiscalYear(date);
        Pair<LocalDate, LocalDate> fiscalYearRange = fyRule.getFiscalYearRange(fiscalYear);

        // Calculate monthly period
        MonthlyPeriodRule mpRule = loadOrCreateMonthlyPeriodRule(mpResolution);
        MonthlyPeriod monthlyPeriod = mpRule.getMonthlyPeriod(date);

        return new DateInfo(
                date,
                fiscalYear,
                fiscalYearRange.first(),
                fiscalYearRange.second(),
                monthlyPeriod.start(),
                monthlyPeriod.end(),
                fyResolution.ruleId,
                mpResolution.ruleId,
                organizationId,
                fyResolution.source,
                mpResolution.source);
    }

    /**
     * Resolves the effective rules for an organization (used by admin effective-rules endpoint).
     */
    @Transactional(readOnly = true)
    public EffectiveRules getEffectiveRules(UUID organizationId) {
        Organization org = organizationRepository
                .findById(new OrganizationId(organizationId))
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + organizationId));

        RuleResolution fyResolution = resolveFiscalYearRule(org);
        RuleResolution mpResolution = resolveMonthlyPeriodRule(org);

        return new EffectiveRules(
                fyResolution.ruleId,
                fyResolution.source,
                resolveSourceName(fyResolution.source),
                mpResolution.ruleId,
                mpResolution.source,
                resolveSourceName(mpResolution.source));
    }

    private String resolveSourceName(String source) {
        if (source.startsWith("organization:")) {
            UUID orgId = UUID.fromString(source.substring("organization:".length()));
            return organizationRepository
                    .findById(new OrganizationId(orgId))
                    .map(Organization::getName)
                    .orElse(null);
        }
        return null;
    }

    /**
     * Resolves fiscal year rule from organization hierarchy, then tenant, then system.
     */
    private RuleResolution resolveFiscalYearRule(Organization org) {
        // Step 1: Walk organization hierarchy
        Organization current = org;
        while (current != null) {
            if (current.getFiscalYearRuleId() != null) {
                return new RuleResolution(
                        current.getFiscalYearRuleId(),
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
        if (tenant != null && tenant.getDefaultFiscalYearRuleId() != null) {
            return new RuleResolution(tenant.getDefaultFiscalYearRuleId(), "tenant");
        }

        // Step 3: Fall back to system default
        return new RuleResolution(null, "system");
    }

    /**
     * Resolves monthly period rule from organization hierarchy, then tenant, then system.
     */
    private RuleResolution resolveMonthlyPeriodRule(Organization org) {
        // Step 1: Walk organization hierarchy
        Organization current = org;
        while (current != null) {
            if (current.getMonthlyPeriodRuleId() != null) {
                return new RuleResolution(
                        current.getMonthlyPeriodRuleId(),
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
        if (tenant != null && tenant.getDefaultMonthlyPeriodRuleId() != null) {
            return new RuleResolution(tenant.getDefaultMonthlyPeriodRuleId(), "tenant");
        }

        // Step 3: Fall back to system default
        return new RuleResolution(null, "system");
    }

    private FiscalYearRule loadOrCreateFiscalYearRule(RuleResolution resolution) {
        if (resolution.ruleId != null) {
            return fiscalYearRuleRepository
                    .findById(FiscalYearRuleId.of(resolution.ruleId))
                    .orElseThrow(() -> new IllegalStateException("Fiscal year rule not found: " + resolution.ruleId));
        }
        // System default: create transient rule for calculation
        SystemDefaultFiscalYearRule systemDefault = systemDefaultSettingsRepository.getDefaultFiscalYearRule();
        return FiscalYearRule.createWithId(
                FiscalYearRuleId.generate(),
                TenantId.of(new UUID(0, 0)),
                "System Default",
                systemDefault.startMonth(),
                systemDefault.startDay());
    }

    private MonthlyPeriodRule loadOrCreateMonthlyPeriodRule(RuleResolution resolution) {
        if (resolution.ruleId != null) {
            return monthlyPeriodRuleRepository
                    .findById(MonthlyPeriodRuleId.of(resolution.ruleId))
                    .orElseThrow(
                            () -> new IllegalStateException("Monthly period rule not found: " + resolution.ruleId));
        }
        // System default: create transient rule for calculation
        SystemDefaultMonthlyPeriodRule systemDefault = systemDefaultSettingsRepository.getDefaultMonthlyPeriodRule();
        return MonthlyPeriodRule.createWithId(
                MonthlyPeriodRuleId.generate(),
                TenantId.of(new UUID(0, 0)),
                "System Default",
                systemDefault.startDay());
    }

    private record RuleResolution(UUID ruleId, String source) {}

    public record EffectiveRules(
            UUID fiscalYearRuleId,
            String fiscalYearSource,
            String fiscalYearSourceName,
            UUID monthlyPeriodRuleId,
            String monthlyPeriodSource,
            String monthlyPeriodSourceName) {}
}
