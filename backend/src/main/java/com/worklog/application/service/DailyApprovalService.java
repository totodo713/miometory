package com.worklog.application.service;

import com.worklog.application.command.ApproveDailyEntryCommand;
import com.worklog.application.command.RecallDailyApprovalCommand;
import com.worklog.application.command.RejectDailyEntryCommand;
import com.worklog.domain.notification.NotificationType;
import com.worklog.domain.shared.DomainException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DailyApprovalService {

    private final JdbcTemplate jdbcTemplate;
    private final NotificationService notificationService;

    public DailyApprovalService(JdbcTemplate jdbcTemplate, NotificationService notificationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<DailyGroupResponse> getDailyEntries(
            UUID supervisorMemberId, LocalDate dateFrom, LocalDate dateTo, UUID memberIdFilter) {
        var sb = new StringBuilder("""
            SELECT wle.id AS entry_id, wle.member_id, wle.project_id, wle.work_date, wle.hours, wle.notes, wle.status,
                   m.display_name AS member_name,
                   p.code AS project_code, p.name AS project_name,
                   dea.id AS approval_id, dea.status AS approval_status, dea.comment AS approval_comment
            FROM work_log_entries_projection wle
            JOIN members m ON m.id = wle.member_id
            JOIN projects p ON p.id = wle.project_id
            LEFT JOIN daily_entry_approvals dea ON dea.work_log_entry_id = wle.id AND dea.status != 'RECALLED'
            WHERE m.manager_id = ?
            AND wle.status IN ('SUBMITTED', 'APPROVED', 'REJECTED')
            """);
        var params = new ArrayList<Object>();
        params.add(supervisorMemberId);

        if (dateFrom != null) {
            sb.append(" AND wle.work_date >= ?");
            params.add(java.sql.Date.valueOf(dateFrom));
        }
        if (dateTo != null) {
            sb.append(" AND wle.work_date <= ?");
            params.add(java.sql.Date.valueOf(dateTo));
        }
        if (memberIdFilter != null) {
            sb.append(" AND wle.member_id = ?");
            params.add(memberIdFilter);
        }

        sb.append(" ORDER BY wle.work_date DESC, m.display_name, p.code");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sb.toString(), params.toArray());

        // Group by date, then by member
        Map<String, Map<String, List<EntryRow>>> grouped = new LinkedHashMap<>();
        for (var row : rows) {
            String date = row.get("work_date").toString();
            String memberId = row.get("member_id").toString();
            String memberName = (String) row.get("member_name");
            String key = memberId + "|" + memberName;

            grouped.computeIfAbsent(date, k -> new LinkedHashMap<>())
                    .computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new EntryRow(
                            row.get("entry_id").toString(),
                            (String) row.get("project_code"),
                            (String) row.get("project_name"),
                            ((Number) row.get("hours")).doubleValue(),
                            (String) row.get("notes"),
                            row.get("approval_id") != null
                                    ? row.get("approval_id").toString()
                                    : null,
                            row.get("approval_status") != null ? (String) row.get("approval_status") : null,
                            row.get("approval_comment") != null ? (String) row.get("approval_comment") : null));
        }

        List<DailyGroupResponse> result = new ArrayList<>();
        for (var dateEntry : grouped.entrySet()) {
            List<MemberEntryGroup> memberGroups = new ArrayList<>();
            for (var memberEntry : dateEntry.getValue().entrySet()) {
                String[] parts = memberEntry.getKey().split("\\|", 2);
                memberGroups.add(new MemberEntryGroup(parts[0], parts[1], memberEntry.getValue()));
            }
            result.add(new DailyGroupResponse(dateEntry.getKey(), memberGroups));
        }
        return result;
    }

    public void approveEntries(ApproveDailyEntryCommand command) {
        // Track entries per member for notification with correct referenceId
        Map<UUID, String> memberNames = new HashMap<>();
        Map<UUID, UUID> memberFirstEntryId = new HashMap<>();

        for (UUID entryId : command.entryIds()) {
            Map<String, Object> entry = jdbcTemplate.queryForMap(
                    "SELECT wle.member_id, m.display_name, m.manager_id FROM work_log_entries_projection wle JOIN members m ON m.id = wle.member_id WHERE wle.id = ?",
                    entryId);

            UUID memberId = (UUID) entry.get("member_id");
            UUID managerId = (UUID) entry.get("manager_id");

            if (!command.supervisorId().equals(managerId)) {
                throw new DomainException("NOT_DIRECT_REPORT", "You can only approve entries of your direct reports");
            }

            memberNames.put(memberId, (String) entry.get("display_name"));
            memberFirstEntryId.putIfAbsent(memberId, entryId);

            // Check no active approval exists
            Integer existing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM daily_entry_approvals WHERE work_log_entry_id = ? AND status != 'RECALLED'",
                    Integer.class,
                    entryId);
            if (existing != null && existing > 0) {
                throw new DomainException("ALREADY_APPROVED", "Entry already has an active approval");
            }

            jdbcTemplate.update(
                    """
                    INSERT INTO daily_entry_approvals (id, work_log_entry_id, member_id, supervisor_id, status, comment, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'APPROVED', ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    entryId,
                    memberId,
                    command.supervisorId(),
                    command.comment(),
                    Timestamp.from(Instant.now()),
                    Timestamp.from(Instant.now()));
        }

        // Send one notification per member with their own entry ID as referenceId
        for (var memberEntry : memberNames.entrySet()) {
            notificationService.createNotification(
                    memberEntry.getKey(),
                    NotificationType.DAILY_APPROVED,
                    memberFirstEntryId.get(memberEntry.getKey()),
                    "日次記録が承認されました",
                    "上司があなたの日次記録を承認しました");
        }
    }

    public void rejectEntry(RejectDailyEntryCommand command) {
        if (command.comment() == null || command.comment().isBlank()) {
            throw new DomainException("COMMENT_REQUIRED", "Comment is required when rejecting an entry");
        }

        Map<String, Object> entry = jdbcTemplate.queryForMap(
                "SELECT wle.member_id, m.manager_id FROM work_log_entries_projection wle JOIN members m ON m.id = wle.member_id WHERE wle.id = ?",
                command.entryId());

        UUID memberId = (UUID) entry.get("member_id");
        UUID managerId = (UUID) entry.get("manager_id");

        if (!command.supervisorId().equals(managerId)) {
            throw new DomainException("NOT_DIRECT_REPORT", "You can only reject entries of your direct reports");
        }

        // Check no active approval exists
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_entry_approvals WHERE work_log_entry_id = ? AND status != 'RECALLED'",
                Integer.class,
                command.entryId());
        if (existing != null && existing > 0) {
            throw new DomainException("ALREADY_APPROVED", "Entry already has an active approval decision");
        }

        jdbcTemplate.update(
                """
                INSERT INTO daily_entry_approvals (id, work_log_entry_id, member_id, supervisor_id, status, comment, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'REJECTED', ?, ?, ?)
                """,
                UUID.randomUUID(),
                command.entryId(),
                memberId,
                command.supervisorId(),
                command.comment(),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));

        notificationService.createNotification(
                memberId,
                NotificationType.DAILY_REJECTED,
                command.entryId(),
                "日次記録が差し戻されました",
                "上司があなたの日次記録を差し戻しました: " + command.comment());
    }

    public void recallApproval(RecallDailyApprovalCommand command) {
        Map<String, Object> approval;
        try {
            approval = jdbcTemplate.queryForMap(
                    "SELECT member_id, supervisor_id, status, work_log_entry_id FROM daily_entry_approvals WHERE id = ?",
                    command.approvalId());
        } catch (Exception e) {
            throw new DomainException("APPROVAL_NOT_FOUND", "Approval not found");
        }

        if (!"APPROVED".equals(approval.get("status"))) {
            throw new DomainException("INVALID_STATUS", "Only APPROVED entries can be recalled");
        }

        UUID supervisorId = (UUID) approval.get("supervisor_id");
        if (!command.supervisorId().equals(supervisorId)) {
            throw new DomainException("UNAUTHORIZED", "Only the approving supervisor can recall this approval");
        }

        jdbcTemplate.update(
                "UPDATE daily_entry_approvals SET status = 'RECALLED', updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                command.approvalId());

        UUID memberId = (UUID) approval.get("member_id");
        notificationService.createNotification(
                memberId,
                NotificationType.DAILY_RECALLED,
                (UUID) approval.get("work_log_entry_id"),
                "承認が取り消されました",
                "上司が日次記録の承認を取り消しました");
    }

    public record EntryRow(
            String entryId,
            String projectCode,
            String projectName,
            double hours,
            String comment,
            String approvalId,
            String approvalStatus,
            String approvalComment) {}

    public record MemberEntryGroup(String memberId, String memberName, List<EntryRow> entries) {}

    public record DailyGroupResponse(String date, List<MemberEntryGroup> members) {}
}
