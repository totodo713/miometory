# Tasks: Work-Log Entry System

**Feature**: 002-work-log-entry  
**Created**: 2026-01-03  
**Status**: Ready for Implementation

**Input**: Design documents from `/specs/002-work-log-entry/`  
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/openapi.yaml ✅

**Organization**: Tasks are grouped by user story (US1-US7) to enable independent implementation and testing of each story.

---

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Verify project structure matches plan.md (backend/, frontend/, infra/docker/)
- [ ] T002 [P] Update backend/build.gradle.kts with OAuth2/SAML2 dependencies (spring-security-oauth2-client, spring-security-saml2-service-provider)
- [ ] T003 [P] Update frontend/package.json with Vitest, React Testing Library, Playwright, TanStack Query, date-fns, papaparse, zod
- [ ] T004 [P] Create frontend/vitest.config.mts and frontend/vitest.setup.ts per research.md
- [ ] T005 [P] Create frontend/playwright.config.ts per research.md
- [ ] T006 [P] Update infra/docker/docker-compose.dev.yml with PostgreSQL 16 + Redis 7 services
- [ ] T007 Create backend/src/main/resources/db/migration/V4__work_log_entry_tables.sql (events table, projections tables)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T008 Implement base Event Sourcing infrastructure in backend/src/main/java/com/worklog/eventsourcing/EventStore.java (if not exists)
- [ ] T009 [P] Implement TimeAmount value object in backend/src/main/java/com/worklog/domain/shared/TimeAmount.java
- [ ] T010 [P] Implement FiscalMonthPeriod value object in backend/src/main/java/com/worklog/domain/shared/FiscalMonthPeriod.java
- [ ] T011 [P] Implement DateRange value object in backend/src/main/java/com/worklog/domain/shared/DateRange.java
- [ ] T012 [P] Extend Member aggregate with managerId field in backend/src/main/java/com/worklog/domain/member/ (for proxy entry permission)
- [ ] T013 [P] Extend Project aggregate with isActive, validFrom, validUntil fields in backend/src/main/java/com/worklog/domain/project/
- [ ] T014 [P] Configure Spring Security OAuth2 client in backend/src/main/kotlin/com/worklog/infrastructure/config/SecurityConfig.kt
- [ ] T015 [P] Configure Spring Security SAML2 in backend/src/main/kotlin/com/worklog/infrastructure/config/SecurityConfig.kt (add SAML2 provider)
- [ ] T016 [P] Implement session timeout configuration (30 minutes) in backend/src/main/kotlin/com/worklog/infrastructure/config/SecurityConfig.kt
- [ ] T017 [P] Setup frontend Zustand store structure in frontend/app/services/worklogStore.ts
- [ ] T018 [P] Setup frontend API client with authentication in frontend/app/services/api.ts
- [ ] T019 Run Flyway migration V4 and verify tables created

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Daily Time Entry (Priority: P1) 🎯 MVP

**Goal**: Engineers can record daily work hours on specific projects with 15-minute granularity

**Independent Test**: An engineer can log in, select a date, enter 8 hours distributed across 2-3 projects in 15-minute increments, save entries, and see them reflected in calendar view with correct total.

### Backend - Domain Model (US1)

- [ ] T020 [P] [US1] Create WorkLogEntry aggregate root in backend/src/main/java/com/worklog/domain/worklog/WorkLogEntry.java
- [ ] T021 [P] [US1] Create WorkLogEntryId value object in backend/src/main/java/com/worklog/domain/worklog/WorkLogEntryId.java
- [ ] T022 [P] [US1] Create WorkLogStatus enum in backend/src/main/java/com/worklog/domain/worklog/WorkLogStatus.java (DRAFT, SUBMITTED, APPROVED, REJECTED)
- [ ] T023 [P] [US1] Create WorkLogEntryCreated event in backend/src/main/java/com/worklog/domain/worklog/events/WorkLogEntryCreated.java
- [ ] T024 [P] [US1] Create WorkLogEntryUpdated event in backend/src/main/java/com/worklog/domain/worklog/events/WorkLogEntryUpdated.java
- [ ] T025 [P] [US1] Create WorkLogEntryDeleted event in backend/src/main/java/com/worklog/domain/worklog/events/WorkLogEntryDeleted.java
- [ ] T026 [P] [US1] Create WorkLogEntryStatusChanged event in backend/src/main/java/com/worklog/domain/worklog/events/WorkLogEntryStatusChanged.java

### Backend - Application Services (US1)

