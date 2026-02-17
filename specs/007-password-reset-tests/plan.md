# Implementation Plan: Password Reset Integration & E2E Tests

**Branch**: `007-password-reset-tests` | **Date**: 2026-02-17 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/007-password-reset-tests/spec.md`

## Summary

Implement integration tests and E2E tests for the password reset feature to cover gaps identified in the existing test suite. The backend implementation (PR #7) is complete. Existing tests cover service unit tests, controller happy-path, email delivery, and rate limiting. This feature adds: (1) repository integration tests against a real PostgreSQL database, (2) controller validation tests for input rejection, (3) CSRF protection tests, and (4) E2E tests for the complete reset lifecycle.

## Technical Context

**Language/Version**: Kotlin 2.3.0 + Java 21 (Spring Boot 3.5.9)
**Primary Dependencies**: Spring Boot Test, Testcontainers 1.21.1, spring-security-test, MockK, JUnit 5
**Storage**: PostgreSQL 16 (via Testcontainers), Flyway migrations (V11__user_auth.sql)
**Testing**: JUnit 5 + Testcontainers + Spring Boot Test + MockK + WebTestClient
**Target Platform**: JVM (backend test suite)
**Project Type**: Web application (backend only for this feature)
**Performance Goals**: N/A (test code only)
**Constraints**: All tests must be CI-executable, deterministic, and isolated
**Scale/Scope**: 4 test classes (1 new repo IT, 1 new CSRF, 1 new E2E, 1 existing controller extension), ~18 test methods total

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Code Quality | PASS | Tests follow established patterns (JdbcUserRepositoryTest, IntegrationTestBase). Inline documentation for test purpose and setup. |
| II. Testing Discipline | PASS | This feature IS the testing implementation. Adds integration + E2E tests per Test Pyramid (many repository unit → some controller integration → few E2E). All tests are deterministic and isolated. |
| III. Consistent UX | N/A | No user-facing changes. |
| IV. Performance Requirements | PASS | Repository tests use @Transactional for rollback (no container restart overhead). E2E tests reuse containers via IntegrationTestBase. |
| Development Workflow | PASS | Maps to Issue #5. All changes are test code only. |
| Additional Constraints | PASS | All dependencies already in build.gradle.kts (Testcontainers, spring-security-test, MockK). No new dependencies needed. |

**Post-Phase 1 Re-check**: All gates still pass. No new dependencies or architectural patterns introduced.

## Project Structure

### Documentation (this feature)

```text
specs/007-password-reset-tests/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0: Technical decisions
├── data-model.md        # Phase 1: Schema & method reference
├── quickstart.md        # Phase 1: Dev setup guide
├── contracts/
│   └── test-contracts.md # Phase 1: Test behavior contracts
└── checklists/
    └── requirements.md  # Spec quality checklist
```

### Source Code (repository root)

```text
backend/src/test/kotlin/com/worklog/
├── IntegrationTestBase.kt                          # Existing shared base (PostgreSQL + Redis + MailHog)
├── infrastructure/persistence/
│   ├── JdbcUserRepositoryTest.kt                   # Existing (pattern reference)
│   └── PasswordResetTokenRepositoryTest.kt         # NEW: Repository integration tests
├── api/
│   ├── AuthControllerTest.kt                       # MODIFY: Add 4 validation test methods
│   ├── PasswordResetCsrfTest.kt                    # NEW: CSRF protection tests
│   └── PasswordResetRateLimitIT.kt                 # Existing (out of scope)
├── e2e/
│   └── PasswordResetE2ETest.kt                     # NEW: End-to-end flow tests
└── application/password/
    └── PasswordResetServiceTest.kt                 # Existing (out of scope)
