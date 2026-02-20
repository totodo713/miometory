# Tasks: Edit Rejected Work Log Entries

**Input**: Design documents from `/specs/014-edit-rejected-entry/`
**Prerequisites**: plan.md, spec.md, data-model.md, contracts/api-contracts.md, research.md, quickstart.md

**Tests**: Included per Constitution Principle II (Testing Discipline). Tests written alongside implementation.

**Organization**: Tasks grouped by user story for independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Exact file paths included in descriptions

## Path Conventions

- **Backend**: `backend/src/main/java/com/worklog/`, `backend/src/main/kotlin/com/worklog/`, `backend/src/main/resources/db/migration/`
- **Frontend**: `frontend/app/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Database migration and new domain event required by multiple user stories

- [x] T001 Create V15 migration for daily_rejection_log table in `backend/src/main/resources/db/migration/V15__daily_rejection_log.sql`
- [x] T002 Create DailyEntriesRejected domain event record in `backend/src/main/java/com/worklog/domain/worklog/events/DailyEntriesRejected.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared backend/frontend components used across multiple user stories

**CRITICAL**: No user story work can begin until this phase is complete

- [x] T003 [P] Create backend DTOs: MemberApprovalResponse, RejectDailyEntriesRequest, RejectDailyEntriesResponse, DailyRejectionResponse in `backend/src/main/java/com/worklog/api/dto/`
- [x] T004 [P] Add new error codes (REJECT_BLOCKED_BY_APPROVAL, DAILY_REJECTION_REASON_REQUIRED, DAILY_REJECTION_REASON_TOO_LONG, NO_SUBMITTED_ENTRIES_FOR_DATE) to `backend/src/main/java/com/worklog/api/GlobalExceptionHandler.java`
- [x] T005 [P] Create JdbcDailyRejectionLogRepository with findByMemberIdAndDateRange and save methods in `backend/src/main/java/com/worklog/infrastructure/repository/JdbcDailyRejectionLogRepository.java`
- [x] T006 [P] Create reusable RejectionBanner component (accepts rejectionReason, rejectionSource, rejectedByName, rejectedAt props) in `frontend/app/components/worklog/RejectionBanner.tsx`

**Checkpoint**: Foundation ready — user story implementation can now begin

---

## Phase 3: User Story 1 — Member Edits Own Rejected Entries (Priority: P1) MVP

**Goal**: Members can view rejection feedback, edit/add/delete entries in a rejected month, and resubmit. Complete monthly rejection-correction cycle.

**Independent Test**: Reject a member's monthly submission → login as member → verify rejection reason visible on calendar, MonthlySummary, and DailyEntryForm → edit entries → resubmit month.

**Covers**: FR-001, FR-002, FR-005, FR-006, FR-007, FR-008, FR-011, FR-012

### Implementation for User Story 1

- [x] T007 [P] [US1] Add GET /api/v1/worklog/approvals/member/{memberId} endpoint (query by fiscalMonthStart/End, return approval status with rejection reason) in `backend/src/main/java/com/worklog/api/ApprovalController.java`
- [x] T008 [P] [US1] Modify calendar service to include monthlyApproval object (status, rejectionReason, reviewedBy, reviewerName, reviewedAt) in calendar response in `backend/src/main/java/com/worklog/api/CalendarController.java`
- [x] T009 [US1] Add fetchMemberApproval API function (GET approval/member/{id}) in `frontend/app/services/api.ts`
- [x] T010 [US1] Add prominent rejection reason banner to MonthlySummary using RejectionBanner component (display when monthlyApproval.status == REJECTED) in `frontend/app/components/worklog/MonthlySummary.tsx`
- [x] T011 [US1] Add monthly rejection visual indicators to Calendar (overlay rejection marker on all days within rejected fiscal month, distinct from normal DRAFT styling) in `frontend/app/components/worklog/Calendar.tsx`
- [x] T012 [US1] Show rejection reason context in DailyEntryForm when editing entries within a rejected month (display RejectionBanner with monthly rejection reason above form) in `frontend/app/components/worklog/DailyEntryForm.tsx`