- [ ] T027 [US1] Create CreateWorkLogEntryCommand in backend/src/main/java/com/worklog/application/command/CreateWorkLogEntryCommand.java
- [ ] T028 [US1] Create UpdateWorkLogEntryCommand in backend/src/main/java/com/worklog/application/command/UpdateWorkLogEntryCommand.java
- [ ] T029 [US1] Create DeleteWorkLogEntryCommand in backend/src/main/java/com/worklog/application/command/DeleteWorkLogEntryCommand.java
- [ ] T030 [US1] Implement WorkLogEntryService in backend/src/main/java/com/worklog/application/service/WorkLogEntryService.java (validate 24h daily limit)
- [ ] T031 [US1] Implement JdbcWorkLogRepository in backend/src/main/java/com/worklog/infrastructure/repository/JdbcWorkLogRepository.java

### Backend - API Endpoints (US1)

- [ ] T032 [US1] Implement POST /api/v1/worklog/entries in backend/src/main/java/com/worklog/api/WorkLogController.java
- [ ] T033 [US1] Implement GET /api/v1/worklog/entries in backend/src/main/java/com/worklog/api/WorkLogController.java (with date range filter)
- [ ] T034 [US1] Implement GET /api/v1/worklog/entries/{id} in backend/src/main/java/com/worklog/api/WorkLogController.java
- [ ] T035 [US1] Implement PATCH /api/v1/worklog/entries/{id} in backend/src/main/java/com/worklog/api/WorkLogController.java (for auto-save with optimistic locking)
- [ ] T036 [US1] Implement DELETE /api/v1/worklog/entries/{id} in backend/src/main/java/com/worklog/api/WorkLogController.java
- [ ] T037 [US1] Add validation handling for 24h daily limit in WorkLogController

### Backend - Projections (US1)

- [ ] T038 [P] [US1] Create MonthlyCalendarProjection in backend/src/main/java/com/worklog/infrastructure/projection/MonthlyCalendarProjection.java
- [ ] T039 [US1] Implement GET /api/v1/worklog/calendar/{year}/{month} in backend/src/main/java/com/worklog/api/CalendarController.java

### Frontend - State Management (US1)

- [ ] T040 [P] [US1] Define WorkLogEntry types in frontend/app/types/worklog.ts
- [ ] T041 [US1] Implement worklogStore with fetchEntries, createEntry, updateEntry, deleteEntry actions in frontend/app/services/worklogStore.ts

### Frontend - Components (US1)

- [ ] T042 [P] [US1] Create Calendar component in frontend/app/components/worklog/Calendar.tsx (monthly view with fiscal period support)
- [ ] T043 [P] [US1] Create DailyEntryForm component in frontend/app/components/worklog/DailyEntryForm.tsx (multi-project input)
- [ ] T044 [P] [US1] Create ProjectSelector component in frontend/app/components/worklog/ProjectSelector.tsx
- [ ] T045 [US1] Implement date click handler in Calendar to open DailyEntryForm
- [ ] T046 [US1] Implement hours validation (0.25h increments, max 24h) in DailyEntryForm with Zod schema
- [ ] T047 [US1] Implement multi-project row addition/removal in DailyEntryForm

### Frontend - Pages (US1)

- [ ] T048 [US1] Create /app/worklog/page.tsx (dashboard with Calendar and fiscal month selector)
- [ ] T049 [US1] Create /app/worklog/[date]/page.tsx (daily entry form route)

### Frontend - Auto-Save (US1)

- [ ] T050 [P] [US1] Implement useAutoSave hook in frontend/app/hooks/useAutoSave.ts (60-second interval with change detection)
- [ ] T051 [P] [US1] Implement localStorage backup service in frontend/app/services/autoSaveService.ts (offline support)
- [ ] T052 [US1] Integrate auto-save into DailyEntryForm with TanStack Query optimistic updates
- [ ] T053 [US1] Implement conflict resolution dialog in frontend/app/components/worklog/ConflictDialog.tsx (409 Conflict handling)
- [ ] T054 [P] [US1] Create AutoSaveIndicator component in frontend/app/components/worklog/AutoSaveIndicator.tsx (FR-031: show status + timestamp)

### Frontend - Session Timeout (US1)

- [ ] T055 [P] [US1] Implement idle detection logic in frontend/app/hooks/useSessionTimeout.ts (mouse/keyboard/touch events)
- [ ] T056 [US1] Create session timeout warning dialog in frontend/app/components/shared/SessionTimeoutDialog.tsx (28-minute warning + 2-minute countdown)
- [ ] T057 [US1] Implement auto-logout after 30 minutes in useSessionTimeout hook

### Testing (US1)

