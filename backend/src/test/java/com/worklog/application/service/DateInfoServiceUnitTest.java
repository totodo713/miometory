package com.worklog.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.worklog.domain.fiscalyear.FiscalYearPattern;
import com.worklog.domain.fiscalyear.FiscalYearPatternId;
import com.worklog.domain.monthlyperiod.MonthlyPeriodPattern;
import com.worklog.domain.monthlyperiod.MonthlyPeriodPatternId;
import com.worklog.domain.organization.Organization;
import com.worklog.domain.organization.OrganizationId;
import com.worklog.domain.settings.SystemDefaultFiscalYearPattern;
import com.worklog.domain.settings.SystemDefaultMonthlyPeriodPattern;
import com.worklog.domain.shared.Code;
import com.worklog.domain.tenant.Tenant;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.FiscalYearPatternRepository;
import com.worklog.infrastructure.repository.MonthlyPeriodPatternRepository;
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
    private FiscalYearPatternRepository fiscalYearPatternRepository;

    @Mock
    private MonthlyPeriodPatternRepository monthlyPeriodPatternRepository;

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
                fiscalYearPatternRepository,
                monthlyPeriodPatternRepository,
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
            org.assignPatterns(fyPatternId, mpPatternId);
        }
        return org;
    }

    private FiscalYearPattern createFyPattern(UUID id) {
        return FiscalYearPattern.createWithId(FiscalYearPatternId.of(id), TENANT_ID, "FY Pattern", 4, 1);
    }

    private MonthlyPeriodPattern createMpPattern(UUID id) {
        return MonthlyPeriodPattern.createWithId(MonthlyPeriodPatternId.of(id), TENANT_ID, "MP Pattern", 1);
    }

    @Nested
    @DisplayName("getDateInfo")
    class GetDateInfo {

        @Test
        @DisplayName("should resolve patterns from organization itself")
        void shouldResolveFromOrganization() {
            UUID orgUuid = UUID.randomUUID();
            UUID fyPatternUuid = UUID.randomUUID();
            UUID mpPatternUuid = UUID.randomUUID();

            Organization org = createOrg(orgUuid, null, fyPatternUuid, mpPatternUuid);
            when(organizationRepository.findById(OrganizationId.of(orgUuid))).thenReturn(Optional.of(org));
            when(fiscalYearPatternRepository.findById(FiscalYearPatternId.of(fyPatternUuid)))
                    .thenReturn(Optional.of(createFyPattern(fyPatternUuid)));
            when(monthlyPeriodPatternRepository.findById(MonthlyPeriodPatternId.of(mpPatternUuid)))
                    .thenReturn(Optional.of(createMpPattern(mpPatternUuid)));

            DateInfo result = service.getDateInfo(orgUuid, TEST_DATE);

            assertNotNull(result);
            assertEquals(fyPatternUuid, result.fiscalYearPatternId());
            assertEquals(mpPatternUuid, result.monthlyPeriodPatternId());
            assertEquals("organization:" + orgUuid, result.fiscalYearSource());
            assertEquals("organization:" + orgUuid, result.monthlyPeriodSource());
        }

        @Test
        @DisplayName("should resolve patterns from parent organization")
        void shouldResolveFromParentOrganization() {
            UUID parentUuid = UUID.randomUUID();
            UUID childUuid = UUID.randomUUID();
            UUID fyPatternUuid = UUID.randomUUID();
            UUID mpPatternUuid = UUID.randomUUID();

            Organization parent = createOrg(parentUuid, null, fyPatternUuid, mpPatternUuid);
            Organization child = createOrg(childUuid, parentUuid, null, null);

            when(organizationRepository.findById(OrganizationId.of(childUuid))).thenReturn(Optional.of(child));
            when(organizationRepository.findById(OrganizationId.of(parentUuid))).thenReturn(Optional.of(parent));
            when(fiscalYearPatternRepository.findById(FiscalYearPatternId.of(fyPatternUuid)))
                    .thenReturn(Optional.of(createFyPattern(fyPatternUuid)));
            when(monthlyPeriodPatternRepository.findById(MonthlyPeriodPatternId.of(mpPatternUuid)))
                    .thenReturn(Optional.of(createMpPattern(mpPatternUuid)));

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
            tenant.assignDefaultPatterns(tenantFyPatternUuid, tenantMpPatternUuid);
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            when(fiscalYearPatternRepository.findById(FiscalYearPatternId.of(tenantFyPatternUuid)))
                    .thenReturn(Optional.of(createFyPattern(tenantFyPatternUuid)));
            when(monthlyPeriodPatternRepository.findById(MonthlyPeriodPatternId.of(tenantMpPatternUuid)))
                    .thenReturn(Optional.of(createMpPattern(tenantMpPatternUuid)));

            DateInfo result = service.getDateInfo(orgUuid, TEST_DATE);

            assertEquals(tenantFyPatternUuid, result.fiscalYearPatternId());
            assertEquals(tenantMpPatternUuid, result.monthlyPeriodPatternId());
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

            when(systemDefaultSettingsRepository.getDefaultFiscalYearPattern())
                    .thenReturn(new SystemDefaultFiscalYearPattern(4, 1));
            when(systemDefaultSettingsRepository.getDefaultMonthlyPeriodPattern())
                    .thenReturn(new SystemDefaultMonthlyPeriodPattern(1));

            DateInfo result = service.getDateInfo(orgUuid, TEST_DATE);

            assertNull(result.fiscalYearPatternId());
            assertNull(result.monthlyPeriodPatternId());
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
    @DisplayName("getEffectivePatterns")
    class GetEffectivePatterns {

        @Test
        @DisplayName("should return organization source when org has patterns")
        void shouldReturnOrganizationSource() {
            UUID orgUuid = UUID.randomUUID();
            UUID fyPatternUuid = UUID.randomUUID();
            UUID mpPatternUuid = UUID.randomUUID();

            Organization org = createOrg(orgUuid, null, fyPatternUuid, mpPatternUuid);
            when(organizationRepository.findById(OrganizationId.of(orgUuid))).thenReturn(Optional.of(org));

            DateInfoService.EffectivePatterns result = service.getEffectivePatterns(orgUuid);

            assertEquals(fyPatternUuid, result.fiscalYearPatternId());
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

            DateInfoService.EffectivePatterns result = service.getEffectivePatterns(orgUuid);

            assertNull(result.fiscalYearPatternId());
            assertEquals("system", result.fiscalYearSource());
            assertNull(result.fiscalYearSourceName());
            assertNull(result.monthlyPeriodPatternId());
            assertEquals("system", result.monthlyPeriodSource());
            assertNull(result.monthlyPeriodSourceName());
        }
    }
}
