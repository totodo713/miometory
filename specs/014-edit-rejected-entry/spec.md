# Feature Specification: Edit Rejected Work Log Entries

**Feature Branch**: `014-edit-rejected-entry`
**Created**: 2026-02-20
**Status**: Draft
**Input**: User description: "REJECTEDになった内容を自分と代理入力で編集できるようにしたい"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Member Edits Own Rejected Entries (Priority: P1)

A member whose monthly work log submission has been rejected by a manager can view the rejection reason, identify which entries need correction, edit those entries, and resubmit the month for approval.

Currently, when a manager rejects a monthly submission, the entries return to DRAFT status. However, the member needs a clear, guided experience to understand what was rejected, why it was rejected, and what needs to be corrected.

**Why this priority**: This is the core use case. Without the ability for members to correct and resubmit their own rejected entries with clear feedback, the rejection workflow has no resolution path.

**Independent Test**: Can be fully tested by rejecting a member's monthly submission, then logging in as that member, viewing the rejection feedback, editing entries, and resubmitting. Delivers the complete rejection-correction cycle.

**Acceptance Scenarios**:

1. **Given** a member's monthly submission has been rejected with a reason, **When** the member opens their work log calendar for that month, **Then** the member sees a prominent notification indicating the month was rejected along with the manager's rejection reason.
2. **Given** a rejected month's entries are displayed, **When** the member views the calendar, **Then** rejected entries are visually distinguished from normal draft entries (e.g., highlighted or marked with a rejection indicator).
3. **Given** a member views a rejected entry, **When** the member clicks on that entry, **Then** the daily entry form opens in edit mode with the existing data pre-filled and the rejection reason visible.
4. **Given** a member has edited rejected entries to address feedback, **When** the member resubmits the month, **Then** the submission follows the same approval flow as the initial submission.
5. **Given** a member's month is in REJECTED status, **When** the member edits an entry and saves, **Then** the changes are persisted and the entry remains editable until the month is resubmitted.
6. **Given** a member's month is in REJECTED status, **When** the member adds a new entry or deletes an existing entry, **Then** the change is persisted and the month remains in REJECTED status until resubmitted.

---

### User Story 2 - Manager Edits Rejected Entries via Proxy Input (Priority: P2)

A manager who has rejected a subordinate's monthly submission can enter proxy mode and directly edit the rejected entries on behalf of the member, then optionally resubmit.

This supports scenarios where the manager knows exactly what corrections are needed and wants to help the member by making the fixes directly rather than going through a back-and-forth cycle.

**Why this priority**: Proxy editing of rejected entries reduces turnaround time for corrections. It is critical for efficiency but depends on the member's own editing flow (P1) as the foundation.

**Independent Test**: Can be tested by rejecting a subordinate's submission, entering proxy mode for that subordinate, editing their rejected entries, and verifying the corrections are saved under the proxy indicator.

**Acceptance Scenarios**:

1. **Given** a manager has rejected a subordinate's monthly submission, **When** the manager enters proxy mode for that subordinate, **Then** the manager can see the subordinate's rejected month with the same rejection indicators visible to the member.
2. **Given** a manager is in proxy mode viewing a subordinate's rejected entries, **When** the manager clicks on a rejected entry, **Then** the entry form opens in edit mode with existing data pre-filled and the rejection reason visible.
3. **Given** a manager edits a rejected entry in proxy mode, **When** the manager saves the change, **Then** the entry is updated with the manager recorded as the person who made the edit (proxy edit tracking).
4. **Given** a manager has finished editing rejected entries in proxy mode, **When** the manager resubmits the month on behalf of the subordinate, **Then** the resubmission is recorded with the manager as the submitter (proxy submission).

---

### User Story 3 - Rejection Reason Visibility Throughout Correction Cycle (Priority: P3)

Throughout the entire correction cycle — from the moment a month is rejected until it is resubmitted — the rejection reason remains visible and accessible to both the member and any proxy editor. This ensures the feedback is always available as context while making corrections.

**Why this priority**: Enhances the quality of corrections by keeping feedback visible. Without this, users may forget or misunderstand what needs to be fixed, leading to repeated rejections.

**Independent Test**: Can be tested by rejecting a submission with a detailed reason, then verifying the reason is visible on the calendar view, the daily entry form, and the monthly summary throughout the correction process until resubmission.

