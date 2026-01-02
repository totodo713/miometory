package com.worklog.domain.worklog;

import com.worklog.domain.member.MemberId;
import com.worklog.domain.project.ProjectId;
import com.worklog.domain.shared.AggregateRoot;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.shared.TimeAmount;
import com.worklog.domain.worklog.events.WorkLogEntryCreated;
import com.worklog.domain.worklog.events.WorkLogEntryDeleted;
import com.worklog.domain.worklog.events.WorkLogEntryStatusChanged;
import com.worklog.domain.worklog.events.WorkLogEntryUpdated;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * WorkLogEntry aggregate root.
 * 
 * Represents hours worked by a member on a specific project on a specific date.
 * 
 * Invariants:
 * - Hours must be in 0.25h increments (enforced by TimeAmount)
 * - Hours must be ≥ 0 and ≤ 24 (enforced by TimeAmount)
 * - Date cannot be in the future
 * - Status transitions:
 *   - DRAFT → SUBMITTED (on submission)
 *   - SUBMITTED → APPROVED (on approval)
 *   - SUBMITTED → REJECTED (on rejection)
 *   - REJECTED → DRAFT (auto-transition, becomes editable)
 * - SUBMITTED/APPROVED entries are read-only
 * - Can only delete entries in DRAFT status
 * - Comment max 500 characters
 * 
 * Note: The 24-hour daily limit across all projects is validated at the service layer,
 * not in this aggregate (as it requires cross-aggregate validation).
 */
public class WorkLogEntry extends AggregateRoot<WorkLogEntryId> {
    
    private WorkLogEntryId id;
    private MemberId memberId;
    private ProjectId projectId;
    private LocalDate date;
    private TimeAmount hours;
    private String comment;
    private WorkLogStatus status;
    private MemberId enteredBy;
    private Instant createdAt;
    private Instant updatedAt;
    
    // Private constructor for factory methods
    private WorkLogEntry() {
    }
    
    /**
     * Creates a new work log entry in DRAFT status.
     * 
     * @param memberId Member who worked (or attributed member for proxy entries)
     * @param projectId Project worked on
     * @param date Date of work
     * @param hours Hours worked
     * @param comment Optional comment
     * @param enteredBy Who actually entered the data
     * @return New WorkLogEntry instance with WorkLogEntryCreated event
     */
    public static WorkLogEntry create(
        MemberId memberId,
        ProjectId projectId,
        LocalDate date,
        TimeAmount hours,
        String comment,
        MemberId enteredBy
    ) {
        validateDate(date);
        validateComment(comment);
        
        WorkLogEntry entry = new WorkLogEntry();
        WorkLogEntryId entryId = WorkLogEntryId.generate();
        
        WorkLogEntryCreated event = WorkLogEntryCreated.create(
            entryId,
            memberId,
            projectId,
            date,
            hours,
            comment,
            enteredBy
        );
        
        entry.raiseEvent(event);
        return entry;
    }
    
    /**
     * Updates hours and/or comment of the entry.
     * 
     * @param hours New hours value
     * @param comment New comment
     * @param updatedBy Who is updating
     * @throws DomainException if entry is not editable
     */
    public void update(TimeAmount hours, String comment, MemberId updatedBy) {
        if (!isEditable()) {
            throw new DomainException(
                "ENTRY_NOT_EDITABLE",
                "Cannot update entry in " + status + " status. Only DRAFT entries can be edited."
            );
        }
        
        validateComment(comment);
        
        WorkLogEntryUpdated event = WorkLogEntryUpdated.create(
            this.id,
            hours,
            comment,
            updatedBy
        );
        
        raiseEvent(event);
    }
    
    /**
     * Deletes the entry.
     * 
     * @param deletedBy Who is deleting
     * @throws DomainException if entry cannot be deleted
     */
    public void delete(MemberId deletedBy) {
        if (!isDeletable()) {
            throw new DomainException(
                "ENTRY_NOT_DELETABLE",
                "Cannot delete entry in " + status + " status. Only DRAFT entries can be deleted."
            );
        }
        
        WorkLogEntryDeleted event = WorkLogEntryDeleted.create(this.id, deletedBy);
        raiseEvent(event);
    }
    
    /**
     * Changes the status of the entry.
     * 
     * @param newStatus New status
     * @param changedBy Who is changing the status
     * @throws DomainException if transition is not allowed
     */
    public void changeStatus(WorkLogStatus newStatus, MemberId changedBy) {
        if (!status.canTransitionTo(newStatus)) {
            throw new DomainException(
                "INVALID_STATUS_TRANSITION",
                "Cannot transition from " + status + " to " + newStatus
            );
        }
        
        WorkLogEntryStatusChanged event = WorkLogEntryStatusChanged.create(
            this.id,
            this.status,
            newStatus,
            changedBy
        );
        
        raiseEvent(event);
    }
    
    /**
     * Checks if the entry can be edited.
     * Only DRAFT entries are editable.
     */
    public boolean isEditable() {
        return status == WorkLogStatus.DRAFT;
    }
    
    /**
     * Checks if the entry can be deleted.
     * Only DRAFT entries can be deleted.
     */
    public boolean isDeletable() {
        return status == WorkLogStatus.DRAFT;
    }
    
    /**
     * Checks if this is a proxy entry (entered by someone other than the member).
     */
    public boolean isProxyEntry() {
        return !enteredBy.equals(memberId);
    }
    
    @Override
    protected void apply(DomainEvent event) {
        switch (event) {
            case WorkLogEntryCreated e -> {
                this.id = WorkLogEntryId.of(e.aggregateId());
                this.memberId = MemberId.of(e.memberId());
                this.projectId = ProjectId.of(e.projectId());
                this.date = e.date();
                this.hours = TimeAmount.of(e.hours());
                this.comment = e.comment();
                this.status = WorkLogStatus.DRAFT;
                this.enteredBy = MemberId.of(e.enteredBy());
                this.createdAt = e.occurredAt();
                this.updatedAt = e.occurredAt();
            }
            case WorkLogEntryUpdated e -> {
                this.hours = TimeAmount.of(e.hours());
                this.comment = e.comment();
                this.updatedAt = e.occurredAt();
            }
            case WorkLogEntryStatusChanged e -> {
                this.status = WorkLogStatus.valueOf(e.toStatus());
                this.updatedAt = e.occurredAt();
            }
            case WorkLogEntryDeleted e -> {
                // For soft delete, we could mark as deleted
                // For event sourcing, the deleted event is stored
                // The projection layer will handle removing from read models
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
    
    private static void validateComment(String comment) {
        if (comment != null && comment.length() > 500) {
            throw new DomainException(
                "COMMENT_TOO_LONG",
                "Comment cannot exceed 500 characters"
            );
        }
    }
    
    // Getters
    
    @Override
    public WorkLogEntryId getId() {
        return id;
    }
    
    @Override
    public String getAggregateType() {
        return "WorkLogEntry";
    }
    
    public MemberId getMemberId() {
        return memberId;
    }
    
    public ProjectId getProjectId() {
        return projectId;
    }
    
    public LocalDate getDate() {
        return date;
    }
    
    public TimeAmount getHours() {
        return hours;
    }
    
    public String getComment() {
        return comment;
    }
    
    public WorkLogStatus getStatus() {
        return status;
    }
    
    public MemberId getEnteredBy() {
        return enteredBy;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
