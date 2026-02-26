package com.worklog.api;

import com.worklog.application.service.AdminTenantService;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.Tenant;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.FiscalYearPatternRepository;
import com.worklog.infrastructure.repository.MonthlyPeriodPatternRepository;
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
    private final FiscalYearPatternRepository fiscalYearPatternRepository;
    private final MonthlyPeriodPatternRepository monthlyPeriodPatternRepository;

    public AdminTenantController(
            AdminTenantService adminTenantService,
            TenantRepository tenantRepository,
            FiscalYearPatternRepository fiscalYearPatternRepository,
            MonthlyPeriodPatternRepository monthlyPeriodPatternRepository) {
        this.adminTenantService = adminTenantService;
        this.tenantRepository = tenantRepository;
        this.fiscalYearPatternRepository = fiscalYearPatternRepository;
        this.monthlyPeriodPatternRepository = monthlyPeriodPatternRepository;
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

    @GetMapping("/{id}/default-patterns")
    @PreAuthorize("hasPermission(null, 'tenant.view')")
    public ResponseEntity<DefaultPatternsResponse> getDefaultPatterns(@PathVariable UUID id) {
        Tenant tenant = tenantRepository
                .findById(TenantId.of(id))
                .orElseThrow(() -> new DomainException("TENANT_NOT_FOUND", "Tenant not found"));
        return ResponseEntity.ok(new DefaultPatternsResponse(
                tenant.getDefaultFiscalYearPatternId(), tenant.getDefaultMonthlyPeriodPatternId()));
    }

    @PutMapping("/{id}/default-patterns")
    @PreAuthorize("hasPermission(null, 'tenant.update')")
    public ResponseEntity<Void> updateDefaultPatterns(
            @PathVariable UUID id, @RequestBody @Valid UpdateDefaultPatternsRequest request) {
        Tenant tenant = tenantRepository
                .findById(TenantId.of(id))
                .orElseThrow(() -> new DomainException("TENANT_NOT_FOUND", "Tenant not found"));

        // Validate that patterns belong to this tenant (if provided)
        if (request.defaultFiscalYearPatternId() != null) {
            var pattern = fiscalYearPatternRepository.findByTenantId(id).stream()
                    .filter(p -> p.getId().value().equals(request.defaultFiscalYearPatternId()))
                    .findFirst()
                    .orElseThrow(() -> new DomainException(
                            "PATTERN_NOT_OWNED", "Fiscal year pattern does not belong to this tenant"));
        }
        if (request.defaultMonthlyPeriodPatternId() != null) {
            var pattern = monthlyPeriodPatternRepository.findByTenantId(id).stream()
                    .filter(p -> p.getId().value().equals(request.defaultMonthlyPeriodPatternId()))
                    .findFirst()
                    .orElseThrow(() -> new DomainException(
                            "PATTERN_NOT_OWNED", "Monthly period pattern does not belong to this tenant"));
        }

        tenant.assignDefaultPatterns(request.defaultFiscalYearPatternId(), request.defaultMonthlyPeriodPatternId());
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
            UUID fiscalYearPatternId,
            UUID monthlyPeriodPatternId) {}

    public record BootstrapMember(
            @NotBlank @Email String email,
            @NotBlank String displayName,
            @NotBlank String organizationCode,
            boolean tenantAdmin) {}

    public record BootstrapTenantResponse(BootstrapResult result) {}

    public record BootstrapResult(List<CreatedOrganization> organizations, List<CreatedMember> members) {}

    public record CreatedOrganization(UUID id, String code) {}

    public record CreatedMember(String id, String email, String temporaryPassword) {}

    public record DefaultPatternsResponse(UUID defaultFiscalYearPatternId, UUID defaultMonthlyPeriodPatternId) {}

    public record UpdateDefaultPatternsRequest(UUID defaultFiscalYearPatternId, UUID defaultMonthlyPeriodPatternId) {}
}
