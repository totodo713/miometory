package com.worklog.infrastructure.repository;

import com.worklog.domain.member.MemberId;
import com.worklog.domain.project.MemberProjectAssignment;
import com.worklog.domain.project.MemberProjectAssignmentId;
import com.worklog.domain.project.ProjectId;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.projection.AssignedProjectInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC-based repository for MemberProjectAssignment entity.
 * 
 * Provides queries for member-project assignments including
 * finding all assigned projects for a member with project details.
 */
@Repository
public class JdbcMemberProjectAssignmentRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMemberProjectAssignmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Finds all active, valid projects assigned to a member.
     * 
     * Filters by:
     * - Assignment is active (is_active = true)
     * - Project is active (projects.is_active = true)
     * - Project is valid on the given date (valid_from <= date <= valid_until, nulls treated as unbounded)
     * 
     * @param memberId The member to find projects for
     * @param validOn The date to check project validity against (usually today)
     * @return List of assigned project information ordered by project code
     */
    public List<AssignedProjectInfo> findActiveProjectsForMember(MemberId memberId, LocalDate validOn) {
        String sql = """
            SELECT p.id AS project_id, p.code, p.name
            FROM member_project_assignments mpa
            INNER JOIN projects p ON mpa.project_id = p.id
            WHERE mpa.member_id = ?
              AND mpa.is_active = true
              AND p.is_active = true
              AND (p.valid_from IS NULL OR p.valid_from <= ?)
              AND (p.valid_until IS NULL OR p.valid_until >= ?)
            ORDER BY p.code
            """;

        return jdbcTemplate.query(
            sql,
            new AssignedProjectInfoRowMapper(),
            memberId.value(),
            validOn,
            validOn
        );
    }

    /**
     * Finds an assignment by ID.
     * 
     * @param id Assignment ID
     * @return Optional containing the assignment if found
     */
    public Optional<MemberProjectAssignment> findById(MemberProjectAssignmentId id) {
        String sql = """
            SELECT id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active
            FROM member_project_assignments
            WHERE id = ?
            """;

        List<MemberProjectAssignment> results = jdbcTemplate.query(
            sql,
            new MemberProjectAssignmentRowMapper(),
            id.value()
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Finds an assignment by tenant, member, and project.
     * 
     * @param tenantId Tenant ID
     * @param memberId Member ID
     * @param projectId Project ID
     * @return Optional containing the assignment if found
     */
    public Optional<MemberProjectAssignment> findByMemberAndProject(
            TenantId tenantId,
            MemberId memberId,
            ProjectId projectId
    ) {
        String sql = """
            SELECT id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active
            FROM member_project_assignments
            WHERE tenant_id = ? AND member_id = ? AND project_id = ?
            """;

        List<MemberProjectAssignment> results = jdbcTemplate.query(
            sql,
            new MemberProjectAssignmentRowMapper(),
            tenantId.value(),
            memberId.value(),
            projectId.value()
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Saves an assignment (insert or update).
     * 
     * Uses the composite unique constraint (tenant_id, member_id, project_id) for conflict detection
     * to match the table's unique constraint and repeatable migration pattern.
     * 
     * @param assignment The assignment to save
     */
    public void save(MemberProjectAssignment assignment) {
        String upsertSql = """
            INSERT INTO member_project_assignments 
                (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET
                is_active = EXCLUDED.is_active,
                assigned_at = EXCLUDED.assigned_at,
                assigned_by = EXCLUDED.assigned_by
            """;

        jdbcTemplate.update(
            upsertSql,
            assignment.getId().value(),
            assignment.getTenantId().value(),
            assignment.getMemberId().value(),
            assignment.getProjectId().value(),
            Timestamp.from(assignment.getAssignedAt()),
            assignment.getAssignedBy() != null ? assignment.getAssignedBy().value() : null,
            assignment.isActive()
        );
    }

    /**
     * Deletes an assignment.
     * 
     * @param id Assignment ID
     */
    public void delete(MemberProjectAssignmentId id) {
        String sql = "DELETE FROM member_project_assignments WHERE id = ?";
        jdbcTemplate.update(sql, id.value());
    }

    /**
     * Row mapper for AssignedProjectInfo projection.
     */
    private static class AssignedProjectInfoRowMapper implements RowMapper<AssignedProjectInfo> {
        @Override
        public AssignedProjectInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AssignedProjectInfo(
                rs.getObject("project_id", UUID.class),
                rs.getString("code"),
                rs.getString("name")
            );
        }
    }

    /**
     * Row mapper for MemberProjectAssignment entity.
     */
    private static class MemberProjectAssignmentRowMapper implements RowMapper<MemberProjectAssignment> {
        @Override
        public MemberProjectAssignment mapRow(ResultSet rs, int rowNum) throws SQLException {
            UUID assignedById = (UUID) rs.getObject("assigned_by");
            
            return new MemberProjectAssignment(
                MemberProjectAssignmentId.of(rs.getObject("id", UUID.class)),
                TenantId.of(rs.getObject("tenant_id", UUID.class)),
                MemberId.of(rs.getObject("member_id", UUID.class)),
                ProjectId.of(rs.getObject("project_id", UUID.class)),
                rs.getTimestamp("assigned_at").toInstant(),
                assignedById != null ? MemberId.of(assignedById) : null,
                rs.getBoolean("is_active")
            );
        }
    }
}
