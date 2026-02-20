# Feature Specification: Submit Work Log Entries

**Feature Branch**: `013-submit-worklog-entry`
**Created**: 2026-02-20
**Status**: Draft
**Input**: User description: "自分の記録をDraftではなく、Submittedにする、submittedで保存する機能を作成して"

## Clarifications

### Session 2026-02-20

- Q: Can a member recall (undo) a submission before the manager acts on it? → A: Yes — members can recall their submission back to DRAFT as long as the manager has not yet approved or rejected it.
- Q: Should the calendar view visually distinguish submitted days from draft days? → A: Yes — display a per-day status indicator (color or icon) so members can quickly see which days are still pending submission.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Submit Daily Entries (Priority: P1)

As a member, I want to submit all my work log entries for a specific day so that they are marked as "Submitted" and sent for manager approval.

Currently, entries are always saved as "Draft" and can only be submitted through the monthly approval workflow. This feature allows members to submit entries on a per-day basis, giving them more control over when entries become read-only and enter the approval queue.

**Why this priority**: This is the core functionality requested. Without this, there is no way to submit entries outside of the monthly batch process.

**Independent Test**: Can be fully tested by creating DRAFT entries for a day, clicking the submit button, and verifying all entries transition to SUBMITTED status and become read-only.

**Acceptance Scenarios**:

1. **Given** a member has one or more DRAFT entries for a specific date, **When** the member clicks "Submit" for that day, **Then** all DRAFT entries for that date transition to SUBMITTED status and become read-only.
2. **Given** a member has no DRAFT entries for a specific date (all already SUBMITTED or APPROVED), **When** the member views that day, **Then** the submit action is not available.
3. **Given** a member has a mix of DRAFT and SUBMITTED entries for a date, **When** the member clicks "Submit", **Then** only the DRAFT entries transition to SUBMITTED; already-submitted entries remain unchanged.

---

### User Story 2 - Visual Feedback on Submission (Priority: P2)

As a member, I want to see clear visual feedback when my entries have been successfully submitted so that I have confidence the action was completed.

**Why this priority**: Without visual feedback, members may be uncertain whether their submission succeeded, leading to repeated attempts or confusion.

**Independent Test**: Can be tested by submitting entries and verifying that a success notification appears and the entry status badges update from "Draft" to "Submitted".

**Acceptance Scenarios**:

1. **Given** a member submits entries for a day, **When** the submission succeeds, **Then** a success notification is displayed and the status badges update to "Submitted".
2. **Given** a member submits entries for a day, **When** the submission fails (e.g., network error, version conflict), **Then** an error notification is displayed and entries remain in DRAFT status.

---

### User Story 3 - Confirmation Before Submission (Priority: P2)

As a member, I want to be asked for confirmation before submitting my entries so that I do not accidentally make them read-only.

**Why this priority**: Submission makes entries read-only (only a manager rejection can revert them). Accidental submission would require manager intervention to correct, so a confirmation step prevents unnecessary friction.

**Independent Test**: Can be tested by clicking Submit and verifying a confirmation dialog appears before the action proceeds.

**Acceptance Scenarios**:

1. **Given** a member clicks "Submit" for a day, **When** the confirmation dialog appears, **Then** the member can confirm to proceed or cancel to abort.
2. **Given** a member cancels the confirmation, **When** the dialog closes, **Then** all entries remain in DRAFT status with no changes.

---

### User Story 4 - Recall Submission (Priority: P3)

As a member, I want to recall my submitted entries back to Draft status so that I can correct mistakes without needing manager intervention, as long as the manager has not yet acted on them.

**Why this priority**: This is a safety net for the submission feature. While the confirmation dialog (P2) prevents most accidental submissions, recall provides a self-service recovery path for genuine mistakes discovered after submission.

**Independent Test**: Can be tested by submitting entries, then clicking "Recall" and verifying entries return to DRAFT and become editable again.

**Acceptance Scenarios**:

1. **Given** a member has SUBMITTED entries for a date that have not been approved or rejected, **When** the member clicks "Recall" for that day, **Then** all SUBMITTED entries for that date transition back to DRAFT and become editable.
2. **Given** a member has SUBMITTED entries that are part of a monthly batch submission (MonthlyApproval) or have been approved/rejected, **When** the member views that day, **Then** the recall action is not available.
3. **Given** a member recalls entries for a day, **When** the recall succeeds, **Then** a success notification is displayed and status badges update to "Draft".

---

### User Story 5 - Calendar Submission Status Indicator (Priority: P3)

As a member, I want to see at a glance which days in the calendar have been submitted and which are still in draft so that I can track my submission progress across the month.

