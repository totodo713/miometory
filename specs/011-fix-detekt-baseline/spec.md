# Feature Specification: Detekt Baseline Violation Cleanup

**Feature Branch**: `011-fix-detekt-baseline`
**Created**: 2026-02-18
**Status**: Draft
**Input**: GitHub Issue #11 — "chore: detekt ベースライン違反の段階的解消（29件）"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Fix Potential Bug Violations (Priority: P1)

As a developer, I want the 5 potential-bug category violations (SwallowedException, TooGenericExceptionCaught) in `RateLimitConfig.kt` and `PerformanceBenchmarkTest.kt` to be properly resolved so that exceptions are handled safely and not silently swallowed, reducing the risk of hidden runtime failures.

**Why this priority**: Potential bugs are the highest-risk violations. Swallowed exceptions and overly generic exception catching can mask real errors in production, making debugging difficult and potentially causing silent data corruption or security issues.

**Independent Test**: Can be fully tested by running `./gradlew detekt` and confirming zero potential-bug violations. Additionally, existing integration tests must continue to pass, verifying that exception handling changes do not break functionality.

**Acceptance Scenarios**:

1. **Given** `RateLimitConfig.kt` has 2 `SwallowedException` and 2 `TooGenericExceptionCaught` violations, **When** the violations are fixed with proper exception handling (logging, specific exception types, or intentional suppression with `@Suppress` and a comment explaining why), **Then** the 4 corresponding entries are removed from `detekt-baseline.xml` and detekt passes without new violations.
2. **Given** `PerformanceBenchmarkTest.kt` has 1 `SwallowedException` violation, **When** the violation is fixed with appropriate test-context exception handling, **Then** the corresponding entry is removed from `detekt-baseline.xml` and the test continues to pass.

---

### User Story 2 - Fix Style Violations (Priority: P2)

As a developer, I want the style violations (MagicNumber, MaxLineLength, PrintStackTrace) across configuration and test files to be resolved so that the codebase follows consistent style conventions and is easier to read and maintain.

**Why this priority**: Style violations reduce code readability and maintainability. Magic numbers obscure intent, long lines hinder code review, and `printStackTrace()` is inappropriate for production-quality code. These are medium-risk but affect developer productivity.

**Independent Test**: Can be fully tested by running `./gradlew detekt` and confirming zero style violations. All existing tests must continue to pass.

**Acceptance Scenarios**:

1. **Given** `CorsConfig.kt`, `RateLimitConfig.kt`, and `WebConfig.kt` contain magic number violations (e.g., `3600L`, `1000L`, `60`, `0xFFFFFFFFL`, `32`, `8`, `3600`), **When** magic numbers are extracted to named constants with descriptive names, **Then** all 7 MagicNumber entries are removed from `detekt-baseline.xml`.
2. **Given** `FiscalYearPatternControllerTest.kt` and `MonthlyPeriodPatternControllerTest.kt` have lines exceeding the maximum length, **When** the long SQL strings are reformatted (e.g., multi-line strings or extracted to constants), **Then** the 2 MaxLineLength entries are removed from `detekt-baseline.xml`.
3. **Given** `JdbcUserRepositoryTest.kt` uses `printStackTrace()`, **When** it is replaced with proper logging or removed, **Then** the PrintStackTrace entry is removed from `detekt-baseline.xml`.

---

### User Story 3 - Fix Complexity Violations (Priority: P3)

As a developer, I want the complexity violations (LargeClass, LongMethod, ComplexCondition) to be resolved so that the code is more modular, easier to understand, and simpler to test.

**Why this priority**: Complexity violations indicate structural issues but are lower risk since they mainly affect maintainability rather than correctness. Test class sizes and method lengths are a natural consequence of comprehensive testing and may only need minor refactoring.

**Independent Test**: Can be fully tested by running `./gradlew detekt` and confirming zero complexity violations. All existing tests must continue to pass with identical coverage.

**Acceptance Scenarios**:

1. **Given** `AbsenceControllerTest.kt` and `WorkLogControllerTest.kt` trigger `LargeClass` violations, **When** `@Suppress` is applied with documented justification (integration test class — splitting would fragment cohesive controller-level endpoint tests), **Then** the 2 LargeClass entries are removed from `detekt-baseline.xml`.
2. **Given** `MemberControllerTest.kt` has a long `@BeforeEach setup` method and `ProjectControllerTest.kt` has a long test method, **When** setup logic and test assertions are refactored into smaller focused methods, **Then** the 2 LongMethod entries are removed from `detekt-baseline.xml`.
3. **Given** `RateLimitConfig.kt` has a complex boolean condition for path matching, **When** the condition is simplified (e.g., extracted to a list of allowed paths with a `.any {}` check), **Then** the ComplexCondition entry is removed from `detekt-baseline.xml`.

---

### User Story 4 - Fix Naming and Other Violations (Priority: P4)

