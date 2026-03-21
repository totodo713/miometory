package com.worklog.api;

import com.worklog.application.service.TenantAccessValidator;
import com.worklog.domain.fiscalyear.FiscalYearRuleId;
import com.worklog.domain.monthlyperiod.MonthlyPeriodRuleId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.Tenant;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.FiscalYearRuleRepository;
import com.worklog.infrastructure.repository.MonthlyPeriodRuleRepository;
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
    private final FiscalYearRuleRepository fiscalYearRuleRepository;
    private final MonthlyPeriodRuleRepository monthlyPeriodRuleRepository;
    private final TenantAccessValidator tenantAccessValidator;

    public TenantSettingsController(
            TenantRepository tenantRepository,
            FiscalYearRuleRepository fiscalYearRuleRepository,
            MonthlyPeriodRuleRepository monthlyPeriodRuleRepository,
            TenantAccessValidator tenantAccessValidator) {
        this.tenantRepository = tenantRepository;
        this.fiscalYearRuleRepository = fiscalYearRuleRepository;
        this.monthlyPeriodRuleRepository = monthlyPeriodRuleRepository;
        this.tenantAccessValidator = tenantAccessValidator;
    }

    @GetMapping("/default-rules")
    @PreAuthorize("hasPermission(null, 'tenant_settings.view')")
    public ResponseEntity<DefaultRulesResponse> getDefaultRules(Authentication auth) {
        UUID tenantId = tenantAccessValidator.resolveUserTenantId(auth);
        Tenant tenant = tenantRepository
                .findById(TenantId.of(tenantId))
                .orElseThrow(() -> new DomainException("TENANT_NOT_FOUND", "Tenant not found"));
        return ResponseEntity.ok(
                new DefaultRulesResponse(tenant.getDefaultFiscalYearRuleId(), tenant.getDefaultMonthlyPeriodRuleId()));
    }

    @PutMapping("/default-rules")
    @PreAuthorize("hasPermission(null, 'tenant_settings.manage')")
    public ResponseEntity<Void> updateDefaultRules(
            Authentication auth, @RequestBody UpdateDefaultRulesRequest request) {
        UUID tenantId = tenantAccessValidator.resolveUserTenantId(auth);
        Tenant tenant = tenantRepository
                .findById(TenantId.of(tenantId))
                .orElseThrow(() -> new DomainException("TENANT_NOT_FOUND", "Tenant not found"));

        if (request.defaultFiscalYearRuleId() != null) {
            fiscalYearRuleRepository
                    .findById(FiscalYearRuleId.of(request.defaultFiscalYearRuleId()))
                    .filter(p -> p.getTenantId().value().equals(tenantId))
                    .orElseThrow(() ->
                            new DomainException("RULE_NOT_OWNED", "Fiscal year rule does not belong to this tenant"));
        }
        if (request.defaultMonthlyPeriodRuleId() != null) {
            monthlyPeriodRuleRepository
                    .findById(MonthlyPeriodRuleId.of(request.defaultMonthlyPeriodRuleId()))
                    .filter(p -> p.getTenantId().value().equals(tenantId))
                    .orElseThrow(() -> new DomainException(
                            "RULE_NOT_OWNED", "Monthly period rule does not belong to this tenant"));
        }

        tenant.assignDefaultRules(request.defaultFiscalYearRuleId(), request.defaultMonthlyPeriodRuleId());
        tenantRepository.save(tenant);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/attendance-times")
    @PreAuthorize("hasPermission(null, 'tenant_settings.view')")
    public ResponseEntity<TenantAttendanceTimesResponse> getAttendanceTimes(Authentication auth) {
        UUID tenantId = tenantAccessValidator.resolveUserTenantId(auth);
        Tenant tenant = tenantRepository
                .findById(TenantId.of(tenantId))
                .orElseThrow(() -> new DomainException("TENANT_NOT_FOUND", "Tenant not found"));
        return ResponseEntity.ok(new TenantAttendanceTimesResponse(
                tenant.getDefaultStartTime() != null
                        ? tenant.getDefaultStartTime().toString()
                        : null,
                tenant.getDefaultEndTime() != null ? tenant.getDefaultEndTime().toString() : null));
    }

    @PutMapping("/attendance-times")
    @PreAuthorize("hasPermission(null, 'tenant_settings.manage')")
    public ResponseEntity<Void> updateAttendanceTimes(
            Authentication auth, @RequestBody UpdateAttendanceTimesRequest request) {
        UUID tenantId = tenantAccessValidator.resolveUserTenantId(auth);
        Tenant tenant = tenantRepository
                .findById(TenantId.of(tenantId))
                .orElseThrow(() -> new DomainException("TENANT_NOT_FOUND", "Tenant not found"));

        java.time.LocalTime startTime =
                request.startTime() != null ? java.time.LocalTime.parse(request.startTime()) : null;
        java.time.LocalTime endTime = request.endTime() != null ? java.time.LocalTime.parse(request.endTime()) : null;

        tenant.assignDefaultAttendanceTimes(startTime, endTime);
        tenantRepository.save(tenant);
        return ResponseEntity.noContent().build();
    }

    public record DefaultRulesResponse(UUID defaultFiscalYearRuleId, UUID defaultMonthlyPeriodRuleId) {}

    public record UpdateDefaultRulesRequest(UUID defaultFiscalYearRuleId, UUID defaultMonthlyPeriodRuleId) {}

    public record TenantAttendanceTimesResponse(String startTime, String endTime) {}

    public record UpdateAttendanceTimesRequest(String startTime, String endTime) {}
}
