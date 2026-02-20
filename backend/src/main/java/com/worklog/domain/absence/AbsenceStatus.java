package com.worklog.domain.absence;

/**
 * Enumeration of absence record statuses.
 *
 * Represents the lifecycle states of an absence record similar to work log entries.
 */
public enum AbsenceStatus {
    /**
     * Initial state - absence recorded but not yet submitted
     */
    DRAFT,

    /**
     * Absence submitted for approval
     */
    SUBMITTED,

    /**
     * Absence approved by manager
     */
    APPROVED,

    /**
     * Absence rejected by manager
     */
    REJECTED;

    /**
     * Checks if this status allows editing.
     */
    public boolean isEditable() {
        return this == DRAFT || this == REJECTED;
    }

    /**
     * Checks if this status allows deletion.
     */
    public boolean isDeletable() {
        return this == DRAFT || this == REJECTED;
    }

    /**
     * Checks if transition to the target status is valid.
     *
     * Transitions:
     * - DRAFT → SUBMITTED (when submitting for approval)
     * - SUBMITTED → APPROVED (when manager approves)
     * - SUBMITTED → REJECTED (when manager rejects)
     * - REJECTED → DRAFT (auto-transition, becomes editable again)
     * - APPROVED → no transitions (locked permanently)
     */
    public boolean canTransitionTo(AbsenceStatus target) {
        return switch (this) {
            case DRAFT -> target == SUBMITTED;
            case SUBMITTED ->
                target == APPROVED || target == REJECTED || target == DRAFT; // Allow direct to DRAFT on rejection
            case REJECTED -> target == DRAFT || target == SUBMITTED;
            case APPROVED -> false; // Approved absences cannot transition
        };
    }
}
