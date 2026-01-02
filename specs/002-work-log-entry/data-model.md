# Data Model: Work-Log Entry System

**Feature**: 002-work-log-entry  
**Date**: 2026-01-03  
**Status**: Phase 1 Design

## Overview

This document defines the domain model for the Work-Log Entry System, following Domain-Driven Design (DDD) principles with Event Sourcing. The model is organized into aggregates, value objects, and projections (read models).

## Aggregates

### 1. WorkLogEntry Aggregate

**Aggregate Root**: `WorkLogEntry`

**Purpose**: Represents hours worked by a member on a specific project on a specific date.

**Identity**: `WorkLogEntryId` (UUID)

**Fields**:

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | WorkLogEntryId | Required, immutable | Unique identifier |
| memberId | MemberId | Required, immutable | Member who worked (or attributed member for proxy entries) |
| projectId | ProjectId | Required | Project worked on |
| date | LocalDate | Required | Date of work |
| hours | TimeAmount | Required | Hours worked (0.25h increments) |
| comment | String | Optional, max 500 chars | Optional comment |
| status | WorkLogStatus | Required | Draft/Submitted/Approved/Rejected |
| enteredBy | MemberId | Required | Who actually entered the data (for proxy entries) |
| createdAt | Instant | Required, immutable | When created |
| updatedAt | Instant | Required | Last updated timestamp |
| version | Long | Required | Optimistic locking version |

**Value Objects**:
- `WorkLogEntryId`: UUID wrapper
- `TimeAmount`: Validates 0.25h increments, max 24h per day
- `WorkLogStatus`: Enum (DRAFT, SUBMITTED, APPROVED, REJECTED)

**Invariants**:
1. Hours must be in 0.25h increments (0.00, 0.25, 0.50, 0.75, 1.00, etc.)
2. Hours must be ≥ 0 and ≤ 24
3. Total hours for a member on a single date across all projects ≤ 24h
4. Status transitions:
   - DRAFT → SUBMITTED (on submission)
   - SUBMITTED → APPROVED (on approval)
   - SUBMITTED → REJECTED (on rejection)
   - REJECTED → DRAFT (auto-transition, becomes editable)
   - SUBMITTED/APPROVED entries are read-only
5. Date cannot be in the future
6. Proxy entries: enteredBy ≠ memberId only if enteredBy is memberId's manager

**Domain Events**:

```kotlin
sealed class WorkLogEntryEvent {
    data class WorkLogEntryCreated(
        val id: WorkLogEntryId,
        val memberId: MemberId,
        val projectId: ProjectId,
        val date: LocalDate,
        val hours: TimeAmount,
        val comment: String?,
        val enteredBy: MemberId,
        val occurredAt: Instant
    )
    
    data class WorkLogEntryUpdated(
        val id: WorkLogEntryId,
        val hours: TimeAmount,
        val comment: String?,
        val updatedBy: MemberId,
        val occurredAt: Instant
    )
    
    data class WorkLogEntryDeleted(
        val id: WorkLogEntryId,
        val deletedBy: MemberId,
        val occurredAt: Instant
    )
    
    data class WorkLogEntryStatusChanged(
        val id: WorkLogEntryId,
        val fromStatus: WorkLogStatus,
        val toStatus: WorkLogStatus,
        val changedBy: MemberId,
        val occurredAt: Instant
    )
}
```

**Business Rules**:
- Cannot modify entries in SUBMITTED or APPROVED status
- Can only delete entries in DRAFT status
- Approval/rejection handled via MonthlyApproval aggregate

---

### 2. Absence Aggregate

**Aggregate Root**: `Absence`

**Purpose**: Represents non-working time (vacation, sick leave, etc.) for a member on a specific date.

**Identity**: `AbsenceId` (UUID)

**Fields**:

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | AbsenceId | Required, immutable | Unique identifier |
| memberId | MemberId | Required, immutable | Member taking absence |
| date | LocalDate | Required | Date of absence |
| type | AbsenceType | Required | Type of absence |
| hours | TimeAmount | Required | Hours of absence (0.25h increments) |
| comment | String | Optional, max 500 chars | Optional comment |
| status | AbsenceStatus | Required | Draft/Submitted/Approved/Rejected |
| createdAt | Instant | Required, immutable | When created |
| updatedAt | Instant | Required | Last updated timestamp |
| version | Long | Required | Optimistic locking version |

