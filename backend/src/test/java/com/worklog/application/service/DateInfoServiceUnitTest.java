package com.worklog.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.worklog.domain.fiscalyear.FiscalYearRule;
import com.worklog.domain.fiscalyear.FiscalYearRuleId;
import com.worklog.domain.monthlyperiod.MonthlyPeriodRule;
import com.worklog.domain.monthlyperiod.MonthlyPeriodRuleId;
import com.worklog.domain.organization.Organization;
import com.worklog.domain.organization.OrganizationId;
import com.worklog.domain.settings.SystemDefaultFiscalYearRule;
import com.worklog.domain.settings.SystemDefaultMonthlyPeriodRule;
import com.worklog.domain.shared.Code;
import com.worklog.domain.tenant.Tenant;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.FiscalYearRuleRepository;
import com.worklog.infrastructure.repository.MonthlyPeriodRuleRepository;
import com.worklog.infrastructure.repository.OrganizationRepository;
import com.worklog.infrastructure.repository.SystemDefaultSettingsRepository;
import com.worklog.infrastructure.repository.TenantRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DateInfoService (Unit)")
class DateInfoServiceUnitTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private FiscalYearRuleRepository fiscalYearRuleRepository;

    @Mock
    private MonthlyPeriodRuleRepository monthlyPeriodRuleRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private SystemDefaultSettingsRepository systemDefaultSettingsRepository;

    private DateInfoService service;

    private static final UUID TENANT_UUID = UUID.randomUUID();
    private static final TenantId TENANT_ID = TenantId.of(TENANT_UUID);
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 2, 15);

    @BeforeEach
    void setUp() {
        service = new DateInfoService(
                organizationRepository,
                fiscalYearRuleRepository,
                monthlyPeriodRuleRepository,
                tenantRepository,
                systemDefaultSettingsRepository);
    }

    private Organization createOrg(UUID id, UUID parentId, UUID fyPatternId, UUID mpPatternId) {
        OrganizationId orgId = OrganizationId.of(id);
        OrganizationId parentOrgId = parentId != null ? OrganizationId.of(parentId) : null;
        int level = parentId == null ? 1 : 2;
        Organization org = Organization.create(
                orgId, TENANT_ID, parentOrgId, Code.of("ORG" + id.toString().substring(0, 4)), "Org", level);
        if (fyPatternId != null || mpPatternId != null) {
            org.assignRules(fyPatternId, mpPatternId);
        }
        return org;
    }

    private FiscalYearRule createFyPattern(UUID id) {
        return FiscalYearRule.createWithId(FiscalYearRuleId.of(id), TENANT_ID, "FY Pattern", 4, 1);
    }

    private MonthlyPeriodRule createMpPattern(UUID id) {
        return MonthlyPeriodRule.createWithId(MonthlyPeriodRuleId.of(id), TENANT_ID, "MP Pattern", 1);
    }

    @Nested
    @DisplayName("getDateInfo")
    class GetDateInfo {

        @Test
        @DisplayName("should resolve patterns from organization itself")
        void shouldResolveFromOrganization() {
            UUID orgUuid = UUID.randomUUID();
            UUID fyRuleUuid = UUID.randomUUID();
            UUID mpRuleUuid = UUID.randomUUID();

            Organization org = createOrg(orgUuid, null, fyRuleUuid, mpRuleUuid);
            when(organizationRepository.findById(OrganizationId.of(orgUuid))).thenReturn(Optional.of(org));
            when(fiscalYearRuleRepository.findById(FiscalYearRuleId.of(fyRuleUuid)))
                    .thenReturn(Optional.of(createFyPattern(fyRuleUuid)));
            when(monthlyPeriodRuleRepository.findById(MonthlyPeriodRuleId.of(mpRuleUuid)))
                    .thenReturn(Optional.of(createMpPattern(mpRuleUuid)));

            DateInfo result = service.getDateInfo(orgUuid, TEST_DATE);

            assertNotNull(result);
            assertEquals(fyRuleUuid, result.fiscalYearRuleId());
            assertEquals(mpRuleUuid, result.monthlyPeriodRuleId());
            assertEquals("organization:" + orgUuid, result.fiscalYearSource());
            assertEquals("organization:" + orgUuid, result.monthlyPeriodSource());
        }

        @Test
        @DisplayName("should resolve patterns from parent organization")
        void shouldResolveFromParentOrganization() {
            UUID parentUuid = UUID.randomUUID();
            UUID childUuid = UUID.randomUUID();
            UUID fyRuleUuid = UUID.randomUUID();
            UUID mpRuleUuid = UUID.randomUUID();

            Organization parent = createOrg(parentUuid, null, fyRuleUuid, mpRuleUuid);
            Organization child = createOrg(childUuid, parentUuid, null, null);

            when(organizationRepository.findById(OrganizationId.of(childUuid))).thenReturn(Optional.of(child));
            when(organizationRepository.findById(OrganizationId.of(parentUuid))).thenReturn(Optional.of(parent));
            when(fiscalYearRuleRepository.findById(FiscalYearRuleId.of(fyRuleUuid)))
                    .thenReturn(Optional.of(createFyPattern(fyRuleUuid)));
            when(monthlyPeriodRuleRepository.findById(MonthlyPeriodRuleId.of(mpRuleUuid)))
                    .thenReturn(Optional.of(createMpPattern(mpRuleUuid)));

            DateInfo result = service.getDateInfo(childUuid, TEST_DATE);

            assertEquals("organization:" + parentUuid, result.fiscalYearSource());
            assertEquals("organization:" + parentUuid, result.monthlyPeriodSource());
        }

        @Test
        @DisplayName("should fall back to tenant default when org hierarchy has no patterns")
        void shouldFallBackToTenantDefault() {
            UUID orgUuid = UUID.randomUUID();
            UUID tenantFyPatternUuid = UUID.randomUUID();
            UUID tenantMpPatternUuid = UUID.randomUUID();

            Organization org = createOrg(orgUuid, null, null, null);
            when(organizationRepository.findById(OrganizationId.of(orgUuid))).thenReturn(Optional.of(org));

            Tenant tenant = Tenant.createWithId(TENANT_ID, "T001", "Test Tenant");
            tenant.assignDefaultRules(tenantFyPatternUuid, tenantMpPatternUuid);
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            when(fiscalYearRuleRepository.findById(FiscalYearRuleId.of(tenantFyPatternUuid)))
                    .thenReturn(Optional.of(createFyPattern(tenantFyPatternUuid)));
            when(monthlyPeriodRuleRepository.findById(MonthlyPeriodRuleId.of(tenantMpPatternUuid)))
                    .thenReturn(Optional.of(createMpPattern(tenantMpPatternUuid)));

            DateInfo result = service.getDateInfo(orgUuid, TEST_DATE);

            assertEquals(tenantFyPatternUuid, result.fiscalYearRuleId());
            assertEquals(tenantMpPatternUuid, result.monthlyPeriodRuleId());
            assertEquals("tenant", result.fiscalYearSource());
            assertEquals("tenant", result.monthlyPeriodSource());
        }

        @Test
        @DisplayName("should fall back to system default when no org or tenant patterns exist")
        void shouldFallBackToSystemDefault() {
            UUID orgUuid = UUID.randomUUID();

            Organization org = createOrg(orgUuid, null, null, null);
            when(organizationRepository.findById(OrganizationId.of(orgUuid))).thenReturn(Optional.of(org));

            Tenant tenant = Tenant.createWithId(TENANT_ID, "T001", "Test Tenant");
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            when(systemDefaultSettingsRepository.getDefaultFiscalYearRule())
                    .thenReturn(new SystemDefaultFiscalYearRule(4, 1));
            when(systemDefaultSettingsRepository.getDefaultMonthlyPeriodRule())
                    .thenReturn(new SystemDefaultMonthlyPeriodRule(1));

            DateInfo result = service.getDateInfo(orgUuid, TEST_DATE);

            assertNull(result.fiscalYearRuleId());
            assertNull(result.monthlyPeriodRuleId());
            assertEquals("system", result.fiscalYearSource());
            assertEquals("system", result.monthlyPeriodSource());
        }

        @Test
        @DisplayName("should throw when organization not found")
        void shouldThrowWhenOrganizationNotFound() {
            UUID orgUuid = UUID.randomUUID();
            when(organizationRepository.findById(OrganizationId.of(orgUuid))).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> service.getDateInfo(orgUuid, TEST_DATE));
        }
    }

    @Nested
    @DisplayName("getEffectiveRules")
    class GetEffectiveRules {

        @Test
        @DisplayName("should return organization source when org has patterns")
        void shouldReturnOrganizationSource() {
            UUID orgUuid = UUID.randomUUID();
            UUID fyRuleUuid = UUID.randomUUID();
            UUID mpRuleUuid = UUID.randomUUID();

            Organization org = createOrg(orgUuid, null, fyRuleUuid, mpRuleUuid);
            when(organizationRepository.findById(OrganizationId.of(orgUuid))).thenReturn(Optional.of(org));

            DateInfoService.EffectiveRules result = service.getEffectiveRules(orgUuid);

            assertEquals(fyRuleUuid, result.fiscalYearRuleId());
            assertEquals("organization:" + orgUuid, result.fiscalYearSource());
            assertNotNull(result.fiscalYearSourceName());
            assertNotNull(result.monthlyPeriodSourceName());
        }

        @Test
        @DisplayName("should return system source when no patterns configured")
        void shouldReturnSystemSource() {
            UUID orgUuid = UUID.randomUUID();

            Organization org = createOrg(orgUuid, null, null, null);
            when(organizationRepository.findById(OrganizationId.of(orgUuid))).thenReturn(Optional.of(org));

            Tenant tenant = Tenant.createWithId(TENANT_ID, "T001", "Test Tenant");
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            DateInfoService.EffectiveRules result = service.getEffectiveRules(orgUuid);

            assertNull(result.fiscalYearRuleId());
            assertEquals("system", result.fiscalYearSource());
            assertNull(result.fiscalYearSourceName());
            assertNull(result.monthlyPeriodRuleId());
            assertEquals("system", result.monthlyPeriodSource());
            assertNull(result.monthlyPeriodSourceName());
        }
    }
}
