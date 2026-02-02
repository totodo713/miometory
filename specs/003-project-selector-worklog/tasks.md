# Tasks: 工数入力画面のプロジェクト選択機能

**Input**: Design documents from `/specs/003-project-selector-worklog/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Tests are included as this feature involves new API endpoints and UI components requiring validation.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Web app**: `backend/src/`, `frontend/app/`
- Backend: Kotlin/Java with Spring Boot
- Frontend: TypeScript with Next.js/React

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Database schema and foundational backend infrastructure

- [ ] T001 Create member_project_assignments table migration in backend/src/main/resources/db/migration/V10__member_project_assignments.sql
- [ ] T002 [P] Create MemberProjectAssignmentId value object in backend/src/main/java/com/worklog/domain/project/MemberProjectAssignmentId.java
- [ ] T003 [P] Create MemberProjectAssignment entity in backend/src/main/java/com/worklog/domain/project/MemberProjectAssignment.java

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T004 Create AssignedProjectInfo projection class in backend/src/main/java/com/worklog/infrastructure/projection/AssignedProjectInfo.java
- [ ] T005 Create JdbcMemberProjectAssignmentRepository in backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberProjectAssignmentRepository.java
- [ ] T006 [P] Create AssignedProject DTO in backend/src/main/java/com/worklog/api/dto/AssignedProject.java
- [ ] T007 [P] Create AssignedProjectsResponse DTO in backend/src/main/java/com/worklog/api/dto/AssignedProjectsResponse.java
- [ ] T008 Add getMemberProjects endpoint to MemberController in backend/src/main/java/com/worklog/api/MemberController.java
- [ ] T009 Add AssignedProject type definition in frontend/app/types/worklog.ts
- [ ] T010 Add getAssignedProjects method to api.members in frontend/app/services/api.ts

**Checkpoint**: Foundation ready - Backend API and Frontend API client complete. User story implementation can now begin.

---

## Phase 3: User Story 1 - アサインされたプロジェクト一覧からの選択 (Priority: P1) 🎯 MVP

**Goal**: ユーザーが工数入力画面でプロジェクトを選択ドロップダウンから選べるようにする

**Independent Test**: プロジェクト選択ドロップダウンを開き、プロジェクト名を確認して選択することで、工数入力が正常に保存されることを確認できる

### Tests for User Story 1

- [ ] T011 [P] [US1] Create repository test in backend/src/test/kotlin/com/worklog/infrastructure/repository/JdbcMemberProjectAssignmentRepositoryTest.kt
- [ ] T012 [P] [US1] Create controller test for getMemberProjects in backend/src/test/kotlin/com/worklog/api/MemberControllerProjectsTest.kt

### Implementation for User Story 1

- [ ] T013 [US1] Create ProjectSelector component with basic dropdown in frontend/app/components/worklog/ProjectSelector.tsx
- [ ] T014 [US1] Add loading state and project list display to ProjectSelector in frontend/app/components/worklog/ProjectSelector.tsx
- [ ] T015 [US1] Replace text input with ProjectSelector in DailyEntryForm in frontend/app/components/worklog/DailyEntryForm.tsx
- [ ] T016 [US1] Handle disabled state for existing entries in ProjectSelector in frontend/app/components/worklog/ProjectSelector.tsx
- [ ] T017 [US1] Add keyboard navigation (Enter, Escape, Arrow keys) to ProjectSelector in frontend/app/components/worklog/ProjectSelector.tsx

**Checkpoint**: User Story 1 complete. Users can select projects from a dropdown list showing name and code.

---

## Phase 4: User Story 2 - プロジェクト名での検索・フィルタリング (Priority: P2)

**Goal**: 多数のプロジェクトがある場合でもテキスト入力でフィルタリングして素早く見つけられる

**Independent Test**: プロジェクト選択フィールドにテキストを入力し、一致するプロジェクトのみがフィルタリングされて表示されることを確認できる

### Tests for User Story 2

- [ ] T018 [P] [US2] Create ProjectSelector filter test in frontend/tests/unit/components/ProjectSelector.filter.test.tsx

### Implementation for User Story 2

- [ ] T019 [US2] Add search input state and filtering logic to ProjectSelector in frontend/app/components/worklog/ProjectSelector.tsx
- [ ] T020 [US2] Implement filter by project name substring in ProjectSelector in frontend/app/components/worklog/ProjectSelector.tsx
- [ ] T021 [US2] Implement filter by project code substring in ProjectSelector in frontend/app/components/worklog/ProjectSelector.tsx
- [ ] T022 [US2] Add clear search functionality to ProjectSelector in frontend/app/components/worklog/ProjectSelector.tsx

**Checkpoint**: User Story 2 complete. Users can filter projects by typing name or code.

---

## Phase 5: User Story 3 - プロジェクトがない場合の対応 (Priority: P3)

**Goal**: プロジェクトがアサインされていない場合に明確なメッセージを表示する

**Independent Test**: プロジェクトがアサインされていないユーザーでログインし、適切なメッセージが表示されることを確認できる

### Tests for User Story 3

- [ ] T023 [P] [US3] Create ProjectSelector empty state test in frontend/tests/unit/components/ProjectSelector.empty.test.tsx

### Implementation for User Story 3

- [ ] T024 [US3] Add empty state message display to ProjectSelector in frontend/app/components/worklog/ProjectSelector.tsx
- [ ] T025 [US3] Add error state with retry button to ProjectSelector in frontend/app/components/worklog/ProjectSelector.tsx
- [ ] T026 [US3] Add "no active projects" message for all-inactive case in ProjectSelector in frontend/app/components/worklog/ProjectSelector.tsx

**Checkpoint**: User Story 3 complete. Users see clear messages when no projects are available.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final quality improvements and comprehensive testing

- [ ] T027 [P] Create comprehensive ProjectSelector unit tests in frontend/tests/unit/components/ProjectSelector.test.tsx
- [ ] T028 [P] Update DailyEntryForm tests for ProjectSelector integration in frontend/tests/unit/components/DailyEntryForm.test.tsx
- [ ] T029 Create E2E test for project selection flow in frontend/tests/e2e/project-selector.spec.ts
- [ ] T030 Add seed data for member_project_assignments in test fixtures
- [ ] T031 Validate implementation against quickstart.md scenarios

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-5)**: All depend on Foundational phase completion
  - User stories can then proceed sequentially in priority order (P1 → P2 → P3)
  - Note: US2 and US3 enhance the same component created in US1
- **Polish (Phase 6)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - Creates ProjectSelector component
- **User Story 2 (P2)**: Depends on US1 - Adds filtering to existing ProjectSelector
- **User Story 3 (P3)**: Depends on US1 - Adds empty/error states to existing ProjectSelector

### Within Each User Story

- Tests SHOULD be written first (TDD approach)
- Backend components before frontend (API must exist)
- Core functionality before edge cases
- Commit after each logical task group

### Parallel Opportunities

**Phase 1 (Setup)**:
```bash
# These can run in parallel:
Task T002: Create MemberProjectAssignmentId value object
Task T003: Create MemberProjectAssignment entity
```

**Phase 2 (Foundational)**:
```bash
# These can run in parallel:
Task T006: Create AssignedProject DTO
Task T007: Create AssignedProjectsResponse DTO
```

**User Story 1 Tests**:
```bash
# These can run in parallel:
Task T011: Repository test
Task T012: Controller test
```

---

## Parallel Example: Phase 2 Foundational

```bash
# After T004 and T005 complete, launch DTO tasks together:
Task: "Create AssignedProject DTO in backend/src/main/java/com/worklog/api/dto/AssignedProject.java"
Task: "Create AssignedProjectsResponse DTO in backend/src/main/java/com/worklog/api/dto/AssignedProjectsResponse.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T003)
2. Complete Phase 2: Foundational (T004-T010) - CRITICAL
3. Complete Phase 3: User Story 1 (T011-T017)
4. **STOP and VALIDATE**: Test project selection dropdown works
5. Deploy/demo if ready - MVP is complete!

