package com.worklog.domain.absence;

import com.worklog.domain.absence.events.AbsenceDeleted;
import com.worklog.domain.absence.events.AbsenceRecorded;
import com.worklog.domain.absence.events.AbsenceStatusChanged;
import com.worklog.domain.absence.events.AbsenceUpdated;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.AggregateRoot;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.shared.TimeAmount;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Absence aggregate root.
 * 
 * Represents time away from work (vacation, sick leave, special leave) recorded
 * separately from project work hours. Used to track non-working time and ensure
 * daily totals (work + absence) don't exceed 24 hours.
 * 
 * Invariants:
 * - Hours must be in 0.25h increments (enforced by TimeAmount)
 * - Hours must be > 0 and ≤ 24 (enforced by TimeAmount)
 * - Date cannot be in the future
 * - Status transitions:
 *   - DRAFT → SUBMITTED (on submission)
 *   - SUBMITTED → APPROVED (on approval)
 *   - SUBMITTED → REJECTED (on rejection)
 *   - REJECTED → DRAFT (auto-transition, becomes editable)
 * - SUBMITTED/APPROVED absences cannot be edited
 * - APPROVED absences cannot be deleted
 * - Reason max 500 characters (optional)
 * 
 * Note: The 24-hour daily limit (work + absence) is validated at the service layer,
 * not in this aggregate (as it requires cross-aggregate validation).
 */
public class Absence extends AggregateRoot<AbsenceId> {
    
    private AbsenceId id;
    private MemberId memberId;
    private LocalDate date;
    private TimeAmount hours;
    private AbsenceType absenceType;
    private String reason;
    private AbsenceStatus status;
    private MemberId recordedBy;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean deleted;
    
    // Private constructor for factory methods
    private Absence() {
    }
    
    /**
     * Records a new absence in DRAFT status.
     * 
     * @param memberId Member who is absent
     * @param date Date of absence
     * @param hours Hours of absence
     * @param absenceType Type of absence (PAID_LEAVE, SICK_LEAVE, etc.)
     * @param reason Optional reason/comment (max 500 chars)
     * @param recordedBy Who is recording the absence
     * @return New Absence instance with AbsenceRecorded event
     * @throws DomainException if validation fails
     */
    public static Absence record(
        MemberId memberId,
        LocalDate date,
        TimeAmount hours,
        AbsenceType absenceType,
        String reason,
        MemberId recordedBy
    ) {
        validateDate(date);
        validateReason(reason);
        
        Absence absence = new Absence();
        AbsenceId absenceId = AbsenceId.generate();
        
        AbsenceRecorded event = AbsenceRecorded.create(
            absenceId,
            memberId,
            date,
            hours,
            absenceType,
            reason,
            recordedBy
        );
        
        absence.raiseEvent(event);
        return absence;
    }
    
    /**
     * Updates the hours, type, and/or reason of the absence.
     * 
     * @param hours New hours value
     * @param absenceType New absence type
     * @param reason New reason/comment
     * @param updatedBy Who is updating
     * @throws DomainException if absence is not editable
     */
    public void update(TimeAmount hours, AbsenceType absenceType, String reason, MemberId updatedBy) {
        if (!isEditable()) {
            throw new DomainException(
                "ABSENCE_NOT_EDITABLE",
                "Cannot update absence in " + status + " status. Only DRAFT and REJECTED absences can be edited."
            );
        }
        
        validateReason(reason);
        
        AbsenceUpdated event = AbsenceUpdated.create(
            this.id,
            hours,
            absenceType,
            reason,
            updatedBy
        );
        
        raiseEvent(event);
    }
    
    /**
     * Deletes the absence.
     * 
     * @param deletedBy Who is deleting
     * @throws DomainException if absence cannot be deleted
     */
    public void delete(MemberId deletedBy) {
        if (!isDeletable()) {
            throw new DomainException(
                "ABSENCE_NOT_DELETABLE",
                "Cannot delete absence in " + status + " status. Only DRAFT and REJECTED absences can be deleted."
            );
        }
        
        AbsenceDeleted event = AbsenceDeleted.create(this.id, deletedBy);
        raiseEvent(event);
    }
    
