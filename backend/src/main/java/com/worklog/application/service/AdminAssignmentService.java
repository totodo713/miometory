package com.worklog.application.service;

import com.worklog.application.command.CreateAssignmentCommand;
import com.worklog.domain.shared.DomainException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminAssignmentService {

    private final JdbcTemplate jdbcTemplate;

    public AdminAssignmentService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<AssignmentRow> listByMember(UUID memberId, UUID tenantId) {
        return jdbcTemplate.query(
                """
                SELECT a.id, a.member_id, a.project_id, a.is_active, a.assigned_at,
                       m.display_name AS member_name, m.email AS member_email,
                       p.code AS project_code, p.name AS project_name
                FROM member_project_assignments a
                JOIN members m ON m.id = a.member_id
                JOIN projects p ON p.id = a.project_id
                WHERE a.member_id = ? AND a.tenant_id = ?
                ORDER BY p.code
                """,
                (rs, rowNum) -> new AssignmentRow(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getObject("member_id", UUID.class).toString(),
                        rs.getString("member_name"),
                        rs.getString("member_email"),
                        rs.getObject("project_id", UUID.class).toString(),
                        rs.getString("project_code"),
                        rs.getString("project_name"),
                        rs.getBoolean("is_active"),
                        rs.getTimestamp("assigned_at").toInstant().toString()),
                memberId,
                tenantId);
    }

    @Transactional(readOnly = true)
    public List<AssignmentRow> listByProject(UUID projectId, UUID tenantId) {
        return jdbcTemplate.query(
                """
                SELECT a.id, a.member_id, a.project_id, a.is_active, a.assigned_at,
                       m.display_name AS member_name, m.email AS member_email,
                       p.code AS project_code, p.name AS project_name
                FROM member_project_assignments a
                JOIN members m ON m.id = a.member_id
                JOIN projects p ON p.id = a.project_id
                WHERE a.project_id = ? AND a.tenant_id = ?
                ORDER BY m.display_name
                """,
                (rs, rowNum) -> new AssignmentRow(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getObject("member_id", UUID.class).toString(),
                        rs.getString("member_name"),
                        rs.getString("member_email"),
                        rs.getObject("project_id", UUID.class).toString(),
                        rs.getString("project_code"),
                        rs.getString("project_name"),
                        rs.getBoolean("is_active"),
                        rs.getTimestamp("assigned_at").toInstant().toString()),
                projectId,
                tenantId);
    }

    public UUID createAssignment(CreateAssignmentCommand command) {
        // Verify member belongs to tenant
        Integer memberCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM members WHERE id = ? AND tenant_id = ? AND is_active = true",
                Integer.class,
                command.memberId(),
                command.tenantId());
        if (memberCount == null || memberCount == 0) {
            throw new DomainException("MEMBER_NOT_FOUND", "Active member not found in your tenant");
        }

        // Verify project belongs to tenant
        Integer projectCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM projects WHERE id = ? AND tenant_id = ? AND is_active = true",
                Integer.class,
                command.projectId(),
                command.tenantId());
        if (projectCount == null || projectCount == 0) {
            throw new DomainException("PROJECT_NOT_FOUND", "Active project not found in your tenant");
        }

        // Check for duplicate assignment
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) > 0 FROM member_project_assignments WHERE tenant_id = ? AND member_id = ? AND project_id = ?",
                Boolean.class,
                command.tenantId(),
                command.memberId(),
                command.projectId());
        if (Boolean.TRUE.equals(exists)) {
            throw new DomainException("DUPLICATE_ASSIGNMENT", "This member is already assigned to this project");
        }

        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, true)
                """,
                id,
                command.tenantId(),
                command.memberId(),
                command.projectId(),
                Timestamp.from(Instant.now()),
                command.assignedBy());
        return id;
    }

    public void deactivateAssignment(UUID assignmentId, UUID tenantId) {
        int rows = jdbcTemplate.update(
                "UPDATE member_project_assignments SET is_active = false WHERE id = ? AND tenant_id = ?",
                assignmentId,
                tenantId);
        if (rows == 0) {
            throw new DomainException("ASSIGNMENT_NOT_FOUND", "Assignment not found");
        }
    }

    public void activateAssignment(UUID assignmentId, UUID tenantId) {
        int rows = jdbcTemplate.update(
                "UPDATE member_project_assignments SET is_active = true WHERE id = ? AND tenant_id = ?",
                assignmentId,
                tenantId);
        if (rows == 0) {
            throw new DomainException("ASSIGNMENT_NOT_FOUND", "Assignment not found");
        }
    }

    /**
     * Validates that a member is a direct report of the given supervisor.
     */
    public void validateDirectReport(UUID memberId, UUID supervisorMemberId) {
        UUID managerId =
                jdbcTemplate.queryForObject("SELECT manager_id FROM members WHERE id = ?", UUID.class, memberId);
        if (managerId == null || !managerId.equals(supervisorMemberId)) {
            throw new DomainException("NOT_DIRECT_REPORT", "You can only manage assignments for your direct reports");
        }
    }

    public record AssignmentRow(
            String id,
            String memberId,
            String memberName,
            String memberEmail,
            String projectId,
            String projectCode,
            String projectName,
            boolean isActive,
            String assignedAt) {}
}
