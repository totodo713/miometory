# Research: Detekt Baseline Violation Cleanup

**Date**: 2026-02-18
**Feature**: 011-fix-detekt-baseline

## Overview

All 25 violations in `detekt-baseline.xml` were investigated by reading the actual source code. This document records the fix strategy for each violation, grouped by category and priority.

## Category 1: Potential Bugs (P1 — 5 entries)

### SwallowedException × 3

#### RateLimitConfig.kt — TrustedProxyChecker (2 locations)

**Current code**: Two `catch (e: Exception)` blocks that silently swallow exceptions:
1. `TrustedProxyChecker.isTrustedProxy()` — catches exception when parsing CIDR ranges, silently skips invalid entries
2. `TrustedProxyChecker.CidrRange.contains()` — catches exception during IP address matching, returns `false`

**Decision**: Add SLF4J logging at `warn` level for both catch blocks, and narrow exception types to `NumberFormatException` and `UnknownHostException` (the actual exceptions that can occur during IP/CIDR parsing).

**Rationale**: These are network configuration edge cases where silent failure is intentional (skip invalid config), but logging is essential for diagnosing misconfiguration. Narrowing exception types prevents masking unexpected errors.

**Alternatives considered**:
- `@Suppress` with comment: Rejected — logging is genuinely useful here for debugging.
- Rethrowing: Rejected — would break rate limiter on config issues, causing availability problems.

#### PerformanceBenchmarkTest.kt (1 location)

**Current code**: `catch (e: Exception) { errorCount.incrementAndGet() }` — counts errors during concurrent load test without logging.

**Decision**: Log the exception at `debug` level within the catch block. This is a test file where the error count is already tracked; logging provides visibility during test debugging.

**Rationale**: Test context makes full error details useful for debugging failed benchmarks while keeping the error counting logic intact.

**Alternatives considered**:
- Using a more specific exception type: Not feasible — the test intentionally catches any failure during concurrent API calls.
- `@Suppress`: Rejected — easy fix, no reason to suppress.

### TooGenericExceptionCaught × 2

**Current code**: Same two locations in RateLimitConfig.kt catching `Exception`.

**Decision**: Narrow to specific exception types (`NumberFormatException`, `UnknownHostException` for IP parsing). Use multi-catch if needed.

**Rationale**: The actual operations (IP address parsing, CIDR range calculation) throw specific, predictable exceptions. Catching `Exception` masks unexpected errors.

**Alternatives considered**:
- Keeping `Exception` with `@Suppress`: Rejected — narrowing is straightforward here.

## Category 2: Style (P2 — 10 entries)

### MagicNumber × 7

| File | Value | Meaning | Fix |
|------|-------|---------|-----|
| CorsConfig.kt | `3600L` | CORS preflight cache duration (1 hour) | Extract to `companion object { private const val CORS_MAX_AGE_SECONDS = 3600L }` |
| WebConfig.kt | `3600` | Static resource cache period (1 hour) | Extract to `companion object { private const val CACHE_PERIOD_SECONDS = 3600 }` |
| RateLimitConfig.kt | `1000L` | Milliseconds-to-seconds divisor | Extract to `companion object` constant `MILLIS_PER_SECOND` |
| RateLimitConfig.kt | `60` | Rate limit window in seconds | Extract to constant `RATE_LIMIT_WINDOW_SECONDS` |
| RateLimitConfig.kt | `0xFFFFFFFFL` | IPv4 full mask (32-bit) | Extract to `IPV4_FULL_MASK` in CidrRange.Companion |
| RateLimitConfig.kt | `32` | IPv4 address bits | Extract to `IPV4_BITS` in CidrRange.Companion |
| RateLimitConfig.kt | `8` | Bits per octet | Extract to `BITS_PER_OCTET` in CidrRange.Companion |

**Decision**: Extract all magic numbers to named `const val` properties in the nearest `companion object`.

**Rationale**: Named constants convey intent and are the standard fix for MagicNumber violations. Companion object scope keeps constants close to their usage.

### MaxLineLength × 2

**Current code**: Both `FiscalYearPatternControllerTest.kt` and `MonthlyPeriodPatternControllerTest.kt` have the same long SQL string:
```
"INSERT INTO tenant (id, code, name, status, created_at) VALUES (?, ?, ?, 'ACTIVE', NOW()) ON CONFLICT (id) DO NOTHING"
```

**Decision**: Extract to a `companion object` constant (e.g., `UPSERT_TENANT_SQL`) in each test class, or use Kotlin multi-line string with `trimIndent()`.

**Rationale**: Extracting to a constant both fixes the line length and improves reusability if the SQL appears multiple times.

**Alternatives considered**:
- String concatenation across lines: Rejected — harder to read than a named constant.
- `@Suppress("MaxLineLength")`: Rejected — easy fix, no reason to suppress.

### PrintStackTrace × 1

**Current code**: `JdbcUserRepositoryTest.kt` line 98: `e.printStackTrace()` in a test catch block.

**Decision**: Replace with SLF4J logger call (`logger.error("...", e)`) or use `fail("Unexpected exception", e)` if the exception indicates test failure.

**Rationale**: `printStackTrace()` bypasses logging infrastructure and is inappropriate even in tests.

## Category 3: Complexity (P3 — 5 entries)

### LargeClass × 2

| File | Lines | Content |
|------|-------|---------|
| AbsenceControllerTest.kt | ~1,121 | Integration tests for absence endpoints |
| WorkLogControllerTest.kt | ~951 | Integration tests for work log endpoints |

