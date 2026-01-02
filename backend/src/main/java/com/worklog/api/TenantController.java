package com.worklog.api;

import com.worklog.application.command.CreateTenantCommand;
import com.worklog.application.service.TenantService;
import com.worklog.domain.tenant.Tenant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for Tenant operations.
 * 
 * Provides endpoints for tenant management.
 */
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * Creates a new tenant.
     * 
     * POST /api/v1/tenants
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTenant(@RequestBody CreateTenantRequest request) {
        CreateTenantCommand command = new CreateTenantCommand(request.code(), request.name());
        UUID tenantId = tenantService.createTenant(command);
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", tenantId.toString());
        response.put("code", request.code());
        response.put("name", request.name());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Gets a tenant by ID.
     * 
     * GET /api/v1/tenants/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTenant(@PathVariable UUID id) {
        Tenant tenant = tenantService.findById(id);
        if (tenant == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", tenant.getId().value().toString());
        response.put("code", tenant.getCode().value());
        response.put("name", tenant.getName());
        response.put("status", tenant.getStatus().toString());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Updates a tenant's name.
     * 
     * PATCH /api/v1/tenants/{id}
     */
    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateTenant(@PathVariable UUID id, @RequestBody UpdateTenantRequest request) {
        tenantService.updateTenant(id, request.name());
        return ResponseEntity.noContent().build();
    }

    /**
     * Deactivates a tenant.
     * 
     * POST /api/v1/tenants/{id}/deactivate
     */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateTenant(@PathVariable UUID id) {
        tenantService.deactivateTenant(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Activates a tenant.
     * 
     * POST /api/v1/tenants/{id}/activate
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activateTenant(@PathVariable UUID id) {
        tenantService.activateTenant(id);
        return ResponseEntity.noContent().build();
    }

    // Request DTOs
    
    public record CreateTenantRequest(
        String code,
        String name
    ) {}
    
    public record UpdateTenantRequest(
        String name
    ) {}
}