**Why this priority**: Enhances usability of the submission feature by providing an overview, but the core submit/recall functionality works without it.

**Independent Test**: Can be tested by having a mix of submitted and draft days in a month, opening the calendar view, and verifying each day cell shows the correct status indicator.

**Acceptance Scenarios**:

1. **Given** a member views the monthly calendar, **When** a day has all entries in DRAFT status, **Then** the day cell displays a "Draft" indicator (e.g., neutral/default styling).
2. **Given** a member views the monthly calendar, **When** a day has all entries in SUBMITTED or APPROVED status, **Then** the day cell displays a "Submitted" indicator (e.g., distinct color or icon).
3. **Given** a member views the monthly calendar, **When** a day has a mix of DRAFT and SUBMITTED entries, **Then** the day cell displays a "Partially Submitted" indicator.
4. **Given** a day has no entries, **When** the member views the calendar, **Then** no status indicator is shown for that day.

---

### Edge Cases

- What happens when a member tries to submit entries while another session has already submitted them? (Optimistic locking conflict — show error and refresh status)
- What happens when a member has unsaved changes (auto-save pending) and clicks Submit? (Save changes first, then submit)
- What happens when a member has entries with 0 hours for a day? (Allow submission — 0-hour entries are valid if they exist)
- What happens when some entries for a day fail to submit (partial failure)? (Roll back all entries for that day to DRAFT and show error — submission is all-or-nothing per day)
- What happens when a member tries to recall entries that a manager is currently reviewing? (Allow recall only if no approval/rejection action has been taken — use optimistic locking to prevent race conditions)
- What happens when a member tries to recall entries submitted via monthly batch? (Recall is blocked — entries associated with a MonthlyApproval cannot be individually recalled to avoid data inconsistency with the approval record)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow a member to submit all their DRAFT work log entries for a specific date in a single action.
- **FR-002**: System MUST transition all targeted DRAFT entries to SUBMITTED status atomically (all-or-nothing per day).
- **FR-003**: System MUST NOT allow submission of entries that are already in SUBMITTED, APPROVED, or REJECTED status.
- **FR-004**: System MUST make SUBMITTED entries read-only for the member (no editing or deleting).
- **FR-005**: System MUST display a confirmation dialog before executing the submission.
- **FR-006**: System MUST provide clear success or error feedback after a submission attempt.
- **FR-007**: System MUST handle optimistic locking conflicts gracefully, informing the member to refresh and retry.
- **FR-008**: System MUST save any unsaved changes before submitting entries.
- **FR-009**: System MUST only allow a member to submit their own entries (not entries belonging to other members).
- **FR-010**: The submit action MUST be available only when there are DRAFT entries for the selected date.
- **FR-011**: System MUST allow a member to recall (undo) their SUBMITTED entries back to DRAFT, provided the entries are not part of an active monthly batch submission (MonthlyApproval) and no manager approval or rejection has been applied.
- **FR-012**: The recall action MUST be available only when there are SUBMITTED entries for the selected date that are not associated with a MonthlyApproval record and have not been approved or rejected.
- **FR-013**: System MUST transition all recalled entries atomically (all-or-nothing per day), consistent with submit behavior.
- **FR-014**: The calendar month view MUST display a per-day status indicator reflecting the aggregate submission state of entries for each day (Draft, Submitted, Partially Submitted, or no entries).
- **FR-015**: The calendar status indicator MUST update immediately after a submit or recall action without requiring a full page refresh.

### Key Entities

- **WorkLogEntry**: Existing entity — gains the ability to be individually transitioned from DRAFT to SUBMITTED via a new submission action (distinct from the existing monthly batch submission).
- **WorkLogStatus**: Existing value object — DRAFT, SUBMITTED, APPROVED, REJECTED. No new statuses needed.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Members can submit all DRAFT entries for a single day in under 3 seconds (from click to confirmation of success).
- **SC-002**: After submission, entries are immediately reflected as "Submitted" and read-only without requiring a page refresh.
- **SC-003**: 100% of submission attempts either fully succeed (all entries for the day transition) or fully fail (no entries transition) — no partial submissions.
- **SC-004**: Members are prevented from accidentally submitting entries via a mandatory confirmation step.

## Assumptions

- The existing monthly batch submission workflow (MonthlyApproval aggregate) continues to work alongside this per-day submission feature. Both paths result in SUBMITTED entries.
- The existing manager approval/rejection workflow applies to entries submitted via this feature identically to entries submitted via the monthly batch process.
- Proxy entry submission is out of scope — only the member who owns the entries can submit them through this feature.
- This feature does not introduce a new "daily approval" aggregate; it operates directly on individual WorkLogEntry aggregates using the existing status transition mechanism.
