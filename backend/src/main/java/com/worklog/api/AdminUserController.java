package com.worklog.api;

import com.worklog.application.service.AdminUserService;
import com.worklog.application.service.TenantAssignmentService;
import com.worklog.application.service.UserContextService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for system-wide user management.
 * Accessible only to SYSTEM_ADMIN users.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final TenantAssignmentService tenantAssignmentService;
    private final UserContextService userContextService;

    public AdminUserController(
            AdminUserService adminUserService,
            TenantAssignmentService tenantAssignmentService,
            UserContextService userContextService) {
        this.adminUserService = adminUserService;
        this.tenantAssignmentService = tenantAssignmentService;
        this.userContextService = userContextService;
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'user.view')")
    public AdminUserService.UserPage listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String roleId,
            @RequestParam(required = false) String accountStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int effectiveSize = Math.min(size, 100);
        return adminUserService.listUsers(search, tenantId, roleId, accountStatus, page, effectiveSize);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasPermission(null, 'user.update_role')")
    public ResponseEntity<Void> changeRole(@PathVariable UUID id, @RequestBody @Valid ChangeRoleRequest request) {
        adminUserService.changeRole(id, request.roleId());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/lock")
    @PreAuthorize("hasPermission(null, 'user.lock')")
    public ResponseEntity<Void> lockUser(@PathVariable UUID id, @RequestBody @Valid LockUserRequest request) {
        adminUserService.lockUser(id, request.durationMinutes());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/unlock")
    @PreAuthorize("hasPermission(null, 'user.lock')")
    public ResponseEntity<Void> unlockUser(@PathVariable UUID id) {
        adminUserService.unlockUser(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/password-reset")
    @PreAuthorize("hasPermission(null, 'user.reset_password')")
    public ResponseEntity<ResetPasswordResponse> resetPassword(@PathVariable UUID id) {
        String tempPassword = adminUserService.resetPassword(id);
        return ResponseEntity.ok(new ResetPasswordResponse(tempPassword));
    }

    @GetMapping("/search-for-assignment")
    @PreAuthorize("hasPermission(null, 'member.assign_tenant')")
    public List<TenantAssignmentService.UserSearchResult> searchForAssignment(
            @RequestParam String email, Authentication authentication) {
        UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
        return tenantAssignmentService.searchUsersForAssignment(email, tenantId);
    }

    // Request/Response DTOs

    public record ChangeRoleRequest(@NotNull UUID roleId) {}

    public record LockUserRequest(@Positive int durationMinutes) {}

    public record ResetPasswordResponse(String temporaryPassword) {}
}