**Value Objects**:
- `AbsenceId`: UUID wrapper
- `AbsenceType`: Enum (PAID_LEAVE, SICK_LEAVE, SPECIAL_LEAVE, OTHER)
- `AbsenceStatus`: Enum (DRAFT, SUBMITTED, APPROVED, REJECTED)

**Invariants**:
1. Hours must be in 0.25h increments
2. Hours must be ≥ 0 and ≤ 24
3. Total absence + work log hours for a member on a single date ≤ 24h
4. Date cannot be in the future
5. Status transitions follow same rules as WorkLogEntry

**Domain Events**:

```kotlin
sealed class AbsenceEvent {
    data class AbsenceRecorded(
        val id: AbsenceId,
        val memberId: MemberId,
        val date: LocalDate,
        val type: AbsenceType,
        val hours: TimeAmount,
        val comment: String?,
        val occurredAt: Instant
    )
    
    data class AbsenceUpdated(
        val id: AbsenceId,
        val hours: TimeAmount,
        val type: AbsenceType,
        val comment: String?,
        val occurredAt: Instant
    )
    
    data class AbsenceDeleted(
        val id: AbsenceId,
        val occurredAt: Instant
    )
}
```

---

### 3. MonthlyApproval Aggregate

**Aggregate Root**: `MonthlyApproval`

**Purpose**: Manages the approval workflow for a member's time entries for a specific fiscal month.

**Identity**: `MonthlyApprovalId` (UUID)

**Fields**:

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | MonthlyApprovalId | Required, immutable | Unique identifier |
| memberId | MemberId | Required, immutable | Member whose time is being approved |
| fiscalMonth | FiscalMonthPeriod | Required, immutable | Fiscal month period (e.g., 2026-01-21 to 2026-02-20) |
| status | ApprovalStatus | Required | Pending/Submitted/Approved/Rejected |
| submittedAt | Instant | Optional | When submitted for approval |
| submittedBy | MemberId | Optional | Who submitted (member or proxy) |
| reviewedAt | Instant | Optional | When approved/rejected |
| reviewedBy | MemberId | Optional | Manager who approved/rejected |
| rejectionReason | String | Optional, max 1000 chars | Reason for rejection |
| workLogEntryIds | Set<WorkLogEntryId> | Required | Associated work log entries |
| absenceIds | Set<AbsenceId> | Required | Associated absence entries |
| createdAt | Instant | Required, immutable | When created |
| version | Long | Required | Optimistic locking version |

**Value Objects**:
- `MonthlyApprovalId`: UUID wrapper
- `ApprovalStatus`: Enum (PENDING, SUBMITTED, APPROVED, REJECTED)
- `FiscalMonthPeriod`: Value object (startDate, endDate)

**Invariants**:
1. Cannot submit if status is SUBMITTED or APPROVED
2. Cannot approve/reject if status is not SUBMITTED
3. Only member's direct manager can approve/reject
4. Submission changes all associated WorkLogEntry/Absence status to SUBMITTED
5. Approval changes all associated entries to APPROVED (permanently read-only)
6. Rejection returns all associated entries to DRAFT

**Domain Events**:

```kotlin
sealed class MonthlyApprovalEvent {
    data class MonthlyApprovalCreated(
        val id: MonthlyApprovalId,
        val memberId: MemberId,
        val fiscalMonth: FiscalMonthPeriod,
        val occurredAt: Instant
    )
    
    data class MonthSubmittedForApproval(
        val id: MonthlyApprovalId,
        val memberId: MemberId,
        val fiscalMonth: FiscalMonthPeriod,
        val submittedBy: MemberId,
        val workLogEntryIds: Set<WorkLogEntryId>,
        val absenceIds: Set<AbsenceId>,
        val occurredAt: Instant
    )
    
    data class MonthApproved(
        val id: MonthlyApprovalId,
        val memberId: MemberId,
        val fiscalMonth: FiscalMonthPeriod,
        val reviewedBy: MemberId,
        val occurredAt: Instant
    )
    
    data class MonthRejected(
        val id: MonthlyApprovalId,
        val memberId: MemberId,
        val fiscalMonth: FiscalMonthPeriod,
        val reviewedBy: MemberId,
        val reason: String,
        val occurredAt: Instant
    )
}
```

