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
import com.worklog.infrastructure.repository.JdbcWorkLogRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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

    public ApprovalService(
            JdbcApprovalRepository approvalRepository,
            JdbcWorkLogRepository workLogRepository,
            JdbcAbsenceRepository absenceRepository) {
        this.approvalRepository = approvalRepository;
        this.workLogRepository = workLogRepository;
        this.absenceRepository = absenceRepository;
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

        // Update all work log entries to SUBMITTED status
        for (UUID entryId : workLogEntryIds) {
            WorkLogEntry entry = workLogRepository
                    .findById(WorkLogEntryId.of(entryId))
                    .orElseThrow(() ->
                            new DomainException("WORK_LOG_ENTRY_NOT_FOUND", "Work log entry not found: " + entryId));
            entry.changeStatus(WorkLogStatus.SUBMITTED, command.submittedBy());
            workLogRepository.save(entry);
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
}
