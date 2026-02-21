package com.worklog.api;

import com.worklog.application.command.InviteMemberCommand;
import com.worklog.application.command.UpdateMemberCommand;
import com.worklog.application.service.AdminMemberService;
import com.worklog.application.service.UserContextService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final UserContextService userContextService;

    public AdminMemberController(AdminMemberService adminMemberService, UserContextService userContextService) {
        this.adminMemberService = adminMemberService;
        this.userContextService = userContextService;
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
        UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
        int effectiveSize = Math.min(size, 100);
        return adminMemberService.listMembers(tenantId, search, organizationId, isActive, page, effectiveSize);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'member.create')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateMemberResponse inviteMember(
            @RequestBody @Valid InviteMemberRequest request, Authentication authentication) {
        UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
        UUID memberId = userContextService.resolveUserMemberId(authentication.getName());
        var command = new InviteMemberCommand(
                request.email(), request.displayName(), request.organizationId(), request.managerId(), memberId);
        var result = adminMemberService.inviteMember(command, tenantId);
        return new CreateMemberResponse(result.memberId().toString(), result.temporaryPassword());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'member.update')")
    public ResponseEntity<Void> updateMember(
            @PathVariable UUID id, @RequestBody @Valid UpdateMemberRequest request, Authentication authentication) {
        UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
        UUID memberId = userContextService.resolveUserMemberId(authentication.getName());
        var command = new UpdateMemberCommand(
                id, request.email(), request.displayName(), request.organizationId(), request.managerId(), memberId);
        adminMemberService.updateMember(command, tenantId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasPermission(null, 'member.deactivate')")
    public ResponseEntity<Void> deactivateMember(@PathVariable UUID id, Authentication authentication) {
        UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
        adminMemberService.deactivateMember(id, tenantId);
        return ResponseEntity.ok().build();
    }

    // Intentionally reuses *.deactivate permission for both activate/deactivate
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasPermission(null, 'member.deactivate')")
    public ResponseEntity<Void> activateMember(@PathVariable UUID id, Authentication authentication) {
        UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
        adminMemberService.activateMember(id, tenantId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/assign-tenant-admin")
    @PreAuthorize("hasPermission(null, 'tenant_admin.assign')")
    public ResponseEntity<Void> assignTenantAdmin(@PathVariable UUID id, Authentication authentication) {
        UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
        adminMemberService.assignTenantAdmin(id, tenantId);
        return ResponseEntity.ok().build();
    }

    // Request/Response DTOs

    public record InviteMemberRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 100) String displayName,
            UUID organizationId,
            UUID managerId) {}

    public record UpdateMemberRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 100) String displayName,
            UUID organizationId,
            UUID managerId) {}

    public record CreateMemberResponse(String id, String temporaryPassword) {}
}
