# Research: Edit Rejected Work Log Entries

**Feature Branch**: `014-edit-rejected-entry`
**Date**: 2026-02-20

## 1. Current Monthly Rejection Flow

### Decision: Leverage existing rejection → DRAFT transition

**Rationale**: When a manager rejects a monthly submission via `ApprovalService.rejectMonth()`, all associated `WorkLogEntry` and `Absence` records are already transitioned from SUBMITTED → DRAFT. DRAFT entries are fully editable (`isEditable() == true`) and deletable (`isDeletable() == true`). The resubmission path also exists: `ApprovalStatus.canSubmit()` returns true for REJECTED, and `SubmitButton` already shows "Resubmit for Approval".

**Alternatives considered**:
- Introducing a separate CORRECTION status: Rejected. Adds unnecessary state complexity when DRAFT already provides full editability. The distinction between "new draft" and "returned for correction" is better handled at the approval level (MonthlyApproval.status == REJECTED), not the entry level.

## 2. Daily Rejection Mechanism

### Decision: Introduce a lightweight daily rejection endpoint using WorkLogEntry status transitions

**Rationale**: The current system only supports rejection at the monthly level. Daily submissions (spec 013) transition entries to SUBMITTED, but there is no mechanism for a manager to reject individual day's entries. To support daily rejection:

1. Add `POST /api/v1/worklog/entries/reject-daily` endpoint.
2. Manager specifies memberId, date, and rejectionReason.
3. All SUBMITTED entries for that member on that date transition to DRAFT.
4. Store rejection metadata in a new `daily_rejection_log` projection table (lightweight, not a full aggregate).
5. Emit `DailyEntriesRejected` domain event for each affected entry.

**Alternatives considered**:
- Full DailyApproval aggregate (like MonthlyApproval): Over-engineered for the use case. Daily submission is a convenience workflow, not a formal approval pipeline. A lightweight log table is sufficient.
- Store rejection reason on each WorkLogEntry: Violates the aggregate boundary (rejection is a review action, not an entry attribute). Also would require schema changes to the core entry table.
- Reuse MonthlyApproval for daily rejection: Conflates two different scopes (month vs. day). A month can be PENDING while individual days within it are rejected.

## 3. Rejection Reason Visibility

### Decision: Add API endpoints to expose rejection reason and enhance UI prominence

**Rationale**: Current gaps identified:
- `ApprovalQueueResponse` does not include `rejectionReason` (manager-facing).
- No GET endpoint to retrieve single approval details with rejection reason.
- MonthlySummary response (`getMonthlySummary`) already includes `rejectionReason`, but the UI displays it as small text (insufficient prominence per spec).
- No mechanism to retrieve daily rejection reason for the entry form.

Changes needed:
1. Add `GET /api/v1/worklog/approvals/member/{memberId}/month?start={date}&end={date}` to retrieve member-facing approval status with rejection reason.
2. Add daily rejection reason to calendar data response.
3. Enhance frontend components to prominently display rejection reason.

**Alternatives considered**:
- Embed rejection reason in each entry's API response: Creates coupling between entry data and approval metadata. Better to keep them separate and let the frontend compose the view.

## 4. Proxy Edit/Submit for Daily Submissions

### Decision: Extend daily submit/recall to support proxy mode

**Rationale**: Current daily submit enforces `SELF_SUBMISSION_ONLY` (submittedBy must equal memberId). For the rejected-entry editing workflow, a manager in proxy mode should be able to resubmit corrected daily entries. This requires:

1. Remove `SELF_SUBMISSION_ONLY` constraint when proxy permission is verified.
2. Add proxy permission check (`memberRepository.isSubordinateOf()`) for non-self submission.
3. Same pattern for daily recall and the new daily reject endpoint.

**Alternatives considered**:
- Keep daily submit self-only, require monthly resubmission for proxy: Forces managers to wait for the full monthly cycle, defeating the purpose of daily submission corrections.
- Separate proxy-submit endpoint: Unnecessary duplication when the same endpoint can handle both with a permission check.

## 5. Concurrent Edit Handling

### Decision: Use existing optimistic locking (version/ETag) mechanism

**Rationale**: The event sourcing infrastructure already provides optimistic locking:
- `EventStore.append()` checks `expectedVersion` against `currentVersion`.
- Throws `OptimisticLockException` → 409 Conflict with detailed version info.
- Frontend API client handles `ConflictError` (412) with retry prompt.

No additional infrastructure needed. The existing mechanism covers concurrent edits by member and proxy manager.

**Alternatives considered**:
- Pessimistic locking (row-level locks): Contradicts event sourcing architecture and adds unnecessary contention.
- UI-level "editing lock" (WebSocket-based): Over-engineered for the expected concurrency level. Optimistic locking is sufficient.

## 6. Calendar Visual Indicators for Rejected Months

### Decision: Use approval-level status overlay on calendar days

**Rationale**: When entries are rejected, they return to DRAFT status. The calendar currently shows DRAFT as gray, which doesn't distinguish "new draft" from "returned for correction". Solution:

1. Frontend queries monthly approval status alongside calendar data.
2. When MonthlyApproval.status == REJECTED, overlay a rejection indicator on all days within that fiscal month (even though individual entries show as DRAFT).
3. Similarly, for daily rejection, the calendar day cell shows a rejection indicator.
4. The daily rejection log provides per-day rejection status.

**Alternatives considered**:
- Add a REJECTED status to individual entries on the calendar: Would require maintaining REJECTED status on entries instead of transitioning to DRAFT. Breaks the existing domain model.
- Color-code only the monthly summary, not individual days: Insufficient per the spec requirement (FR-007).

## 7. Technology Stack Decisions

### Decision: Use existing technology stack without new dependencies

- **Backend**: Java 21 (domain) + Kotlin 2.3.0 (infrastructure), Spring Boot 3.5.9
- **Frontend**: TypeScript 5.x, React 19.x, Next.js 16.x, Tailwind CSS, Zustand
- **Database**: PostgreSQL 17 with JSONB event store + projection tables
- **Testing**: JUnit 5, Spring Boot Test, Testcontainers (backend); Vitest, React Testing Library (frontend)

No new libraries or frameworks required. All changes fit within existing patterns.
