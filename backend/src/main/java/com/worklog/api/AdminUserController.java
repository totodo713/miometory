package com.worklog.api;

import com.worklog.application.service.AdminUserService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for system-wide user management.
 * Accessible only to SYSTEM_ADMIN users.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
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
        return adminUserService.listUsers(search, tenantId, roleId, accountStatus, page, size);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasPermission(null, 'user.manage')")
    public ResponseEntity<Void> changeRole(@PathVariable UUID id, @RequestBody ChangeRoleRequest request) {
        adminUserService.changeRole(id, request.roleId());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/lock")
    @PreAuthorize("hasPermission(null, 'user.manage')")
    public ResponseEntity<Void> lockUser(@PathVariable UUID id, @RequestBody LockRequest request) {
        adminUserService.lockUser(id, request.durationMinutes());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/unlock")
    @PreAuthorize("hasPermission(null, 'user.manage')")
    public ResponseEntity<Void> unlockUser(@PathVariable UUID id) {
        adminUserService.unlockUser(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/password-reset")
    @PreAuthorize("hasPermission(null, 'user.manage')")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id) {
        adminUserService.resetPassword(id);
        return ResponseEntity.ok().build();
    }

    // Request DTOs

    public record ChangeRoleRequest(UUID roleId) {}

    public record LockRequest(int durationMinutes) {}
}
