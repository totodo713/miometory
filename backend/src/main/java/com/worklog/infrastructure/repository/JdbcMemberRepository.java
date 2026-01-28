package com.worklog.infrastructure.repository;

import com.worklog.domain.member.Member;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.organization.OrganizationId;
import com.worklog.domain.tenant.TenantId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
            FROM member
            WHERE id = ?
            """;

        List<Member> results = jdbcTemplate.query(sql, new MemberRowMapper(), id.value());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Finds a member by email.
     * 
     * @param email Email address
     * @return Optional containing the member if found
     */
    public Optional<Member> findByEmail(String email) {
        String sql = """
            SELECT id, tenant_id, organization_id, email, display_name, 
                   manager_id, is_active, version, created_at, updated_at
            FROM member
            WHERE email = ?
            """;

        List<Member> results = jdbcTemplate.query(sql, new MemberRowMapper(), email);
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
            FROM member
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
                FROM member
                WHERE manager_id = ? AND is_active = true
                
                UNION ALL
                
                -- Recursive case: reports of reports
                SELECT m.id, m.tenant_id, m.organization_id, m.email, m.display_name, 
                       m.manager_id, m.is_active, m.version, m.created_at, m.updated_at, s.level + 1
                FROM member m
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
                FROM member
                WHERE id = ?
                
                UNION ALL
                
                SELECT m.id, m.manager_id, c.level + 1
                FROM member m
                INNER JOIN chain c ON m.id = c.manager_id
                WHERE c.level < 10  -- Prevent infinite loops
            )
            SELECT 1 FROM chain WHERE manager_id = ? LIMIT 1
            """;

        List<Integer> results = jdbcTemplate.query(
            sql,
            (rs, rowNum) -> 1,
            memberId.value(),
            managerId.value()
        );
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
            SELECT 1 FROM member 
            WHERE id = ? AND manager_id = ? AND is_active = true
            LIMIT 1
            """;

        List<Integer> results = jdbcTemplate.query(
            sql,
            (rs, rowNum) -> 1,
            memberId.value(),
            managerId.value()
        );
        return !results.isEmpty();
    }

    /**
     * Saves a member (insert or update).
     * 
     * @param member The member to save
     */
    public void save(Member member) {
        String upsertSql = """
            INSERT INTO member (id, tenant_id, organization_id, email, display_name, 
                               manager_id, is_active, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                email = EXCLUDED.email,
                display_name = EXCLUDED.display_name,
                manager_id = EXCLUDED.manager_id,
                is_active = EXCLUDED.is_active,
                version = member.version + 1,
                updated_at = EXCLUDED.updated_at
            """;

        jdbcTemplate.update(
            upsertSql,
            member.getId().value(),
            member.getTenantId().value(),
            member.getOrganizationId().value(),
            member.getEmail(),
            member.getDisplayName(),
            member.getManagerId() != null ? member.getManagerId().value() : null,
            member.isActive(),
            0, // Version starts at 0 for new records
            Timestamp.from(member.getCreatedAt()),
            Timestamp.from(member.getUpdatedAt())
        );
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
            FROM member
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
            
            return new Member(
                MemberId.of(rs.getObject("id", UUID.class)),
                TenantId.of(rs.getObject("tenant_id", UUID.class)),
                OrganizationId.of(rs.getObject("organization_id", UUID.class)),
                rs.getString("email"),
                rs.getString("display_name"),
                managerId != null ? MemberId.of(managerId) : null,
                rs.getBoolean("is_active"),
                createdAt
            );
        }
    }
}
