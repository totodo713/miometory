package com.worklog.application.approval;

import com.worklog.domain.absence.Absence;
import com.worklog.domain.absence.AbsenceId;
import com.worklog.domain.absence.AbsenceStatus;
import com.worklog.domain.approval.MonthlyApproval;
import com.worklog.domain.approval.MonthlyApprovalId;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.worklog.WorkLogEntry;
import com.worklog.domain.worklog.WorkLogEntryId;
import com.worklog.domain.worklog.WorkLogStatus;
import com.worklog.infrastructure.repository.JdbcAbsenceRepository;
import com.worklog.infrastructure.repository.JdbcApprovalRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import com.worklog.infrastructure.repository.JdbcWorkLogRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for monthly approval workflow.
 *
 * Coordinates the approval workflow across multiple aggregates:
 * - MonthlyApproval (approval record)
 * - WorkLogEntry (work hours)
 * - Absence (absence hours)
 *
 * Responsibilities:
 * - Submit month for approval (transitions entries to SUBMITTED)
 * - Approve submitted month (transitions entries to APPROVED - permanent lock)
 * - Reject submitted month (transitions entries back to DRAFT - editable)
 * - Validate manager permissions
 * - Ensure atomic updates across aggregates
 */
@Service
public class ApprovalService {

    private final JdbcApprovalRepository approvalRepository;
    private final JdbcWorkLogRepository workLogRepository;
    private final JdbcAbsenceRepository absenceRepository;
    private final JdbcMemberRepository memberRepository;
    private final JdbcTemplate jdbcTemplate;