**Decision**: Use `@Suppress("LargeClass")` with a comment: "Integration test class — splitting would fragment related API endpoint tests and reduce cohesion."

**Rationale**: These are integration test classes that test REST controller endpoints. Each class maps to one controller. Splitting them would create artificial boundaries between tests for the same API. The test pyramid principle already places these as fewer, larger integration tests. The classes are large but not complex — they follow a repetitive test pattern (setup → request → assert).

**Alternatives considered**:
- Splitting into nested classes by HTTP method: Increases indentation and adds boilerplate without improving readability.
- Splitting into separate files per endpoint group: Fragments the mental model of "all tests for this controller."
- Extracting helper methods: Already done where appropriate; further extraction would obscure test intent.

### LongMethod × 2

| File | Method | ~Lines |
|------|--------|--------|
| MemberControllerTest.kt | `@BeforeEach fun setup()` | ~85 |
| ProjectControllerTest.kt | Long test method | ~94 |

**Decision for MemberControllerTest.kt**: Extract setup into helper methods (e.g., `insertTenant()`, `insertOrganization()`, `insertMember()`). The `@BeforeEach setup()` method creates 5 entities with raw JDBC — this naturally decomposes into entity-specific helper functions.

**Decision for ProjectControllerTest.kt**: Extract the test data creation section into a helper method (e.g., `createWorkLogEntries()`), keeping the assertion logic in the test method itself.

**Rationale**: Unlike LargeClass, long methods can be meaningfully decomposed into focused helper functions that improve readability.

### ComplexCondition × 1

**Current code** in RateLimitConfig.kt:
```kotlin
path.startsWith("/actuator") || path == "/health" || path == "/api/v1/health" || path == "/ready"
```

**Decision**: Extract paths to a list and use `.any {}`:
```kotlin
private val healthCheckPaths = listOf("/health", "/api/v1/health", "/ready")
// ...
path.startsWith("/actuator") || path in healthCheckPaths
```

**Rationale**: List-based approach is readable, extensible, and eliminates the complex OR chain. The `/actuator` prefix check stays separate since it's a prefix match, not exact.

## Category 4: Naming & Other (P4 — 5 entries)

### FunctionOnlyReturningConstant × 1

**Current code** in `UserFixtures.kt`: `fun validHashedPassword(): String = "$2a$10$N9qo8u..."`

**Decision**: Convert to `const val VALID_HASHED_PASSWORD` in companion object.

**Rationale**: A function that always returns the same value should be a constant. As a `const val`, it's evaluated at compile time and communicates immutability.

### UnusedParameter × 2

| File | Method | Unused Param |
|------|--------|-------------|
| ApprovalServiceTest.kt | `createWorkLogEntry(id: UUID)` | `id` |
| OrganizationFixtures.kt | `createHierarchy(tenantId: UUID, ...)` | `tenantId` |

**Decision**: Investigate whether the parameters were intended to be used. If the ID/tenantId should have been used in the entity creation but was accidentally omitted, wire it in. If truly unnecessary, remove the parameter and update all callers.

**Rationale**: Unused parameters typically indicate either a bug (parameter should be used) or dead code (parameter is vestigial). Both cases need investigation.

### UtilityClassWithPublicConstructor × 2

| File | Class | Nature |
|------|-------|--------|
| IntegrationTestBase.kt | `abstract class IntegrationTestBase` | Abstract base class for tests |
| OrganizationFixtures.kt | `class OrganizationFixtures` | Fixture factory with companion object |

**Decision for IntegrationTestBase.kt**: Use `@Suppress("UtilityClassWithPublicConstructor")` with comment. This is an `abstract class` used as a test base — the public constructor is required for subclass instantiation. This is a false positive from detekt.

**Decision for OrganizationFixtures.kt**: Convert to `object OrganizationFixtures` since it only contains companion object methods and no instance state.

**Rationale**: IntegrationTestBase is abstract and must be subclassable — suppression is correct. OrganizationFixtures is a pure utility/factory — `object` is the idiomatic Kotlin pattern.

## Summary of Fix Approaches

| Approach | Count | Violations |
|----------|-------|------------|
| Code fix (narrow exceptions, add logging) | 5 | SwallowedException × 3, TooGenericExceptionCaught × 2 |
| Extract named constants | 8 | MagicNumber × 7, FunctionOnlyReturningConstant × 1 |
| Reformat/extract strings | 2 | MaxLineLength × 2 |
| Replace with logger | 1 | PrintStackTrace × 1 |
| Extract helper methods | 2 | LongMethod × 2 |
| Refactor condition | 1 | ComplexCondition × 1 |
| Convert to `object` | 1 | UtilityClassWithPublicConstructor (OrganizationFixtures) |
| Investigate & fix/remove | 2 | UnusedParameter × 2 |
| `@Suppress` with justification | 3 | LargeClass × 2, UtilityClassWithPublicConstructor (IntegrationTestBase) |
| **Total** | **25** | |

## Key Decisions

1. **`@Suppress` usage**: Limited to 3 cases where the fix would reduce code quality (splitting cohesive test classes, suppressing abstract class false positive).
2. **Exception handling strategy**: Add logging + narrow exception types rather than just suppressing.
3. **Test class splitting**: Not recommended for LargeClass — cohesion of controller-level integration tests is more valuable than arbitrary size limits.
