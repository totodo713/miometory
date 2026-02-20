package com.worklog.application.service;

import com.worklog.domain.shared.DomainException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminUserService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserPage listUsers(String search, String tenantId, String roleId, String accountStatus, int page, int size) {
        var sb = new StringBuilder("SELECT u.id, u.email, u.name, r.name AS role_name, t.name AS tenant_name,"
                + " u.account_status, u.last_login_at"
                + " FROM users u"
                + " JOIN roles r ON r.id = u.role_id"
                + " LEFT JOIN members m ON LOWER(m.email) = LOWER(u.email)"
                + " LEFT JOIN tenant t ON t.id = m.tenant_id"
                + " WHERE 1=1");
        var countSb = new StringBuilder("SELECT COUNT(*) FROM users u"
                + " JOIN roles r ON r.id = u.role_id"
                + " LEFT JOIN members m ON LOWER(m.email) = LOWER(u.email)"
                + " LEFT JOIN tenant t ON t.id = m.tenant_id"
                + " WHERE 1=1");
        var params = new java.util.ArrayList<Object>();
        var countParams = new java.util.ArrayList<Object>();

        if (search != null && !search.isBlank()) {
            sb.append(" AND (LOWER(u.email) LIKE LOWER(?) OR LOWER(u.name) LIKE LOWER(?))");
            countSb.append(" AND (LOWER(u.email) LIKE LOWER(?) OR LOWER(u.name) LIKE LOWER(?))");
            String pattern = "%" + search + "%";
            params.add(pattern);
            params.add(pattern);
            countParams.add(pattern);
            countParams.add(pattern);
        }

        if (tenantId != null && !tenantId.isBlank()) {
            sb.append(" AND m.tenant_id = ?");
            countSb.append(" AND m.tenant_id = ?");
            UUID tid = UUID.fromString(tenantId);
            params.add(tid);
            countParams.add(tid);
        }

        if (roleId != null && !roleId.isBlank()) {
            sb.append(" AND u.role_id = ?");
            countSb.append(" AND u.role_id = ?");
            UUID rid = UUID.fromString(roleId);
            params.add(rid);
            countParams.add(rid);
        }

        if (accountStatus != null && !accountStatus.isBlank()) {
            sb.append(" AND LOWER(u.account_status) = LOWER(?)");
            countSb.append(" AND LOWER(u.account_status) = LOWER(?)");
            params.add(accountStatus);
            countParams.add(accountStatus);
        }

        Long total = jdbcTemplate.queryForObject(countSb.toString(), Long.class, countParams.toArray());
        long totalElements = total != null ? total : 0;

        sb.append(" ORDER BY u.email LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        List<UserRow> content = jdbcTemplate.query(
                sb.toString(),
                (rs, rowNum) -> new UserRow(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("email"),
                        rs.getString("name"),
                        rs.getString("role_name"),
                        rs.getString("tenant_name"),
                        rs.getString("account_status"),
                        rs.getTimestamp("last_login_at") != null
                                ? rs.getTimestamp("last_login_at").toInstant().toString()
                                : null),
                params.toArray());

        int totalPages = (int) Math.ceil((double) totalElements / size);
        return new UserPage(content, totalElements, totalPages, page);
    }

    public void changeRole(UUID userId, UUID roleId) {
        Boolean userExists =
                jdbcTemplate.queryForObject("SELECT COUNT(*) > 0 FROM users WHERE id = ?", Boolean.class, userId);
        if (!Boolean.TRUE.equals(userExists)) {
            throw new DomainException("USER_NOT_FOUND", "User not found");
        }

        Boolean roleExists =
                jdbcTemplate.queryForObject("SELECT COUNT(*) > 0 FROM roles WHERE id = ?", Boolean.class, roleId);
        if (!Boolean.TRUE.equals(roleExists)) {
            throw new DomainException("ROLE_NOT_FOUND", "Role not found");
        }

        jdbcTemplate.update("UPDATE users SET role_id = ?, updated_at = NOW() WHERE id = ?", roleId, userId);
    }

    public void lockUser(UUID userId, int durationMinutes) {
        Boolean userExists =
                jdbcTemplate.queryForObject("SELECT COUNT(*) > 0 FROM users WHERE id = ?", Boolean.class, userId);
        if (!Boolean.TRUE.equals(userExists)) {
            throw new DomainException("USER_NOT_FOUND", "User not found");
        }

        jdbcTemplate.update(
                "UPDATE users SET account_status = 'locked', locked_until = NOW() + (? || ' minutes')::INTERVAL, updated_at = NOW() WHERE id = ?",
                String.valueOf(durationMinutes),
                userId);
    }

    public void unlockUser(UUID userId) {
        Boolean userExists =
                jdbcTemplate.queryForObject("SELECT COUNT(*) > 0 FROM users WHERE id = ?", Boolean.class, userId);
        if (!Boolean.TRUE.equals(userExists)) {
            throw new DomainException("USER_NOT_FOUND", "User not found");
        }

        jdbcTemplate.update(
                "UPDATE users SET account_status = 'active', locked_until = NULL, failed_login_attempts = 0, updated_at = NOW() WHERE id = ?",
                userId);
    }

    public void resetPassword(UUID userId) {
        Boolean userExists =
                jdbcTemplate.queryForObject("SELECT COUNT(*) > 0 FROM users WHERE id = ?", Boolean.class, userId);
        if (!Boolean.TRUE.equals(userExists)) {
            throw new DomainException("USER_NOT_FOUND", "User not found");
        }

        String tempPassword = UUID.randomUUID().toString().substring(0, 12);
        String hashed = passwordEncoder.encode(tempPassword);
        jdbcTemplate.update("UPDATE users SET hashed_password = ?, updated_at = NOW() WHERE id = ?", hashed, userId);
    }

    public record UserRow(
            String id,
            String email,
            String name,
            String roleName,
            String tenantName,
            String accountStatus,
            String lastLoginAt) {}

    public record UserPage(List<UserRow> content, long totalElements, int totalPages, int number) {}
}
