package com.worklog.infrastructure.config

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.io.Serializable

/**
 * Custom permission evaluator that checks `resource.action` permissions
 * against the role_permissions table.
 *
 * Usage in controllers:
 * ```
 * @PreAuthorize("hasPermission(null, 'member.view')")
 * ```
 *
 * Resolves the authenticated user's role from SecurityContext, then queries
 * whether that role has the requested permission in the role_permissions table.
 */
@Component
class CustomPermissionEvaluator(private val jdbcTemplate: JdbcTemplate) : PermissionEvaluator {

    /**
     * Checks if the authenticated user has the given permission.
     *
     * @param authentication the current authentication
     * @param targetDomainObject unused (pass null)
     * @param permission the resource.action permission string (e.g., "member.view")
     */
    override fun hasPermission(authentication: Authentication?, targetDomainObject: Any?, permission: Any?): Boolean {
        val email = authentication?.takeIf { it.isAuthenticated }?.name
        val permissionStr = permission as? String
        return if (email != null && permissionStr != null) checkPermission(email, permissionStr) else false
    }

    /**
     * Not used — all permission checks use the resource.action pattern.
     */
    override fun hasPermission(
        authentication: Authentication?,
        targetId: Serializable?,
        targetType: String?,
        permission: Any?,
    ): Boolean = hasPermission(authentication, null, permission)

    private fun checkPermission(email: String, permission: String): Boolean {
        val sql = """
            SELECT COUNT(*) > 0
            FROM users u
            JOIN roles r ON r.id = u.role_id
            JOIN role_permissions rp ON rp.role_id = r.id
            JOIN permissions p ON p.id = rp.permission_id
            WHERE LOWER(u.email) = LOWER(?)
              AND p.name = ?
        """
        val result = jdbcTemplate.queryForObject(sql, Boolean::class.java, email, permission)
        return result ?: false
    }
}
