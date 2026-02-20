package com.worklog.application.service;

import com.worklog.application.command.CreateProjectCommand;
import com.worklog.application.command.UpdateProjectCommand;
import com.worklog.domain.shared.DomainException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminProjectService {

    private final JdbcTemplate jdbcTemplate;

    public AdminProjectService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public ProjectPage listProjects(UUID tenantId, String search, Boolean isActive, int page, int size) {
        var sb = new StringBuilder("""
            SELECT p.id, p.code, p.name, p.is_active, p.valid_from, p.valid_until,
                   (SELECT COUNT(*) FROM member_project_assignments mpa WHERE mpa.project_id = p.id AND mpa.is_active = true) AS assigned_member_count
            FROM projects p
            WHERE p.tenant_id = ?
            """);
        var countSb = new StringBuilder("SELECT COUNT(*) FROM projects p WHERE p.tenant_id = ?");
        var params = new java.util.ArrayList<Object>();
        params.add(tenantId);
        var countParams = new java.util.ArrayList<Object>();
        countParams.add(tenantId);

        if (search != null && !search.isBlank()) {
            String clause = " AND (LOWER(p.code) LIKE ? OR LOWER(p.name) LIKE ?)";
            sb.append(clause);
            countSb.append(clause);
            String pattern = "%" + search.toLowerCase() + "%";
            params.add(pattern);
            params.add(pattern);
            countParams.add(pattern);
            countParams.add(pattern);
        }
        if (isActive != null) {
            String clause = " AND p.is_active = ?";
            sb.append(clause);
            countSb.append(clause);
            params.add(isActive);
            countParams.add(isActive);
        }

        Long total = jdbcTemplate.queryForObject(countSb.toString(), Long.class, countParams.toArray());
        long totalElements = total != null ? total : 0;

        sb.append(" ORDER BY p.code LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        List<ProjectRow> content = jdbcTemplate.query(
                sb.toString(),
                (rs, rowNum) -> new ProjectRow(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getBoolean("is_active"),
                        rs.getDate("valid_from") != null
                                ? rs.getDate("valid_from").toString()
                                : null,
                        rs.getDate("valid_until") != null
                                ? rs.getDate("valid_until").toString()
                                : null,
                        rs.getInt("assigned_member_count")),
                params.toArray());

        int totalPages = (int) Math.ceil((double) totalElements / size);
        return new ProjectPage(content, totalElements, totalPages, page);
    }

    public UUID createProject(CreateProjectCommand command) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) > 0 FROM projects WHERE tenant_id = ? AND LOWER(code) = LOWER(?)",
                Boolean.class,
                command.tenantId(),
                command.code());
        if (Boolean.TRUE.equals(exists)) {
            throw new DomainException("DUPLICATE_CODE", "A project with this code already exists in your tenant");
        }

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO projects (id, tenant_id, code, name, is_active, valid_from, valid_until, created_at, updated_at)
                VALUES (?, ?, ?, ?, true, ?, ?, ?, ?)
                """,
                id,
                command.tenantId(),
                command.code(),
                command.name(),
                command.validFrom() != null ? Date.valueOf(command.validFrom()) : null,
                command.validUntil() != null ? Date.valueOf(command.validUntil()) : null,
                Timestamp.from(now),
                Timestamp.from(now));
        return id;
    }

    public void updateProject(UpdateProjectCommand command, UUID tenantId) {
        int rows = jdbcTemplate.update(
                """
                UPDATE projects SET name = ?, valid_from = ?, valid_until = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ?
                """,
                command.name(),
                command.validFrom() != null ? Date.valueOf(command.validFrom()) : null,
                command.validUntil() != null ? Date.valueOf(command.validUntil()) : null,
                Timestamp.from(Instant.now()),
                command.projectId(),
                tenantId);
        if (rows == 0) {
            throw new DomainException("PROJECT_NOT_FOUND", "Project not found");
        }
    }

    public void deactivateProject(UUID projectId, UUID tenantId) {
        int rows = jdbcTemplate.update(
                "UPDATE projects SET is_active = false, updated_at = ? WHERE id = ? AND tenant_id = ?",
                Timestamp.from(Instant.now()),
                projectId,
                tenantId);
        if (rows == 0) {
            throw new DomainException("PROJECT_NOT_FOUND", "Project not found");
        }
    }

    public void activateProject(UUID projectId, UUID tenantId) {
        int rows = jdbcTemplate.update(
                "UPDATE projects SET is_active = true, updated_at = ? WHERE id = ? AND tenant_id = ?",
                Timestamp.from(Instant.now()),
                projectId,
                tenantId);
        if (rows == 0) {
            throw new DomainException("PROJECT_NOT_FOUND", "Project not found");
        }
    }

    public record ProjectRow(
            String id,
            String code,
            String name,
            boolean isActive,
            String validFrom,
            String validUntil,
            int assignedMemberCount) {}

    public record ProjectPage(List<ProjectRow> content, long totalElements, int totalPages, int number) {}
}
