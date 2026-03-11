package com.worklog.api;

import com.worklog.application.service.AdminTenantService;
import com.worklog.domain.fiscalyear.FiscalYearRuleId;
import com.worklog.domain.monthlyperiod.MonthlyPeriodRuleId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.Tenant;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.FiscalYearRuleRepository;
import com.worklog.infrastructure.repository.MonthlyPeriodRuleRepository;
import com.worklog.infrastructure.repository.TenantRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for system-wide tenant management.
 * Accessible only to SYSTEM_ADMIN users.
 */
@RestController
@RequestMapping("/api/v1/admin/tenants")
public class AdminTenantController {

    private final AdminTenantService adminTenantService;
    private final TenantRepository tenantRepository;
    private final FiscalYearRuleRepository fiscalYearRuleRepository;
    private final MonthlyPeriodRuleRepository monthlyPeriodRuleRepository;

    public AdminTenantController(
            AdminTenantService adminTenantService,
            TenantRepository tenantRepository,
            FiscalYearRuleRepository fiscalYearRuleRepository,
            MonthlyPeriodRuleRepository monthlyPeriodRuleRepository) {
        this.adminTenantService = adminTenantService;
        this.tenantRepository = tenantRepository;
        this.fiscalYearRuleRepository = fiscalYearRuleRepository;
        this.monthlyPeriodRuleRepository = monthlyPeriodRuleRepository;
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'tenant.view')")
    public AdminTenantService.TenantPage listTenants(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int effectiveSize = Math.min(size, 100);
        return adminTenantService.listTenants(status, page, effectiveSize);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'tenant.create')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateTenantResponse createTenant(@RequestBody @Valid CreateTenantRequest request) {
        UUID id = adminTenantService.createTenant(request.code(), request.name());
        return new CreateTenantResponse(id.toString());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'tenant.update')")
    public ResponseEntity<Void> updateTenant(@PathVariable UUID id, @RequestBody @Valid UpdateTenantRequest request) {
        adminTenantService.updateTenant(id, request.name());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasPermission(null, 'tenant.deactivate')")
    public ResponseEntity<Void> deactivateTenant(@PathVariable UUID id) {
        adminTenantService.deactivateTenant(id);
        return ResponseEntity.ok().build();
    }

    // Intentionally reuses *.deactivate permission for both activate/deactivate
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasPermission(null, 'tenant.deactivate')")
    public ResponseEntity<Void> activateTenant(@PathVariable UUID id) {
        adminTenantService.activateTenant(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{tenantId}/bootstrap")
    @PreAuthorize("hasPermission(null, 'tenant.create')")
    @ResponseStatus(HttpStatus.CREATED)
    public BootstrapTenantResponse bootstrapTenant(
            @PathVariable UUID tenantId, @RequestBody @Valid BootstrapTenantRequest request) {
        var result = adminTenantService.bootstrapTenant(tenantId, request);
        return new BootstrapTenantResponse(result);
    }

    @GetMapping("/{id}/default-rules")
    @PreAuthorize("hasPermission(null, 'tenant.view')")
    public ResponseEntity<DefaultRulesResponse> getDefaultRules(@PathVariable UUID id) {
        Tenant tenant = tenantRepository
                .findById(TenantId.of(id))
                .orElseThrow(() -> new DomainException("TENANT_NOT_FOUND", "Tenant not found"));
        return ResponseEntity.ok(
                new DefaultRulesResponse(tenant.getDefaultFiscalYearRuleId(), tenant.getDefaultMonthlyPeriodRuleId()));
    }

    @PutMapping("/{id}/default-rules")
    @PreAuthorize("hasPermission(null, 'tenant.update')")
    public ResponseEntity<Void> updateDefaultRules(
            @PathVariable UUID id, @RequestBody @Valid UpdateDefaultRulesRequest request) {
        Tenant tenant = tenantRepository
                .findById(TenantId.of(id))
                .orElseThrow(() -> new DomainException("TENANT_NOT_FOUND", "Tenant not found"));

        // Validate that rules belong to this tenant (if provided)
        if (request.defaultFiscalYearRuleId() != null) {
            fiscalYearRuleRepository
                    .findById(FiscalYearRuleId.of(request.defaultFiscalYearRuleId()))
                    .filter(p -> p.getTenantId().value().equals(id))
                    .orElseThrow(() ->
                            new DomainException("RULE_NOT_OWNED", "Fiscal year rule does not belong to this tenant"));
        }
        if (request.defaultMonthlyPeriodRuleId() != null) {
            monthlyPeriodRuleRepository
                    .findById(MonthlyPeriodRuleId.of(request.defaultMonthlyPeriodRuleId()))
                    .filter(p -> p.getTenantId().value().equals(id))
                    .orElseThrow(() -> new DomainException(
                            "RULE_NOT_OWNED", "Monthly period rule does not belong to this tenant"));
        }

        tenant.assignDefaultRules(request.defaultFiscalYearRuleId(), request.defaultMonthlyPeriodRuleId());
        tenantRepository.save(tenant);
        return ResponseEntity.noContent().build();
    }

    // Request/Response DTOs

    public record CreateTenantRequest(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 100) String name) {}

    public record UpdateTenantRequest(
            @NotBlank @Size(max = 100) String name) {}

    public record CreateTenantResponse(String id) {}

    // Bootstrap DTOs

    public record BootstrapTenantRequest(
            @Valid @NotEmpty List<BootstrapOrganization> organizations,
            @Valid @NotEmpty List<BootstrapMember> members) {}

    public record BootstrapOrganization(
            @NotBlank String code,
            @NotBlank String name,
            UUID parentId,
            UUID fiscalYearRuleId,
            UUID monthlyPeriodRuleId) {}

    public record BootstrapMember(
            @NotBlank @Email String email,
            @NotBlank String displayName,
            @NotBlank String organizationCode,
            boolean tenantAdmin) {}

    public record BootstrapTenantResponse(BootstrapResult result) {}

    public record BootstrapResult(List<CreatedOrganization> organizations, List<CreatedMember> members) {}

    public record CreatedOrganization(UUID id, String code) {}

    public record CreatedMember(String id, String email, String temporaryPassword) {}

    public record DefaultRulesResponse(UUID defaultFiscalYearRuleId, UUID defaultMonthlyPeriodRuleId) {}

    public record UpdateDefaultRulesRequest(UUID defaultFiscalYearRuleId, UUID defaultMonthlyPeriodRuleId) {}
}