As a developer, I want the remaining naming and structural violations (FunctionOnlyReturningConstant, UnusedParameter, UtilityClassWithPublicConstructor) to be resolved for a fully clean detekt baseline.

**Why this priority**: These are low-risk violations that are mostly cosmetic or structural. They are easy to fix and completing them allows the baseline file itself to be removed entirely.

**Independent Test**: Can be fully tested by running `./gradlew detekt` and confirming zero violations of any kind. The `detekt-baseline.xml` file can be deleted entirely after all violations are resolved.

**Acceptance Scenarios**:

1. **Given** `UserFixtures.kt` has a function that only returns a constant, **When** it is converted to a `const val` or `val` property, **Then** the FunctionOnlyReturningConstant entry is removed from `detekt-baseline.xml`.
2. **Given** `ApprovalServiceTest.kt` and `OrganizationFixtures.kt` have unused parameters, **When** the parameters are removed or used, **Then** the 2 UnusedParameter entries are removed from `detekt-baseline.xml`.
3. **Given** `OrganizationFixtures.kt` is a utility class with a public constructor, **When** it is converted to an `object` declaration, **Then** the UtilityClassWithPublicConstructor entry is removed from `detekt-baseline.xml`.
4. **Given** `IntegrationTestBase.kt` is an abstract base class flagged as UtilityClassWithPublicConstructor (false positive — public constructor is required for subclass instantiation), **When** `@Suppress` is applied with documented justification, **Then** the UtilityClassWithPublicConstructor entry is removed from `detekt-baseline.xml`.
5. **Given** all 25 baseline violations have been resolved, **When** `./gradlew detekt` is run without any baseline file, **Then** detekt passes with zero violations, and `detekt-baseline.xml` is deleted from the repository.

---

### Edge Cases

- What happens if fixing one violation introduces a new detekt violation? Each fix must be validated against the full detekt ruleset, not just the targeted rule.
- What happens if a suppressed violation cannot be cleanly fixed without significant refactoring? Use `@Suppress` with an explanatory comment as a last resort, and document the reasoning.
- What happens if removing a baseline entry causes a detekt failure in CI before the fix is merged? Baseline entries should only be removed in the same commit as the corresponding code fix.
- What happens if test behavior changes after refactoring large test classes? All existing tests must pass with identical assertions; refactoring must be behavior-preserving.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Each resolved violation MUST have its corresponding entry removed from `detekt-baseline.xml` in the same commit as the code fix.
- **FR-002**: All existing tests MUST continue to pass after each violation fix with no changes to test assertions or expected behavior.
- **FR-003**: Magic numbers MUST be extracted to named constants with descriptive names that convey the value's purpose.
- **FR-004**: Swallowed exceptions MUST be replaced with proper handling: logging the exception, rethrowing, or using `@Suppress` with a mandatory comment explaining why suppression is acceptable.
- **FR-005**: Generic exception catches (`Exception`) MUST be replaced with specific exception types where feasible.
- **FR-006**: `printStackTrace()` calls MUST be replaced with proper logging framework usage or removed.
- **FR-007**: Long lines MUST be reformatted to comply with the project's 120-character line limit.
- **FR-008**: Complex conditions MUST be refactored for readability (e.g., using collection operations or extracted helper functions).
- **FR-009**: Utility classes MUST either have private constructors or be converted to Kotlin `object` declarations.
- **FR-010**: Unused parameters MUST be removed or utilized; parameter removal must not break any public API contract.
- **FR-011**: After all violations are resolved, `detekt-baseline.xml` MUST be deleted from the repository.
- **FR-012**: The detekt Gradle task MUST pass cleanly (zero violations) after the baseline file is deleted.

### Key Entities

- **Detekt Baseline (`detekt-baseline.xml`)**: XML file containing 25 suppressed violation entries across 4 categories. Each entry maps to a specific code location and rule violation.
- **Violation Categories**: potential-bugs (5 entries), style (10 entries), complexity (5 entries), naming/other (5 entries). Prioritized from high to low risk.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 25 detekt baseline violations are resolved and their entries removed from the baseline file.
- **SC-002**: The `detekt-baseline.xml` file is deleted from the repository with detekt passing cleanly.
- **SC-003**: All existing tests pass with no test failures or behavioral changes after the cleanup.
- **SC-004**: No new detekt violations are introduced during the cleanup process.
- **SC-005**: Code changes are grouped into small, focused commits organized by violation category for easy review.

## Assumptions

- The current `detekt-baseline.xml` contains 25 entries (verified from the actual file), though the original issue referenced 29. The baseline file is the source of truth.
- Fixes should prioritize correctness and readability over minimal diff size.
- `@Suppress` annotations are acceptable as a last resort when fixing a violation would require disproportionate refactoring, provided a comment explains the reasoning.
- Test class refactoring (for LargeClass/LongMethod) should preserve existing test coverage and assertions exactly.
- The cleanup can be performed in a single branch with commits organized by category, or split across multiple PRs — the approach is flexible.