**Acceptance Scenarios**:

1. **Given** a month has been rejected with a reason, **When** the member (or proxy) views the monthly summary, **Then** the full rejection reason is displayed prominently.
2. **Given** a month has been rejected, **When** the member (or proxy) opens any individual entry within that month, **Then** the rejection reason is shown as contextual information in the entry form.
3. **Given** a rejected month's entries have been partially edited, **When** the member navigates between entries, **Then** the rejection reason persists and does not disappear after edits are saved.
4. **Given** a rejected month is resubmitted and subsequently approved, **When** the member views the month, **Then** the previous rejection reason is no longer prominently displayed (the month shows approved status).

---

### User Story 4 - Edit Daily-Submitted Rejected Entries (Priority: P2)

A member (or manager via proxy) whose individual daily work log submission has been rejected can view the rejection feedback, edit, add, or delete entries for that specific day, and resubmit. Unlike the monthly flow, daily rejection applies to a single day's entries rather than the entire fiscal month. Note: daily rejection transitions entries from SUBMITTED to DRAFT (same as monthly); the rejection reason is tracked in a separate daily rejection log, not on individual entry status.

**Why this priority**: Daily submission is an alternative workflow introduced alongside monthly submission. Supporting rejection editing at this granularity ensures both submission pathways have a complete correction cycle.

**Independent Test**: Can be tested by submitting a single day's entries, having a manager reject that day, then editing the day's entries and resubmitting. Confirms the daily rejection-correction cycle independently of monthly approval.

**Acceptance Scenarios**:

1. **Given** a member's daily submission has been rejected with a reason, **When** the member views that day on the calendar, **Then** the day is visually marked as rejected with the rejection reason accessible.
2. **Given** a daily submission has been rejected (entries returned to DRAFT with rejection tracked in daily rejection log), **When** the member opens the daily entry form, **Then** the form is editable with existing data pre-filled and the rejection reason visible.
3. **Given** a daily submission has been rejected (entries in DRAFT), **When** the member adds a new entry, edits an existing entry, or deletes an entry for that day, **Then** the change is persisted and the day remains editable until resubmitted.
4. **Given** a manager is in proxy mode for a subordinate with a rejected daily submission, **When** the manager edits entries for that day, **Then** the edits are tracked as proxy edits and the day can be resubmitted by the manager.
5. **Given** a member has corrected a rejected daily submission, **When** the member resubmits that day, **Then** only that day's entries are resubmitted (not the entire month).

---

### Edge Cases

- What happens when a member tries to edit an entry from a month that is in SUBMITTED (not REJECTED) status? The system must prevent edits and show an appropriate message.
- What happens when a manager and a member simultaneously attempt to edit the same rejected entry? The system must handle concurrent edits gracefully using optimistic locking, notifying the second editor of the conflict.
- What happens when a member tries to resubmit a rejected month without making any changes? The system should allow resubmission (the original content may have been correct and the rejection was in error).
- What happens when a rejected month contains both work log entries and absences? Both types of entries must be editable after rejection.
- What happens if a manager in proxy mode attempts to resubmit but the member has also started editing? The system must prevent conflicting submissions via optimistic locking.
- What happens when a daily submission is rejected but the same day's entries are also part of a monthly submission cycle? The daily rejection status takes precedence; the day must be resolved before the month can be submitted.
- What happens when a member has multiple rejected daily submissions? Each day is independently editable and resubmittable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow members to edit, add new, and delete their own work log entries when the monthly submission is in REJECTED status.
- **FR-002**: System MUST allow members to edit, add new, and delete their own absence entries when the monthly submission is in REJECTED status.
- **FR-003**: System MUST allow managers to edit, add new, and delete a subordinate's rejected work log entries via proxy input mode.
- **FR-004**: System MUST allow managers to edit, add new, and delete a subordinate's rejected absence entries via proxy input mode.
- **FR-005**: System MUST display the rejection reason on the monthly summary view when a month is in REJECTED status.
- **FR-006**: System MUST display the rejection reason as contextual information when editing individual entries within a rejected month.
- **FR-007**: System MUST visually distinguish entries belonging to a rejected month from normal draft entries on the calendar view.
- **FR-008**: System MUST allow resubmission of a rejected month by the member after corrections (or without changes).
- **FR-009**: System MUST allow resubmission of a rejected month by a manager on behalf of the member via proxy mode.
- **FR-010**: System MUST track who performed the edit (member or proxy manager) for audit purposes.
- **FR-011**: System MUST handle concurrent edits to the same entry using optimistic locking and notify the user of conflicts.
- **FR-012**: System MUST prevent editing of entries in months that are in SUBMITTED or APPROVED status.
- **FR-013**: System MUST allow members to edit, add new, and delete entries for a daily submission that is in REJECTED status.
- **FR-014**: System MUST allow managers to edit, add new, and delete entries for a subordinate's rejected daily submission via proxy input mode.
- **FR-015**: System MUST allow resubmission of a rejected daily submission independently of monthly approval status.
- **FR-016**: System MUST display the rejection reason when viewing or editing entries from a rejected daily submission.
- **FR-017**: System MUST display only the most recent rejection reason when a submission has been rejected multiple times. Previous rejection reasons are retained in the event history but not shown in the UI.

