# Tasks: Detekt Baseline Violation Cleanup

**Input**: Design documents from `/specs/011-fix-detekt-baseline/`
**Prerequisites**: plan.md (required), spec.md (required), research.md

**Tests**: Not required — this is a code quality refactoring task. Existing tests serve as the verification mechanism (FR-002).

**Organization**: Tasks are grouped by violation category (matching user stories from spec.md), enabling independent implementation and verification per category.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Phase 1: Setup

**Purpose**: Verify current state before making changes

- [x] T001 Verify current baseline state by running `./gradlew detekt` and `./gradlew test` to establish a green starting point in `backend/`

**Checkpoint**: Baseline confirmed — all 25 violations suppressed, all tests pass

---

## Phase 2: User Story 1 — Fix Potential Bug Violations (Priority: P1) 🎯 MVP

**Goal**: Resolve all 5 potential-bug category violations (SwallowedException, TooGenericExceptionCaught) to eliminate hidden runtime failure risks.

**Independent Test**: Run `./gradlew detekt` — zero potential-bug violations; run `./gradlew test` — all tests pass.

### Implementation for User Story 1

- [x] T002 [P] [US1] Fix SwallowedException and TooGenericExceptionCaught in `backend/src/main/java/com/worklog/infrastructure/config/RateLimitConfig.kt`: In `TrustedProxyChecker.isTrustedProxy()`, narrow `catch (e: Exception)` to `catch (e: NumberFormatException)` and `catch (e: UnknownHostException)`, add SLF4J `logger.warn("Failed to parse CIDR range: {}", cidr, e)`. In `TrustedProxyChecker.CidrRange.contains()`, apply same narrowing and add `logger.warn("Failed to check IP against CIDR range", e)`. Remove 4 corresponding entries (SwallowedException × 2, TooGenericExceptionCaught × 2) from `backend/detekt-baseline.xml`.
- [x] T003 [P] [US1] Fix SwallowedException in `backend/src/test/kotlin/com/worklog/benchmark/PerformanceBenchmarkTest.kt`: Add `logger.debug("Request failed during benchmark", e)` inside the `catch (e: Exception)` block (keep `errorCount.incrementAndGet()`). Add SLF4J logger field if not present. Remove corresponding SwallowedException entry from `backend/detekt-baseline.xml`.
- [x] T004 [US1] Verify Phase 2: Run `./gradlew detekt` and confirm 5 potential-bug entries removed from baseline (20 remaining) and no new violations introduced (SC-004). Run `./gradlew test` to confirm all tests pass. Run `./gradlew formatAll` to ensure code formatting.

**Checkpoint**: All potential-bug violations resolved. 20 baseline entries remaining.

---

## Phase 3: User Story 2 — Fix Style Violations (Priority: P2)

**Goal**: Resolve all 10 style violations (MagicNumber × 7, MaxLineLength × 2, PrintStackTrace × 1) to improve code readability and consistency.

**Independent Test**: Run `./gradlew detekt` — zero style violations; run `./gradlew test` — all tests pass.

### Implementation for User Story 2