### Tests for User Story 1

- [x] T033 [P] [US1] Integration test for GET /api/v1/worklog/approvals/member/{memberId} endpoint in `backend/src/test/java/com/worklog/api/ApprovalControllerMemberViewTest.java`
- [x] T034 [P] [US1] Integration test for calendar response monthlyApproval field in `backend/src/test/java/com/worklog/api/WorkLogControllerCalendarRejectionTest.java`
- [x] T035 [P] [US1] Component test for RejectionBanner in `frontend/tests/unit/components/worklog/RejectionBanner.test.tsx`
- [x] T036 [P] [US1] Component test for MonthlySummary rejection display in `frontend/tests/unit/components/worklog/MonthlySummary.rejection.test.tsx`

**Checkpoint**: User Story 1 fully functional — members can see rejection feedback, edit entries, and resubmit rejected months

---

## Phase 4: User Story 2 — Manager Proxy Edit of Rejected Entries (Priority: P2)

**Goal**: Managers can edit rejected entries via proxy mode and resubmit on behalf of members for both monthly and daily submissions.

**Independent Test**: Reject subordinate's submission → enter proxy mode → edit entries → verify proxy edit tracking → resubmit via proxy.

**Covers**: FR-003, FR-004, FR-009, FR-010

### Implementation for User Story 2

- [x] T013 [US2] Remove SELF_SUBMISSION_ONLY constraint from submitDaily and add proxy permission check (memberRepository.isSubordinateOf) in `backend/src/main/java/com/worklog/application/service/WorkLogEntryService.java`
- [x] T014 [US2] Remove SELF_RECALL_ONLY constraint from recallDaily and add proxy permission check in `backend/src/main/java/com/worklog/application/service/WorkLogEntryService.java`
- [x] T015 [US2] Add proxy submission support to monthly submit (validate proxy permission when submittedBy != memberId, track in MonthSubmittedForApproval event) in `backend/src/main/java/com/worklog/application/approval/ApprovalService.java`
- [x] T016 [US2] Add proxy monthly resubmission support to SubmitButton (pass current user as submittedBy in proxy mode, show proxy indicator) in `frontend/app/components/worklog/SubmitButton.tsx`
- [x] T017 [US2] Add proxy daily submission and recall support to SubmitDailyButton (pass submittedBy/recalledBy in proxy mode) in `frontend/app/components/worklog/SubmitDailyButton.tsx`

### Tests for User Story 2

- [x] T037 [US2] Unit test for proxy permission check in submitDaily/recallDaily in `backend/src/test/java/com/worklog/application/WorkLogEntryServiceProxyTest.java`
- [x] T038 [P] [US2] Unit test for proxy monthly submission in `backend/src/test/java/com/worklog/application/ApprovalServiceProxySubmitTest.java`

**Checkpoint**: User Stories 1 AND 2 independently functional — proxy edit and resubmission works for both monthly and daily flows

---

## Phase 5: User Story 4 — Edit Daily-Submitted Rejected Entries (Priority: P2)

**Goal**: Managers can reject individual daily submissions; members (and proxies) can edit and resubmit rejected daily entries.

**Independent Test**: Submit a single day → manager rejects that day → verify day marked as rejected on calendar → edit entries for that day → resubmit day only.

**Covers**: FR-013, FR-014, FR-015, FR-016

**Note**: T025 (daily resubmit via proxy) depends on T013 (US2) for proxy submission support. If implementing US4 before US2, proxy daily resubmit will only work for self-submission.

### Implementation for User Story 4

