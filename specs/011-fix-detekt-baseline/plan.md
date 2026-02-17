# Implementation Plan: Detekt Baseline Violation Cleanup

**Branch**: `011-fix-detekt-baseline` | **Date**: 2026-02-18 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/011-fix-detekt-baseline/spec.md`

## Summary

Resolve all 25 detekt baseline violations across 12 backend Kotlin files, organized by priority category (potential-bugs → style → complexity → naming/other). Each fix is paired with its baseline entry removal in the same commit. After all violations are resolved, the `detekt-baseline.xml` file is deleted. Three violations (2 LargeClass, 1 UtilityClassWithPublicConstructor) will use justified `@Suppress` annotations instead of code restructuring.

## Technical Context

**Language/Version**: Kotlin 2.3.0 (infrastructure/tests), Java 21 (domain)
**Primary Dependencies**: Spring Boot 3.5.9, detekt (static analysis), SLF4J (logging)
**Storage**: N/A (no storage changes)
**Testing**: JUnit 5 + Testcontainers, `./gradlew test` and `./gradlew detekt`
**Target Platform**: JVM / Spring Boot backend
**Project Type**: Web application (backend only for this feature)
**Performance Goals**: N/A (code quality cleanup — no runtime performance impact)
**Constraints**: All existing tests must pass; no new detekt violations; behavior-preserving changes only
**Scale/Scope**: 25 violations across 12 files in `backend/src/main/java/` and `backend/src/test/kotlin/`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Code Quality | **PASS** | This feature directly enforces code quality — resolving static analysis violations |
| II. Testing Discipline | **PASS** | Behavior-preserving refactoring validated by two automated assertion layers: (1) existing unit/integration tests confirm no behavioral regression, (2) `./gradlew detekt` confirms violation resolution. No new public API or logic added — existing coverage is sufficient per constitution |
| III. Consistent UX | **N/A** | No user-facing changes |
| IV. Performance | **N/A** | No performance-sensitive changes. Exception logging adds negligible overhead |
| Additional Constraints | **PASS** | No new dependencies introduced |
| Development Workflow | **PASS** | Mapped to Issue #11; changes organized by category for reviewable commits |

**Gate result**: PASS — no violations.

**Post-Phase 1 re-check**: PASS — fix strategies confirmed. `@Suppress` usage (3 instances) is justified in research.md with documented rationale per Code Quality principle requirement.

## Project Structure

### Documentation (this feature)

```text
specs/011-fix-detekt-baseline/
├── plan.md              # This file
├── research.md          # Phase 0 output — fix strategies per violation
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (files to be modified)

```text
backend/
├── src/main/java/com/worklog/infrastructure/config/
│   ├── RateLimitConfig.kt    # 9 violations (highest concentration)
│   ├── CorsConfig.kt         # 1 violation
│   └── WebConfig.kt          # 1 violation
├── src/test/kotlin/com/worklog/
│   ├── IntegrationTestBase.kt                    # 1 violation
│   ├── api/
│   │   ├── AbsenceControllerTest.kt              # 1 violation
│   │   ├── WorkLogControllerTest.kt              # 1 violation
│   │   ├── MemberControllerTest.kt               # 1 violation
│   │   ├── ProjectControllerTest.kt              # 1 violation
│   │   ├── FiscalYearPatternControllerTest.kt    # 1 violation
│   │   └── MonthlyPeriodPatternControllerTest.kt # 1 violation
│   ├── application/approval/
│   │   └── ApprovalServiceTest.kt                # 1 violation
│   ├── benchmark/
│   │   └── PerformanceBenchmarkTest.kt           # 1 violation
│   ├── fixtures/
│   │   ├── UserFixtures.kt                       # 1 violation
│   │   └── OrganizationFixtures.kt               # 2 violations
│   └── infrastructure/persistence/
│       └── JdbcUserRepositoryTest.kt             # 1 violation
└── detekt-baseline.xml  # 25 entries → 0 entries → deleted
```

**Structure Decision**: No new files or directories created. All changes are modifications to existing files plus eventual deletion of `detekt-baseline.xml`.

## Fix Strategy Summary

Derived from [research.md](./research.md):

### Phase A: Potential Bugs (P1 — 5 violations)

