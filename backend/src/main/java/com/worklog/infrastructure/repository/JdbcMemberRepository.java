package com.worklog.infrastructure.repository;

import com.worklog.domain.member.Member;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.organization.OrganizationId;
import com.worklog.domain.tenant.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC-based repository for Member aggregate.
 *
 * Supports queries for member lookup and manager-subordinate relationships
 * required for proxy entry feature (US7).
 */
@Repository
public class JdbcMemberRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Finds a member by ID.
     *
     * @param id Member ID
     * @return Optional containing the member if found
     */
    public Optional<Member> findById(MemberId id) {
        String sql = """
            SELECT id, tenant_id, organization_id, email, display_name,
                   manager_id, is_active, version, created_at, updated_at
            FROM members
            WHERE id = ?
            """;

        List<Member> results = jdbcTemplate.query(sql, new MemberRowMapper(), id.value());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Finds a member by email within a specific tenant.
     *
     * The schema allows the same email across different tenants (uk_member_tenant_email),
     * so tenant scoping is required to prevent cross-tenant data exposure.
     *
     * @param tenantId Tenant ID to scope the search
     * @param email Email address
     * @return Optional containing the member if found within the tenant
     */
    public Optional<Member> findByEmail(TenantId tenantId, String email) {
        String sql = """
            SELECT id, tenant_id, organization_id, email, display_name,
                   manager_id, is_active, version, created_at, updated_at
            FROM members
            WHERE tenant_id = ? AND email = ?
            """;

        List<Member> results = jdbcTemplate.query(sql, new MemberRowMapper(), tenantId.value(), email);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Finds all direct subordinates of a manager.
     * Returns only active members who have the specified member as their direct manager.
     *
     * @param managerId Manager's member ID
     * @return List of direct subordinates
     */
    public List<Member> findDirectSubordinates(MemberId managerId) {
        String sql = """
            SELECT id, tenant_id, organization_id, email, display_name,
                   manager_id, is_active, version, created_at, updated_at
            FROM members
            WHERE manager_id = ? AND is_active = true
            ORDER BY display_name
            """;

        return jdbcTemplate.query(sql, new MemberRowMapper(), managerId.value());
    }

    /**
     * Finds all subordinates (recursive) of a manager.
     * Uses recursive CTE to traverse the manager hierarchy.
     *
     * @param managerId Manager's member ID
     * @return List of all subordinates (direct and indirect)
     */
    public List<Member> findAllSubordinates(MemberId managerId) {
        String sql = """
            WITH RECURSIVE subordinates AS (
                -- Base case: direct reports
                SELECT id, tenant_id, organization_id, email, display_name,
                       manager_id, is_active, version, created_at, updated_at, 1 as level
                FROM members
                WHERE manager_id = ? AND is_active = true

                UNION ALL

                -- Recursive case: reports of reports
                SELECT m.id, m.tenant_id, m.organization_id, m.email, m.display_name,
                       m.manager_id, m.is_active, m.version, m.created_at, m.updated_at, s.level + 1
                FROM members m
                INNER JOIN subordinates s ON m.manager_id = s.id
                WHERE m.is_active = true AND s.level < 10  -- Prevent infinite loops, max 10 levels
            )
            SELECT id, tenant_id, organization_id, email, display_name,
                   manager_id, is_active, version, created_at, updated_at
            FROM subordinates
            ORDER BY level, display_name
            """;

        return jdbcTemplate.query(sql, new MemberRowMapper(), managerId.value());
    }

    /**
     * Checks if a member is a subordinate (direct or indirect) of a manager.
     *
     * @param managerId The potential manager
     * @param memberId The potential subordinate
     * @return true if memberId is a subordinate of managerId
     */
    public boolean isSubordinateOf(MemberId managerId, MemberId memberId) {
        String sql = """
            WITH RECURSIVE chain AS (
                -- Start from the member and walk up the chain
                SELECT id, manager_id, 1 as level
                FROM members
                WHERE id = ?

                UNION ALL

                SELECT m.id, m.manager_id, c.level + 1
                FROM members m
                INNER JOIN chain c ON m.id = c.manager_id
                WHERE c.level < 10  -- Prevent infinite loops
            )
            SELECT 1 FROM chain WHERE manager_id = ? LIMIT 1
            """;

        List<Integer> results = jdbcTemplate.query(sql, (rs, rowNum) -> 1, memberId.value(), managerId.value());
        return !results.isEmpty();
    }

    /**
     * Checks if a member is a direct subordinate of a manager.
     *
     * @param managerId The manager
     * @param memberId The potential direct subordinate
     * @return true if memberId's manager_id equals managerId
     */
    public boolean isDirectSubordinateOf(MemberId managerId, MemberId memberId) {
        String sql = """
            SELECT 1 FROM members
            WHERE id = ? AND manager_id = ? AND is_active = true
            LIMIT 1
            """;

        List<Integer> results = jdbcTemplate.query(sql, (rs, rowNum) -> 1, memberId.value(), managerId.value());
        return !results.isEmpty();
    }

    /**
     * Saves a member (insert or update).
     * Uses optimistic locking: update only succeeds if the version matches.
     *
     * @param member The member to save
     * @param expectedVersion The expected current version for optimistic locking (use 0 for new records)
     * @throws OptimisticLockingException if the version doesn't match (concurrent modification)
     */
    public void save(Member member, int expectedVersion) {
        String upsertSql = """
            INSERT INTO members (id, tenant_id, organization_id, email, display_name,
                               manager_id, is_active, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                email = EXCLUDED.email,
                display_name = EXCLUDED.display_name,
                manager_id = EXCLUDED.manager_id,
                is_active = EXCLUDED.is_active,
                version = members.version + 1,
                updated_at = EXCLUDED.updated_at
            WHERE members.version = ?
            """;

        int rowsAffected = jdbcTemplate.update(
                upsertSql,
                member.getId().value(),
                member.getTenantId().value(),
                member.getOrganizationId().value(),
                member.getEmail(),
                member.getDisplayName(),
                member.getManagerId() != null ? member.getManagerId().value() : null,
                member.isActive(),
                expectedVersion, // Version for new records
                Timestamp.from(member.getCreatedAt()),
                Timestamp.from(member.getUpdatedAt()),
                expectedVersion // Version check for update
                );

        // For inserts (new records), rowsAffected will be 1
        // For updates, rowsAffected will be 1 if version matched, 0 if not
        if (rowsAffected == 0) {
            // Check if record exists - if it does, it's a version mismatch
            if (findById(member.getId()).isPresent()) {
                throw new org.springframework.dao.OptimisticLockingFailureException(
                        "Member was modified by another transaction. Expected version: " + expectedVersion);
            }
        }
    }

    /**
     * Saves a member using automatic version detection.
     * For new records, version starts at 0. For existing records, uses the current DB version.
     *
     * @param member The member to save
     */
    public void save(Member member) {
        List<Integer> versions = jdbcTemplate.query(
                "SELECT version FROM members WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getInt("version"),
                member.getId().value());
        save(member, versions.isEmpty() ? 0 : versions.get(0));
    }

    /**
     * Finds all active members in an organization.
     *
     * @param organizationId Organization ID
     * @return List of active members
     */
    public List<Member> findByOrganization(OrganizationId organizationId) {
        String sql = """
            SELECT id, tenant_id, organization_id, email, display_name,
                   manager_id, is_active, version, created_at, updated_at
            FROM members
            WHERE organization_id = ? AND is_active = true
            ORDER BY display_name
            """;

        return jdbcTemplate.query(sql, new MemberRowMapper(), organizationId.value());
    }

    /**
     * Row mapper for Member entity.
     */
    private static class MemberRowMapper implements RowMapper<Member> {
        @Override
        public Member mapRow(ResultSet rs, int rowNum) throws SQLException {
            UUID managerId = (UUID) rs.getObject("manager_id");
            Instant createdAt = rs.getTimestamp("created_at").toInstant();
            Instant updatedAt = rs.getTimestamp("updated_at").toInstant();

            return new Member(
                    MemberId.of(rs.getObject("id", UUID.class)),
                    TenantId.of(rs.getObject("tenant_id", UUID.class)),
                    OrganizationId.of(rs.getObject("organization_id", UUID.class)),
                    rs.getString("email"),
                    rs.getString("display_name"),
                    managerId != null ? MemberId.of(managerId) : null,
                    rs.getBoolean("is_active"),
                    createdAt,
                    updatedAt);
        }
    }
}
