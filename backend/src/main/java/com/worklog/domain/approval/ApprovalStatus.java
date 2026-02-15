package com.worklog.domain.approval;

/**
 * Status of a monthly time approval.
 *
 * <p>State transitions:
 * <ul>
 *   <li>PENDING → SUBMITTED (engineer submits for approval)</li>
 *   <li>SUBMITTED → APPROVED (manager approves)</li>
 *   <li>SUBMITTED → REJECTED (manager rejects)</li>
 *   <li>REJECTED → SUBMITTED (engineer resubmits after corrections)</li>
 * </ul>
 *
 * <p>Read-only rules:
 * <ul>
 *   <li>PENDING: editable by member</li>
 *   <li>SUBMITTED: read-only for member, awaiting manager action</li>
 *   <li>APPROVED: permanently read-only for all users</li>
 *   <li>REJECTED: editable by member again</li>
 * </ul>
 */
public enum ApprovalStatus {
    /**
     * Initial state when monthly approval is created.
     * Member can edit their time entries.
     */
    PENDING,

    /**
     * Member has submitted the month for manager approval.
     * Time entries are read-only for the member.
     */
    SUBMITTED,

    /**
     * Manager has approved the submitted time.
     * Time entries are permanently read-only.
     */
    APPROVED,

    /**
     * Manager has rejected the submitted time with a reason.
     * Member can edit time entries and resubmit.
     */
    REJECTED;

    /**
     * Checks if this status allows editing by the member.
     *
     * @return true if member can edit time entries in this status
     */
    public boolean isEditable() {
        return this == PENDING || this == REJECTED;
    }

    /**
     * Checks if this status is permanently locked.
     *
     * @return true if time entries cannot be modified by anyone
     */
    public boolean isPermanentlyLocked() {
        return this == APPROVED;
    }

    /**
     * Checks if this status is awaiting manager action.
     *
     * @return true if manager needs to approve or reject
     */
    public boolean isPendingReview() {
        return this == SUBMITTED;
    }

    /**
     * Checks if submission is allowed from this status.
     *
     * @return true if can transition to SUBMITTED
     */
    public boolean canSubmit() {
        return this == PENDING || this == REJECTED;
    }

    /**
     * Checks if approval is allowed from this status.
     *
     * @return true if can transition to APPROVED
     */
    public boolean canApprove() {
        return this == SUBMITTED;
    }

    /**
     * Checks if rejection is allowed from this status.
     *
     * @return true if can transition to REJECTED
     */
    public boolean canReject() {
        return this == SUBMITTED;
    }
}