- [x] T005 [P] [US2] Fix MagicNumber in `backend/src/main/java/com/worklog/infrastructure/config/CorsConfig.kt`: Extract `3600L` to `companion object { private const val CORS_MAX_AGE_SECONDS = 3600L }` and replace usage. Remove MagicNumber:CorsConfig entry from `backend/detekt-baseline.xml`.
- [x] T006 [P] [US2] Fix MagicNumber in `backend/src/main/java/com/worklog/infrastructure/config/WebConfig.kt`: Extract `3600` to `companion object { private const val CACHE_PERIOD_SECONDS = 3600 }` and replace usage. Remove MagicNumber:WebConfig entry from `backend/detekt-baseline.xml`.
- [x] T007 [P] [US2] Fix MagicNumber × 5 in `backend/src/main/java/com/worklog/infrastructure/config/RateLimitConfig.kt`: Extract to companion object constants: `MILLIS_PER_SECOND = 1000L`, `RATE_LIMIT_WINDOW_SECONDS = 60` in RateLimitFilter; `IPV4_FULL_MASK = 0xFFFFFFFFL`, `IPV4_BITS = 32`, `BITS_PER_OCTET = 8` in CidrRange.Companion. Replace all usages. Remove 5 MagicNumber:RateLimitConfig entries from `backend/detekt-baseline.xml`.
- [x] T008 [P] [US2] Fix MaxLineLength in `backend/src/test/kotlin/com/worklog/api/FiscalYearPatternControllerTest.kt`: Extract the long SQL INSERT string to a `companion object` constant (e.g., `private const val UPSERT_TENANT_SQL = ...`) or use Kotlin multi-line string with `trimIndent()`. Remove MaxLineLength:FiscalYearPatternControllerTest entry from `backend/detekt-baseline.xml`.
- [x] T009 [P] [US2] Fix MaxLineLength in `backend/src/test/kotlin/com/worklog/api/MonthlyPeriodPatternControllerTest.kt`: Same fix as T008 — extract long SQL INSERT string to named constant. Remove MaxLineLength:MonthlyPeriodPatternControllerTest entry from `backend/detekt-baseline.xml`.
- [x] T010 [P] [US2] Fix PrintStackTrace in `backend/src/test/kotlin/com/worklog/infrastructure/persistence/JdbcUserRepositoryTest.kt`: Replace `e.printStackTrace()` with SLF4J `logger.error("Unexpected error", e)` or `fail("Unexpected exception", e)` depending on whether the catch block is expected to handle errors or indicates test failure. Add SLF4J logger field if not present. Remove PrintStackTrace:JdbcUserRepositoryTest entry from `backend/detekt-baseline.xml`.
- [x] T011 [US2] Verify Phase 3: Run `./gradlew detekt` and confirm 10 style entries removed from baseline (10 remaining) and no new violations introduced (SC-004). Run `./gradlew test` to confirm all tests pass. Run `./gradlew formatAll` to ensure code formatting.

**Checkpoint**: All style violations resolved. 10 baseline entries remaining.

---

## Phase 4: User Story 3 — Fix Complexity Violations (Priority: P3)

**Goal**: Resolve all 5 complexity violations (LargeClass × 2, LongMethod × 2, ComplexCondition × 1) to improve code modularity and readability.

**Independent Test**: Run `./gradlew detekt` — zero complexity violations; run `./gradlew test` — all tests pass with identical behavior.

### Implementation for User Story 3

- [x] T012 [P] [US3] Fix LargeClass in `backend/src/test/kotlin/com/worklog/api/AbsenceControllerTest.kt`: Add `@Suppress("LargeClass") // Integration test class — splitting would fragment related API endpoint tests and reduce cohesion` annotation to the class declaration. Remove LargeClass:AbsenceControllerTest entry from `backend/detekt-baseline.xml`.
- [x] T013 [P] [US3] Fix LargeClass in `backend/src/test/kotlin/com/worklog/api/WorkLogControllerTest.kt`: Add `@Suppress("LargeClass") // Integration test class — splitting would fragment related API endpoint tests and reduce cohesion` annotation to the class declaration. Remove LargeClass:WorkLogControllerTest entry from `backend/detekt-baseline.xml`.
- [x] T014 [P] [US3] Fix LongMethod in `backend/src/test/kotlin/com/worklog/api/MemberControllerTest.kt`: Refactor the `@BeforeEach fun setup()` method (~85 lines) by extracting entity-specific helper methods (e.g., `private fun insertTenant()`, `private fun insertOrganization()`, `private fun insertMember(...)`). The setup method should call these helpers sequentially. Remove LongMethod:MemberControllerTest entry from `backend/detekt-baseline.xml`.
- [x] T015 [P] [US3] Fix LongMethod in `backend/src/test/kotlin/com/worklog/api/ProjectControllerTest.kt`: Refactor the long test method by extracting the test data creation section into a helper method (e.g., `private fun createWorkLogEntries()`). Keep assertion logic in the test method. Remove LongMethod:ProjectControllerTest entry from `backend/detekt-baseline.xml`.
- [x] T016 [P] [US3] Fix ComplexCondition in `backend/src/main/java/com/worklog/infrastructure/config/RateLimitConfig.kt`: Extract health check paths to a list: `private val healthCheckPaths = listOf("/health", "/api/v1/health", "/ready")`. Replace complex OR condition with `path.startsWith("/actuator") || path in healthCheckPaths`. Remove ComplexCondition:RateLimitConfig entry from `backend/detekt-baseline.xml`.
- [x] T017 [US3] Verify Phase 4: Run `./gradlew detekt` and confirm 5 complexity entries removed from baseline (5 remaining) and no new violations introduced (SC-004). Run `./gradlew test` to confirm all tests pass. Run `./gradlew formatAll` to ensure code formatting.