**Business Rules**:
- Late submissions (after fiscal month end + grace period) are flagged
- Cannot submit if required working days have zero hours entered
- Manager can only approve direct reports

---

## Extended Aggregates

### 4. Member Aggregate (Extended)

**Existing aggregate extended with new fields**:

| New Field | Type | Constraints | Description |
|-----------|------|-------------|-------------|
| managerId | MemberId | Optional | Direct manager (for proxy entry permission) |
| proxyEntryPermissions | Set<MemberId> | Required | Members this person can enter time for |

**New Business Rules**:
- Proxy entry allowed only if enteredBy.managerId == targetMember.id
- Proxy entry audit trail maintained in WorkLogEntry.enteredBy

---

### 5. Project Aggregate (Extended)

**Existing aggregate extended with new fields**:

| New Field | Type | Constraints | Description |
|-----------|------|-------------|-------------|
| isActive | Boolean | Required | Whether project accepts new time entries |
| validFrom | LocalDate | Optional | Start date for accepting time entries |
| validUntil | LocalDate | Optional | End date for accepting time entries |

**New Business Rules**:
- Cannot log time to inactive projects
- Cannot log time outside project validity period

---

## Value Objects

### TimeAmount

```kotlin
data class TimeAmount(val hours: BigDecimal) {
    init {
        require(hours >= BigDecimal.ZERO) { "Hours cannot be negative" }
        require(hours <= BigDecimal.valueOf(24)) { "Hours cannot exceed 24" }
        require(hours.remainder(BigDecimal.valueOf(0.25)) == BigDecimal.ZERO) {
            "Hours must be in 0.25h increments"
        }
    }
    
    operator fun plus(other: TimeAmount): TimeAmount = 
        TimeAmount(hours + other.hours)
    
    operator fun minus(other: TimeAmount): TimeAmount = 
        TimeAmount(hours - other.hours)
}
```

### FiscalMonthPeriod

```kotlin
data class FiscalMonthPeriod(
    val startDate: LocalDate,
    val endDate: LocalDate
) {
    init {
        require(startDate.isBefore(endDate)) { "Start date must be before end date" }
        require(startDate.dayOfMonth == 21) { "Fiscal month starts on 21st" }
        require(endDate.dayOfMonth == 20) { "Fiscal month ends on 20th" }
        require(ChronoUnit.DAYS.between(startDate, endDate) in 27..30) {
            "Fiscal month must be approximately 1 calendar month"
        }
    }
    
    fun contains(date: LocalDate): Boolean = 
        !date.isBefore(startDate) && !date.isAfter(endDate)
    
    fun next(): FiscalMonthPeriod = 
        FiscalMonthPeriod(
            startDate.plusMonths(1),
            endDate.plusMonths(1)
        )
    
    fun previous(): FiscalMonthPeriod = 
        FiscalMonthPeriod(
            startDate.minusMonths(1),
            endDate.minusMonths(1)
        )
}
```

### DateRange

```kotlin
data class DateRange(
    val startDate: LocalDate,
    val endDate: LocalDate
) {
    init {
        require(!startDate.isAfter(endDate)) { "Start date cannot be after end date" }
    }
    
    fun contains(date: LocalDate): Boolean = 
        !date.isBefore(startDate) && !date.isAfter(endDate)
    
    fun overlaps(other: DateRange): Boolean = 
        !endDate.isBefore(other.startDate) && !other.endDate.isBefore(startDate)
}
```

---

## Projections (Read Models)

### 1. MonthlyCalendarProjection

**Purpose**: Optimized read model for displaying calendar view

**Fields**:

| Field | Type | Description |
|-------|------|-------------|
| memberId | MemberId | Member whose calendar this is |
| fiscalMonth | FiscalMonthPeriod | Month period |
| dates | List<DailyCalendarEntry> | Daily entries |

**DailyCalendarEntry**:

