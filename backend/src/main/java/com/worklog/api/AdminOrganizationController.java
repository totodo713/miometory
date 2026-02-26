package com.worklog.api;

import com.worklog.application.command.CreateOrganizationCommand;
import com.worklog.application.service.AdminOrganizationService;
import com.worklog.application.service.DateInfoService;
import com.worklog.application.service.DateInfoService.EffectivePatterns;
import com.worklog.application.service.UserContextService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for tenant-scoped organization management.
 */
@RestController
@RequestMapping("/api/v1/admin/organizations")
@Validated
public class AdminOrganizationController {

    private final AdminOrganizationService service;
    private final UserContextService userContextService;
    private final DateInfoService dateInfoService;

    public AdminOrganizationController(
            AdminOrganizationService service, UserContextService userContextService, DateInfoService dateInfoService) {
        this.service = service;
        this.userContextService = userContextService;
        this.dateInfoService = dateInfoService;
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'organization.view')")
    public AdminOrganizationService.OrganizationPage listOrganizations(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) UUID parentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            Authentication authentication) {
        UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
        return service.listOrganizations(tenantId, search, isActive, parentId, page, Math.min(size, 100));
    }

    @GetMapping("/tree")
    @PreAuthorize("hasPermission(null, 'organization.view')")
    public List<AdminOrganizationService.OrganizationTreeNode> getOrganizationTree(
            @RequestParam(defaultValue = "false") boolean includeInactive, Authentication authentication) {
        UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
        return service.getOrganizationTree(tenantId, includeInactive);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'organization.create')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrganizationResponse createOrganization(
            @RequestBody @Valid CreateOrganizationRequest request, Authentication authentication) {
        UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());

        var command =
                new CreateOrganizationCommand(tenantId, request.parentId(), request.code(), request.name(), null, null);
        UUID id = service.createOrganization(command);
        return new CreateOrganizationResponse(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'organization.update')")
    public ResponseEntity<Void> updateOrganization(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateOrganizationRequest request,
            Authentication authentication) {
        UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
        service.updateOrganization(id, tenantId, request.name());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasPermission(null, 'organization.deactivate')")
    public DeactivateResponse deactivateOrganization(@PathVariable UUID id, Authentication authentication) {
        UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
        List<String> warnings = service.deactivateOrganization(id, tenantId);
        return new DeactivateResponse(warnings);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasPermission(null, 'organization.deactivate')")
    public ResponseEntity<Void> activateOrganization(@PathVariable UUID id, Authentication authentication) {
        UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
        service.activateOrganization(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/patterns")
    @PreAuthorize("hasPermission(null, 'organization.update')")
    public ResponseEntity<Void> assignPatterns(
            @PathVariable UUID id, @RequestBody @Valid AssignPatternsRequest request, Authentication authentication) {
        UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
        service.assignPatterns(id, tenantId, request.fiscalYearPatternId(), request.monthlyPeriodPatternId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasPermission(null, 'organization.view')")
    public AdminOrganizationService.OrganizationMemberPage listOrganizationMembers(
            @PathVariable UUID id,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            Authentication authentication) {
        UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
        return service.listMembersByOrganization(id, tenantId, page, Math.min(size, 100), isActive);
    }

    @GetMapping("/{id}/effective-patterns")
    @PreAuthorize("hasPermission(null, 'organization.view')")
    public ResponseEntity<EffectivePatterns> getEffectivePatterns(@PathVariable UUID id) {
        EffectivePatterns patterns = dateInfoService.getEffectivePatterns(id);
        return ResponseEntity.ok(patterns);
    }

    // Request/Response DTOs

    public record CreateOrganizationRequest(
            @NotBlank @Size(max = 32) String code,
            @NotBlank @Size(max = 256) String name,
            UUID parentId) {}

    public record UpdateOrganizationRequest(
            @NotBlank @Size(max = 256) String name) {}

    public record CreateOrganizationResponse(UUID id) {}

    public record DeactivateResponse(List<String> warnings) {}

    public record AssignPatternsRequest(UUID fiscalYearPatternId, UUID monthlyPeriodPatternId) {}
}
