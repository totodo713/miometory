package com.worklog.api;

import com.worklog.application.command.InviteMemberCommand;
import com.worklog.application.command.UpdateMemberCommand;
import com.worklog.application.service.AdminMemberService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for tenant-scoped member management.
 */
@RestController
@RequestMapping("/api/v1/admin/members")
public class AdminMemberController {

    private final AdminMemberService adminMemberService;
    private final JdbcTemplate jdbcTemplate;

    public AdminMemberController(AdminMemberService adminMemberService, JdbcTemplate jdbcTemplate) {
        this.adminMemberService = adminMemberService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'member.view')")
    public AdminMemberService.MemberPage listMembers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        UUID tenantId = resolveUserTenantId(authentication.getName());
        return adminMemberService.listMembers(tenantId, search, organizationId, isActive, page, size);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'member.create')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateMemberResponse inviteMember(@RequestBody InviteMemberRequest request, Authentication authentication) {
        UUID tenantId = resolveUserTenantId(authentication.getName());
        UUID memberId = resolveUserMemberId(authentication.getName());
        var command = new InviteMemberCommand(
                request.email(), request.displayName(), request.organizationId(), request.managerId(), memberId);
        UUID id = adminMemberService.inviteMember(command, tenantId);
        return new CreateMemberResponse(id.toString());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'member.update')")
    public ResponseEntity<Void> updateMember(
            @PathVariable UUID id, @RequestBody UpdateMemberRequest request, Authentication authentication) {
        UUID tenantId = resolveUserTenantId(authentication.getName());
        UUID memberId = resolveUserMemberId(authentication.getName());
        var command = new UpdateMemberCommand(
                id, request.email(), request.displayName(), request.organizationId(), request.managerId(), memberId);
        adminMemberService.updateMember(command, tenantId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasPermission(null, 'member.deactivate')")
    public ResponseEntity<Void> deactivateMember(@PathVariable UUID id, Authentication authentication) {
        UUID tenantId = resolveUserTenantId(authentication.getName());
        adminMemberService.deactivateMember(id, tenantId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasPermission(null, 'member.deactivate')")
    public ResponseEntity<Void> activateMember(@PathVariable UUID id, Authentication authentication) {
        UUID tenantId = resolveUserTenantId(authentication.getName());
        adminMemberService.activateMember(id, tenantId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/assign-tenant-admin")
    @PreAuthorize("hasPermission(null, 'tenant_admin.assign')")
    public ResponseEntity<Void> assignTenantAdmin(@PathVariable UUID id, Authentication authentication) {
        UUID tenantId = resolveUserTenantId(authentication.getName());
        adminMemberService.assignTenantAdmin(id, tenantId);
        return ResponseEntity.ok().build();
    }

    private UUID resolveUserTenantId(String email) {
        String sql = "SELECT m.tenant_id FROM members m WHERE LOWER(m.email) = LOWER(?) LIMIT 1";
        return jdbcTemplate.queryForObject(sql, UUID.class, email);
    }

    private UUID resolveUserMemberId(String email) {
        String sql = "SELECT m.id FROM members m WHERE LOWER(m.email) = LOWER(?) LIMIT 1";
        return jdbcTemplate.queryForObject(sql, UUID.class, email);
    }

    // Request/Response DTOs

    public record InviteMemberRequest(String email, String displayName, UUID organizationId, UUID managerId) {}

    public record UpdateMemberRequest(String email, String displayName, UUID organizationId, UUID managerId) {}

    public record CreateMemberResponse(String id) {}
}