| Field | Type | Description |
|-------|------|-------------|
| date | LocalDate | Date |
| totalWorkHours | TimeAmount | Total work hours across all projects |
| totalAbsenceHours | TimeAmount | Total absence hours |
| status | WorkLogStatus | Overall status (DRAFT if any draft, APPROVED if all approved, etc.) |
| isWeekend | Boolean | Whether date is Saturday/Sunday |
| isHoliday | Boolean | Whether date is a holiday |
| projects | List<ProjectHours> | Hours per project |

**ProjectHours**:

| Field | Type | Description |
|-------|------|-------------|
| projectId | ProjectId | Project |
| projectName | String | Project display name |
| hours | TimeAmount | Hours on this project |

**Rebuild Trigger**: WorkLogEntryCreated, WorkLogEntryUpdated, WorkLogEntryDeleted, AbsenceRecorded, AbsenceUpdated, AbsenceDeleted

---

### 2. MonthlySummaryProjection

**Purpose**: Aggregated summary for monthly submission/approval

**Fields**:

| Field | Type | Description |
|-------|------|-------------|
| memberId | MemberId | Member |
| fiscalMonth | FiscalMonthPeriod | Month period |
| totalWorkHours | TimeAmount | Total work hours |
| totalAbsenceHours | TimeAmount | Total absence hours |
| expectedWorkHours | TimeAmount | Expected hours (business days * 8h) |
| completionPercentage | BigDecimal | (totalWorkHours / expectedWorkHours) * 100 |
| projectBreakdown | List<ProjectSummary> | Hours + % per project |
| absenceBreakdown | Map<AbsenceType, TimeAmount> | Hours per absence type |
| status | ApprovalStatus | Overall approval status |
| draftDaysCount | Int | Number of days with draft entries |
| submittedDaysCount | Int | Number of days submitted |
| approvedDaysCount | Int | Number of days approved |

**ProjectSummary**:

| Field | Type | Description |
|-------|------|-------------|
| projectId | ProjectId | Project |
| projectName | String | Project display name |
| totalHours | TimeAmount | Total hours on project |
| percentage | BigDecimal | (totalHours / totalWorkHours) * 100 |

**Rebuild Trigger**: WorkLogEntryCreated, WorkLogEntryUpdated, WorkLogEntryDeleted, AbsenceRecorded, AbsenceUpdated, MonthSubmittedForApproval, MonthApproved, MonthRejected

---

### 3. ApprovalQueueProjection

**Purpose**: Manager's view of pending approvals

**Fields**:

| Field | Type | Description |
|-------|------|-------------|
| managerId | MemberId | Manager |
| pendingApprovals | List<PendingApproval> | Pending submissions |

**PendingApproval**:

| Field | Type | Description |
|-------|------|-------------|
| approvalId | MonthlyApprovalId | Approval ID |
| memberId | MemberId | Member who submitted |
| memberName | String | Member display name |
| fiscalMonth | FiscalMonthPeriod | Month period |
| submittedAt | Instant | Submission timestamp |
| totalWorkHours | TimeAmount | Total work hours |
| totalAbsenceHours | TimeAmount | Total absence hours |
| isLateSubmission | Boolean | Whether submitted after deadline |

**Rebuild Trigger**: MonthSubmittedForApproval, MonthApproved, MonthRejected

---

## Database Schema (Event Store + Projections)

### Event Store Table

```sql
CREATE TABLE events (
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    sequence_number BIGINT NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (aggregate_id, sequence_number)
);

CREATE INDEX idx_events_aggregate_type ON events(aggregate_type);
CREATE INDEX idx_events_occurred_at ON events(occurred_at);
```

### Projection Tables

**monthly_calendar_projection**:

```sql
CREATE TABLE monthly_calendar_projection (
    member_id UUID NOT NULL,
    fiscal_month_start DATE NOT NULL,
    fiscal_month_end DATE NOT NULL,
    projection_data JSONB NOT NULL,
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (member_id, fiscal_month_start)
);

CREATE INDEX idx_calendar_last_updated ON monthly_calendar_projection(last_updated_at);
```

**monthly_summary_projection**:

