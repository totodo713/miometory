package com.worklog.api;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides the authenticated user's admin context (role, permissions, tenant info).
 * Intentionally open to all authenticated users - needed for frontend to determine admin UI visibility
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminContextController {

    private final JdbcTemplate jdbcTemplate;

    public AdminContextController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/context")
    public AdminContextResponse getContext(Authentication authentication) {
        String email = authentication.getName();

        String sql = """
            SELECT u.id AS user_id, r.name AS role_name, m.id AS member_id,
                   m.tenant_id, t.name AS tenant_name
            FROM users u
            JOIN roles r ON r.id = u.role_id
            LEFT JOIN members m ON LOWER(m.email) = LOWER(u.email)
            LEFT JOIN tenant t ON t.id = m.tenant_id
            WHERE LOWER(u.email) = LOWER(?)
            """;

        var row = jdbcTemplate.queryForMap(sql, email);
        String roleName = (String) row.get("role_name");
        UUID memberId = (UUID) row.get("member_id");
        UUID tenantId = (UUID) row.get("tenant_id");
        String tenantName = (String) row.get("tenant_name");

        String permissionsSql = """
            SELECT p.name
            FROM role_permissions rp
            JOIN permissions p ON p.id = rp.permission_id
            JOIN roles r ON r.id = rp.role_id
            WHERE r.name = ?
            ORDER BY p.name
            """;

        List<String> permissions = jdbcTemplate.queryForList(permissionsSql, String.class, roleName);

        return new AdminContextResponse(
                roleName,
                permissions,
                tenantId != null ? tenantId.toString() : null,
                tenantName,
                memberId != null ? memberId.toString() : null);
    }

    public record AdminContextResponse(
            String role, List<String> permissions, String tenantId, String tenantName, String memberId) {}
}