- [x] T018 [US4] Implement daily rejection service logic (find SUBMITTED entries for member/date, transition to DRAFT, persist DailyRejectionLog, emit DailyEntriesRejected event, validate no conflicting monthly approval) in `backend/src/main/java/com/worklog/application/service/WorkLogEntryService.java`
- [x] T019 [US4] Add POST /api/v1/worklog/entries/reject-daily endpoint (validate request, delegate to service, return RejectDailyEntriesResponse) in `backend/src/main/java/com/worklog/api/WorkLogController.java`
- [x] T020 [US4] Add GET /api/v1/worklog/rejections/daily endpoint (query DailyRejectionLogRepository by memberId and date range, return list of rejections) in `backend/src/main/java/com/worklog/api/RejectionController.java`
- [x] T021 [US4] Modify calendar service to enrich daily calendar entries with daily rejection data (rejectionSource, rejectionReason fields from daily_rejection_log) in `backend/src/main/java/com/worklog/api/CalendarController.java`
- [x] T022 [US4] Add fetchDailyRejections and rejectDailyEntries API functions in `frontend/app/services/api.ts`
- [x] T023 [US4] Add daily rejection visual indicators to Calendar (mark individually rejected days, distinct from monthly rejection) in `frontend/app/components/worklog/Calendar.tsx`
- [x] T024 [US4] Show daily rejection reason in DailyEntryForm (display RejectionBanner with daily rejection reason when day has rejectionSource == "daily") in `frontend/app/components/worklog/DailyEntryForm.tsx`
- [x] T025 [US4] Update SubmitDailyButton to show daily rejection status indicator and enable resubmission after correction in `frontend/app/components/worklog/SubmitDailyButton.tsx`

### Tests for User Story 4

- [x] T039 [P] [US4] Integration test for POST /api/v1/worklog/entries/reject-daily endpoint in `backend/src/test/java/com/worklog/api/WorkLogControllerRejectDailyTest.java`
- [x] T040 [P] [US4] Integration test for GET /api/v1/worklog/rejections/daily endpoint in `backend/src/test/java/com/worklog/api/WorkLogControllerRejectDailyTest.java`
- [x] T041 [P] [US4] Unit test for daily rejection service logic in `backend/src/test/java/com/worklog/application/WorkLogEntryServiceDailyRejectTest.java`
- [x] T042 [P] [US4] Repository test for JdbcDailyRejectionLogRepository in `backend/src/test/kotlin/com/worklog/infrastructure/JdbcDailyRejectionLogRepositoryTest.kt`

**Checkpoint**: Daily rejection-correction cycle fully functional alongside monthly flow

---

## Phase 6: User Story 3 — Rejection Reason Visibility Throughout Correction Cycle (Priority: P3)

**Goal**: Rejection reason remains visible and accessible on every screen (calendar, entry form, monthly summary) throughout the entire correction cycle until resubmission and approval.

**Independent Test**: Reject a submission with detailed reason → navigate between calendar, entry form, and monthly summary → verify reason visible on all screens → partially edit entries → verify reason persists → resubmit and get approved → verify reason no longer displayed.

**Covers**: FR-005, FR-006, FR-017

### Implementation for User Story 3

- [x] T026 [US3] Create useRejectionStatus hook (consolidates monthly and daily rejection state, provides rejectionReason/rejectionSource/isRejected per date range) in `frontend/app/hooks/useRejectionStatus.ts`
- [x] T027 [US3] Integrate useRejectionStatus hook into Calendar.tsx, DailyEntryForm.tsx, and MonthlySummary.tsx to replace inline rejection state logic with unified hook
- [x] T028 [US3] Ensure rejection reason persists across navigation within the correction cycle and clears after reapproval (verify worklogStore.ts state management for rejection lifecycle)

### Tests for User Story 3

- [x] T043 [US3] Component test for useRejectionStatus hook in `frontend/tests/unit/hooks/useRejectionStatus.test.ts`

**Checkpoint**: All user stories independently functional with consistent rejection visibility

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Code quality, formatting, and final validation