- [ ] T058 [P] [US1] Write unit tests for WorkLogEntry aggregate in backend/src/test/java/com/worklog/domain/worklog/WorkLogEntryTest.java
- [ ] T059 [P] [US1] Write unit tests for TimeAmount validation in backend/src/test/java/com/worklog/domain/shared/TimeAmountTest.java
- [ ] T060 [P] [US1] Write integration tests for POST /api/v1/worklog/entries in backend/src/test/kotlin/com/worklog/api/WorkLogControllerTest.kt
- [ ] T061 [P] [US1] Write component tests for Calendar in frontend/tests/unit/components/Calendar.test.tsx
- [ ] T062 [P] [US1] Write component tests for DailyEntryForm in frontend/tests/unit/components/DailyEntryForm.test.tsx
- [ ] T063 [P] [US1] Write E2E test for daily entry workflow in frontend/e2e/daily-entry.spec.ts (login → select date → enter time → save → verify calendar)
- [ ] T064 [US1] Write E2E test for auto-save reliability in frontend/e2e/auto-save.spec.ts (SC-011: 99.9% reliability)

**Checkpoint**: User Story 1 (Daily Time Entry) is fully functional and independently testable

---

## Phase 4: User Story 2 - Multi-Project Time Allocation (Priority: P1)

**Goal**: Engineers can accurately record time divided between multiple projects in a single day

**Independent Test**: An engineer can enter a single day's work showing 4.5 hours on Project A, 2.5 hours on Project B, and 1 hour on internal tasks (3 separate entries), save all simultaneously, and verify calendar shows 8 total hours for that day.

### Backend - Multi-Project Support (US2)

- [ ] T065 [US2] Add multi-project daily aggregation logic to WorkLogEntryService in backend/src/main/java/com/worklog/application/service/WorkLogEntryService.java (ensure sum ≤ 24h)
- [ ] T066 [US2] Update MonthlyCalendarProjection to group entries by date in backend/src/main/java/com/worklog/infrastructure/projection/MonthlyCalendarProjection.java

### Frontend - Multi-Project UI (US2)

- [ ] T067 [US2] Enhance DailyEntryForm to display running total across all project rows in frontend/app/components/worklog/DailyEntryForm.tsx
- [ ] T068 [US2] Add real-time validation warning when total exceeds 24h in DailyEntryForm

### Frontend - Summary View (US2)

- [ ] T069 [P] [US2] Create MonthlySummary component in frontend/app/components/worklog/MonthlySummary.tsx (project breakdown table with hours + percentage)
- [ ] T070 [US2] Implement MonthlySummaryProjection backend in backend/src/main/java/com/worklog/infrastructure/projection/MonthlySummaryProjection.java
- [ ] T071 [US2] Implement GET /api/v1/worklog/summary/{year}/{month} in backend/src/main/java/com/worklog/api/CalendarController.java
- [ ] T072 [US2] Integrate MonthlySummary component into /app/worklog/page.tsx

### Testing (US2)

- [ ] T073 [P] [US2] Write integration test for multi-project 24h validation in backend/src/test/kotlin/com/worklog/api/WorkLogControllerTest.kt
- [ ] T074 [P] [US2] Write E2E test for multi-project allocation in frontend/e2e/multi-project-entry.spec.ts (3 projects, verify total)

**Checkpoint**: User Stories 1 AND 2 both work independently

---

## Phase 5: User Story 3 - Absence Recording (Priority: P1)

**Goal**: Engineers can record vacation, sick leave, and special leave separate from project work hours

**Independent Test**: An engineer can mark a date as "Paid Leave (8 hours)", save it, and verify that date shows as leave in the calendar with no expectation of project hours being entered.

### Backend - Absence Domain (US3)

- [ ] T075 [P] [US3] Create Absence aggregate root in backend/src/main/java/com/worklog/domain/absence/Absence.java
- [ ] T076 [P] [US3] Create AbsenceId value object in backend/src/main/java/com/worklog/domain/absence/AbsenceId.java
- [ ] T077 [P] [US3] Create AbsenceType enum in backend/src/main/java/com/worklog/domain/absence/AbsenceType.java (PAID_LEAVE, SICK_LEAVE, SPECIAL_LEAVE, OTHER)
- [ ] T078 [P] [US3] Create AbsenceStatus enum in backend/src/main/java/com/worklog/domain/absence/AbsenceStatus.java
- [ ] T079 [P] [US3] Create AbsenceRecorded event in backend/src/main/java/com/worklog/domain/absence/events/AbsenceRecorded.java
- [ ] T080 [P] [US3] Create AbsenceUpdated event in backend/src/main/java/com/worklog/domain/absence/events/AbsenceUpdated.java
- [ ] T081 [P] [US3] Create AbsenceDeleted event in backend/src/main/java/com/worklog/domain/absence/events/AbsenceDeleted.java

### Backend - Absence Services (US3)

