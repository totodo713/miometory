# Data Model: Submit Work Log Entries

**Feature Branch**: `013-submit-worklog-entry`
**Created**: 2026-02-20

## Existing Entities (No Schema Changes)

This feature operates entirely on existing domain entities and events. No new database migrations, tables, or columns are required.

### WorkLogEntry (Aggregate)

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Unique identifier |
| memberId | UUID | Member who worked |
| projectId | UUID | Project worked on |
| date | LocalDate | Date of work |
| hours | BigDecimal | Hours (0.25 increments) |
| comment | String | Optional (max 500 chars) |
| **status** | WorkLogStatus | **DRAFT → SUBMITTED (submit) / SUBMITTED → DRAFT (recall)** |
| enteredBy | UUID | Who entered (proxy support) |
| createdAt | Instant | Creation timestamp |
| updatedAt | Instant | Last update timestamp |
| version | long | Optimistic locking version |

### WorkLogStatus (Value Object / Enum)

| Status | Description | Editable | Deletable |
|--------|-------------|----------|-----------|
| DRAFT | Initial state | Yes | Yes |
| SUBMITTED | Submitted for approval | No | No |
| APPROVED | Manager approved | No | No |
| REJECTED | Manager rejected (auto → DRAFT) | Yes | Yes |

### Allowed Transitions (Existing)

```
DRAFT       → SUBMITTED  (daily submit or monthly batch)
SUBMITTED   → APPROVED   (manager approval)
SUBMITTED   → REJECTED   (manager rejection)
SUBMITTED   → DRAFT      (recall — NEW usage of existing transition)
REJECTED    → DRAFT      (existing auto-transition)
REJECTED    → SUBMITTED  (resubmit via monthly batch)
```

**Key insight**: The transition `SUBMITTED → DRAFT` is already defined in `WorkLogStatus.canTransitionTo()`. The recall feature uses this existing transition path — no domain model changes needed.

### Domain Events (Existing)

| Event | Trigger | Data |
|-------|---------|------|
| WorkLogEntryStatusChanged | submit/recall | eventId, occurredAt, aggregateId, fromStatus, toStatus, changedBy |

The existing `WorkLogEntryStatusChanged` event captures both submit (DRAFT→SUBMITTED) and recall (SUBMITTED→DRAFT) transitions with full audit trail.

## New Application Layer Objects

### SubmitDailyEntriesCommand

| Field | Type | Validation |
|-------|------|------------|
| memberId | MemberId | Required |
| date | LocalDate | Required |
| submittedBy | MemberId | Required, must equal memberId (no proxy submission) |

### RecallDailyEntriesCommand

| Field | Type | Validation |
|-------|------|------------|
| memberId | MemberId | Required |
| date | LocalDate | Required |
| recalledBy | MemberId | Required, must equal memberId |

## Read Model Impact

### DailyCalendarEntry (Projection — Existing)

The existing calendar projection already computes per-day aggregate status:
- All DRAFT → status = "DRAFT"
- All SUBMITTED/APPROVED → status = "SUBMITTED" or "APPROVED"
- Mix → status = "MIXED"

After submit/recall, the projection automatically reflects the new status when the calendar data is re-fetched. No projection changes needed.

### work_log_entries_projection (Existing)

The existing projection table includes a `status` column that is updated by `WorkLogEntryStatusChanged` event handlers. No changes needed.