- [x] T029 [P] Run backend formatAll and detekt checks in `backend/` (`./gradlew formatAll && ./gradlew detekt`)
- [x] T030 [P] Run frontend lint and format checks in `frontend/` (`npm run check:ci`)
- [x] T031 Verify optimistic locking for concurrent edit scenarios (member + proxy editing same entry simultaneously) and verify proxy edit audit trail (enteredBy/recordedBy correctly set to proxy manager UUID for proxy edits of rejected entries)
- [x] T032 Run quickstart.md manual testing validation (all 3 testing flows: monthly rejection, proxy edit, daily rejection)
- [x] T044 Verify absence entries follow same rejection-edit-resubmit cycle as work log entries (edit, add, delete absences in rejected month/day via both member and proxy mode)
- [x] T045 Verify performance targets: reject-daily and GET approval endpoints respond <200ms p95; calendar with rejection enrichment loads <500ms p95

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 completion — BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Phase 2 — can start immediately after foundational
- **US2 (Phase 4)**: Depends on Phase 2 — can run in parallel with US1 (different files)
- **US4 (Phase 5)**: Depends on Phase 2 — can run in parallel with US1/US2
- **US3 (Phase 6)**: Depends on US1 and US4 (refactors their rejection display into unified hook)
- **Polish (Phase 7)**: Depends on all user stories being complete

### User Story Dependencies

- **US1 (P1, MVP)**: Phase 2 only — no dependencies on other stories
- **US2 (P2)**: Phase 2 only — independent of US1 (proxy edit is separate from member edit)
- **US4 (P2)**: Phase 2 only — independent of US1/US2 (daily rejection is separate mechanism)
- **US3 (P3)**: Depends on US1 and US4 — refactors frontend rejection display into unified hook

### Within Each User Story

- Backend changes before frontend changes (APIs must exist before UI consumes them)
- Service logic before controller endpoints
- API client functions before UI components that call them
- Core implementation before integration/polish

### Parallel Opportunities

**Phase 2** — all tasks are [P] (different files):
```
T003 (DTOs) | T004 (error codes) | T005 (repository) | T006 (RejectionBanner)
```

**Phase 3 (US1)** — backend tasks are [P] (different files):
```
T007 (ApprovalController) | T008 (calendar service)
```

**After Phase 2** — user stories US1, US2, US4 can start in parallel:
```
Phase 3 (US1) | Phase 4 (US2) | Phase 5 (US4)
```

---

## Parallel Example: User Story 1

```bash
# Launch backend tasks in parallel (different files):
Task T007: "Add GET approval/member/{memberId} in ApprovalController.java"
Task T008: "Modify calendar service to include monthlyApproval"

# Then sequential frontend tasks (api.ts needed before components):
Task T009: "Add fetchMemberApproval in api.ts"

# Then frontend components in parallel (different files):
Task T010: "Add rejection banner to MonthlySummary.tsx"
Task T011: "Add rejection indicators to Calendar.tsx"
Task T012: "Show rejection reason in DailyEntryForm.tsx"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (migration + domain event)
2. Complete Phase 2: Foundational (DTOs, error codes, repository, RejectionBanner)
3. Complete Phase 3: User Story 1 (member edits own rejected monthly entries)
4. **STOP and VALIDATE**: Test monthly rejection-correction cycle end-to-end
5. Deploy/demo if ready — core value delivered

### Incremental Delivery

1. Phase 1 + 2 → Foundation ready
2. Add US1 → Test independently → Deploy/Demo (**MVP!**)
3. Add US2 → Test proxy edit independently → Deploy/Demo
4. Add US4 → Test daily rejection independently → Deploy/Demo
5. Add US3 → Test rejection visibility consistency → Deploy/Demo
6. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: US1 (monthly rejection edit)
   - Developer B: US2 (proxy edit) + US4 (daily rejection)
3. After US1 and US4 complete:
   - Developer A or B: US3 (rejection visibility unification)
4. Stories integrate independently via shared RejectionBanner and API layer

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story independently completable and testable (except US3 which unifies UI patterns from US1/US4)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Existing edit/delete/create endpoints already work for DRAFT entries — no changes needed there
- Optimistic locking already implemented via event store versioning — reuse existing infrastructure
