package com.worklog.application.service;

import com.worklog.application.command.CopyFromPreviousMonthCommand;
import com.worklog.application.command.CreateWorkLogEntryCommand;
import com.worklog.application.command.DeleteWorkLogEntryCommand;
import com.worklog.application.command.RecallDailyEntriesCommand;
import com.worklog.application.command.SubmitDailyEntriesCommand;
import com.worklog.application.command.UpdateWorkLogEntryCommand;
import com.worklog.domain.approval.ApprovalStatus;
import com.worklog.domain.approval.MonthlyApproval;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.project.ProjectId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.shared.FiscalMonthPeriod;
import com.worklog.domain.shared.TimeAmount;
import com.worklog.domain.worklog.WorkLogEntry;
import com.worklog.domain.worklog.WorkLogEntryId;
import com.worklog.domain.worklog.WorkLogStatus;
import com.worklog.infrastructure.repository.JdbcApprovalRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import com.worklog.infrastructure.repository.JdbcWorkLogRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for WorkLogEntry operations.
 *
 * Coordinates work log entry-related use cases and enforces business rules,
 * including the 24-hour daily limit validation across all projects and
 * proxy entry permission validation for manager entry on behalf of subordinates.
 */
@Service
public class WorkLogEntryService {

    private static final BigDecimal MAX_DAILY_HOURS = BigDecimal.valueOf(24);

    private final JdbcWorkLogRepository workLogRepository;
    private final JdbcMemberRepository memberRepository;
    private final JdbcApprovalRepository approvalRepository;

    public WorkLogEntryService(
            JdbcWorkLogRepository workLogRepository,
            JdbcMemberRepository memberRepository,
            JdbcApprovalRepository approvalRepository) {
        this.workLogRepository = workLogRepository;
        this.memberRepository = memberRepository;
        this.approvalRepository = approvalRepository;
    }

    /**
     * Creates a new work log entry.
     *
     * Validates that the total hours for the member on the specified date
     * (including this new entry) does not exceed 24 hours.
     * Also validates proxy entry permission if enteredBy differs from memberId.
     *
     * @param command The creation command
     * @return ID of the newly created work log entry
     * @throws DomainException if 24-hour limit would be exceeded or proxy permission denied
     */
    @Transactional
    public UUID createEntry(CreateWorkLogEntryCommand command) {
        // Validate proxy entry permission if entering on behalf of someone else
        if (!command.memberId().equals(command.enteredBy())) {
            validateProxyEntryPermission(command.enteredBy(), command.memberId());
        }

        // Check for existing entry with same member, project, date
        if (workLogRepository.existsByMemberProjectAndDate(command.memberId(), command.projectId(), command.date())) {
            throw new DomainException(
                    "DUPLICATE_ENTRY",
                    "An entry already exists for this project on this date. Please update the existing entry.");
        }

        // Validate hours format (0.25h increments, max 24h)
        TimeAmount hours = TimeAmount.of(command.hours());

        // Check 24-hour daily limit
        validateDailyLimit(
                command.memberId(), command.date(), hours, null // no entry to exclude for creates
                );

        // Create the aggregate
        WorkLogEntry entry = WorkLogEntry.create(
                MemberId.of(command.memberId()),
                ProjectId.of(command.projectId()),
                command.date(),
                hours,
                command.comment(),
                MemberId.of(command.enteredBy()));

        // Persist
        workLogRepository.save(entry);

        return entry.getId().value();
    }

    /**
     * Updates an existing work log entry.
     *
     * Validates that the total hours for the member on the specified date
     * (including this updated entry) does not exceed 24 hours.
     *
     * @param command The update command
     * @throws DomainException if entry not found, not editable, or 24-hour limit would be exceeded
     */
    @Transactional
    public void updateEntry(UpdateWorkLogEntryCommand command) {
        WorkLogEntry entry = workLogRepository
                .findById(WorkLogEntryId.of(command.id()))
                .orElseThrow(() -> new DomainException("ENTRY_NOT_FOUND", "Work log entry not found: " + command.id()));

        // Check version for optimistic locking
        if (entry.getVersion() != command.version()) {
            throw new DomainException(
                    "OPTIMISTIC_LOCK_FAILURE",
                    "Entry has been modified by another user. Please refresh and try again.");
        }

        // Validate hours format
        TimeAmount hours = TimeAmount.of(command.hours());

        // Check 24-hour daily limit (excluding this entry's current hours)
        validateDailyLimit(entry.getMemberId().value(), entry.getDate(), hours, command.id());

        // Update the aggregate
        entry.update(hours, command.comment(), MemberId.of(command.updatedBy()));

        // Persist
        workLogRepository.save(entry);
    }

