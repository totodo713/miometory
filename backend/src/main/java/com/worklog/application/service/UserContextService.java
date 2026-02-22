package com.worklog.application.service;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for resolving user context (tenant ID, member ID) from the authenticated user's email.
 * Centralizes the lookup logic previously duplicated across admin controllers.
 */
@Service
@Transactional(readOnly = true)
public class UserContextService {

    private final JdbcTemplate jdbcTemplate;

    public UserContextService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Resolves the tenant ID for the authenticated user by looking up their member record.
     *
     * @param email the user's email address
     * @return the tenant ID associated with the user
     */
    public UUID resolveUserTenantId(String email) {
        String sql = "SELECT m.tenant_id FROM members m WHERE LOWER(m.email) = LOWER(?) LIMIT 1";
        return jdbcTemplate.queryForObject(sql, UUID.class, email);
    }

    /**
     * Resolves the member ID for the authenticated user by looking up their member record.
     * Throws if no member record is found — use for endpoints that require a valid member.
     *
     * @param email the user's email address
     * @return the member ID associated with the user (never null)
     * @throws org.springframework.dao.EmptyResultDataAccessException if no member found
     */
    public UUID resolveUserMemberId(String email) {
        String sql = "SELECT m.id FROM members m WHERE LOWER(m.email) = LOWER(?) LIMIT 1";
        return jdbcTemplate.queryForObject(sql, UUID.class, email);
    }

    /**
     * Resolves the member ID for the authenticated user, returning null if no member record exists.
     * Use for endpoints that should gracefully handle users without a member record
     * (e.g., notifications, admin context).
     *
     * @param email the user's email address
     * @return the member ID, or null if no member record exists
     */
    public UUID resolveUserMemberIdOrNull(String email) {
        String sql = "SELECT m.id FROM members m WHERE LOWER(m.email) = LOWER(?) LIMIT 1";
        List<UUID> results = jdbcTemplate.queryForList(sql, UUID.class, email);
        return results.isEmpty() ? null : results.get(0);
    }
}