**Checkpoint**: All complexity violations resolved. 5 baseline entries remaining.

---

## Phase 5: User Story 4 — Fix Naming and Other Violations (Priority: P4)

**Goal**: Resolve all 5 remaining violations (FunctionOnlyReturningConstant × 1, UnusedParameter × 2, UtilityClassWithPublicConstructor × 2) and eliminate the baseline file entirely.

**Independent Test**: Run `./gradlew detekt` without baseline file — zero violations; run `./gradlew test` — all tests pass.

### Implementation for User Story 4

- [x] T018 [P] [US4] Fix FunctionOnlyReturningConstant in `backend/src/test/kotlin/com/worklog/fixtures/UserFixtures.kt`: Convert `fun validHashedPassword(): String` to `const val VALID_HASHED_PASSWORD: String` in the companion object. Update all callers from `validHashedPassword()` to `VALID_HASHED_PASSWORD`. Remove FunctionOnlyReturningConstant:UserFixtures entry from `backend/detekt-baseline.xml`.
- [x] T019 [P] [US4] Fix UnusedParameter in `backend/src/test/kotlin/com/worklog/application/approval/ApprovalServiceTest.kt`: Investigate `createWorkLogEntry(id: UUID)` — if `id` was intended to be used as the entry's ID, wire it in; if truly unnecessary, remove the parameter and update all callers. Remove UnusedParameter:ApprovalServiceTest entry from `backend/detekt-baseline.xml`.
- [x] T020 [P] [US4] Fix UnusedParameter and UtilityClassWithPublicConstructor in `backend/src/test/kotlin/com/worklog/fixtures/OrganizationFixtures.kt`: (a) Investigate `createHierarchy(tenantId: UUID, ...)` — wire in `tenantId` if it should be used, or remove and update callers. (b) Convert `class OrganizationFixtures` to `object OrganizationFixtures` (move companion object members to top-level object members). Update all callers that reference `OrganizationFixtures.Companion` to `OrganizationFixtures`. Remove both UnusedParameter and UtilityClassWithPublicConstructor entries from `backend/detekt-baseline.xml`.
- [x] T021 [P] [US4] Fix UtilityClassWithPublicConstructor in `backend/src/test/kotlin/com/worklog/IntegrationTestBase.kt`: Add `@Suppress("UtilityClassWithPublicConstructor") // Abstract base class for integration tests — public constructor required for subclass instantiation` annotation to the class declaration. Remove UtilityClassWithPublicConstructor:IntegrationTestBase entry from `backend/detekt-baseline.xml`.
- [x] T022 [US4] Verify Phase 5: Run `./gradlew detekt` and confirm all 5 remaining entries removed from baseline (0 remaining) and no new violations introduced (SC-004). Run `./gradlew test` to confirm all tests pass. Run `./gradlew formatAll` to ensure code formatting.

**Checkpoint**: All 25 violations resolved. Baseline file should now be empty.

---

## Phase 6: Finalization

**Purpose**: Delete the empty baseline file and perform final validation

