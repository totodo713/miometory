# Research: Submit Work Log Entries

**Feature Branch**: `013-submit-worklog-entry`
**Created**: 2026-02-20

## R1: Daily Submission Architecture (Individual vs. Aggregate-Based)

**Decision**: Operate directly on individual WorkLogEntry aggregates without creating a MonthlyApproval aggregate.

**Rationale**: The existing monthly batch submission creates a MonthlyApproval aggregate that groups all entries for a fiscal month. Daily submission is a lighter-weight operation — it transitions individual entries from DRAFT to SUBMITTED. The WorkLogEntry aggregate already supports `changeStatus(SUBMITTED)` via the existing `WorkLogEntryStatusChanged` event. Creating a new aggregate for daily submission would add unnecessary complexity.

**Alternatives considered**:
- **Create a DailyApproval aggregate**: Rejected — over-engineering for a simple status transition. The monthly approval already handles grouping for manager review.
- **Extend MonthlyApproval**: Rejected — would couple daily submission to the monthly workflow and complicate the existing approval lifecycle.

## R2: Atomicity Strategy for Multi-Entry Submission

**Decision**: Use application service-level transaction to iterate over all DRAFT entries for a member/date and call `changeStatus()` on each. Spring's `@Transactional` ensures all-or-nothing behavior.

**Rationale**: Each WorkLogEntry is a separate aggregate in the event store. Since the event store uses PostgreSQL, wrapping the loop in a single database transaction guarantees atomicity. If any entry fails (e.g., optimistic lock conflict), the entire transaction rolls back.

**Alternatives considered**:
- **Saga pattern**: Rejected — unnecessary for same-database operations within a single bounded context.
- **Domain service with compensating events**: Rejected — adds complexity when a simple database transaction suffices.

## R3: Recall Feasibility Check (MonthlyApproval Interaction)

**Decision**: Before allowing recall, check whether any of the member's entries for that date are part of an approved/rejected MonthlyApproval. If the MonthlyApproval status is APPROVED or REJECTED, recall is blocked.

**Rationale**: Entries submitted individually (not through MonthlyApproval) have no associated MonthlyApproval record — recall is always allowed. Entries submitted via monthly batch have a MonthlyApproval record — recall should be blocked if the manager has already acted (APPROVED/REJECTED). If the MonthlyApproval is still in SUBMITTED status, recall of individual entries is also blocked to avoid inconsistency with the monthly approval record.

**Alternatives considered**:
- **Always allow recall regardless of MonthlyApproval**: Rejected — could create inconsistency where a MonthlyApproval references entries that are no longer SUBMITTED.
- **Automatically update MonthlyApproval on recall**: Rejected — complicates the MonthlyApproval aggregate and its lifecycle.

## R4: API Endpoint Design

**Decision**: Add two new endpoints to WorkLogController:
- `POST /api/v1/worklog/entries/submit-daily` — submit all DRAFT entries for a member/date
- `POST /api/v1/worklog/entries/recall-daily` — recall all SUBMITTED entries for a member/date back to DRAFT

**Rationale**: These are batch operations on entries filtered by member and date, not operations on individual entries (which use `PATCH /entries/{id}`). Using dedicated endpoints makes the intent clear and avoids overloading the existing PATCH semantics.

**Alternatives considered**:
- **PATCH on individual entries with status field**: Rejected — requires multiple API calls (one per entry) and doesn't guarantee atomicity from the client's perspective.
- **PUT /api/v1/worklog/entries/status**: Rejected — too generic; dedicated endpoints are more self-documenting.

## R5: Frontend Submit/Recall Button Placement

**Decision**: Add submit and recall buttons to the DailyEntryForm component, below the entry list and above the save button area.

**Rationale**: The DailyEntryForm is where members view and edit their daily entries. Placing the submit/recall buttons here keeps the action in context with the entries being affected. The existing SubmitButton component is for monthly submission in MonthlySummary — a separate SubmitDailyButton component will be created for daily submission.

**Alternatives considered**:
- **Calendar day cell action**: Rejected — day cells are too compact for action buttons; clicking a day should continue to open the DailyEntryForm.
- **Both calendar and form**: Rejected — duplicating the action increases complexity and risk of UX confusion.

## R6: Calendar Status Indicator

**Decision**: The calendar already displays per-day status indicators (DRAFT, SUBMITTED, APPROVED, REJECTED, MIXED) via the DailyCalendarEntry.status field and the Calendar component's status color scheme. No backend changes needed — the existing `GET /api/v1/worklog/calendar/{year}/{month}` endpoint already returns per-day status.

**Rationale**: The backend projection already computes aggregate daily status. When entries transition via submit/recall, the projection updates. The frontend Calendar component already renders status badges with the existing color scheme (gray=DRAFT, blue=SUBMITTED, green=APPROVED, red=REJECTED, yellow=MIXED).

**Alternatives considered**:
- **Add new status indicator component**: Rejected — the existing Calendar.tsx status badge rendering (lines 24-30, 194) already handles all statuses including MIXED.

## R7: Confirmation Dialog Pattern

**Decision**: Use a custom confirmation dialog matching the existing CopyPreviousMonthDialog pattern (modal overlay with confirm/cancel buttons), not `window.confirm()`.

**Rationale**: The codebase uses custom dialog components for important actions (CopyPreviousMonthDialog, SessionTimeoutDialog). Using `window.confirm()` would be inconsistent with the established UX pattern and lacks styling control.

**Alternatives considered**:
- **window.confirm()**: Rejected — inconsistent with codebase patterns, no styling control.
- **Inline toggle/checkbox**: Rejected — doesn't provide a clear "point of no return" moment.