### Incremental Delivery

1. Setup + Foundational → API ready
2. Add User Story 1 → Basic dropdown works → Deploy (MVP!)
3. Add User Story 2 → Search/filter works → Deploy
4. Add User Story 3 → Empty states handled → Deploy
5. Polish → Tests and quality → Final release

### Single Developer Strategy

Execute phases sequentially:
1. Phase 1 → Phase 2 → Phase 3 (MVP)
2. Validate MVP works end-to-end
3. Continue with Phase 4 → Phase 5 → Phase 6

---

## Task Summary

| Phase | Tasks | Priority | Effort |
|-------|-------|----------|--------|
| Phase 1: Setup | T001-T003 | - | S |
| Phase 2: Foundational | T004-T010 | - | M |
| Phase 3: User Story 1 | T011-T017 | P1/MVP | L |
| Phase 4: User Story 2 | T018-T022 | P2 | M |
| Phase 5: User Story 3 | T023-T026 | P3 | S |
| Phase 6: Polish | T027-T031 | - | M |

**Total Tasks**: 31
**Estimated Effort**: 3-4 days

---

## Notes

- [P] tasks = different files, no dependencies within same phase
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable (after its dependencies)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- US2 and US3 modify the same component (ProjectSelector) created in US1, so they must run after US1