### Key Entities

- **MonthlyApproval**: Represents the approval status of a member's work log for a fiscal month. Contains the rejection reason and tracks the lifecycle (PENDING, SUBMITTED, APPROVED, REJECTED with resubmission cycle).
- **WorkLogEntry**: An individual daily time entry belonging to a member. Has its own status that changes in coordination with the monthly approval. Tracks who created and last edited the entry.
- **Absence**: A daily absence record for a member. Follows the same status lifecycle as work log entries in relation to monthly approval.
- **Member**: A person who enters time and submits for approval. Can edit their own rejected entries.
- **Manager**: A person who reviews submissions, can approve or reject, and can edit subordinate entries via proxy input mode.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Members can view rejection feedback and complete corrections to all entries in a rejected month within 5 minutes.
- **SC-002**: Managers can complete proxy corrections to a subordinate's rejected entries within 5 minutes without leaving proxy mode.
- **SC-003**: 100% of rejected months can be corrected and resubmitted without requiring manual workarounds (e.g., deleting and re-creating entries).
- **SC-004**: Rejection reason is visible on every screen where the member or proxy editor interacts with rejected entries (calendar, entry form, monthly summary).
- **SC-005**: Concurrent edit conflicts are detected and communicated to the user within the same interaction, without silent data loss.

## Clarifications

### Session 2026-02-20

- Q: Can members add new entries or delete existing entries in a rejected month, or only edit existing ones? → A: Full correction allowed — edit, add new, and delete entries are all permitted.
- Q: Does this feature cover only monthly rejection or also daily submit rejection? → A: Both monthly and daily rejection are in scope.
- Q: When a month/day is rejected multiple times, should all previous rejection reasons be accessible? → A: Display only the latest rejection reason; past reasons are retained in the event history for audit purposes.
- Q: Can any authorized manager edit rejected entries via proxy, or only the rejecting manager? → A: Any manager with existing proxy input permission for the member can edit rejected entries (not limited to the rejecting manager).

## Assumptions

- The existing fiscal month pattern (21st-20th) applies to the monthly rejection scope — a rejected month means all entries within that fiscal month period are affected.
- Daily rejection applies to a single date's entries only, independent of the monthly approval cycle.
- The existing proxy input permission model (manager-subordinate relationship) governs who can edit rejected entries via proxy mode. Any manager with proxy permission for the member can perform proxy edits on rejected entries, not limited to the manager who performed the rejection.
- The rejection reason is provided at the monthly level (not per individual entry). If per-entry feedback is needed, it would be a separate enhancement.
- The existing auto-save behavior (60-second timer) applies to editing rejected entries, same as editing draft entries.
- Absence entries follow the exact same rejection-edit-resubmit cycle as work log entries.

## Scope Boundaries

### In Scope

- Editing, adding, and deleting work log entries and absences within a rejected month or day (by member and proxy)
- Displaying rejection reason throughout the correction cycle (monthly and daily)
- Visual indicators for rejected status on calendar and entry forms
- Resubmission of corrected months and days
- Audit tracking of edits (who edited, when)

### Out of Scope

- Per-entry rejection feedback (rejection reason applies to the entire month or day, not individual entries)
- Partial monthly resubmission (submitting only some corrected entries — the entire month is resubmitted as a unit)
- Automated notifications to the member when their submission is rejected (separate notification feature)
- Changes to the approval queue or manager review interface
- Adding new rejection reasons or categories
