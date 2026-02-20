# Data Model: Edit Rejected Work Log Entries

**Feature Branch**: `014-edit-rejected-entry`
**Date**: 2026-02-20

## Existing Entities (Unchanged)

### WorkLogEntry (Aggregate)

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | UUID | PK | WorkLogEntryId |
| memberId | UUID | Required | Owner of the entry |
| projectId | UUID | Required | Assigned project |
| date | LocalDate | Required, not future | Work date |
| hours | TimeAmount | 0.25-24.0, 0.25 increments | Hours worked |
| comment | String | Max 500 chars | Optional note |
| status | WorkLogStatus | DRAFT/SUBMITTED/APPROVED/REJECTED | Entry status |
| enteredBy | UUID | Required | Self or proxy manager |
| version | long | Auto-increment | Optimistic locking |

**Status Transitions** (updated):
```
DRAFT → SUBMITTED (daily or monthly submit)
SUBMITTED → APPROVED (monthly approve)
SUBMITTED → REJECTED (monthly reject)
SUBMITTED → DRAFT (daily reject or daily recall)
REJECTED → SUBMITTED (monthly resubmit)
```

**Edit Rules** (updated):
- `isEditable()`: true when status == DRAFT || REJECTED
- `isDeletable()`: true when status == DRAFT || REJECTED

### Absence (Aggregate)

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | UUID | PK | AbsenceId |
| memberId | UUID | Required | Owner |
| date | LocalDate | Required | Absence date |
| hours | TimeAmount | 0.25-24.0 | Hours absent |
| absenceType | AbsenceType | PAID_LEAVE/SICK_LEAVE/SPECIAL_LEAVE/OTHER | Category |
| reason | String | Max 500 chars | Optional |
| status | AbsenceStatus | DRAFT/SUBMITTED/APPROVED/REJECTED | Same lifecycle |
| recordedBy | UUID | Required | Self or proxy |
| version | long | Auto-increment | Optimistic locking |

### MonthlyApproval (Aggregate)

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | UUID | PK | MonthlyApprovalId |
| memberId | UUID | Required | Member whose month is reviewed |
| fiscalMonth | FiscalMonthPeriod | Required | 21st-20th period |
| status | ApprovalStatus | PENDING/SUBMITTED/APPROVED/REJECTED | Approval state |
| submittedAt | Instant | Set on submit | When submitted |
| submittedBy | MemberId | Set on submit | Who submitted (member or proxy) |
| reviewedAt | Instant | Set on approve/reject | When reviewed |
| reviewedBy | MemberId | Set on approve/reject | Manager who reviewed |
| rejectionReason | String | Max 1000 chars, required on reject | Manager's feedback |
| workLogEntryIds | Set<UUID> | Set on submit | Associated entries |
| absenceIds | Set<UUID> | Set on submit | Associated absences |

**Status Transitions** (unchanged):
```
PENDING → SUBMITTED (member or proxy submits)
SUBMITTED → APPROVED (manager approves → entries permanently locked)
SUBMITTED → REJECTED (manager rejects → entries return to DRAFT)
REJECTED → SUBMITTED (member or proxy resubmits)
```

---

## New Entity

### DailyRejectionLog (Projection — not a full aggregate)

Lightweight record tracking per-day rejection by a manager. Used for daily rejection reason display and calendar indicators.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | UUID | PK | Auto-generated |
| memberId | UUID | Required | Target member |
| workDate | LocalDate | Required | Rejected date |
| rejectedBy | UUID | Required | Manager who rejected |
| rejectionReason | String | Required, max 1000 chars | Manager's feedback |
| affectedEntryIds | UUID[] | Required | Entries that were transitioned to DRAFT |
| createdAt | Instant | Auto | When rejection occurred |

**Unique Constraint**: (memberId, workDate) — only the latest rejection per member-date is active. New rejections overwrite previous ones (consistent with monthly pattern per clarification Q3).

**Database Migration** (new table):

