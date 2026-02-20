package com.worklog.api;

import com.worklog.application.command.CreateProjectCommand;
import com.worklog.application.command.UpdateProjectCommand;
import com.worklog.application.service.AdminProjectService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/projects")
public class AdminProjectController {

    private final AdminProjectService adminProjectService;
    private final JdbcTemplate jdbcTemplate;

    public AdminProjectController(AdminProjectService adminProjectService, JdbcTemplate jdbcTemplate) {
        this.adminProjectService = adminProjectService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'project.view')")
    public AdminProjectService.ProjectPage listProjects(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        UUID tenantId = resolveUserTenantId(authentication.getName());
        return adminProjectService.listProjects(tenantId, search, isActive, page, size);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'project.create')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateProjectResponse createProject(
            @RequestBody CreateProjectRequest request, Authentication authentication) {
        UUID tenantId = resolveUserTenantId(authentication.getName());
        var command = new CreateProjectCommand(
                tenantId, request.code(), request.name(), request.validFrom(), request.validUntil());
        UUID id = adminProjectService.createProject(command);
        return new CreateProjectResponse(id.toString());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'project.update')")
    public ResponseEntity<Void> updateProject(
            @PathVariable UUID id, @RequestBody UpdateProjectRequest request, Authentication authentication) {
        UUID tenantId = resolveUserTenantId(authentication.getName());
        var command = new UpdateProjectCommand(id, request.name(), request.validFrom(), request.validUntil());
        adminProjectService.updateProject(command, tenantId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasPermission(null, 'project.deactivate')")
    public ResponseEntity<Void> deactivateProject(@PathVariable UUID id, Authentication authentication) {
        UUID tenantId = resolveUserTenantId(authentication.getName());
        adminProjectService.deactivateProject(id, tenantId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasPermission(null, 'project.deactivate')")
    public ResponseEntity<Void> activateProject(@PathVariable UUID id, Authentication authentication) {
        UUID tenantId = resolveUserTenantId(authentication.getName());
        adminProjectService.activateProject(id, tenantId);
        return ResponseEntity.ok().build();
    }

    private UUID resolveUserTenantId(String email) {
        String sql = "SELECT m.tenant_id FROM members m WHERE LOWER(m.email) = LOWER(?) LIMIT 1";
        return jdbcTemplate.queryForObject(sql, UUID.class, email);
    }

    public record CreateProjectRequest(String code, String name, LocalDate validFrom, LocalDate validUntil) {}

    public record UpdateProjectRequest(String name, LocalDate validFrom, LocalDate validUntil) {}

    public record CreateProjectResponse(String id) {}
}