- [ ] T082 [US3] Create CreateAbsenceCommand in backend/src/main/java/com/worklog/application/command/CreateAbsenceCommand.java
- [ ] T083 [US3] Implement AbsenceService in backend/src/main/java/com/worklog/application/service/AbsenceService.java (validate absence + work hours ≤ 24h)
- [ ] T084 [US3] Implement JdbcAbsenceRepository in backend/src/main/java/com/worklog/infrastructure/repository/JdbcAbsenceRepository.java

### Backend - Absence API (US3)

- [ ] T085 [P] [US3] Implement POST /api/v1/absences in backend/src/main/java/com/worklog/api/AbsenceController.java
- [ ] T086 [P] [US3] Implement GET /api/v1/absences in backend/src/main/java/com/worklog/api/AbsenceController.java
- [ ] T087 [P] [US3] Implement PATCH /api/v1/absences/{id} in backend/src/main/java/com/worklog/api/AbsenceController.java
- [ ] T088 [P] [US3] Implement DELETE /api/v1/absences/{id} in backend/src/main/java/com/worklog/api/AbsenceController.java

### Backend - Calendar Integration (US3)

- [ ] T089 [US3] Update MonthlyCalendarProjection to include absence hours in backend/src/main/java/com/worklog/infrastructure/projection/MonthlyCalendarProjection.java
- [ ] T090 [US3] Update MonthlySummaryProjection to calculate absence hours separately in backend/src/main/java/com/worklog/infrastructure/projection/MonthlySummaryProjection.java

### Frontend - Absence UI (US3)

- [ ] T091 [P] [US3] Define Absence types in frontend/app/types/absence.ts
- [ ] T092 [P] [US3] Create AbsenceForm component in frontend/app/components/worklog/AbsenceForm.tsx (absence type selector + hours input)
- [ ] T093 [US3] Integrate AbsenceForm into DailyEntryForm (tab or section) in frontend/app/components/worklog/DailyEntryForm.tsx
- [ ] T094 [US3] Add absence type color coding to Calendar component in frontend/app/components/worklog/Calendar.tsx
- [ ] T095 [US3] Update MonthlySummary to show absence breakdown by type in frontend/app/components/worklog/MonthlySummary.tsx

### Testing (US3)

- [ ] T096 [P] [US3] Write unit tests for Absence aggregate in backend/src/test/java/com/worklog/domain/absence/AbsenceTest.java
- [ ] T097 [P] [US3] Write integration tests for POST /api/v1/absences in backend/src/test/kotlin/com/worklog/api/AbsenceControllerTest.kt
- [ ] T098 [P] [US3] Write E2E test for absence recording in frontend/e2e/absence-entry.spec.ts (full day paid leave + partial sick leave)

**Checkpoint**: User Stories 1, 2, AND 3 all work independently

---

## Phase 6: User Story 4 - Monthly Time Approval Workflow (Priority: P1)

**Goal**: Engineers can submit time logs for approval, managers can approve/reject, and approved entries become read-only

**Independent Test**: An engineer completes all entries for a month, clicks "Submit for Approval", their manager reviews the submitted hours, clicks "Approve", and the engineer's time entries for that month become read-only with "Approved" status indicator.

### Backend - Approval Domain (US4)

- [ ] T099 [P] [US4] Create MonthlyApproval aggregate root in backend/src/main/java/com/worklog/domain/approval/MonthlyApproval.java
- [ ] T100 [P] [US4] Create MonthlyApprovalId value object in backend/src/main/java/com/worklog/domain/approval/MonthlyApprovalId.java
- [ ] T101 [P] [US4] Create ApprovalStatus enum in backend/src/main/java/com/worklog/domain/approval/ApprovalStatus.java (PENDING, SUBMITTED, APPROVED, REJECTED)
- [ ] T102 [P] [US4] Create MonthlyApprovalCreated event in backend/src/main/java/com/worklog/domain/approval/events/MonthlyApprovalCreated.java
- [ ] T103 [P] [US4] Create MonthSubmittedForApproval event in backend/src/main/java/com/worklog/domain/approval/events/MonthSubmittedForApproval.java
- [ ] T104 [P] [US4] Create MonthApproved event in backend/src/main/java/com/worklog/domain/approval/events/MonthApproved.java
- [ ] T105 [P] [US4] Create MonthRejected event in backend/src/main/java/com/worklog/domain/approval/events/MonthRejected.java

### Backend - Approval Services (US4)

- [ ] T106 [US4] Create SubmitMonthForApprovalCommand in backend/src/main/java/com/worklog/application/command/SubmitMonthForApprovalCommand.java
- [ ] T107 [US4] Create ApproveMonthCommand in backend/src/main/java/com/worklog/application/command/ApproveMonthCommand.java
- [ ] T108 [US4] Create RejectMonthCommand in backend/src/main/java/com/worklog/application/command/RejectMonthCommand.java
- [ ] T109 [US4] Implement ApprovalService in backend/src/main/java/com/worklog/application/service/ApprovalService.java (validate manager permission)
- [ ] T110 [US4] Implement JdbcApprovalRepository in backend/src/main/java/com/worklog/infrastructure/repository/JdbcApprovalRepository.java