- [x] T023 Delete `backend/detekt-baseline.xml` from the repository
- [x] T024 Run `./gradlew detekt` without any baseline file and confirm zero violations
- [x] T025 Run `./gradlew test` for full test suite verification
- [x] T026 Run `./gradlew formatAll` and `./gradlew checkFormat` to confirm all formatting is clean

**Checkpoint**: Feature complete — detekt passes cleanly with no baseline, all tests green.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **US1 (Phase 2)**: Depends on Setup. FIRST priority — highest risk violations.
- **US2 (Phase 3)**: Depends on US1 completion (RateLimitConfig.kt is modified in both phases)
- **US3 (Phase 4)**: Depends on US2 completion (RateLimitConfig.kt is modified in this phase too)
- **US4 (Phase 5)**: Can technically start after Setup, but sequential ordering is cleaner for baseline entry tracking
- **Finalization (Phase 6)**: Depends on ALL user stories being complete

### User Story Dependencies

- **US1 (P1)**: No dependencies on other stories. Modifies `RateLimitConfig.kt` and `PerformanceBenchmarkTest.kt`.
- **US2 (P2)**: Depends on US1 (shares `RateLimitConfig.kt`). Modifies 6 files.
- **US3 (P3)**: Depends on US2 (shares `RateLimitConfig.kt`). Modifies 5 files.
- **US4 (P4)**: Independent of US1-US3 file-wise. Modifies 4 files.

### Within Each User Story

- All [P]-marked tasks within a phase can run in parallel (different files)
- Verify task must run after all implementation tasks in that phase complete
- Each phase commits: code fix + baseline entry removal together (FR-001)

### Parallel Opportunities

- Within US1: T002 and T003 can run in parallel (different files)
- Within US2: T005, T006, T007, T008, T009, T010 can ALL run in parallel (6 different files)
- Within US3: T012, T013, T014, T015, T016 can ALL run in parallel (5 different files)
- Within US4: T018, T019, T020, T021 can ALL run in parallel (4 different files)

---

## Parallel Example: User Story 2

```bash
# Launch all US2 fixes in parallel (6 independent files):
Task: "Fix MagicNumber in CorsConfig.kt"
Task: "Fix MagicNumber in WebConfig.kt"
Task: "Fix MagicNumber × 5 in RateLimitConfig.kt"
Task: "Fix MaxLineLength in FiscalYearPatternControllerTest.kt"
Task: "Fix MaxLineLength in MonthlyPeriodPatternControllerTest.kt"
Task: "Fix PrintStackTrace in JdbcUserRepositoryTest.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (verify green state)
2. Complete Phase 2: US1 — Fix potential-bug violations
3. **STOP and VALIDATE**: Run detekt + tests — 5 highest-risk violations resolved
4. Can merge/demo at this point — production code is safer

### Incremental Delivery

1. Setup → Green baseline confirmed
2. US1 (P1) → Potential bugs fixed → 20 remaining
3. US2 (P2) → Style violations fixed → 10 remaining
4. US3 (P3) → Complexity violations fixed → 5 remaining
5. US4 (P4) → All violations fixed → 0 remaining
6. Finalization → Baseline file deleted → Feature complete

### Single-Developer Strategy (Recommended)

This feature is best executed sequentially by a single developer:

1. Work through phases in order (RateLimitConfig.kt touched in US1→US2→US3)
2. Commit after each phase with descriptive message (e.g., `fix: resolve potential-bug detekt violations`)
3. Run verify task after each phase to catch issues early
4. Final commit: delete baseline file

---

## Notes

- [P] tasks = different files, no dependencies within the phase
- [Story] label maps task to specific user story for traceability
- Each user story is independently verifiable via `./gradlew detekt`
- `backend/detekt-baseline.xml` entries are removed incrementally per phase, not all at once
- RateLimitConfig.kt is the most-touched file (9 violations across US1, US2, US3) — phases must be sequential for this file
- Three violations use `@Suppress` with documented justification (T012, T013, T021) per research.md decisions
- Commit after each phase or logical group, with `./gradlew formatAll` before each commit
