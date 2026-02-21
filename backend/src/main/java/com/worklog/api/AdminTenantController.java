package com.worklog.api;

import com.worklog.application.service.AdminTenantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    public AdminTenantController(AdminTenantService adminTenantService) {
        this.adminTenantService = adminTenantService;
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

    // Request/Response DTOs

    public record CreateTenantRequest(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 100) String name) {}

    public record UpdateTenantRequest(
            @NotBlank @Size(max = 100) String name) {}

    public record CreateTenantResponse(String id) {}
}