    /**
     * Deletes a work log entry.
     *
     * @param command The delete command
     * @throws DomainException if entry not found or not deletable
     */
    @Transactional
    public void deleteEntry(DeleteWorkLogEntryCommand command) {
        WorkLogEntry entry = workLogRepository
                .findById(WorkLogEntryId.of(command.id()))
                .orElseThrow(() -> new DomainException("ENTRY_NOT_FOUND", "Work log entry not found: " + command.id()));

        // Delete the aggregate
        entry.delete(MemberId.of(command.deletedBy()));

        // Persist
        workLogRepository.save(entry);
    }

    /**
     * Submits all DRAFT entries for a member on a specific date.
     *
     * Transitions all DRAFT entries to SUBMITTED atomically.
     * Only the member themselves can submit their own entries.
     *
     * @param command The submit command
     * @return List of updated entries (now SUBMITTED)
     * @throws DomainException if submitter is not the member, or no DRAFT entries found
     */
    @Transactional
    public List<WorkLogEntry> submitDailyEntries(SubmitDailyEntriesCommand command) {
        // Validate self-submission
        if (!command.memberId().equals(command.submittedBy())) {
            throw new DomainException(
                    "SELF_SUBMISSION_ONLY",
                    "Only the member can submit their own entries. Proxy submission is not allowed.");
        }

        // Query DRAFT entries for member + date
        List<WorkLogEntry> draftEntries = workLogRepository.findByDateRange(
                command.memberId(), command.date(), command.date(), WorkLogStatus.DRAFT);

        if (draftEntries.isEmpty()) {
            throw new DomainException(
                    "NO_DRAFT_ENTRIES",
                    "No DRAFT entries found for member " + command.memberId() + " on " + command.date());
        }

        // Transition all DRAFT entries to SUBMITTED
        MemberId submittedBy = MemberId.of(command.submittedBy());
        for (WorkLogEntry entry : draftEntries) {
            entry.changeStatus(WorkLogStatus.SUBMITTED, submittedBy);
            workLogRepository.save(entry);
        }

        return draftEntries;
    }

    /**
     * Recalls all SUBMITTED entries for a member on a specific date.
     *
     * Transitions all SUBMITTED entries back to DRAFT atomically.
     * Blocked if any entry is part of a MonthlyApproval with non-PENDING status.
     * Only the member themselves can recall their own entries.
     *
     * @param command The recall command
     * @return List of updated entries (now DRAFT)
     * @throws DomainException if recaller is not the member, no SUBMITTED entries found, or blocked by approval
     */
    @Transactional
    public List<WorkLogEntry> recallDailyEntries(RecallDailyEntriesCommand command) {
        // Validate self-recall
        if (!command.memberId().equals(command.recalledBy())) {
            throw new DomainException(
                    "SELF_RECALL_ONLY", "Only the member can recall their own entries. Proxy recall is not allowed.");
        }

        // Query SUBMITTED entries for member + date
        List<WorkLogEntry> submittedEntries = workLogRepository.findByDateRange(
                command.memberId(), command.date(), command.date(), WorkLogStatus.SUBMITTED);

        if (submittedEntries.isEmpty()) {
            throw new DomainException(
                    "NO_SUBMITTED_ENTRIES",
                    "No SUBMITTED entries found for member " + command.memberId() + " on " + command.date());
        }

        // Check if any of these entries are part of a MonthlyApproval with non-PENDING status
        FiscalMonthPeriod fiscalMonth = FiscalMonthPeriod.forDate(command.date());
        java.util.Optional<MonthlyApproval> approval =
                approvalRepository.findByMemberAndFiscalMonth(MemberId.of(command.memberId()), fiscalMonth);
        if (approval.isPresent() && approval.get().getStatus() != ApprovalStatus.PENDING) {
            // Only block if the approval actually contains any of the entries being recalled
            java.util.Set<UUID> approvalEntryIds = approval.get().getWorkLogEntryIds();
            boolean hasOverlap = submittedEntries.stream()
                    .anyMatch(entry -> approvalEntryIds.contains(entry.getId().value()));
            if (hasOverlap) {
                throw new DomainException(
                        "RECALL_BLOCKED_BY_APPROVAL",
                        "Cannot recall entries — the monthly approval for this period is in "
                                + approval.get().getStatus() + " status.");
            }
        }

        // Transition all SUBMITTED entries back to DRAFT
        MemberId recalledBy = MemberId.of(command.recalledBy());
        for (WorkLogEntry entry : submittedEntries) {
            entry.changeStatus(WorkLogStatus.DRAFT, recalledBy);
            workLogRepository.save(entry);
        }

        return submittedEntries;
    }