```sql
CREATE TABLE daily_rejection_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id UUID NOT NULL,
    work_date DATE NOT NULL,
    rejected_by UUID NOT NULL,
    rejection_reason TEXT NOT NULL,
    affected_entry_ids UUID[] NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_daily_rejection_member_date UNIQUE (member_id, work_date)
);
```

---

## State Diagram: Rejection Edit Cycle

### Monthly Flow

```
Member enters entries (DRAFT)
  → Member submits month (entries: DRAFT → SUBMITTED, approval: PENDING → SUBMITTED)
  → Manager rejects (entries: SUBMITTED → DRAFT, approval: SUBMITTED → REJECTED)
  → Member/Proxy edits entries (DRAFT, editable)
  → Member/Proxy adds new entries (DRAFT)
  → Member/Proxy deletes entries (DRAFT)
  → Member/Proxy resubmits (entries: DRAFT → SUBMITTED, approval: REJECTED → SUBMITTED)
  → Manager approves (entries: SUBMITTED → APPROVED, approval: SUBMITTED → APPROVED)
```

### Daily Flow

```
Member enters entries (DRAFT)
  → Member submits day (entries: DRAFT → SUBMITTED)
  → Manager rejects day (entries: SUBMITTED → DRAFT, daily_rejection_log created)
  → Member/Proxy edits entries (DRAFT, editable)
  → Member/Proxy resubmits day (entries: DRAFT → SUBMITTED)
```

---

## Domain Events

### Existing Events (no changes)

- `WorkLogEntryCreated`, `WorkLogEntryUpdated`, `WorkLogEntryDeleted`, `WorkLogEntryStatusChanged`
- `AbsenceRecorded`, `AbsenceUpdated`, `AbsenceDeleted`, `AbsenceStatusChanged`
- `MonthlyApprovalCreated`, `MonthSubmittedForApproval`, `MonthApproved`, `MonthRejected`

### New Domain Event

**DailyEntriesRejected**:
```
{
  eventId: UUID,
  occurredAt: Instant,
  memberId: UUID,
  workDate: LocalDate,
  rejectedBy: UUID,
  rejectionReason: String,
  affectedEntryIds: Set<UUID>
}
```

This event is emitted by the service layer (not from a single aggregate) since it coordinates multiple WorkLogEntry aggregates. The `daily_rejection_log` projection is updated synchronously.

---

## Relationship Diagram

```
Member (1) ──── (N) WorkLogEntry
  │                    │
  │                    └── status: DRAFT after rejection (editable)
  │
  ├── (1) ──── (N) Absence
  │                    └── status: DRAFT after rejection (editable)
  │
  ├── (1) ──── (N) MonthlyApproval
  │                    ├── rejectionReason: stored on approve/reject
  │                    └── workLogEntryIds, absenceIds: links to entries
  │
  └── (1) ──── (N) DailyRejectionLog [NEW]
                       ├── rejectionReason: daily-level feedback
                       └── affectedEntryIds: which entries were rejected
```

---

## Query Patterns

### Get rejection reason for a member's month
```sql
SELECT rejection_reason, status, reviewed_by, reviewed_at
FROM monthly_approvals_projection
WHERE member_id = ? AND fiscal_year = ? AND fiscal_month = ?
AND status = 'REJECTED'
```

### Get daily rejection reason for calendar display
```sql
SELECT work_date, rejection_reason, rejected_by, created_at
FROM daily_rejection_log
WHERE member_id = ?
AND work_date BETWEEN ? AND ?
```

### Get combined rejection info for a date range (calendar enrichment)
```sql
-- Monthly rejection covers all days in fiscal month
SELECT 'monthly' AS source, fiscal_month_start AS start_date, fiscal_month_end AS end_date,
       rejection_reason, reviewed_by
FROM monthly_approvals_projection
WHERE member_id = ? AND status = 'REJECTED'
AND fiscal_month_start <= ? AND fiscal_month_end >= ?

UNION ALL

-- Daily rejection covers individual days
SELECT 'daily' AS source, work_date AS start_date, work_date AS end_date,
       rejection_reason, rejected_by AS reviewed_by
FROM daily_rejection_log
WHERE member_id = ? AND work_date BETWEEN ? AND ?
```