### Backend - Approval Projections (US4)

- [ ] T111 [P] [US4] Create ApprovalQueueProjection in backend/src/main/java/com/worklog/infrastructure/projection/ApprovalQueueProjection.java (manager's pending approvals)
- [ ] T112 [US4] Update MonthlySummaryProjection to include approval status in backend/src/main/java/com/worklog/infrastructure/projection/MonthlySummaryProjection.java

### Backend - Approval API (US4)

- [ ] T113 [P] [US4] Implement POST /api/v1/worklog/submissions in backend/src/main/java/com/worklog/api/ApprovalController.java
- [ ] T114 [P] [US4] Implement GET /api/v1/worklog/approvals/queue in backend/src/main/java/com/worklog/api/ApprovalController.java (manager's pending approvals)
- [ ] T115 [P] [US4] Implement POST /api/v1/worklog/approvals/{id}/approve in backend/src/main/java/com/worklog/api/ApprovalController.java
- [ ] T116 [P] [US4] Implement POST /api/v1/worklog/approvals/{id}/reject in backend/src/main/java/com/worklog/api/ApprovalController.java

### Frontend - Approval UI (Engineer) (US4)

- [ ] T117 [P] [US4] Define Approval types in frontend/app/types/approval.ts
- [ ] T118 [US4] Create SubmitButton component in frontend/app/components/worklog/SubmitButton.tsx (with confirmation dialog)
- [ ] T119 [US4] Add submit button to /app/worklog/page.tsx with monthly completion check
- [ ] T120 [US4] Implement read-only state for SUBMITTED/APPROVED entries in DailyEntryForm
- [ ] T121 [US4] Add status badge to Calendar component (draft/submitted/approved) in frontend/app/components/worklog/Calendar.tsx

### Frontend - Approval UI (Manager) (US4)

- [ ] T122 [P] [US4] Create /app/worklog/approval/page.tsx (manager approval queue)
- [ ] T123 [P] [US4] Create ApprovalPanel component in frontend/app/components/worklog/ApprovalPanel.tsx (pending submissions list)
- [ ] T124 [US4] Implement approve/reject actions in ApprovalPanel with rejection reason textarea

### Testing (US4)

- [ ] T125 [P] [US4] Write unit tests for MonthlyApproval aggregate in backend/src/test/java/com/worklog/domain/approval/MonthlyApprovalTest.java
- [ ] T126 [P] [US4] Write integration tests for approval workflow in backend/src/test/kotlin/com/worklog/api/ApprovalControllerTest.kt
- [ ] T127 [P] [US4] Write E2E test for approval workflow in frontend/e2e/approval-workflow.spec.ts (submit → approve → verify read-only)
- [ ] T128 [P] [US4] Write E2E test for rejection workflow in frontend/e2e/approval-workflow.spec.ts (submit → reject → verify editable)

**Checkpoint**: Approval workflow complete, entries become read-only after approval (SC-009 verification)

---

## Phase 7: User Story 5 - Bulk Data Import/Export (Priority: P2)

**Goal**: Engineers can efficiently import large time datasets from CSV and export data for external analysis

**Independent Test**: An engineer downloads a CSV template, fills in 20 days of time entries in Excel, uploads the file, and sees all 20 entries appear in their calendar. Later, they export a month's data to CSV and verify all entries are present with correct values.

### Backend - CSV Domain (US5)

- [ ] T129 [P] [US5] Create CSV template definition in backend/src/main/resources/csv-templates/worklog-template.csv
- [ ] T130 [P] [US5] Create CsvValidationService in backend/src/main/java/com/worklog/infrastructure/csv/CsvValidationService.java (row validation)
- [ ] T131 [US5] Implement StreamingCsvProcessor in backend/src/main/java/com/worklog/infrastructure/csv/StreamingCsvProcessor.java (Apache Commons CSV)

### Backend - CSV API (US5)

- [ ] T132 [US5] Implement POST /api/v1/worklog/csv/import in backend/src/main/java/com/worklog/api/CsvImportController.java (streaming upload, max 100K rows UI)
- [ ] T133 [US5] Implement Server-Sent Events for import progress in backend/src/main/java/com/worklog/api/CsvImportController.java (SC-005: real-time progress)
- [ ] T134 [US5] Implement GET /api/v1/worklog/csv/export/{year}/{month} in backend/src/main/java/com/worklog/api/CsvExportController.java (streaming download)
- [ ] T135 [US5] Implement GET /api/v1/worklog/csv/template in backend/src/main/java/com/worklog/api/CsvImportController.java (download template)

### Frontend - CSV UI (US5)

- [ ] T136 [P] [US5] Create /app/worklog/import/page.tsx (CSV import page)
- [ ] T137 [P] [US5] Create CsvUploader component in frontend/app/components/worklog/CsvUploader.tsx (drag-drop + file input)
- [ ] T138 [US5] Implement progress bar with SSE connection in CsvUploader (rows processed, percentage, ETA)
- [ ] T139 [US5] Implement row-level error display in CsvUploader (show specific validation errors)
- [ ] T140 [US5] Add export button to /app/worklog/page.tsx with month selection
- [ ] T141 [US5] Implement CSV download trigger in frontend/app/services/csvService.ts

### Testing (US5)

- [ ] T142 [P] [US5] Write unit tests for CsvValidationService in backend/src/test/java/com/worklog/infrastructure/csv/CsvValidationServiceTest.java
- [ ] T143 [P] [US5] Write integration test for CSV import (100 rows) in backend/src/test/kotlin/com/worklog/api/CsvImportControllerTest.kt
- [ ] T144 [P] [US5] Write performance test for CSV import (100K rows in <1000s, SC-005) in backend/src/test/kotlin/com/worklog/api/CsvImportControllerTest.kt
- [ ] T145 [P] [US5] Write E2E test for CSV import/export roundtrip in frontend/e2e/csv-operations.spec.ts

**Checkpoint**: CSV operations complete with progress feedback and validation

---

## Phase 8: User Story 6 - Copy Previous Month's Projects (Priority: P2)

**Goal**: Engineers can quickly populate a new month by copying their previous month's project list

**Independent Test**: An engineer who worked on Projects A, B, and C in January can click "Copy from Previous Month" in February, see Projects A, B, and C appear as selectable options (with zero hours), and then fill in February's actual hours for each project.

### Backend - Copy Service (US6)

- [ ] T146 [US6] Create CopyFromPreviousMonthCommand in backend/src/main/java/com/worklog/application/command/CopyFromPreviousMonthCommand.java
- [ ] T147 [US6] Implement project list extraction logic in WorkLogEntryService in backend/src/main/java/com/worklog/application/service/WorkLogEntryService.java

### Backend - Copy API (US6)

- [ ] T148 [US6] Implement POST /api/v1/worklog/copy-previous-month in backend/src/main/java/com/worklog/api/WorkLogController.java (returns unique project list)

### Frontend - Copy UI (US6)

- [ ] T149 [US6] Add "Copy from Previous Month" button to /app/worklog/page.tsx
- [ ] T150 [US6] Implement confirmation dialog with project preview in frontend/app/components/worklog/CopyPreviousMonthDialog.tsx
- [ ] T151 [US6] Update worklogStore to handle copied projects in frontend/app/services/worklogStore.ts

### Testing (US6)

- [ ] T152 [P] [US6] Write integration test for copy previous month in backend/src/test/kotlin/com/worklog/api/WorkLogControllerTest.kt
- [ ] T153 [P] [US6] Write E2E test for copy previous month in frontend/e2e/copy-previous-month.spec.ts (verify projects copied, hours are zero)

**Checkpoint**: Copy previous month feature complete (SC-001: 15 minutes to complete month)

---

## Phase 9: User Story 7 - Manager Proxy Entry (Priority: P2)

**Goal**: Managers can enter time on behalf of their direct reports with proper audit trail

**Independent Test**: A manager can select one of their direct reports from a dropdown list, switch to "Proxy Entry Mode", enter time entries as if they were that engineer, save the entries, and the system records who actually entered the data (the manager) while attributing the work hours to the engineer.

### Backend - Proxy Domain (US7)

- [ ] T154 [US7] Add proxy permission validation to WorkLogEntryService in backend/src/main/java/com/worklog/application/service/WorkLogEntryService.java (check managerId relationship)
- [ ] T155 [US7] Implement GET /api/v1/members/{id}/subordinates in backend/src/main/java/com/worklog/api/MemberController.java (recursive subordinate query)

### Frontend - Proxy UI (US7)

- [ ] T156 [P] [US7] Create /app/worklog/proxy/page.tsx (proxy entry mode selection)
- [ ] T157 [P] [US7] Create MemberSelector component in frontend/app/components/worklog/MemberSelector.tsx (subordinates dropdown)
- [ ] T158 [US7] Add proxy mode toggle to /app/worklog/page.tsx header
- [ ] T159 [US7] Update DailyEntryForm to show "Entering as [Member Name]" banner in proxy mode
- [ ] T160 [US7] Update Calendar to show proxy entry indicator icon for entries with enteredBy ≠ memberId

### Testing (US7)

- [ ] T161 [P] [US7] Write integration test for proxy entry authorization in backend/src/test/kotlin/com/worklog/api/WorkLogControllerTest.kt (verify manager can enter for subordinate, non-manager cannot)
- [ ] T162 [P] [US7] Write E2E test for proxy entry workflow in frontend/e2e/proxy-entry.spec.ts (manager enters for subordinate, verify audit trail in SC-010)

**Checkpoint**: All 7 user stories independently functional

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

### UI/UX Refinements

- [ ] T163 [P] Configure responsive breakpoints (375/768/1024px) in frontend/tailwind.config.js per FR-023
- [ ] T164 [P] Add touch target sizing (≥44px) for mobile in frontend/app/globals.css
- [ ] T165 [P] Implement custom color scheme (weekend blue, holiday orange) in frontend/tailwind.config.js per research.md
- [ ] T166 [P] Create LoadingSpinner component in frontend/app/components/shared/LoadingSpinner.tsx
- [ ] T167 [P] Create ErrorBoundary component in frontend/app/components/shared/ErrorBoundary.tsx
- [ ] T168 [P] Add ARIA labels to all interactive elements for accessibility (FR-023, T101-T105)

### Performance Optimization

- [ ] T169 [P] Add database indices for common queries in V7__performance_indices.sql (member_id + date range)
- [ ] T170 [P] Implement projection caching with Redis in backend/src/main/java/com/worklog/infrastructure/projection/
- [ ] T171 [P] Optimize calendar query to use projection instead of event replay in MonthlyCalendarProjection
- [ ] T172 Run performance benchmarks and verify SC-006 (1s calendar load), SC-007 (100 concurrent users), SC-008 (2min mobile entry)

### Security Hardening

- [ ] T173 [P] Add rate limiting to API endpoints in backend/src/main/kotlin/com/worklog/infrastructure/config/SecurityConfig.kt
- [ ] T174 [P] Implement CSRF protection for state-changing operations in SecurityConfig.kt
- [ ] T175 [P] Add request/response logging with sensitive data masking in backend/src/main/kotlin/com/worklog/infrastructure/config/LoggingConfig.kt
- [ ] T176 Verify TLS/HTTPS configuration for production in infra/docker/docker-compose.prod.yml (FR-032)

### Testing & Quality

- [ ] T177 Run all E2E tests and verify acceptance scenarios from spec.md (35 scenarios across 7 stories)
- [ ] T178 Run accessibility tests with axe-core in frontend/e2e/accessibility.spec.ts (WCAG 2.1 AA compliance)
- [ ] T179 Run browser compatibility tests (Chrome, Firefox, Safari, Edge latest 2 versions)
- [ ] T180 Verify code coverage targets (backend ≥85%, frontend ≥80%)
- [ ] T181 Run security scan with OWASP dependency check

### Documentation

- [ ] T182 [P] Update quickstart.md with SSO mock configuration (4 test users per quickstart.md)
- [ ] T183 [P] Create API documentation with OpenAPI spec in backend/src/main/resources/static/api-docs.html
- [ ] T184 [P] Create user manual for engineers in docs/user-manual.md
- [ ] T185 [P] Create manager guide for approval workflow in docs/manager-guide.md
- [ ] T186 [P] Update AGENTS.md with Phase 1 technology decisions per plan.md

### Deployment

- [ ] T187 Create production Docker Compose configuration in infra/docker/docker-compose.prod.yml
- [ ] T188 Add health check endpoints verification in deployment script
- [ ] T189 Create database backup strategy documentation in docs/backup-strategy.md
- [ ] T190 Run quickstart.md validation (verify 5-minute setup works)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup - BLOCKS all user stories
- **User Stories (Phase 3-9)**: All depend on Foundational phase completion
  - US1 (Daily Time Entry): No dependencies on other stories
  - US2 (Multi-Project): Depends on US1 (extends time entry)
  - US3 (Absence): Independent of US1/US2, but shares calendar view
  - US4 (Approval): Depends on US1 (requires entries to approve)
  - US5 (CSV Import/Export): Depends on US1 (imports work log entries)
  - US6 (Copy Previous Month): Depends on US1 (copies work log entries)
  - US7 (Proxy Entry): Depends on US1 (proxy creates work log entries)
- **Polish (Phase 10)**: Depends on desired user stories being complete

### Recommended Implementation Order

1. **Phase 1 (Setup)** → **Phase 2 (Foundational)** - Sequential, MUST complete
2. **Phase 3 (US1: Daily Time Entry)** - 🎯 MVP, complete before moving forward
3. **Phase 4 (US2: Multi-Project)** - Natural extension of US1
4. **Phase 5 (US3: Absence)** - Can be parallel with US2 if staffed
5. **Phase 6 (US4: Approval)** - Required for production deployment
6. **Phase 7 (US5: CSV Import/Export)** - Can be deferred if not critical
7. **Phase 8 (US6: Copy Previous)** - Can be deferred if not critical
8. **Phase 9 (US7: Proxy Entry)** - Can be deferred if not critical
9. **Phase 10 (Polish)** - After all desired stories complete

### Parallel Opportunities

**Within Foundational Phase (After T008 complete)**:
- T009-T013 (value objects + entity extensions) can run in parallel
- T014-T016 (security configs) can run in parallel
- T017-T018 (frontend setup) can run in parallel

**Within User Story 1 (After domain model complete)**:
- T023-T026 (events) can run in parallel
- T032-T036 (API endpoints) can run in parallel after T030
- T042-T044 (frontend components) can run in parallel
- T050-T051 (auto-save services) can run in parallel
- T055-T056 (session timeout) can run in parallel
- T058-T062 (tests) can run in parallel

**Across User Stories (If team capacity allows)**:
- US3 (Absence) can start in parallel with US2 (Multi-Project) after US1 completes
- US5-US7 can be worked on by different team members once US1-US4 are stable

---

## Implementation Strategy

### MVP First (Recommended)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL)
3. Complete Phase 3: User Story 1 (Daily Time Entry)
4. **STOP and VALIDATE**: Test US1 independently per acceptance scenario
5. Deploy MVP for early user feedback

**MVP Scope**: US1 only provides core value (daily time entry with auto-save and calendar view)

### Incremental Delivery (Recommended for Team)

1. Foundation Ready: Phase 1 + Phase 2 → Deploy infrastructure
2. MVP: Phase 3 (US1) → Test independently → Deploy/Demo ✅
3. Production Ready: Phase 4 (US2) + Phase 5 (US3) + Phase 6 (US4) → Deploy/Demo ✅
4. Enhanced: Phase 7 (US5) CSV operations → Deploy/Demo
5. Convenience: Phase 8 (US6) + Phase 9 (US7) → Deploy/Demo
6. Production Hardened: Phase 10 (Polish) → Final deployment

### Parallel Team Strategy

With 3 developers after Foundational phase:
- Developer A: US1 (Days 1-5) → US4 (Days 6-8) → US7 (Days 9-10)
- Developer B: US2 (Days 1-2) → US3 (Days 3-5) → US5 (Days 6-8)
- Developer C: Frontend infrastructure (Days 1-5) → US6 (Days 6-7) → Polish (Days 8-10)

---

## Estimated Timeline

- **Phase 1 (Setup)**: 1 day
- **Phase 2 (Foundational)**: 2-3 days
- **Phase 3 (US1 MVP)**: 5-6 days
- **Phase 4 (US2)**: 2-3 days
- **Phase 5 (US3)**: 3-4 days
- **Phase 6 (US4)**: 4-5 days
- **Phase 7 (US5)**: 3-4 days
- **Phase 8 (US6)**: 2 days
- **Phase 9 (US7)**: 2-3 days
- **Phase 10 (Polish)**: 3-4 days

**Total Estimate**: 27-35 business days (single developer, sequential)  
**Parallel Estimate**: 15-20 business days (3 developers, optimal parallelization)

---

## Success Criteria Mapping

| Success Criterion | Task Verification |
|-------------------|-------------------|
| SC-001: 15min/month entry | T153 (copy previous month E2E test) |
| SC-002: 40% accuracy improvement | T177 (E2E tests for all acceptance scenarios) |
| SC-003: 95% on-time submissions | T127 (approval workflow E2E) |
| SC-004: 10min approval for 10 members | T126 (approval integration tests) |
| SC-005: 100K rows @ 100 rows/s | T144 (CSV performance test) |
| SC-006: 1s calendar load | T172 (performance benchmarks) |
| SC-007: 100 concurrent users | T172 (performance benchmarks) |
| SC-008: 2min mobile entry | T172 (mobile performance test) |
| SC-009: Zero accidental approved edits | T127 (approval E2E test) |
| SC-010: 100% proxy audit trail | T162 (proxy entry E2E test) |
| SC-011: 99.9% auto-save reliability | T064 (auto-save E2E test) |

---

## Notes

- All tasks include file paths for immediate execution
- [P] tasks can run in parallel (different files, no dependencies)
- [Story] labels enable independent story tracking and testing
- Each story has independent test criteria (can demo individually)
- Constitution compliance verified: Code quality ✅, Testing discipline ✅, Consistent UX ✅, Performance ✅
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently

---

**Generated**: 2026-01-03  
**Total Tasks**: 190  
**Parallel Opportunities**: 68 tasks marked [P]  
**MVP Scope**: Phase 1 + Phase 2 + Phase 3 (T001-T064, 64 tasks, ~8-10 days)
