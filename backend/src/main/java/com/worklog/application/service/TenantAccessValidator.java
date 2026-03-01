package com.worklog.application.service;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Validates that the authenticated user has access to a specific tenant's resources.
 *
 * <p>SYSTEM_ADMINs with "tenant.view" permission can access any tenant.
 * Other users can only access their own tenant.
 */
@Service
public class TenantAccessValidator {

    private final UserContextService userContextService;
    private final PermissionEvaluator permissionEvaluator;

    public TenantAccessValidator(UserContextService userContextService, PermissionEvaluator permissionEvaluator) {
        this.userContextService = userContextService;
        this.permissionEvaluator = permissionEvaluator;
    }

    /**
     * Resolves the tenant ID for the currently authenticated user.
     *
     * @param auth the current authentication
     * @return the user's tenant ID
     */
    public UUID resolveUserTenantId(Authentication auth) {
        return userContextService.resolveUserTenantId(auth.getName());
    }

    /**
     * Validates that the authenticated user can access the given tenant.
     *
     * <p>Users with "tenant.view" permission (SYSTEM_ADMIN) can access any tenant.
     * Other users must belong to the specified tenant.
     *
     * @param auth the current authentication
     * @param tenantId the tenant to access
     * @throws AccessDeniedException if the user cannot access the tenant
     */
    public void validateAccess(Authentication auth, UUID tenantId) {
        if (permissionEvaluator.hasPermission(auth, null, "tenant.view")) {
            return;
        }
        UUID userTenantId = resolveUserTenantId(auth);
        if (!tenantId.equals(userTenantId)) {
            throw new AccessDeniedException("Access denied: tenant mismatch");
        }
    }
}