```sql
CREATE TABLE monthly_summary_projection (
    member_id UUID NOT NULL,
    fiscal_month_start DATE NOT NULL,
    fiscal_month_end DATE NOT NULL,
    total_work_hours NUMERIC(5,2) NOT NULL,
    total_absence_hours NUMERIC(5,2) NOT NULL,
    expected_work_hours NUMERIC(5,2) NOT NULL,
    completion_percentage NUMERIC(5,2) NOT NULL,
    approval_status VARCHAR(20) NOT NULL,
    projection_data JSONB NOT NULL,
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (member_id, fiscal_month_start)
);

CREATE INDEX idx_summary_status ON monthly_summary_projection(approval_status);
CREATE INDEX idx_summary_last_updated ON monthly_summary_projection(last_updated_at);
```

**approval_queue_projection**:

```sql
CREATE TABLE approval_queue_projection (
    manager_id UUID NOT NULL,
    projection_data JSONB NOT NULL,
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (manager_id)
);

CREATE INDEX idx_queue_last_updated ON approval_queue_projection(last_updated_at);
```

---

## Validation Rules Summary

### Cross-Aggregate Validation

1. **Daily Total Hours Constraint**:
   - Query: SUM(WorkLogEntry.hours WHERE memberId = X AND date = Y) + SUM(Absence.hours WHERE memberId = X AND date = Y) ≤ 24h
   - Enforcement: Application service validates before persisting event

2. **Proxy Entry Authorization**:
   - Query: Member WHERE id = enteredBy AND managerId = targetMemberId
   - Enforcement: Application service checks before CreateWorkLogEntryCommand

3. **Approval Authorization**:
   - Query: Member WHERE id = reviewerId AND EXISTS(SELECT 1 FROM members WHERE id = targetMemberId AND manager_id = reviewerId)
   - Enforcement: Application service checks before ApproveMonthCommand

4. **Project Validity**:
   - Query: Project WHERE id = projectId AND isActive = true AND (validFrom IS NULL OR validFrom <= date) AND (validUntil IS NULL OR validUntil >= date)
   - Enforcement: Application service validates before CreateWorkLogEntryCommand

---

## Migration Path

### New Tables (Flyway Migrations)

**V4__work_log_entry_tables.sql**:
- Create events table (if not exists)
- Create monthly_calendar_projection table
- Create monthly_summary_projection table

**V5__absence_tables.sql**:
- Extend events table indices for absence events
- Add absence-specific projection columns to monthly_calendar_projection

**V6__approval_workflow_tables.sql**:
- Create approval_queue_projection table
- Add approval_status column to monthly_summary_projection

**V7__extend_member_project.sql**:
- ALTER TABLE members ADD COLUMN manager_id UUID REFERENCES members(id)
- ALTER TABLE projects ADD COLUMN is_active BOOLEAN DEFAULT true
- ALTER TABLE projects ADD COLUMN valid_from DATE
- ALTER TABLE projects ADD COLUMN valid_until DATE

---

## Concurrency & Conflict Resolution

### Optimistic Locking

All aggregates include a `version` field for optimistic locking:

```kotlin
fun updateWorkLogEntry(command: UpdateWorkLogEntryCommand) {
    val currentVersion = command.expectedVersion
    val aggregate = repository.load(command.id, currentVersion)
    // ... apply changes ...
    repository.save(aggregate, currentVersion + 1)
}
```

If version mismatch occurs:
1. Backend returns HTTP 409 Conflict with current state
2. Frontend displays conflict resolution dialog
3. User chooses: retry with current state OR discard local changes

### Auto-Save Conflict Handling

Auto-save uses TanStack Query optimistic updates:
1. Save immediately to localStorage (offline backup)
2. Send PATCH request with version
3. On 409 Conflict:
   - Show notification: "Another user modified this entry. Reload?"
   - User can review changes and merge manually

---

## Summary

This data model provides:
- ✅ Event-sourced aggregates for full audit trail
- ✅ Optimized read models (projections) for fast queries
- ✅ Strong invariants enforced at aggregate boundaries
- ✅ Proxy entry audit trail (enteredBy field)
- ✅ Fiscal month period support
- ✅ 7-year retention via event store
- ✅ Optimistic locking for concurrent editing
- ✅ Clear state transitions for approval workflow

**Next Steps**: Define REST API contracts in contracts/openapi.yaml based on this model.
