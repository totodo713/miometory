package com.worklog.domain.worklog;

/**
 * Represents the status of a work log entry in the approval workflow.
 *
 * Status transitions:
 * - DRAFT → SUBMITTED (when member submits for approval)
 * - SUBMITTED → APPROVED (when manager approves)
 * - SUBMITTED → REJECTED (when manager rejects)
 * - REJECTED → DRAFT (auto-transition, entry becomes editable again)
 *
 * Business rules:
 * - DRAFT entries are editable and deletable
 * - SUBMITTED entries are read-only (pending manager approval)
 * - APPROVED entries are read-only (locked permanently)
 * - REJECTED entries auto-transition to DRAFT for correction
 */
public enum WorkLogStatus {
    /**
     * Initial state - entry is being edited by the member.
     * Editable and deletable.
     */
    DRAFT,

    /**
     * Entry has been submitted for manager approval.
     * Read-only until approved or rejected.
     */
    SUBMITTED,

    /**
     * Entry has been approved by manager.
     * Read-only and locked permanently.
     */
    APPROVED,
    /**
     * Entry was rejected by manager.
     * Auto-transitions to DRAFT for correction.
     */
    REJECTED;

    /**
     * Checks if this status allows editing.
     */
    public boolean isEditable() {
        return this == DRAFT;
    }

    /**
     * Checks if this status allows deletion.
     */
    public boolean isDeletable() {
        return this == DRAFT;
    }

    /**
     * Checks if transition to the target status is valid.
     */
    public boolean canTransitionTo(WorkLogStatus target) {
        return switch (this) {
            case DRAFT -> target == SUBMITTED;
            case SUBMITTED ->
                target == APPROVED || target == REJECTED || target == DRAFT; // Allow direct to DRAFT on rejection
            case REJECTED -> target == DRAFT;
            case APPROVED -> false; // Approved entries cannot transition
        };
    }
}