    /**
     * Changes the status of the absence.
     * 
     * @param newStatus New status
     * @param changedBy Who is changing the status
     * @throws DomainException if transition is not allowed
     */
    public void changeStatus(AbsenceStatus newStatus, MemberId changedBy) {
        if (!status.canTransitionTo(newStatus)) {
            throw new DomainException(
                "INVALID_STATUS_TRANSITION",
                "Cannot transition from " + status + " to " + newStatus
            );
        }
        
        AbsenceStatusChanged event = AbsenceStatusChanged.create(
            this.id,
            this.status.name(),
            newStatus.name(),
            changedBy
        );
        
        raiseEvent(event);
    }
    
    /**
     * Checks if the absence can be edited.
     * Only DRAFT and REJECTED absences are editable.
     */
    public boolean isEditable() {
        return status.isEditable();
    }
    
    /**
     * Checks if the absence can be deleted.
     * Only DRAFT and REJECTED absences can be deleted.
     */
    public boolean isDeletable() {
        return status.isDeletable();
    }
    
    /**
     * Checks if this is a proxy entry (recorded by someone other than the member).
     */
    public boolean isProxyEntry() {
        return !recordedBy.equals(memberId);
    }
    
    @Override
    protected void apply(DomainEvent event) {
        switch (event) {
            case AbsenceRecorded e -> {
                this.id = AbsenceId.of(e.aggregateId());
                this.memberId = MemberId.of(e.memberId());
                this.date = e.date();
                this.hours = TimeAmount.of(e.hours());
                this.absenceType = AbsenceType.valueOf(e.absenceType());
                this.reason = e.reason();
                this.status = AbsenceStatus.DRAFT;
                this.recordedBy = MemberId.of(e.recordedBy());
                this.createdAt = e.occurredAt();
                this.updatedAt = e.occurredAt();
            }
            case AbsenceUpdated e -> {
                this.hours = TimeAmount.of(e.hours());
                this.absenceType = AbsenceType.valueOf(e.absenceType());
                this.reason = e.reason();
                this.updatedAt = e.occurredAt();
            }
            case AbsenceStatusChanged e -> {
                this.status = AbsenceStatus.valueOf(e.toStatus());
                this.updatedAt = e.occurredAt();
            }
            case AbsenceDeleted e -> {
                // Mark as deleted - repository will filter out deleted aggregates
                this.deleted = true;
                this.updatedAt = e.occurredAt();
            }
            default -> throw new IllegalArgumentException(
                "Unknown event type: " + event.getClass().getName()
            );
        }
    }
    
    private static void validateDate(LocalDate date) {
        if (date == null) {
            throw new DomainException("DATE_REQUIRED", "Date cannot be null");
        }
        
        if (date.isAfter(LocalDate.now())) {
            throw new DomainException("DATE_IN_FUTURE", "Date cannot be in the future");
        }
    }
    
    private static void validateReason(String reason) {
        if (reason != null && reason.length() > 500) {
            throw new DomainException(
                "REASON_TOO_LONG",
                "Reason cannot exceed 500 characters"
            );
        }
    }
    
    // Getters
    
    @Override
    public AbsenceId getId() {
        return id;
    }
    
    @Override
    public String getAggregateType() {
        return "Absence";
    }
    
    public MemberId getMemberId() {
        return memberId;
    }
    
    public LocalDate getDate() {
        return date;
    }
    
    public TimeAmount getHours() {
        return hours;
    }
    
    public AbsenceType getAbsenceType() {
        return absenceType;
    }
    
    public String getReason() {
        return reason;
    }
    
    public AbsenceStatus getStatus() {
        return status;
    }
    
    public MemberId getRecordedBy() {
        return recordedBy;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public boolean isDeleted() {
        return deleted;
    }
}
