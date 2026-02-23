# Coverage Improvement Design

## Goal

Bring all backend packages to 80%+ LINE coverage and fix failing TenantControllerTest (5 tests).

## Current State

- Overall LINE coverage: 82.6%
- 8 packages below 80% threshold
- TenantControllerTest: 5 failures due to DB container reuse + unique constraint violations

## Approach: Domain-Centric + Hybrid Test Data

### TenantControllerTest Fix

**Root cause:** `withReuse(true)` persists data between test runs. Tenant codes like `TEST_TENANT_001` violate the UNIQUE constraint on re-runs.

**Fix:** Generate unique tenant codes per test using UUID-based suffixes. This preserves container reuse performance while ensuring test isolation.

### Coverage Improvement

**Strategy:** Prioritize domain model unit tests (fast, no external dependencies), then application layer, then infrastructure.

#### Priority 1: Domain Models (Unit Tests)

| Package | Current | Target | New Test File | Tests |
|---|---|---|---|---|
| domain/dailyapproval | 0% | 80%+ | DailyEntryApprovalTest.kt | ~10 |
| domain/permission | 0% | 80%+ | PermissionTest.kt | ~10 |
| domain/notification | 24% | 80%+ | InAppNotificationTest.kt | ~6 |
| domain/session | 54% | 80%+ | UserSessionTest.kt (extend) | ~6 |
| domain/role | 59% | 80%+ | RoleTest.kt | ~6 |

#### Priority 2: Application Layer (Unit Tests with MockK)

| Package | Current | Target | Approach | Tests |
|---|---|---|---|---|
| application/approval | 62% | 80%+ | Extend existing ApprovalServiceTest | ~5 |
| application/command | 74% | 80%+ | CommandValidationTest.kt | ~4 |

#### Priority 3: Infrastructure (Unit Tests)

| Package | Current | Target | New Test File | Tests |
|---|---|---|---|---|
| infrastructure/persistence | 62% | 80%+ | IdConverterTest.kt | ~4 |

### Test Data Management (Hybrid)

- **Master data (immutable):** Loaded via Flyway migrations, persists with container reuse
- **Test-specific data (mutable):** Generated with random UUID suffixes per test
- **Shared test data:** `@BeforeAll` + companion object for expensive setup

### Total: ~51 new tests + 5 test fixes
