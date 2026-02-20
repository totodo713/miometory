package com.worklog.api;

import com.worklog.application.command.CreateAssignmentCommand;
import com.worklog.application.service.AdminAssignmentService;
import com.worklog.shared.AdminRole;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/assignments")
public class AdminAssignmentController {

    private final AdminAssignmentService adminAssignmentService;
    private final JdbcTemplate jdbcTemplate;

    public AdminAssignmentController(AdminAssignmentService adminAssignmentService, JdbcTemplate jdbcTemplate) {
        this.adminAssignmentService = adminAssignmentService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/by-member/{memberId}")
    @PreAuthorize("hasPermission(null, 'assignment.view')")
    public List<AdminAssignmentService.AssignmentRow> listByMember(
            @PathVariable UUID memberId, Authentication authentication) {
        UUID tenantId = resolveUserTenantId(authentication.getName());
        enforceDirectReportIfSupervisor(authentication.getName(), memberId);
        return adminAssignmentService.listByMember(memberId, tenantId);
    }

    @GetMapping("/by-project/{projectId}")
    @PreAuthorize("hasPermission(null, 'assignment.view')")
    public List<AdminAssignmentService.AssignmentRow> listByProject(
            @PathVariable UUID projectId, Authentication authentication) {
        UUID tenantId = resolveUserTenantId(authentication.getName());
        return adminAssignmentService.listByProject(projectId, tenantId);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'assignment.create')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateAssignmentResponse createAssignment(
            @RequestBody CreateAssignmentRequest request, Authentication authentication) {
        UUID tenantId = resolveUserTenantId(authentication.getName());
        UUID actorMemberId = resolveUserMemberId(authentication.getName());

        enforceDirectReportIfSupervisor(authentication.getName(), request.memberId());

        var command = new CreateAssignmentCommand(tenantId, request.memberId(), request.projectId(), actorMemberId);
        UUID id = adminAssignmentService.createAssignment(command);
        return new CreateAssignmentResponse(id.toString());
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasPermission(null, 'assignment.deactivate')")
    public void deactivateAssignment(@PathVariable UUID id, Authentication authentication) {
        UUID tenantId = resolveUserTenantId(authentication.getName());
        adminAssignmentService.deactivateAssignment(id, tenantId);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasPermission(null, 'assignment.deactivate')")
    public void activateAssignment(@PathVariable UUID id, Authentication authentication) {
        UUID tenantId = resolveUserTenantId(authentication.getName());
        adminAssignmentService.activateAssignment(id, tenantId);
    }

    private void enforceDirectReportIfSupervisor(String email, UUID targetMemberId) {
        String roleName = jdbcTemplate.queryForObject("""
                SELECT r.name FROM users u
                JOIN roles r ON r.id = u.role_id
                WHERE LOWER(u.email) = LOWER(?)
                """, String.class, email);
        if (AdminRole.SUPERVISOR.equals(roleName)) {
            UUID supervisorMemberId = resolveUserMemberId(email);
            adminAssignmentService.validateDirectReport(targetMemberId, supervisorMemberId);
        }
    }

    private UUID resolveUserTenantId(String email) {
        String sql = "SELECT m.tenant_id FROM members m WHERE LOWER(m.email) = LOWER(?) LIMIT 1";
        return jdbcTemplate.queryForObject(sql, UUID.class, email);
    }

    private UUID resolveUserMemberId(String email) {
        String sql = "SELECT m.id FROM members m WHERE LOWER(m.email) = LOWER(?) LIMIT 1";
        return jdbcTemplate.queryForObject(sql, UUID.class, email);
    }

    public record CreateAssignmentRequest(UUID memberId, UUID projectId) {}

    public record CreateAssignmentResponse(String id) {}
}
