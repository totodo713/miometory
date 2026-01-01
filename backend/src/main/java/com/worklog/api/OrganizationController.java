package com.worklog.api;

import com.worklog.application.command.CreateOrganizationCommand;
import com.worklog.application.service.DateInfoService;
import com.worklog.application.service.OrganizationService;
import com.worklog.application.service.DateInfo;
import com.worklog.domain.organization.Organization;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for Organization operations.
 * 
 * Provides endpoints for organization management within a tenant.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final DateInfoService dateInfoService;

    public OrganizationController(OrganizationService organizationService, DateInfoService dateInfoService) {
        this.organizationService = organizationService;
        this.dateInfoService = dateInfoService;
    }

    /**
     * Creates a new organization.
     * 
     * POST /api/v1/tenants/{tenantId}/organizations
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrganization(
            @PathVariable UUID tenantId,
            @RequestBody CreateOrganizationRequest request) {
        
        CreateOrganizationCommand command = new CreateOrganizationCommand(
                tenantId,
                request.parentId(),
                request.code(),
                request.name(),
                request.level(),
                request.fiscalYearPatternId(),
                request.monthlyPeriodPatternId()
        );
        
        UUID organizationId = organizationService.createOrganization(command);
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", organizationId.toString());
        response.put("tenantId", tenantId.toString());
        response.put("parentId", request.parentId() != null ? request.parentId().toString() : null);
        response.put("code", request.code());
        response.put("name", request.name());
        response.put("level", request.level());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Gets an organization by ID.
     * 
     * GET /api/v1/tenants/{tenantId}/organizations/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getOrganization(
            @PathVariable UUID tenantId,
            @PathVariable UUID id) {
        
        Organization organization = organizationService.findById(id);
        if (organization == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Verify organization belongs to the tenant
        if (!organization.getTenantId().value().equals(tenantId)) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", organization.getId().value().toString());
        response.put("tenantId", organization.getTenantId().value().toString());
        response.put("parentId", organization.getParentId() != null ? organization.getParentId().value().toString() : null);
        response.put("code", organization.getCode().value());
        response.put("name", organization.getName());
        response.put("level", organization.getLevel());
        response.put("fiscalYearPatternId", organization.getFiscalYearPatternId() != null ? organization.getFiscalYearPatternId().toString() : null);
        response.put("monthlyPeriodPatternId", organization.getMonthlyPeriodPatternId() != null ? organization.getMonthlyPeriodPatternId().toString() : null);
        response.put("isActive", organization.isActive());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Updates an organization's name.
     * 
     * PATCH /api/v1/tenants/{tenantId}/organizations/{id}
     */
    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateOrganization(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @RequestBody UpdateOrganizationRequest request) {
        
        organizationService.updateOrganization(id, request.name());
        return ResponseEntity.noContent().build();
    }

    /**
     * Deactivates an organization.
     * 
     * POST /api/v1/tenants/{tenantId}/organizations/{id}/deactivate
     */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateOrganization(
            @PathVariable UUID tenantId,
            @PathVariable UUID id) {
        
        organizationService.deactivateOrganization(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Activates an organization.
     * 
     * POST /api/v1/tenants/{tenantId}/organizations/{id}/activate
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activateOrganization(
            @PathVariable UUID tenantId,
            @PathVariable UUID id) {
        
        organizationService.activateOrganization(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Assigns fiscal year and monthly period patterns to an organization.
     * 
     * POST /api/v1/tenants/{tenantId}/organizations/{id}/assign-patterns
     */
    @PostMapping("/{id}/assign-patterns")
    public ResponseEntity<Void> assignPatterns(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @RequestBody AssignPatternsRequest request) {
        
        organizationService.assignPatterns(id, request.fiscalYearPatternId(), request.monthlyPeriodPatternId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Calculates fiscal year and monthly period information for a given date.
     * 
     * POST /api/v1/tenants/{tenantId}/organizations/{id}/date-info
     */
    @PostMapping("/{id}/date-info")
    public ResponseEntity<Map<String, Object>> getDateInfo(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @RequestBody DateInfoRequest request) {
        
        DateInfo dateInfo = dateInfoService.getDateInfo(id, request.date());
        
        Map<String, Object> response = new HashMap<>();
        response.put("date", dateInfo.date().toString());
        response.put("fiscalYear", dateInfo.fiscalYear());
        response.put("fiscalYearStart", dateInfo.fiscalYearStart().toString());
        response.put("fiscalYearEnd", dateInfo.fiscalYearEnd().toString());
        response.put("monthlyPeriodStart", dateInfo.monthlyPeriodStart().toString());
        response.put("monthlyPeriodEnd", dateInfo.monthlyPeriodEnd().toString());
        response.put("fiscalYearPatternId", dateInfo.fiscalYearPatternId().toString());
        response.put("monthlyPeriodPatternId", dateInfo.monthlyPeriodPatternId().toString());
        response.put("organizationId", dateInfo.organizationId().toString());
        
        return ResponseEntity.ok(response);
    }

    // Request DTOs
    
    public record CreateOrganizationRequest(
        UUID parentId,
        String code,
        String name,
        int level,
        UUID fiscalYearPatternId,
        UUID monthlyPeriodPatternId
    ) {}
    
    public record UpdateOrganizationRequest(
        String name
    ) {}
    
    public record AssignPatternsRequest(
        UUID fiscalYearPatternId,
        UUID monthlyPeriodPatternId
    ) {}
    
    public record DateInfoRequest(
        LocalDate date
    ) {}
}
