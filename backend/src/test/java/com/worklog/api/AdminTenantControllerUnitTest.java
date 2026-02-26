package com.worklog.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.worklog.application.service.AdminTenantService;
import com.worklog.domain.fiscalyear.FiscalYearPattern;
import com.worklog.domain.fiscalyear.FiscalYearPatternId;
import com.worklog.domain.monthlyperiod.MonthlyPeriodPattern;
import com.worklog.domain.monthlyperiod.MonthlyPeriodPatternId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.Tenant;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.FiscalYearPatternRepository;
import com.worklog.infrastructure.repository.MonthlyPeriodPatternRepository;
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
    private FiscalYearPatternRepository fiscalYearPatternRepository;

    @Mock
    private MonthlyPeriodPatternRepository monthlyPeriodPatternRepository;

    private AdminTenantController controller;

    private static final UUID TENANT_UUID = UUID.randomUUID();
    private static final TenantId TENANT_ID = TenantId.of(TENANT_UUID);

    @BeforeEach
    void setUp() {
        controller = new AdminTenantController(
                adminTenantService, tenantRepository, fiscalYearPatternRepository, monthlyPeriodPatternRepository);
    }

    @Nested
    @DisplayName("GET /{id}/default-patterns")
    class GetDefaultPatterns {

        @Test
        @DisplayName("should return default patterns for tenant")
        void shouldReturnDefaultPatterns() {
            UUID fyId = UUID.randomUUID();
            UUID mpId = UUID.randomUUID();
            Tenant tenant = Tenant.createWithId(TENANT_ID, "T001", "Test Tenant");
            tenant.assignDefaultPatterns(fyId, mpId);
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            var response = controller.getDefaultPatterns(TENANT_UUID);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(fyId, response.getBody().defaultFiscalYearPatternId());
            assertEquals(mpId, response.getBody().defaultMonthlyPeriodPatternId());
        }

        @Test
        @DisplayName("should return null patterns when none assigned")
        void shouldReturnNullWhenNoneAssigned() {
            Tenant tenant = Tenant.createWithId(TENANT_ID, "T001", "Test Tenant");
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            var response = controller.getDefaultPatterns(TENANT_UUID);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertNull(response.getBody().defaultFiscalYearPatternId());
            assertNull(response.getBody().defaultMonthlyPeriodPatternId());
        }

        @Test
        @DisplayName("should throw when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

            assertThrows(DomainException.class, () -> controller.getDefaultPatterns(TENANT_UUID));
        }
    }

    @Nested
    @DisplayName("PUT /{id}/default-patterns")
    class UpdateDefaultPatterns {

        @Test
        @DisplayName("should update default patterns with valid owned patterns")
        void shouldUpdateWithValidPatterns() {
            UUID fyPatternId = UUID.randomUUID();
            UUID mpPatternId = UUID.randomUUID();

            Tenant tenant = Tenant.createWithId(TENANT_ID, "T001", "Test Tenant");
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            FiscalYearPattern fyPattern = mock(FiscalYearPattern.class);
            when(fyPattern.getTenantId()).thenReturn(TENANT_ID);
            when(fiscalYearPatternRepository.findById(FiscalYearPatternId.of(fyPatternId)))
                    .thenReturn(Optional.of(fyPattern));

            MonthlyPeriodPattern mpPattern = mock(MonthlyPeriodPattern.class);
            when(mpPattern.getTenantId()).thenReturn(TENANT_ID);
            when(monthlyPeriodPatternRepository.findById(MonthlyPeriodPatternId.of(mpPatternId)))
                    .thenReturn(Optional.of(mpPattern));

            var request = new AdminTenantController.UpdateDefaultPatternsRequest(fyPatternId, mpPatternId);
            var response = controller.updateDefaultPatterns(TENANT_UUID, request);

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            verify(tenantRepository).save(tenant);
        }

        @Test
        @DisplayName("should update with null patterns (clear defaults)")
        void shouldUpdateWithNullPatterns() {
            Tenant tenant = Tenant.createWithId(TENANT_ID, "T001", "Test Tenant");
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            var request = new AdminTenantController.UpdateDefaultPatternsRequest(null, null);
            var response = controller.updateDefaultPatterns(TENANT_UUID, request);

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            verify(tenantRepository).save(tenant);
        }

        @Test
        @DisplayName("should reject fiscal year pattern not belonging to tenant")
        void shouldRejectFyPatternNotOwned() {
            UUID fyPatternId = UUID.randomUUID();
            TenantId otherTenantId = TenantId.of(UUID.randomUUID());

            Tenant tenant = Tenant.createWithId(TENANT_ID, "T001", "Test Tenant");
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            FiscalYearPattern fyPattern = mock(FiscalYearPattern.class);
            when(fyPattern.getTenantId()).thenReturn(otherTenantId);
            when(fiscalYearPatternRepository.findById(FiscalYearPatternId.of(fyPatternId)))
                    .thenReturn(Optional.of(fyPattern));

            var request = new AdminTenantController.UpdateDefaultPatternsRequest(fyPatternId, null);

            assertThrows(DomainException.class, () -> controller.updateDefaultPatterns(TENANT_UUID, request));
            verify(tenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject monthly period pattern not belonging to tenant")
        void shouldRejectMpPatternNotOwned() {
            UUID mpPatternId = UUID.randomUUID();
            TenantId otherTenantId = TenantId.of(UUID.randomUUID());

            Tenant tenant = Tenant.createWithId(TENANT_ID, "T001", "Test Tenant");
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            MonthlyPeriodPattern mpPattern = mock(MonthlyPeriodPattern.class);
            when(mpPattern.getTenantId()).thenReturn(otherTenantId);
            when(monthlyPeriodPatternRepository.findById(MonthlyPeriodPatternId.of(mpPatternId)))
                    .thenReturn(Optional.of(mpPattern));

            var request = new AdminTenantController.UpdateDefaultPatternsRequest(null, mpPatternId);

            assertThrows(DomainException.class, () -> controller.updateDefaultPatterns(TENANT_UUID, request));
            verify(tenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject non-existent fiscal year pattern")
        void shouldRejectNonExistentFyPattern() {
            UUID fyPatternId = UUID.randomUUID();

            Tenant tenant = Tenant.createWithId(TENANT_ID, "T001", "Test Tenant");
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
            when(fiscalYearPatternRepository.findById(FiscalYearPatternId.of(fyPatternId)))
                    .thenReturn(Optional.empty());

            var request = new AdminTenantController.UpdateDefaultPatternsRequest(fyPatternId, null);

            assertThrows(DomainException.class, () -> controller.updateDefaultPatterns(TENANT_UUID, request));
        }

        @Test
        @DisplayName("should throw when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

            var request = new AdminTenantController.UpdateDefaultPatternsRequest(null, null);

            assertThrows(DomainException.class, () -> controller.updateDefaultPatterns(TENANT_UUID, request));
        }
    }
}
