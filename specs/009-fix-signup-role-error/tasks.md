# Tasks: Fix Signup API Role Instantiation Error

**Input**: Design documents from `/specs/009-fix-signup-role-error/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, quickstart.md

**Tests**: Included — Constitution Principle II requires automated tests for every bug fix.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Foundational (The Fix)

**Purpose**: Apply the core bug fix that all user stories depend on

**⚠️ CRITICAL**: No test or verification work can proceed until this phase is complete

- [x] T001 Add `@PersistenceCreator` annotation to the 5-arg rehydration constructor and add the `org.springframework.data.annotation.PersistenceCreator` import in `backend/src/main/java/com/worklog/domain/role/Role.java`
- [x] T002 Add `@ExceptionHandler(IllegalStateException.class)` method to `backend/src/main/java/com/worklog/api/GlobalExceptionHandler.java` that returns HTTP 503 Service Unavailable with error code `SERVICE_CONFIGURATION_ERROR` and the exception message, following the existing handler pattern (ErrorResponse, logging, request path)

**Checkpoint**: Role entity can be instantiated by Spring Data JDBC; missing role returns 503 instead of 500

---

## Phase 2: User Story 1 - New User Successfully Signs Up (Priority: P1) 🎯 MVP

**Goal**: Verify the signup endpoint works end-to-end — user account is created, default role is assigned, and verification email is sent.

**Independent Test**: POST to `/api/v1/auth/signup` with valid credentials returns a success response with user data.

### Tests for User Story 1

- [x] T003 [US1] Create integration test class extending `IntegrationTestBase` in `backend/src/test/kotlin/com/worklog/api/AuthControllerSignupTest.kt` with test method verifying successful signup: POST `/api/v1/auth/signup` with `{"email":"newuser@example.com","name":"New User","password":"Password1"}` returns HTTP 200 with response body containing user id, email, and name, and verify that a verification email was dispatched via MailHog
- [x] T004 [US1] Add test method in `backend/src/test/kotlin/com/worklog/api/AuthControllerSignupTest.kt` verifying that the created user has the default "USER" role assigned by querying the database after signup

**Checkpoint**: User Story 1 is verified — signup endpoint returns success instead of 500 error

---

## Phase 3: User Story 2 - Signup Validation Still Works (Priority: P2)

**Goal**: Confirm existing validation rules (duplicate email, password strength) are not broken by the fix.

**Independent Test**: Submit invalid registration data and verify appropriate error responses.

### Tests for User Story 2

- [x] T005 [P] [US2] Add test method in `backend/src/test/kotlin/com/worklog/api/AuthControllerSignupTest.kt` verifying duplicate email signup returns HTTP 400 error with descriptive message
- [x] T006 [P] [US2] Add test method in `backend/src/test/kotlin/com/worklog/api/AuthControllerSignupTest.kt` verifying weak password signup returns HTTP 400 error with validation message
- [x] T007 [P] [US2] Add test method in `backend/src/test/kotlin/com/worklog/api/AuthControllerSignupTest.kt` verifying signup when default role is missing from database returns HTTP 503 with error code `SERVICE_CONFIGURATION_ERROR` and descriptive message (not a generic 500)

**Checkpoint**: Validation rules confirmed working — no regressions

---

## Phase 4: User Story 3 - Role Data Integrity Preserved (Priority: P2)

**Goal**: Verify Role entity loaded from the database has all fields correctly populated with domain invariants enforced.

**Independent Test**: Load a Role via repository and assert all fields match stored values.

### Tests for User Story 3

- [x] T008 [US3] Add integration test in `backend/src/test/kotlin/com/worklog/infrastructure/persistence/RoleRepositoryTest.kt` verifying `RoleRepository.findByName("USER")` returns a Role with all fields populated (id, name, description, createdAt, updatedAt) and name is uppercase

**Checkpoint**: Role data integrity verified — persistence layer correctly hydrates all fields

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Code quality and final verification

- [x] T009 Run code formatter: `cd backend && ./gradlew formatAll`
- [x] T010 Run full test suite to verify no regressions: `cd backend && ./gradlew test`
- [x] T011 Run format check: `cd backend && ./gradlew checkFormat`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — start immediately (T001 and T002 can run in parallel — different files)
- **User Story 1 (Phase 2)**: Depends on Phase 1 completion (T001, T002)
- **User Story 2 (Phase 3)**: Depends on Phase 1 completion (T001, T002), can run in parallel with Phase 2
- **User Story 3 (Phase 4)**: Depends on Phase 1 completion (T001), can run in parallel with Phase 2 and 3
- **Polish (Phase 5)**: Depends on all previous phases

### User Story Dependencies

- **User Story 1 (P1)**: Depends on T001 and T002 — no cross-story dependencies
- **User Story 2 (P2)**: Depends on T001 and T002 — independent of US1 tests; T007 tests the error handler from T002
- **User Story 3 (P2)**: Depends on T001 only — independent of US1/US2 tests

### Within Each User Story

- All test tasks within a story can run in parallel where marked [P]
- T005, T006, T007 (US2) can be written in parallel since they test different validation rules in different test methods

### Parallel Opportunities

```text
Phase 1 (both can run in parallel — different files):
  T001 (Role.java fix) + T002 (GlobalExceptionHandler.java)

After Phase 1, all three user story phases can start in parallel:
    ├── T003, T004 (US1 - signup success)
    ├── T005, T006, T007 (US2 - validation + missing role, parallelizable)
    └── T008 (US3 - role integrity)

After all stories complete:
    └── T009, T010, T011 (polish, sequential)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Apply fixes (T001 + T002)
2. Complete Phase 2: Verify signup works (T003, T004)
3. **STOP and VALIDATE**: Signup endpoint returns success
4. This alone resolves Issue #17

### Full Delivery

1. T001 + T002 → Fixes applied (parallel)
2. T003–T004 → Signup verified (MVP complete)
3. T005–T008 → Regressions and integrity verified (parallel)
4. T009–T011 → Code quality and full test suite pass

---

## Notes

- [P] tasks = different files or different test methods with no dependencies
- Integration tests use `IntegrationTestBase` which provides PostgreSQL + Redis + MailHog via Testcontainers
- Test files are in Kotlin (matching existing test pattern in `backend/src/test/kotlin/`)
- The core fix (T001) is a single annotation addition — minimal blast radius
- T002 adds one exception handler method following existing patterns in GlobalExceptionHandler
- Commit after T001 + T002 + T003 for a minimal verifiable fix, then add remaining tests
