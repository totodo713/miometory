# Tasks: Password Reset Integration & E2E Tests

**Input**: Design documents from `/specs/007-password-reset-tests/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/test-contracts.md

**Tests**: This feature IS test implementation — all tasks produce test code.

**Organization**: Tasks grouped by user story. Each story can be implemented and verified independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Backend tests**: `backend/src/test/kotlin/com/worklog/`
- **Reference pattern (repository)**: `backend/src/test/kotlin/com/worklog/infrastructure/persistence/JdbcUserRepositoryTest.kt`
- **Reference pattern (E2E base)**: `backend/src/test/kotlin/com/worklog/IntegrationTestBase.kt`
- **Reference pattern (controller)**: `backend/src/test/kotlin/com/worklog/api/AuthControllerTest.kt`

---

## Phase 1: Verification

**Purpose**: Confirm existing infrastructure is ready before writing tests

- [X] T001 Verify backend builds and existing tests pass by running `cd backend && ./gradlew test` — all existing password reset tests (PasswordResetServiceTest, AuthControllerTest, PasswordResetRateLimitIT, EmailServiceTest) must be green

**Checkpoint**: Existing test suite is healthy, ready to add new tests

---

## Phase 2: User Story 1 - Repository Integration Tests (Priority: P1) 🎯 MVP

**Goal**: Verify all PasswordResetTokenRepository methods work correctly against a real PostgreSQL database via Testcontainers

**Independent Test**: `./gradlew test --tests "com.worklog.infrastructure.persistence.PasswordResetTokenRepositoryTest"`

### Implementation for User Story 1

- [X] T002 [US1] Create test class scaffolding with Testcontainers setup in `backend/src/test/kotlin/com/worklog/infrastructure/persistence/PasswordResetTokenRepositoryTest.kt` — follow JdbcUserRepositoryTest pattern: `@SpringBootTest`, `@Testcontainers`, `@Transactional`, own `PostgreSQLContainer("postgres:16-alpine")`, `@DynamicPropertySource` for datasource, `@BeforeEach` to clean `password_reset_tokens` + `users` tables and ensure test role exists, helper methods `createTestUser(email)` and `createTestToken(userId, tokenString)` (token string ≥32 chars)
- [X] T003 [US1] Implement save + findByToken test (TC-1.1) and findValidByToken happy-path test (TC-1.2) in `backend/src/test/kotlin/com/worklog/infrastructure/persistence/PasswordResetTokenRepositoryTest.kt` — save a token via repository, retrieve by token string, assert all fields match; save an unused non-expired token, call findValidByToken, assert token returned
- [X] T004 [US1] Implement findValidByToken negative tests (TC-1.3, TC-1.4) in `backend/src/test/kotlin/com/worklog/infrastructure/persistence/PasswordResetTokenRepositoryTest.kt` — TC-1.3: save token, mark as used via repository, verify findValidByToken returns empty; TC-1.4: save token, UPDATE expires_at to past via JdbcTemplate, verify findValidByToken returns empty
- [X] T005 [US1] Implement markAsUsed test (TC-1.5) in `backend/src/test/kotlin/com/worklog/infrastructure/persistence/PasswordResetTokenRepositoryTest.kt` — save token, call markAsUsed, verify findByToken shows used_at is set and findValidByToken returns empty
- [X] T006 [US1] Implement invalidateUnusedTokensForUser tests (TC-1.6, TC-1.7) in `backend/src/test/kotlin/com/worklog/infrastructure/persistence/PasswordResetTokenRepositoryTest.kt` — TC-1.6: create user with 2 unused tokens, call invalidateUnusedTokensForUser, verify both have used_at set; TC-1.7: create user with 1 used + 1 unused token, call invalidateUnusedTokensForUser, verify only unused token gets used_at
- [X] T007 [US1] Implement deleteExpired test (TC-1.8) in `backend/src/test/kotlin/com/worklog/infrastructure/persistence/PasswordResetTokenRepositoryTest.kt` — create mix of expired (UPDATE expires_at to past via JdbcTemplate) and valid tokens, call deleteExpired, verify only expired tokens removed
- [X] T008 [US1] Implement constraint and cascade tests (TC-1.9, TC-1.10) in `backend/src/test/kotlin/com/worklog/infrastructure/persistence/PasswordResetTokenRepositoryTest.kt` — TC-1.9: save two tokens with same token string, expect DataIntegrityViolationException; TC-1.10: create user with token, delete user via JdbcTemplate `DELETE FROM users WHERE id = ?`, verify tokens also deleted (CASCADE)
- [X] T009 [US1] Run repository tests and verify all 10 pass: `./gradlew test --tests "com.worklog.infrastructure.persistence.PasswordResetTokenRepositoryTest"`

**Checkpoint**: All 10 repository integration tests pass — repository layer fully verified against real PostgreSQL

---

## Phase 3: User Story 2 - Controller Validation & CSRF Tests (Priority: P2)

**Goal**: Verify input validation rejects malformed requests and CSRF protection works on password reset endpoints

**Independent Test**: `./gradlew test --tests "com.worklog.api.AuthControllerTest" --tests "com.worklog.api.PasswordResetCsrfTest"`

### Implementation for User Story 2

- [X] T010 [P] [US2] Add 4 validation test methods to existing `backend/src/test/kotlin/com/worklog/api/AuthControllerTest.kt` — add new section "// Password Reset Validation Tests" after existing password reset tests: TC-2.1: POST /api/v1/auth/password-reset/request with `{"email":""}` → expect 400; TC-2.2: POST /request with `{"email":"not-email"}` → expect 400; TC-2.3: POST /api/v1/auth/password-reset/confirm with `{"token":"","newPassword":"ValidPW123"}` → expect 400; TC-2.4: POST /confirm with `{"token":"valid-token","newPassword":"short"}` → expect 400. Use `.with(csrf())` on all requests. Verify service mocks are NOT called (validation rejects before reaching service)
- [X] T011 [P] [US2] Create CSRF protection test class in `backend/src/test/kotlin/com/worklog/api/PasswordResetCsrfTest.kt` — `@WebMvcTest(AuthController::class)` WITHOUT excluding SecurityAutoConfiguration; `@TestConfiguration` that provides: (1) SecurityFilterChain bean with CSRF enabled via CookieCsrfTokenRepository, (2) mock PasswordResetService bean (relaxed), (3) mock AuthService bean (relaxed), (4) LoggingProperties and RateLimitProperties beans (disabled). Implement 4 tests: TC-3.1: POST /request without csrf() → 403; TC-3.2: POST /request with csrf() → 200; TC-3.3: POST /confirm without csrf() → 403; TC-3.4: POST /confirm with csrf() → 200
- [X] T012 [US2] Run controller and CSRF tests and verify all pass: `./gradlew test --tests "com.worklog.api.AuthControllerTest" --tests "com.worklog.api.PasswordResetCsrfTest"`

**Checkpoint**: All validation (4 tests) and CSRF (4 tests) pass — controller layer security fully verified

---

## Phase 4: User Story 3 - End-to-End Password Reset Flow Test (Priority: P2)

**Goal**: Verify the complete password reset lifecycle works end-to-end: request → token → reset → login

**Independent Test**: `./gradlew test --tests "com.worklog.e2e.PasswordResetE2ETest"`

### Implementation for User Story 3

- [X] T013 [US3] Create E2E test class scaffolding in `backend/src/test/kotlin/com/worklog/e2e/PasswordResetE2ETest.kt` — extends `IntegrationTestBase()`, `@AutoConfigureWebTestClient`, `@Autowired WebTestClient`, `@Autowired JdbcTemplate`. `@BeforeEach`: clean password_reset_tokens, users tables, ensure test role exists. Helper methods: `createAndActivateUser(email, password)` — insert user with BCrypt-hashed password and ACTIVE status directly via JdbcTemplate (preferred over endpoint calls for speed and test isolation); `extractTokenFromDb(userId)` — query `SELECT token FROM password_reset_tokens WHERE user_id = ? AND used_at IS NULL ORDER BY created_at DESC LIMIT 1`
- [X] T014 [US3] Implement complete password reset flow test (TC-4.1) in `backend/src/test/kotlin/com/worklog/e2e/PasswordResetE2ETest.kt` — create user → POST /api/v1/auth/password-reset/request with user's email → extract token from DB → POST /api/v1/auth/password-reset/confirm with token + new password → POST /api/v1/auth/login with new password → expect 200; POST /api/v1/auth/login with old password → expect 401
- [X] T015 [US3] Implement expired token rejection test (TC-4.2) in `backend/src/test/kotlin/com/worklog/e2e/PasswordResetE2ETest.kt` — create user → POST /request → UPDATE expires_at to past via JdbcTemplate `UPDATE password_reset_tokens SET expires_at = NOW() - INTERVAL '1 hour' WHERE user_id = ?` → POST /confirm with extracted token → expect 404 with "Invalid or expired token"
- [X] T016 [US3] Implement used token rejection test (TC-4.3) in `backend/src/test/kotlin/com/worklog/e2e/PasswordResetE2ETest.kt` — create user → POST /request → extract token → POST /confirm (succeeds, 200) → POST /confirm again with same token → expect 404
- [X] T017 [US3] Implement session invalidation test (TC-4.4) in `backend/src/test/kotlin/com/worklog/e2e/PasswordResetE2ETest.kt` — create user → POST /login (capture session cookie from response) → POST /request → extract token → POST /confirm → verify old session is invalidated by checking user_sessions table is empty for that user via JdbcTemplate `SELECT COUNT(*) FROM user_sessions WHERE user_id = ?` → expect 0
- [X] T018 [US3] Run E2E tests and verify all 4 pass: `./gradlew test --tests "com.worklog.e2e.PasswordResetE2ETest"`

**Checkpoint**: All 4 E2E tests pass — complete password reset lifecycle verified end-to-end

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final validation across all test classes

- [X] T019 Run format check and fix any violations: `cd backend && ./gradlew formatAll && ./gradlew checkFormat`
- [X] T020 Run full test suite to verify no regressions: `cd backend && ./gradlew test`
- [X] T021 Verify password reset test coverage meets 85% threshold by running `./gradlew test jacocoTestReport` and checking coverage for `com.worklog.domain.password`, `com.worklog.infrastructure.persistence.PasswordResetTokenRepository`, and `com.worklog.application.password.PasswordResetService` packages

---

## Dependencies & Execution Order

### Phase Dependencies

- **Verification (Phase 1)**: No dependencies — start immediately
- **User Story 1 (Phase 2)**: Depends on Phase 1 — standalone PostgreSQL container, no cross-story dependencies
- **User Story 2 (Phase 3)**: Depends on Phase 1 — no dependency on US1 (different test layer)
- **User Story 3 (Phase 4)**: Depends on Phase 1 — uses IntegrationTestBase (different container setup than US1)
- **Polish (Phase 5)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Independent — repository-level tests, own Testcontainers setup
- **User Story 2 (P2)**: Independent — controller-level tests, @WebMvcTest with mocks
- **User Story 3 (P2)**: Independent — E2E tests, IntegrationTestBase with full stack

**All three user stories can be implemented in parallel** after Phase 1 verification passes.

### Within Each User Story

- Class scaffolding and setup MUST be done first
- Test methods can be implemented sequentially within the same file
- Verification run at the end confirms all tests pass

### Parallel Opportunities

- **T010 and T011** are parallelizable [P] — different files (AuthControllerTest.kt vs PasswordResetCsrfTest.kt)
- **US1, US2, US3** are fully independent — can be assigned to different developers or executed in any order
- Within US1: T003–T008 are sequential (same file, building on shared setup)

---

## Parallel Example: All User Stories Simultaneously

```bash
# After Phase 1 verification passes, launch all stories in parallel:

