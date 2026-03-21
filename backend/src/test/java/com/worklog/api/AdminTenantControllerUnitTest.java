package com.worklog.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.worklog.application.service.AdminTenantService;
import com.worklog.domain.fiscalyear.FiscalYearRule;
import com.worklog.domain.fiscalyear.FiscalYearRuleId;
import com.worklog.domain.monthlyperiod.MonthlyPeriodRule;
import com.worklog.domain.monthlyperiod.MonthlyPeriodRuleId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.Tenant;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.FiscalYearRuleRepository;
import com.worklog.infrastructure.repository.MonthlyPeriodRuleRepository;
import com.worklog.infrastructure.repository.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminTenantController (Unit)")
class AdminTenantControllerUnitTest {

    @Mock
    private AdminTenantService adminTenantService;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private FiscalYearRuleRepository fiscalYearRuleRepository;

    @Mock
    private MonthlyPeriodRuleRepository monthlyPeriodRuleRepository;

    private AdminTenantController controller;

    private static final UUID TENANT_UUID = UUID.randomUUID();
    private static final TenantId TENANT_ID = TenantId.of(TENANT_UUID);

    @BeforeEach
    void setUp() {
        controller = new AdminTenantController(
                adminTenantService, tenantRepository, fiscalYearRuleRepository, monthlyPeriodRuleRepository);
    }

    @Nested
    @DisplayName("GET /{id}/default-rules")
    class GetDefaultRules {

        @Test
        @DisplayName("should return default patterns for tenant")
        void shouldReturnDefaultPatterns() {
            UUID fyId = UUID.randomUUID();
            UUID mpId = UUID.randomUUID();
            Tenant tenant = Tenant.createWithId(TENANT_ID, "T001", "Test Tenant");
            tenant.assignDefaultRules(fyId, mpId);
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            var response = controller.getDefaultRules(TENANT_UUID);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(fyId, response.getBody().defaultFiscalYearRuleId());
            assertEquals(mpId, response.getBody().defaultMonthlyPeriodRuleId());
        }

        @Test
        @DisplayName("should return null patterns when none assigned")
        void shouldReturnNullWhenNoneAssigned() {
            Tenant tenant = Tenant.createWithId(TENANT_ID, "T001", "Test Tenant");
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            var response = controller.getDefaultRules(TENANT_UUID);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertNull(response.getBody().defaultFiscalYearRuleId());
            assertNull(response.getBody().defaultMonthlyPeriodRuleId());
        }

        @Test
        @DisplayName("should throw when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

            assertThrows(DomainException.class, () -> controller.getDefaultRules(TENANT_UUID));
        }
    }

    @Nested
    @DisplayName("PUT /{id}/default-rules")
    class UpdateDefaultRules {

        @Test
        @DisplayName("should update default patterns with valid owned patterns")
        void shouldUpdateWithValidPatterns() {
            UUID fyRuleId = UUID.randomUUID();
            UUID mpRuleId = UUID.randomUUID();

            Tenant tenant = Tenant.createWithId(TENANT_ID, "T001", "Test Tenant");
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            FiscalYearRule fyRule = mock(FiscalYearRule.class);
            when(fyRule.getTenantId()).thenReturn(TENANT_ID);
            when(fiscalYearRuleRepository.findById(FiscalYearRuleId.of(fyRuleId)))
                    .thenReturn(Optional.of(fyRule));

            MonthlyPeriodRule mpRule = mock(MonthlyPeriodRule.class);
            when(mpRule.getTenantId()).thenReturn(TENANT_ID);
            when(monthlyPeriodRuleRepository.findById(MonthlyPeriodRuleId.of(mpRuleId)))
                    .thenReturn(Optional.of(mpRule));

            var request = new AdminTenantController.UpdateDefaultRulesRequest(fyRuleId, mpRuleId);
            var response = controller.updateDefaultRules(TENANT_UUID, request);

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            verify(tenantRepository).save(tenant);
        }

        @Test
        @DisplayName("should update with null patterns (clear defaults)")
        void shouldUpdateWithNullPatterns() {
            Tenant tenant = Tenant.createWithId(TENANT_ID, "T001", "Test Tenant");
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            var request = new AdminTenantController.UpdateDefaultRulesRequest(null, null);
            var response = controller.updateDefaultRules(TENANT_UUID, request);

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            verify(tenantRepository).save(tenant);
        }

        @Test
        @DisplayName("should reject fiscal year pattern not belonging to tenant")
        void shouldRejectFyPatternNotOwned() {
            UUID fyRuleId = UUID.randomUUID();
            TenantId otherTenantId = TenantId.of(UUID.randomUUID());

            Tenant tenant = Tenant.createWithId(TENANT_ID, "T001", "Test Tenant");
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            FiscalYearRule fyRule = mock(FiscalYearRule.class);
            when(fyRule.getTenantId()).thenReturn(otherTenantId);
            when(fiscalYearRuleRepository.findById(FiscalYearRuleId.of(fyRuleId)))
                    .thenReturn(Optional.of(fyRule));

            var request = new AdminTenantController.UpdateDefaultRulesRequest(fyRuleId, null);

            assertThrows(DomainException.class, () -> controller.updateDefaultRules(TENANT_UUID, request));
            verify(tenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject monthly period pattern not belonging to tenant")
        void shouldRejectMpPatternNotOwned() {
            UUID mpRuleId = UUID.randomUUID();
            TenantId otherTenantId = TenantId.of(UUID.randomUUID());

            Tenant tenant = Tenant.createWithId(TENANT_ID, "T001", "Test Tenant");
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            MonthlyPeriodRule mpRule = mock(MonthlyPeriodRule.class);
            when(mpRule.getTenantId()).thenReturn(otherTenantId);
            when(monthlyPeriodRuleRepository.findById(MonthlyPeriodRuleId.of(mpRuleId)))
                    .thenReturn(Optional.of(mpRule));

            var request = new AdminTenantController.UpdateDefaultRulesRequest(null, mpRuleId);

            assertThrows(DomainException.class, () -> controller.updateDefaultRules(TENANT_UUID, request));
            verify(tenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject non-existent fiscal year pattern")
        void shouldRejectNonExistentFyPattern() {
            UUID fyRuleId = UUID.randomUUID();

            Tenant tenant = Tenant.createWithId(TENANT_ID, "T001", "Test Tenant");
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
            when(fiscalYearRuleRepository.findById(FiscalYearRuleId.of(fyRuleId)))
                    .thenReturn(Optional.empty());

            var request = new AdminTenantController.UpdateDefaultRulesRequest(fyRuleId, null);

            assertThrows(DomainException.class, () -> controller.updateDefaultRules(TENANT_UUID, request));
        }

        @Test
        @DisplayName("should throw when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

            var request = new AdminTenantController.UpdateDefaultRulesRequest(null, null);

            assertThrows(DomainException.class, () -> controller.updateDefaultRules(TENANT_UUID, request));
        }
    }
}