    public ApprovalService(
            JdbcApprovalRepository approvalRepository,
            JdbcWorkLogRepository workLogRepository,
            JdbcAbsenceRepository absenceRepository,
            JdbcMemberRepository memberRepository,
            JdbcTemplate jdbcTemplate) {
        this.approvalRepository = approvalRepository;
        this.workLogRepository = workLogRepository;
        this.absenceRepository = absenceRepository;
        this.memberRepository = memberRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Submit a month's time entries for manager approval.
     *
     * Process:
     * 1. Find or create MonthlyApproval aggregate
     * 2. Find all WorkLogEntry and Absence entries for the fiscal month
     * 3. Call aggregate.submit() with entry IDs
     * 4. Update all WorkLogEntry and Absence statuses to SUBMITTED
     * 5. Save all changes atomically
     *
     * @param command Submit command with member, fiscal month, submittedBy
     * @return ID of the monthly approval record
     * @throws DomainException if submission is not allowed
     */
    @Transactional
    public MonthlyApprovalId submitMonth(SubmitMonthForApprovalCommand command) {
        // Validate proxy permission if submitting on behalf of someone else
        if (!command.memberId().equals(command.submittedBy())) {
            if (!memberRepository.isSubordinateOf(command.submittedBy(), command.memberId())) {
                throw new DomainException(
                        "PROXY_ENTRY_NOT_ALLOWED",
                        "Manager does not have permission to submit on behalf of member " + command.memberId());
            }
        }

        // Find or create approval record
        MonthlyApproval approval = approvalRepository
                .findByMemberAndFiscalMonth(command.memberId(), command.fiscalMonth())
                .orElseGet(() -> MonthlyApproval.create(command.memberId(), command.fiscalMonth()));

        // Find all work log entries and absences for this fiscal month
        Set<UUID> workLogEntryIds = new HashSet<>(approvalRepository.findWorkLogEntryIds(
                command.memberId().value(),
                command.fiscalMonth().startDate(),
                command.fiscalMonth().endDate()));

        Set<UUID> absenceIds = new HashSet<>(approvalRepository.findAbsenceIds(
                command.memberId().value(),
                command.fiscalMonth().startDate(),
                command.fiscalMonth().endDate()));

        // Submit the approval (this validates status and generates event)
        Set<WorkLogEntryId> workLogIds =
                workLogEntryIds.stream().map(WorkLogEntryId::of).collect(Collectors.toSet());

        Set<AbsenceId> absIds = absenceIds.stream().map(AbsenceId::of).collect(Collectors.toSet());

        approval.submit(command.submittedBy(), workLogIds, absIds);

        // Update all work log entries to SUBMITTED status (skip entries already SUBMITTED via daily submit)
        for (UUID entryId : workLogEntryIds) {
            WorkLogEntry entry = workLogRepository
                    .findById(WorkLogEntryId.of(entryId))
                    .orElseThrow(() ->
                            new DomainException("WORK_LOG_ENTRY_NOT_FOUND", "Work log entry not found: " + entryId));
            if (entry.getStatus() == WorkLogStatus.DRAFT) {
                entry.changeStatus(WorkLogStatus.SUBMITTED, command.submittedBy());
                workLogRepository.save(entry);
            }
        }

        // Update all absences to SUBMITTED status
        for (UUID absenceId : absenceIds) {
            Absence absence = absenceRepository
                    .findById(AbsenceId.of(absenceId))
                    .orElseThrow(() -> new DomainException("ABSENCE_NOT_FOUND", "Absence not found: " + absenceId));
            absence.changeStatus(AbsenceStatus.SUBMITTED, command.submittedBy());
            absenceRepository.save(absence);
        }

        // Save the approval aggregate
        approvalRepository.save(approval);

        return approval.getId();
    }

    /**
     * Approve a submitted month.
     *
     * Process:
     * 1. Load MonthlyApproval aggregate
     * 2. Validate manager permission (TODO: check if reviewedBy is member's manager)
     * 3. Call aggregate.approve()
     * 4. Update all associated WorkLogEntry and Absence statuses to APPROVED
     * 5. Save all changes atomically
     *
     * @param command Approve command with approval ID and reviewedBy
     * @throws DomainException if approval is not allowed or manager permission denied
     */
    @Transactional
    public void approveMonth(ApproveMonthCommand command) {
        // Load approval aggregate
        MonthlyApproval approval = approvalRepository
                .findById(command.approvalId())
                .orElseThrow(() -> new DomainException(
                        "APPROVAL_NOT_FOUND", "Monthly approval not found: " + command.approvalId()));

        // TODO: Validate manager permission
        // For now, we skip this validation until Member aggregate with manager relationship is implemented
        // validateManagerPermission(approval.getMemberId(), command.reviewedBy());

        // Approve the month (this validates status and generates event)
        approval.approve(command.reviewedBy());

        // Update all work log entries to APPROVED status
        for (UUID entryId : approval.getWorkLogEntryIds()) {
            WorkLogEntry entry = workLogRepository
                    .findById(WorkLogEntryId.of(entryId))
                    .orElseThrow(() ->
                            new DomainException("WORK_LOG_ENTRY_NOT_FOUND", "Work log entry not found: " + entryId));
            entry.changeStatus(WorkLogStatus.APPROVED, command.reviewedBy());
            workLogRepository.save(entry);
        }

        // Update all absences to APPROVED status
        for (UUID absenceId : approval.getAbsenceIds()) {
            Absence absence = absenceRepository
                    .findById(AbsenceId.of(absenceId))
                    .orElseThrow(() -> new DomainException("ABSENCE_NOT_FOUND", "Absence not found: " + absenceId));
            absence.changeStatus(AbsenceStatus.APPROVED, command.reviewedBy());
            absenceRepository.save(absence);
        }

        // Save the approval aggregate
        approvalRepository.save(approval);
    }

    /**
     * Reject a submitted month with a reason.
     *
     * Process:
     * 1. Load MonthlyApproval aggregate
     * 2. Validate manager permission (TODO: check if reviewedBy is member's manager)
     * 3. Call aggregate.reject(reason)
     * 4. Update all associated WorkLogEntry and Absence statuses back to DRAFT
     * 5. Save all changes atomically
     *
     * @param command Reject command with approval ID, reviewedBy, and reason
     * @throws DomainException if rejection is not allowed or manager permission denied
     */
    @Transactional
    public void rejectMonth(RejectMonthCommand command) {
        // Load approval aggregate
        MonthlyApproval approval = approvalRepository
                .findById(command.approvalId())
                .orElseThrow(() -> new DomainException(
                        "APPROVAL_NOT_FOUND", "Monthly approval not found: " + command.approvalId()));

        // TODO: Validate manager permission
        // For now, we skip this validation until Member aggregate with manager relationship is implemented
        // validateManagerPermission(approval.getMemberId(), command.reviewedBy());

        // Reject the month (this validates status, reason, and generates event)
        approval.reject(command.reviewedBy(), command.rejectionReason());

        // Update all work log entries back to DRAFT status
        for (UUID entryId : approval.getWorkLogEntryIds()) {
            WorkLogEntry entry = workLogRepository
                    .findById(WorkLogEntryId.of(entryId))
                    .orElseThrow(() ->
                            new DomainException("WORK_LOG_ENTRY_NOT_FOUND", "Work log entry not found: " + entryId));
            entry.changeStatus(WorkLogStatus.DRAFT, command.reviewedBy());
            workLogRepository.save(entry);
        }

        // Update all absences back to DRAFT status
        for (UUID absenceId : approval.getAbsenceIds()) {
            Absence absence = absenceRepository
                    .findById(AbsenceId.of(absenceId))
                    .orElseThrow(() -> new DomainException("ABSENCE_NOT_FOUND", "Absence not found: " + absenceId));
            absence.changeStatus(AbsenceStatus.DRAFT, command.reviewedBy());
            absenceRepository.save(absence);
        }

        // Save the approval aggregate
        approvalRepository.save(approval);
    }

    /**
     * Validate that the reviewer is the member's manager.
     *
     * TODO: Implement this when Member aggregate includes manager relationship.
     * For now, this is a placeholder that always passes.
     *
     * @param memberId Member whose time is being reviewed
     * @param reviewedBy Manager who is reviewing
     * @throws DomainException if reviewer is not the member's manager
     */
    private void validateManagerPermission(MemberId memberId, MemberId reviewedBy) {
        // TODO: Implement manager permission check
        // Example implementation:
        // Member member = memberRepository.findById(memberId)
        //     .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found"));
        //
        // if (!member.getManagerId().equals(reviewedBy)) {
        //     throw new DomainException(
        //         "MANAGER_PERMISSION_DENIED",
        //         "Only the member's direct manager can approve or reject their time"
        //     );
        // }
    }

    @Transactional(readOnly = true)
    public MonthlyApprovalDetail getMonthlyApprovalDetail(MonthlyApprovalId approvalId) {
        MonthlyApproval approval = approvalRepository
                .findById(approvalId)
                .orElseThrow(
                        () -> new DomainException("APPROVAL_NOT_FOUND", "Monthly approval not found: " + approvalId));

        var member = memberRepository
                .findById(approval.getMemberId())
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found"));

        // Project breakdown
        List<Map<String, Object>> projectRows = jdbcTemplate.queryForList(
                """
                SELECT p.code AS project_code, p.name AS project_name, SUM(wle.hours) AS total_hours
                FROM work_log_entries_projection wle
                JOIN projects p ON p.id = wle.project_id
                WHERE wle.member_id = ? AND wle.work_date BETWEEN ? AND ?
                GROUP BY p.code, p.name
                ORDER BY p.code
                """,
                approval.getMemberId().value(),
                java.sql.Date.valueOf(approval.getFiscalMonth().startDate()),
                java.sql.Date.valueOf(approval.getFiscalMonth().endDate()));

        List<ProjectBreakdown> projectBreakdowns = projectRows.stream()
                .map(row -> new ProjectBreakdown(
                        (String) row.get("project_code"),
                        (String) row.get("project_name"),
                        ((Number) row.get("total_hours")).doubleValue()))
                .toList();

        double totalWorkHours =
                projectBreakdowns.stream().mapToDouble(ProjectBreakdown::hours).sum();

        // Absence summary
        Double absenceHours = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(hours), 0) FROM absence_projection
                WHERE member_id = ? AND start_date >= ? AND start_date <= ?
                """,
                Double.class,
                approval.getMemberId().value(),
                java.sql.Date.valueOf(approval.getFiscalMonth().startDate()),
                java.sql.Date.valueOf(approval.getFiscalMonth().endDate()));
        double totalAbsenceHours = absenceHours != null ? absenceHours : 0;

        // Daily approval status summary
        List<Map<String, Object>> dailyStatusRows = jdbcTemplate.queryForList(
                """
                SELECT dea.status, COUNT(*) AS cnt
                FROM daily_entry_approvals dea
                JOIN work_log_entries_projection wle ON wle.id = dea.work_log_entry_id
                WHERE wle.member_id = ? AND wle.work_date BETWEEN ? AND ? AND dea.status != 'RECALLED'
                GROUP BY dea.status
                """,
                approval.getMemberId().value(),
                java.sql.Date.valueOf(approval.getFiscalMonth().startDate()),
                java.sql.Date.valueOf(approval.getFiscalMonth().endDate()));

        int approvedCount = 0;
        int rejectedCount = 0;
        for (var row : dailyStatusRows) {
            String status = (String) row.get("status");
            int cnt = ((Number) row.get("cnt")).intValue();
            if ("APPROVED".equals(status)) approvedCount = cnt;
            else if ("REJECTED".equals(status)) rejectedCount = cnt;
        }

        // Count total entries and unapproved
        Integer totalEntries = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_log_entries_projection WHERE member_id = ? AND work_date BETWEEN ? AND ?",
                Integer.class,
                approval.getMemberId().value(),
                java.sql.Date.valueOf(approval.getFiscalMonth().startDate()),
                java.sql.Date.valueOf(approval.getFiscalMonth().endDate()));
        int total = totalEntries != null ? totalEntries : 0;
        int unapprovedCount = total - approvedCount - rejectedCount;

        DailyApprovalSummary dailySummary =
                new DailyApprovalSummary(approvedCount, rejectedCount, Math.max(0, unapprovedCount));

        // Unresolved daily rejections
        List<Map<String, Object>> unresolvedRows = jdbcTemplate.queryForList(
                """
                SELECT wle.id AS entry_id, wle.work_date, p.code AS project_code, dea.comment
                FROM daily_entry_approvals dea
                JOIN work_log_entries_projection wle ON wle.id = dea.work_log_entry_id
                JOIN projects p ON p.id = wle.project_id
                WHERE wle.member_id = ? AND wle.work_date BETWEEN ? AND ?
                AND dea.status = 'REJECTED'
                ORDER BY wle.work_date
                """,
                approval.getMemberId().value(),
                java.sql.Date.valueOf(approval.getFiscalMonth().startDate()),
                java.sql.Date.valueOf(approval.getFiscalMonth().endDate()));

        List<UnresolvedEntry> unresolvedEntries = unresolvedRows.stream()
                .map(row -> new UnresolvedEntry(
                        row.get("entry_id").toString(),
                        row.get("work_date").toString(),
                        (String) row.get("project_code"),
                        (String) row.get("comment")))
                .toList();

        return new MonthlyApprovalDetail(
                approval.getId().value().toString(),
                approval.getStatus().toString(),
                member.getDisplayName(),
                approval.getFiscalMonth().startDate().toString(),
                approval.getFiscalMonth().endDate().toString(),
                totalWorkHours,
                totalAbsenceHours,
                projectBreakdowns,
                dailySummary,
                unresolvedEntries);
    }

    public record MonthlyApprovalDetail(
            String approvalId,
            String status,
            String memberName,
            String fiscalMonthStart,
            String fiscalMonthEnd,
            double totalWorkHours,
            double totalAbsenceHours,
            List<ProjectBreakdown> projectBreakdown,
            DailyApprovalSummary dailyApprovalSummary,
            List<UnresolvedEntry> unresolvedEntries) {}

    public record ProjectBreakdown(String projectCode, String projectName, double hours) {}

    public record DailyApprovalSummary(int approvedCount, int rejectedCount, int unapprovedCount) {}

    public record UnresolvedEntry(String entryId, String date, String projectCode, String rejectionComment) {}
}