# Developer A / Agent 1: US1 - Repository Tests
Task: T002 → T003 → T004 → T005 → T006 → T007 → T008 → T009

# Developer B / Agent 2: US2 - Controller + CSRF Tests (T010 and T011 in parallel)
Task: T010 + T011 (parallel) → T012

# Developer C / Agent 3: US3 - E2E Tests
Task: T013 → T014 → T015 → T016 → T017 → T018
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Verification
2. Complete Phase 2: Repository Integration Tests (US1)
3. **STOP and VALIDATE**: `./gradlew test --tests "com.worklog.infrastructure.persistence.PasswordResetTokenRepositoryTest"` — all 10 tests green
4. This alone delivers significant value: verifies data layer correctness against real database

### Incremental Delivery

1. US1 complete → Repository layer verified (10 tests) → Commit
2. US2 complete → Controller validation + CSRF verified (8 tests) → Commit
3. US3 complete → E2E lifecycle verified (4 tests) → Commit
4. Polish → Format, full regression, coverage check → Final commit
5. Each increment adds test coverage without breaking previous tests

### Single Developer Strategy

Execute phases sequentially in priority order:
1. Phase 1 → Phase 2 (US1) → Phase 3 (US2) → Phase 4 (US3) → Phase 5

---

## Notes

- All new test files use Kotlin (matching existing test codebase convention)
- Token strings in tests MUST be ≥32 characters (PasswordResetToken constructor validation)
- For expired token testing, manipulate `expires_at` via JdbcTemplate SQL UPDATE (same pattern as JdbcUserRepositoryTest)
- CSRF test requires custom SecurityFilterChain because test/dev profiles disable CSRF (see research.md R2)
- E2E tests use IntegrationTestBase which provides PostgreSQL + Redis + MailHog containers with reuse=true
- Repository tests use standalone PostgreSQLContainer to avoid unnecessary Redis/MailHog overhead
- Commit after each user story checkpoint for clean git history
