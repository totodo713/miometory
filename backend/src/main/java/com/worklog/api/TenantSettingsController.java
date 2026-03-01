package com.worklog.api;

import com.worklog.application.service.TenantAccessValidator;
import com.worklog.domain.fiscalyear.FiscalYearPatternId;
import com.worklog.domain.monthlyperiod.MonthlyPeriodPatternId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.Tenant;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.FiscalYearPatternRepository;
import com.worklog.infrastructure.repository.MonthlyPeriodPatternRepository;
import com.worklog.infrastructure.repository.TenantRepository;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for tenant-admin self-service settings.
 *
 * <p>Unlike AdminTenantController (which requires SYSTEM_ADMIN and takes tenantId in URL),
 * this controller resolves the tenant from the authenticated user's context.
 */
@RestController
@RequestMapping("/api/v1/tenant-settings")
public class TenantSettingsController {

    private final TenantRepository tenantRepository;
    private final FiscalYearPatternRepository fiscalYearPatternRepository;
    private final MonthlyPeriodPatternRepository monthlyPeriodPatternRepository;
    private final TenantAccessValidator tenantAccessValidator;

    public TenantSettingsController(
            TenantRepository tenantRepository,
            FiscalYearPatternRepository fiscalYearPatternRepository,
            MonthlyPeriodPatternRepository monthlyPeriodPatternRepository,
            TenantAccessValidator tenantAccessValidator) {
        this.tenantRepository = tenantRepository;
        this.fiscalYearPatternRepository = fiscalYearPatternRepository;
        this.monthlyPeriodPatternRepository = monthlyPeriodPatternRepository;
        this.tenantAccessValidator = tenantAccessValidator;
    }

    @GetMapping("/default-patterns")
    @PreAuthorize("hasPermission(null, 'tenant_settings.view')")
    public ResponseEntity<DefaultPatternsResponse> getDefaultPatterns(Authentication auth) {
        UUID tenantId = tenantAccessValidator.resolveUserTenantId(auth);
        Tenant tenant = tenantRepository
                .findById(TenantId.of(tenantId))
                .orElseThrow(() -> new DomainException("TENANT_NOT_FOUND", "Tenant not found"));
        return ResponseEntity.ok(new DefaultPatternsResponse(
                tenant.getDefaultFiscalYearPatternId(), tenant.getDefaultMonthlyPeriodPatternId()));
    }

    @PutMapping("/default-patterns")
    @PreAuthorize("hasPermission(null, 'tenant_settings.manage')")
    public ResponseEntity<Void> updateDefaultPatterns(
            Authentication auth, @RequestBody UpdateDefaultPatternsRequest request) {
        UUID tenantId = tenantAccessValidator.resolveUserTenantId(auth);
        Tenant tenant = tenantRepository
                .findById(TenantId.of(tenantId))
                .orElseThrow(() -> new DomainException("TENANT_NOT_FOUND", "Tenant not found"));

        if (request.defaultFiscalYearPatternId() != null) {
            fiscalYearPatternRepository
                    .findById(FiscalYearPatternId.of(request.defaultFiscalYearPatternId()))
                    .filter(p -> p.getTenantId().value().equals(tenantId))
                    .orElseThrow(() -> new DomainException(
                            "PATTERN_NOT_OWNED", "Fiscal year pattern does not belong to this tenant"));
        }
        if (request.defaultMonthlyPeriodPatternId() != null) {
            monthlyPeriodPatternRepository
                    .findById(MonthlyPeriodPatternId.of(request.defaultMonthlyPeriodPatternId()))
                    .filter(p -> p.getTenantId().value().equals(tenantId))
                    .orElseThrow(() -> new DomainException(
                            "PATTERN_NOT_OWNED", "Monthly period pattern does not belong to this tenant"));
        }

        tenant.assignDefaultPatterns(request.defaultFiscalYearPatternId(), request.defaultMonthlyPeriodPatternId());
        tenantRepository.save(tenant);
        return ResponseEntity.noContent().build();
    }

    public record DefaultPatternsResponse(UUID defaultFiscalYearPatternId, UUID defaultMonthlyPeriodPatternId) {}

    public record UpdateDefaultPatternsRequest(UUID defaultFiscalYearPatternId, UUID defaultMonthlyPeriodPatternId) {}
}
