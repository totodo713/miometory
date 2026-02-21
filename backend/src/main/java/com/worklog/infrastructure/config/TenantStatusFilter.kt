package com.worklog.infrastructure.config

import com.worklog.shared.AdminRole
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Filter that blocks requests from users whose tenant is deactivated.
 *
 * Resolves the authenticated user's tenant via members table, then checks
 * the tenant's status. If tenant status is not 'ACTIVE', returns 403.
 *
 * Skips the check for:
 * - Unauthenticated requests
 * - Users with the SYSTEM_ADMIN role (who operate cross-tenant)
 */
@Component
class TenantStatusFilter(private val jdbcTemplate: JdbcTemplate) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val email = request.userPrincipal?.name
        val shouldBlock = email != null && isTenantDeactivated(email)

        if (shouldBlock) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = "application/json"
            response.writer.write(
                """{"error":"TENANT_DEACTIVATED","message":"Your tenant has been deactivated"}""",
            )
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun isTenantDeactivated(email: String): Boolean {
        val result = resolveRoleAndTenantStatus(email)
        val (roleName, tenantStatus) = result ?: return false
        return roleName != AdminRole.SYSTEM_ADMIN && tenantStatus != null && tenantStatus != "ACTIVE"
    }

    /**
     * Returns (roleName, tenantStatus) for the given user email.
     * tenantStatus is null if the user has no associated member/tenant.
     */
    private fun resolveRoleAndTenantStatus(email: String): Pair<String, String?>? {
        val sql = """
            SELECT r.name AS role_name, t.status AS tenant_status
            FROM users u
            JOIN roles r ON r.id = u.role_id
            LEFT JOIN members m ON LOWER(m.email) = LOWER(u.email)
            LEFT JOIN tenant t ON t.id = m.tenant_id
            WHERE LOWER(u.email) = LOWER(?)
            LIMIT 1
        """

        return try {
            jdbcTemplate.queryForObject(sql, { rs, _ ->
                Pair(rs.getString("role_name"), rs.getString("tenant_status"))
            }, email)
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }
}
