package com.worklog.domain.approval;

import com.worklog.domain.absence.AbsenceId;
import com.worklog.domain.approval.events.MonthApproved;
import com.worklog.domain.approval.events.MonthRejected;
import com.worklog.domain.approval.events.MonthSubmittedForApproval;
import com.worklog.domain.approval.events.MonthlyApprovalCreated;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.AggregateRoot;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.shared.FiscalMonthPeriod;
import com.worklog.domain.worklog.WorkLogEntryId;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MonthlyApproval aggregate root.
 *
 * Represents a fiscal month period for a member's time tracking that can be
 * submitted for manager approval. Coordinates the approval workflow for all
 * work log entries and absences within a fiscal month.
 *
 * Invariants:
 * - Cannot submit if status is SUBMITTED or APPROVED
 * - Cannot approve/reject if status is not SUBMITTED
 * - Submission changes all associated WorkLogEntry/Absence status to SUBMITTED
 * - Approval changes all associated entries to APPROVED (permanently read-only)
 * - Rejection returns all associated entries to DRAFT (editable again)
 * - Rejection reason is required and max 1000 characters
 *
 * Status transitions:
 * - PENDING → SUBMITTED (engineer submits)
 * - SUBMITTED → APPROVED (manager approves)
 * - SUBMITTED → REJECTED (manager rejects with reason)
 * - REJECTED → SUBMITTED (engineer resubmits after corrections)
 *
 * Note: Manager permission validation (checking if reviewedBy is member's manager)
 * is done at the service layer, not in this aggregate.
 */
public class MonthlyApproval extends AggregateRoot<MonthlyApprovalId> {

    private MonthlyApprovalId id;
    private MemberId memberId;
    private FiscalMonthPeriod fiscalMonth;
    private ApprovalStatus status;
    private Instant submittedAt;
    private MemberId submittedBy;
    private Instant reviewedAt;
    private MemberId reviewedBy;
    private String rejectionReason;
    private Set<UUID> workLogEntryIds;
    private Set<UUID> absenceIds;
    private Instant createdAt;

    // Private constructor for factory methods
    private MonthlyApproval() {
        this.workLogEntryIds = new HashSet<>();
        this.absenceIds = new HashSet<>();
    }

    /**
     * Creates a new monthly approval record in PENDING status.
     *
     * @param memberId Member whose time is being tracked
     * @param fiscalMonth Fiscal month period
     * @return New MonthlyApproval instance with MonthlyApprovalCreated event
     */
    public static MonthlyApproval create(MemberId memberId, FiscalMonthPeriod fiscalMonth) {
        MonthlyApproval approval = new MonthlyApproval();
        MonthlyApprovalId approvalId = MonthlyApprovalId.generate();

        MonthlyApprovalCreated event = MonthlyApprovalCreated.create(approvalId, memberId, fiscalMonth);

        approval.raiseEvent(event);
        return approval;
    }

    /**
     * Submits the month for manager approval.
     *
     * @param submittedBy Who is submitting (typically the member or a proxy)
     * @param workLogEntryIds Set of work log entry IDs for this month
     * @param absenceIds Set of absence IDs for this month
     * @throws DomainException if submission is not allowed
     */
    public void submit(MemberId submittedBy, Set<WorkLogEntryId> workLogEntryIds, Set<AbsenceId> absenceIds) {
        if (!canSubmit()) {
            throw new DomainException(
                    "CANNOT_SUBMIT_MONTH",
                    "Cannot submit month in " + status + " status. Can only submit from PENDING or REJECTED status.");
        }

        Set<UUID> workLogUuids =
                workLogEntryIds.stream().map(WorkLogEntryId::value).collect(Collectors.toSet());

        Set<UUID> absenceUuids = absenceIds.stream().map(AbsenceId::value).collect(Collectors.toSet());

        MonthSubmittedForApproval event =
                MonthSubmittedForApproval.create(this.id, submittedBy, workLogUuids, absenceUuids);

        raiseEvent(event);
    }

    /**
     * Approves the submitted month.
     *
     * @param reviewedBy Manager who is approving
     * @throws DomainException if approval is not allowed
     */
    public void approve(MemberId reviewedBy) {
        if (!canApprove()) {
            throw new DomainException(
                    "CANNOT_APPROVE_MONTH", "Cannot approve month in " + status + " status. Month must be SUBMITTED.");
        }

        MonthApproved event = MonthApproved.create(this.id, reviewedBy);
        raiseEvent(event);
    }

    /**
     * Rejects the submitted month with a reason.
     *
     * @param reviewedBy Manager who is rejecting
     * @param rejectionReason Reason for rejection (required, max 1000 chars)
     * @throws DomainException if rejection is not allowed or reason is invalid
     */
    public void reject(MemberId reviewedBy, String rejectionReason) {
        if (!canReject()) {
            throw new DomainException(
                    "CANNOT_REJECT_MONTH", "Cannot reject month in " + status + " status. Month must be SUBMITTED.");
        }

        validateRejectionReason(rejectionReason);

        MonthRejected event = MonthRejected.create(this.id, reviewedBy, rejectionReason);
        raiseEvent(event);
    }

    // Query methods

    public boolean canSubmit() {
        return status.canSubmit();
    }

    public boolean canApprove() {
        return status.canApprove();
    }

    public boolean canReject() {
        return status.canReject();
    }

    public boolean isReadOnly() {
        return status.isPermanentlyLocked() || status.isPendingReview();
    }

    public boolean isEditable() {
        return status.isEditable();
    }

    // Event application methods

    @Override
    public String getAggregateType() {
        return "MonthlyApproval";
    }

    @Override
    protected void apply(DomainEvent event) {
        switch (event) {
            case MonthlyApprovalCreated e -> apply(e);
            case MonthSubmittedForApproval e -> apply(e);
            case MonthApproved e -> apply(e);
            case MonthRejected e -> apply(e);
            default ->
                throw new IllegalArgumentException(
                        "Unknown event type: " + event.getClass().getName());
        }
    }

    private void apply(MonthlyApprovalCreated event) {
        this.id = MonthlyApprovalId.of(event.aggregateId());
        this.memberId = MemberId.of(event.memberId());
        this.fiscalMonth = new FiscalMonthPeriod(event.fiscalMonthStart(), event.fiscalMonthEnd());
        this.status = ApprovalStatus.PENDING;
        this.createdAt = event.occurredAt();
    }

    private void apply(MonthSubmittedForApproval event) {
        this.status = ApprovalStatus.SUBMITTED;
        this.submittedAt = event.submittedAt();
        this.submittedBy = MemberId.of(event.submittedBy());
        this.workLogEntryIds = new HashSet<>(event.workLogEntryIds());
        this.absenceIds = new HashSet<>(event.absenceIds());
    }

    private void apply(MonthApproved event) {
        this.status = ApprovalStatus.APPROVED;
        this.reviewedAt = event.reviewedAt();
        this.reviewedBy = MemberId.of(event.reviewedBy());
    }

    private void apply(MonthRejected event) {
        this.status = ApprovalStatus.REJECTED;
        this.reviewedAt = event.reviewedAt();
        this.reviewedBy = MemberId.of(event.reviewedBy());
        this.rejectionReason = event.rejectionReason();
    }

    // Validation

    private static void validateRejectionReason(String rejectionReason) {
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new DomainException("REJECTION_REASON_REQUIRED", "Rejection reason is required");
        }
        if (rejectionReason.length() > 1000) {
            throw new DomainException("REJECTION_REASON_TOO_LONG", "Rejection reason must not exceed 1000 characters");
        }
    }

    // Getters

    @Override
    public MonthlyApprovalId getId() {
        return id;
    }

    public MemberId getMemberId() {
        return memberId;
    }

    public FiscalMonthPeriod getFiscalMonth() {
        return fiscalMonth;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public MemberId getSubmittedBy() {
        return submittedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public MemberId getReviewedBy() {
        return reviewedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Set<UUID> getWorkLogEntryIds() {
        return Collections.unmodifiableSet(workLogEntryIds);
    }

    public Set<UUID> getAbsenceIds() {
        return Collections.unmodifiableSet(absenceIds);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