```

**Structure Decision**: All new test files placed alongside existing test classes in their respective packages. The `e2e/` package is new but follows the pattern suggested in Issue #5. No new production code files.

## Implementation Design

### Component 1: PasswordResetTokenRepositoryTest

**File**: `backend/src/test/kotlin/com/worklog/infrastructure/persistence/PasswordResetTokenRepositoryTest.kt`
**Pattern**: Follows `JdbcUserRepositoryTest` — `@SpringBootTest` + `@Testcontainers` + `@Transactional`
**Research**: R1 (Repository Integration Test Pattern)

**Setup**:
- Own PostgreSQLContainer (not shared via IntegrationTestBase — avoids Redis/MailHog overhead)
- `@BeforeEach`: Clean `password_reset_tokens` and `users` tables, ensure test role exists
- Helper method to create test user (needed for FK constraint)
- Helper method to create test token (string ≥32 chars)

**Test methods** (10): TC-1.1 through TC-1.10 (see contracts/test-contracts.md)

**Key techniques**:
- For expiration tests (TC-1.4): INSERT token normally, then UPDATE `expires_at` to past via JdbcTemplate
- For CASCADE test (TC-1.10): Create user with token, delete user via JdbcTemplate, verify tokens gone
- For constraint test (TC-1.9): INSERT two tokens with same string, catch `DataIntegrityViolationException`

### Component 2: AuthControllerTest Validation Extensions

**File**: `backend/src/test/kotlin/com/worklog/api/AuthControllerTest.kt` (modify existing)
**Pattern**: Existing `@WebMvcTest` setup — no changes to test infrastructure
**Research**: R3 (Controller Validation Test Approach)

**Added tests** (4): TC-2.1 through TC-2.4

**Key technique**:
- Send requests with invalid payloads via MockMvc
- Jakarta validation (`@NotBlank`, `@Email`, `@Size`) is processed before service layer
- Service mocks are NOT called for validation failures
- Expect 400 status with validation error response

### Component 3: PasswordResetCsrfTest

**File**: `backend/src/test/kotlin/com/worklog/api/PasswordResetCsrfTest.kt`
**Pattern**: `@WebMvcTest(AuthController.class)` with custom SecurityFilterChain
**Research**: R2 (CSRF Testing Strategy)

**Setup**:
- `@TestConfiguration` providing a `SecurityFilterChain` bean with CSRF enabled (CookieCsrfTokenRepository)
- Mock `PasswordResetService` and `AuthService` beans
- Does NOT exclude SecurityAutoConfiguration

**Test methods** (4): TC-3.1 through TC-3.4

**Key technique**:
- Without `.with(csrf())`: expect 403 Forbidden
- With `.with(csrf())`: expect 200 OK (service mocked to succeed)

### Component 4: PasswordResetE2ETest

**File**: `backend/src/test/kotlin/com/worklog/e2e/PasswordResetE2ETest.kt`
**Pattern**: Extends `IntegrationTestBase`, uses WebTestClient
**Research**: R4 (E2E Test Architecture), R5 (Token Expiration), R6 (Session Invalidation)

**Setup**:
- Extends `IntegrationTestBase` (full stack: PostgreSQL + Redis + MailHog)
- `@Autowired WebTestClient`, `@Autowired JdbcTemplate`
- `@BeforeEach`: Clean test data (password_reset_tokens, users), ensure test role
- Helper: `createTestUser(email, password)` — inserts user directly via JdbcTemplate with BCrypt-hashed password and ACTIVE status (preferred over endpoint calls for speed and test isolation)
- Helper: `extractTokenFromDb(userId)` — queries password_reset_tokens

**Test methods** (4): TC-4.1 through TC-4.4

**Key techniques**:
- TC-4.1 (full flow): Create user → POST /request → extract token from DB → POST /confirm → POST /login with new password (200) → POST /login with old password (401)
- TC-4.2 (expired token): After token creation, UPDATE `expires_at` to past via JdbcTemplate → POST /confirm → expect 404
- TC-4.3 (used token): POST /confirm twice with same token → second returns 404
- TC-4.4 (session invalidation): POST /login (save session cookie) → POST /confirm → attempt authenticated request with old session cookie → expect failure

## Complexity Tracking

No constitution violations to justify. All patterns align with existing codebase conventions.