    /**
     * Validates that the daily total hours for a member on a specific date
     * does not exceed 24 hours.
     *
     * @param memberId Member ID
     * @param date Date to check
     * @param newHours Hours being added/updated
     * @param excludeEntryId Entry ID to exclude from calculation (for updates)
     * @throws DomainException if 24-hour limit would be exceeded
     */
    private void validateDailyLimit(UUID memberId, java.time.LocalDate date, TimeAmount newHours, UUID excludeEntryId) {
        BigDecimal existingTotal = workLogRepository.getTotalHoursForDate(memberId, date, excludeEntryId);

        BigDecimal newTotal = existingTotal.add(newHours.hours());

        if (newTotal.compareTo(MAX_DAILY_HOURS) > 0) {
            throw new DomainException(
                    "DAILY_LIMIT_EXCEEDED",
                    String.format(
                            "Daily limit of 24 hours exceeded. Current total: %s hours, Attempting to add: %s hours, Would result in: %s hours",
                            existingTotal, newHours.hours(), newTotal));
        }
    }

    /**
     * Finds a work log entry by ID.
     *
     * @param entryId ID of the entry to find
     * @return The work log entry, or null if not found
     */
    public WorkLogEntry findById(UUID entryId) {
        return workLogRepository.findById(WorkLogEntryId.of(entryId)).orElse(null);
    }

    /**
     * Gets unique project IDs from the previous fiscal month for a member.
     * This is used for the "Copy from Previous Month" feature (FR-016).
     *
     * @param command The command containing member ID and target month info
     * @return List of unique project IDs from the previous month
     */
    @Transactional(readOnly = true)
    public List<UUID> getProjectsFromPreviousMonth(CopyFromPreviousMonthCommand command) {
        // Calculate the target fiscal month period
        // The target month is what the user wants to populate, so we need the PREVIOUS month's data
        LocalDate targetDate = LocalDate.of(command.targetYear(), command.targetMonth(), 15);
        FiscalMonthPeriod targetPeriod = FiscalMonthPeriod.forDate(targetDate);
        FiscalMonthPeriod previousPeriod = targetPeriod.previous();

        // Find unique projects from the previous period
        return workLogRepository.findUniqueProjectIdsByDateRange(
                command.memberId(), previousPeriod.startDate(), previousPeriod.endDate());
    }

    /**
     * Gets the previous fiscal month period for a given target year/month.
     *
     * @param targetYear The target year
     * @param targetMonth The target month (1-12)
     * @return The previous fiscal month period
     */
    public FiscalMonthPeriod getPreviousFiscalMonth(int targetYear, int targetMonth) {
        LocalDate targetDate = LocalDate.of(targetYear, targetMonth, 15);
        FiscalMonthPeriod targetPeriod = FiscalMonthPeriod.forDate(targetDate);
        return targetPeriod.previous();
    }

    /**
     * Validates that a manager has permission to enter time on behalf of a subordinate.
     *
     * A manager can enter time for their direct or indirect subordinates (those in their
     * management chain). This supports the Manager Proxy Entry feature (US7).
     *
     * @param managerId The manager attempting to enter time
     * @param memberId The member whose time is being entered
     * @throws DomainException if the manager does not have permission
     */
    public void validateProxyEntryPermission(UUID managerId, UUID memberId) {
        MemberId managerMemberId = MemberId.of(managerId);
        MemberId targetMemberId = MemberId.of(memberId);

        // Check if the member is a subordinate (direct or indirect) of the manager
        boolean isSubordinate = memberRepository.isSubordinateOf(managerMemberId, targetMemberId);

        if (!isSubordinate) {
            throw new DomainException(
                    "PROXY_ENTRY_NOT_ALLOWED",
                    String.format(
                            "Manager %s does not have permission to enter time on behalf of member %s. "
                                    + "Proxy entry is only allowed for direct or indirect subordinates.",
                            managerId, memberId));
        }
    }

    /**
     * Checks if a manager can enter time on behalf of a member.
     * This is a non-throwing version for UI checks.
     *
     * @param managerId The manager
     * @param memberId The member
     * @return true if the manager can enter time for the member
     */
    public boolean canEnterTimeFor(UUID managerId, UUID memberId) {
        if (managerId.equals(memberId)) {
            return true; // Anyone can enter their own time
        }
        return memberRepository.isSubordinateOf(MemberId.of(managerId), MemberId.of(memberId));
    }
}
