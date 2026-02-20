# Tasks: Submit Work Log Entries

**Input**: Design documents from `/specs/013-submit-worklog-entry/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Test tasks are included per Constitution Principle II (Testing Discipline).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup

**Purpose**: No new project setup required — existing project structure. Verify domain model supports needed transitions.

- [x] T001 Verify WorkLogStatus.canTransitionTo() supports SUBMITTED→DRAFT transition in backend/src/main/java/com/worklog/domain/worklog/WorkLogStatus.java

**Checkpoint**: Confirmed existing domain model supports all required status transitions without modification.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Backend DTOs, commands, and response records shared across user stories

**⚠️ CRITICAL**: US1 and US4 backend work depends on these objects

- [x] T002 [P] Create SubmitDailyEntriesCommand record in backend/src/main/java/com/worklog/application/command/SubmitDailyEntriesCommand.java (fields: MemberId memberId, LocalDate date, MemberId submittedBy; validate all required, submittedBy must equal memberId)
- [x] T003 [P] Create SubmitDailyEntriesRequest record in backend/src/main/java/com/worklog/api/dto/SubmitDailyEntriesRequest.java (fields: UUID memberId, LocalDate date, UUID submittedBy; constructor validation for required fields)
- [x] T004 [P] Create SubmitDailyEntriesResponse record in backend/src/main/java/com/worklog/api/dto/SubmitDailyEntriesResponse.java (fields: int submittedCount, LocalDate date, List<EntryStatusItem> entries; inner record EntryStatusItem with id, projectId, hours, status, version)
- [x] T005 [P] Create RecallDailyEntriesCommand record in backend/src/main/java/com/worklog/application/command/RecallDailyEntriesCommand.java (fields: MemberId memberId, LocalDate date, MemberId recalledBy; validate all required, recalledBy must equal memberId)
- [x] T006 [P] Create RecallDailyEntriesRequest record in backend/src/main/java/com/worklog/api/dto/RecallDailyEntriesRequest.java (fields: UUID memberId, LocalDate date, UUID recalledBy; constructor validation)
- [x] T007 [P] Create RecallDailyEntriesResponse record in backend/src/main/java/com/worklog/api/dto/RecallDailyEntriesResponse.java (fields: int recalledCount, LocalDate date, List<EntryStatusItem> entries; reuse or mirror EntryStatusItem from SubmitDailyEntriesResponse)

**Checkpoint**: All DTOs and commands ready — user story implementation can begin.

---

## Phase 3: User Story 1 — Submit Daily Entries (Priority: P1) 🎯 MVP

**Goal**: Members can submit all DRAFT entries for a specific day, transitioning them to SUBMITTED status atomically.

**Independent Test**: Create DRAFT entries for a day, call POST /entries/submit-daily, verify all entries become SUBMITTED and are read-only.

### Backend Implementation

- [x] T008 [US1] Implement submitDailyEntries() method in backend/src/main/java/com/worklog/application/service/WorkLogEntryService.java (@Transactional; validate submittedBy==memberId or throw SELF_SUBMISSION_ONLY; query DRAFT entries for member+date; throw NO_DRAFT_ENTRIES if none; iterate and call entry.changeStatus(SUBMITTED, submittedBy); save each to event store; return updated entries)
- [x] T009 [US1] Add POST /api/v1/worklog/entries/submit-daily endpoint in backend/src/main/java/com/worklog/api/WorkLogController.java (accept SubmitDailyEntriesRequest, convert to command, call service, return 200 with SubmitDailyEntriesResponse)
- [x] T010 [US1] Register new error codes in backend/src/main/java/com/worklog/api/GlobalExceptionHandler.java (add NO_DRAFT_ENTRIES to 404 mappings, SELF_SUBMISSION_ONLY to 403 mappings)

### Backend Tests

- [x] T011 [P] [US1] Write integration test for submit-daily endpoint in backend/src/test/java/com/worklog/api/WorkLogSubmitDailyTest.java (test: successful submit transitions DRAFT→SUBMITTED; no DRAFT entries returns 404; mixed statuses only submits DRAFT; self-submission validation returns 403; optimistic lock conflict returns 409; verify endpoint responds within 1000ms for 5 entries as SC-001 regression guard; verify PATCH on newly SUBMITTED entries returns 422 NOT_EDITABLE confirming FR-004)
- [x] T012 [P] [US1] Write unit test for submitDailyEntries service method in backend/src/test/java/com/worklog/application/service/WorkLogEntryServiceSubmitTest.java (test: atomicity — all or nothing; validates submittedBy==memberId; filters only DRAFT entries)

### Frontend Implementation

- [x] T013 [US1] Add submitDailyEntries() method to worklog API client in frontend/app/services/api.ts (POST /api/v1/worklog/entries/submit-daily with request body { memberId, date, submittedBy }; return { submittedCount, date, entries[] })
- [x] T014 [US1] Create SubmitDailyButton component in frontend/app/components/worklog/SubmitDailyButton.tsx (props: date, memberId, entries, onSubmitSuccess, onRecallSuccess; render "Submit" button when DRAFT entries exist; disabled when no DRAFT entries; call api.worklog.submitDailyEntries; basic version without confirmation dialog)
- [x] T015 [US1] Integrate SubmitDailyButton into DailyEntryForm in frontend/app/components/worklog/DailyEntryForm.tsx (render SubmitDailyButton below entry list; pass current entries and date; if hasUnsavedChanges is true when submit is triggered, call handleSave() first with "Saving changes..." state, then proceed with submission; on submit success: trigger calendarRefreshKey and reload entries; hide button when all entries are SUBMITTED/APPROVED)

### Frontend Tests

- [x] T016 [P] [US1] Write component test for SubmitDailyButton in frontend/__tests__/components/worklog/SubmitDailyButton.test.tsx (test: renders submit button when DRAFT entries exist; button disabled when no DRAFT entries; calls API on click; updates parent state on success)

**Checkpoint**: User Story 1 complete — members can submit daily entries via button. Entries become SUBMITTED and read-only. Calendar refreshes to show updated status.

---

## Phase 4: User Story 2 & 3 — Visual Feedback & Confirmation (Priority: P2)

**Goal**: Add confirmation dialog before submission and clear success/error feedback after submission attempts.

**Independent Test**: Click Submit → confirmation dialog appears → confirm → success notification shown. On failure → error notification shown.

### Implementation

- [x] T017 [US3] Add confirmation dialog to SubmitDailyButton in frontend/app/components/worklog/SubmitDailyButton.tsx (modal overlay matching CopyPreviousMonthDialog pattern; show entry count and total hours in dialog; "Submit" and "Cancel" buttons; Escape key closes; focus trap within dialog)
- [x] T018 [US2] Add success and error notification handling to SubmitDailyButton in frontend/app/components/worklog/SubmitDailyButton.tsx (success: inline green alert with submitted count, auto-dismiss after 3 seconds; error: inline red alert with error message; 409 conflict: specific message to refresh and retry; use role="alert" aria-live="polite" for accessibility)
### Tests

- [x] T020 [P] [US3] Write component test for confirmation dialog in frontend/__tests__/components/worklog/SubmitDailyButton.test.tsx (test: dialog appears on submit click; cancel closes dialog without API call; confirm triggers API call; Escape key dismisses)
- [x] T021 [P] [US2] Write component test for success/error notifications in frontend/__tests__/components/worklog/SubmitDailyButton.test.tsx (test: success notification shown after submit; error notification shown on API failure; 409 conflict shows specific message)

**Checkpoint**: User Stories 2 and 3 complete — submission has confirmation step and clear visual feedback.

---

## Phase 5: User Story 4 — Recall Submission (Priority: P3)

**Goal**: Members can recall submitted entries back to DRAFT before manager approval/rejection.

**Independent Test**: Submit entries, then click Recall, verify entries return to DRAFT and become editable again. Entries with manager action cannot be recalled.

### Backend Implementation

- [x] T022 [US4] Implement recallDailyEntries() method in backend/src/main/java/com/worklog/application/service/WorkLogEntryService.java (@Transactional; validate recalledBy==memberId or throw SELF_RECALL_ONLY; query SUBMITTED entries for member+date; throw NO_SUBMITTED_ENTRIES if none; check MonthlyApproval: query approvals containing any of these entry IDs where status is SUBMITTED/APPROVED/REJECTED — if found, throw RECALL_BLOCKED_BY_APPROVAL; iterate and call entry.changeStatus(DRAFT, recalledBy); save each; return updated entries)
- [x] T023 [US4] Add POST /api/v1/worklog/entries/recall-daily endpoint in backend/src/main/java/com/worklog/api/WorkLogController.java (accept RecallDailyEntriesRequest, convert to command, call service, return 200 with RecallDailyEntriesResponse)
- [x] T024 [US4] Register recall error codes in backend/src/main/java/com/worklog/api/GlobalExceptionHandler.java (add NO_SUBMITTED_ENTRIES to 404 mappings, SELF_RECALL_ONLY to 403 mappings, RECALL_BLOCKED_BY_APPROVAL to 422 state violation mappings)

### Backend Tests

- [x] T025 [P] [US4] Write integration test for recall-daily endpoint in backend/src/test/java/com/worklog/api/WorkLogRecallDailyTest.java (test: successful recall transitions SUBMITTED→DRAFT; no SUBMITTED entries returns 404; entries with MonthlyApproval returns 422; self-recall validation returns 403; entries become editable after recall)
- [x] T026 [P] [US4] Write unit test for recallDailyEntries service method in backend/src/test/java/com/worklog/application/service/WorkLogEntryServiceRecallTest.java (test: atomicity; validates recalledBy==memberId; MonthlyApproval blocking logic; filters only SUBMITTED entries)

### Frontend Implementation

- [x] T027 [US4] Add recallDailyEntries() method to worklog API client in frontend/app/services/api.ts (POST /api/v1/worklog/entries/recall-daily with request body { memberId, date, recalledBy }; return { recalledCount, date, entries[] })
- [x] T028 [US4] Add recall mode to SubmitDailyButton in frontend/app/components/worklog/SubmitDailyButton.tsx (show "Recall" button when all entries are SUBMITTED and no approval action taken; call api.worklog.recallDailyEntries; on success: show success notification, trigger entry reload and calendarRefreshKey; on 422 RECALL_BLOCKED_BY_APPROVAL: show specific message that manager has already acted; on 409 conflict: show refresh-and-retry message matching submit flow; no confirmation dialog for recall — it's a reversible action back to editable state)

### Frontend Tests

- [x] T029 [P] [US4] Write component test for recall functionality in frontend/__tests__/components/worklog/SubmitDailyButton.test.tsx (test: recall button shown when entries are SUBMITTED; recall calls API; success notification on recall; error notification on blocked recall; button hidden when entries have approval action)

**Checkpoint**: User Story 4 complete — members can recall submitted entries before manager action.

---

## Phase 6: User Story 5 — Calendar Submission Status Indicator (Priority: P3)

**Goal**: Calendar month view displays per-day status indicators reflecting submission state.

**Independent Test**: View monthly calendar with mix of DRAFT, SUBMITTED, and MIXED days — each shows correct status badge color.

### Verification

- [x] T030 [US5] Verify existing Calendar.tsx status badge rendering handles all states correctly in frontend/app/components/worklog/Calendar.tsx (confirm status color map includes DRAFT=gray, SUBMITTED=blue, APPROVED=green, REJECTED=red, MIXED=yellow; confirm DailyCalendarEntry.status is rendered in day cells; no code changes expected per research R6)
- [x] T031 [US5] Verify calendar refresh after submit/recall in frontend/app/worklog/page.tsx (confirm calendarRefreshKey increment triggers loadCalendar(); confirm DailyEntryForm onSave callback triggers refresh; verify submit/recall success callbacks in SubmitDailyButton also trigger refresh)
- [x] T032 [US5] Write integration test verifying calendar status updates in frontend/__tests__/components/worklog/Calendar.test.tsx (test: day with all DRAFT entries shows gray indicator; day with all SUBMITTED shows blue; day with mixed shows yellow; day with no entries shows no indicator)

**Checkpoint**: User Story 5 verified — calendar correctly shows per-day submission status.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Code quality, formatting, and final validation

- [x] T033 [P] Run backend format check and fix in backend/ (./gradlew formatAll && ./gradlew checkFormat && ./gradlew detekt)
- [x] T034 [P] Run frontend lint and format check in frontend/ (npm run check:ci)
- [x] T035 Execute manual testing flow per specs/013-submit-worklog-entry/quickstart.md
- [x] T036 Verify existing monthly batch submission still works alongside daily submission (create entries, submit daily, then trigger monthly submission — entries already SUBMITTED should be skipped)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — verify existing domain support
- **Phase 2 (Foundational)**: Depends on Phase 1 — creates DTOs and commands
- **Phase 3 (US1 — Submit)**: Depends on Phase 2 — core backend + frontend
- **Phase 4 (US2+US3 — Feedback & Confirmation)**: Depends on Phase 3 — enhances submit UX
- **Phase 5 (US4 — Recall)**: Depends on Phase 2 — can run in parallel with Phase 3/4
- **Phase 6 (US5 — Calendar)**: Depends on Phase 3 — verification of existing behavior
- **Phase 7 (Polish)**: Depends on all previous phases

### User Story Dependencies

- **US1 (P1)**: Depends on Phase 2 foundational DTOs/commands
- **US2 (P2)**: Depends on US1 (enhances existing submit button)
- **US3 (P2)**: Depends on US1 (adds dialog to existing submit button)
- **US4 (P3)**: Depends on Phase 2 foundational DTOs/commands — **independent of US1/US2/US3** at backend level; frontend recall button is added to same SubmitDailyButton component so depends on T014
- **US5 (P3)**: Depends on US1 for verification — **no code changes expected**

### Within Each User Story

- Commands/DTOs before service methods
- Service methods before controller endpoints
- Backend endpoints before frontend API client
- API client before UI components
- Tests can run in parallel with implementation (marked [P])

### Parallel Opportunities

- All Phase 2 DTOs/commands (T002–T007) can run in parallel
- Backend tests (T011, T012) can run in parallel with frontend implementation (T013–T015)
- US4 backend (T022–T026) can start in parallel with US2/US3 (T017–T021) since they touch different backend methods
- Phase 7 format checks (T033, T034) can run in parallel

---

## Parallel Example: Phase 2 (Foundational)

```bash
# All DTOs and commands in parallel (different files):
T002: Create SubmitDailyEntriesCommand.java
T003: Create SubmitDailyEntriesRequest.java
T004: Create SubmitDailyEntriesResponse.java
T005: Create RecallDailyEntriesCommand.java
T006: Create RecallDailyEntriesRequest.java
T007: Create RecallDailyEntriesResponse.java
```

## Parallel Example: US1 Tests + Frontend

```bash
# Backend tests in parallel with frontend work (different directories):
T011: Integration test WorkLogSubmitDailyTest.java     # backend/src/test/
T012: Unit test WorkLogEntryServiceSubmitTest.java      # backend/src/test/
T013: API client submitDailyEntries()                   # frontend/app/services/
T014: SubmitDailyButton component                       # frontend/app/components/
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Verify domain model (T001)
2. Complete Phase 2: Create DTOs and commands (T002–T004 only, skip recall DTOs)
3. Complete Phase 3: US1 backend + frontend (T008–T016)
4. **STOP and VALIDATE**: Test daily submission end-to-end
5. Deploy/demo if ready — core value delivered

### Incremental Delivery

1. Phase 1 + Phase 2 → Foundation ready
2. US1 (Submit) → Test independently → **MVP deployed**
3. US2+US3 (Feedback + Confirmation) → Test independently → UX polished
4. US4 (Recall) → Test independently → Safety net added
5. US5 (Calendar verification) → Confirm existing behavior → Feature complete
6. Each increment adds value without breaking previous stories

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- No database migrations needed — all changes use existing schema and event sourcing
- Domain model already supports DRAFT→SUBMITTED and SUBMITTED→DRAFT transitions
- Calendar already displays per-day status — US5 is verification only
- Existing SubmitButton.tsx (monthly) is separate from new SubmitDailyButton.tsx (daily)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