| # | File | Violation | Fix Strategy |
|---|------|-----------|-------------|
| 1 | RateLimitConfig.kt | SwallowedException (TrustedProxyChecker) | Add SLF4J warn logging |
| 2 | RateLimitConfig.kt | SwallowedException (CidrRange) | Add SLF4J warn logging |
| 3 | RateLimitConfig.kt | TooGenericExceptionCaught (TrustedProxyChecker) | Narrow to NumberFormatException, UnknownHostException |
| 4 | RateLimitConfig.kt | TooGenericExceptionCaught (CidrRange) | Narrow to NumberFormatException, UnknownHostException |
| 5 | PerformanceBenchmarkTest.kt | SwallowedException | Add debug-level logging |

### Phase B: Style (P2 — 10 violations)

| # | File | Violation | Fix Strategy |
|---|------|-----------|-------------|
| 6 | CorsConfig.kt | MagicNumber (3600L) | Extract to `CORS_MAX_AGE_SECONDS` constant |
| 7 | WebConfig.kt | MagicNumber (3600) | Extract to `CACHE_PERIOD_SECONDS` constant |
| 8-11 | RateLimitConfig.kt | MagicNumber × 4 | Extract to companion object constants |
| 12 | FiscalYearPatternControllerTest.kt | MaxLineLength | Extract SQL to named constant |
| 13 | MonthlyPeriodPatternControllerTest.kt | MaxLineLength | Extract SQL to named constant |
| 14 | JdbcUserRepositoryTest.kt | PrintStackTrace | Replace with SLF4J logger or `fail()` |
| 15 | RateLimitConfig.kt | MagicNumber (1000L) | Extract to `MILLIS_PER_SECOND` constant |

### Phase C: Complexity (P3 — 5 violations)

| # | File | Violation | Fix Strategy |
|---|------|-----------|-------------|
| 16 | AbsenceControllerTest.kt | LargeClass | `@Suppress` with justification comment |
| 17 | WorkLogControllerTest.kt | LargeClass | `@Suppress` with justification comment |
| 18 | MemberControllerTest.kt | LongMethod | Extract setup into helper methods |
| 19 | ProjectControllerTest.kt | LongMethod | Extract data creation into helper method |
| 20 | RateLimitConfig.kt | ComplexCondition | Extract paths to list + `.any {}` / `in` |

### Phase D: Naming & Other (P4 — 5 violations)

| # | File | Violation | Fix Strategy |
|---|------|-----------|-------------|
| 21 | UserFixtures.kt | FunctionOnlyReturningConstant | Convert to `const val` |
| 22 | ApprovalServiceTest.kt | UnusedParameter | Investigate: use or remove `id` param |
| 23 | OrganizationFixtures.kt | UnusedParameter | Investigate: use or remove `tenantId` param |
| 24 | OrganizationFixtures.kt | UtilityClassWithPublicConstructor | Convert to `object` |
| 25 | IntegrationTestBase.kt | UtilityClassWithPublicConstructor | `@Suppress` (abstract class — false positive) |

### Phase E: Finalization

- Remove all resolved entries from `detekt-baseline.xml` (done incrementally per phase)
- Delete `detekt-baseline.xml` entirely
- Run `./gradlew detekt` to confirm zero violations without baseline
- Run `./gradlew test` to confirm all tests pass

## Complexity Tracking

| Violation | Why `@Suppress` Needed | Simpler Alternative Rejected Because |
|-----------|------------------------|--------------------------------------|
| LargeClass (AbsenceControllerTest) | Integration test — 1 class per controller is idiomatic | Splitting fragments cohesive endpoint tests |
| LargeClass (WorkLogControllerTest) | Same rationale as above | Same as above |
| UtilityClassWithPublicConstructor (IntegrationTestBase) | Abstract class requires public/protected constructor for subclassing | Making constructor private would break all test subclasses |

## Artifacts Not Generated

The following Phase 1 artifacts are **not applicable** to this feature and are intentionally omitted:

- **data-model.md**: No data model changes — this is a code quality refactoring task
- **contracts/**: No API contract changes — no endpoints added or modified
- **quickstart.md**: No new setup or dependencies introduced
