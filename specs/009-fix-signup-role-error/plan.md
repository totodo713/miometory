# Implementation Plan: Fix Signup API Role Instantiation Error

**Branch**: `009-fix-signup-role-error` | **Date**: 2026-02-17 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/009-fix-signup-role-error/spec.md`

## Summary

The signup API (`POST /api/v1/auth/signup`) returns a 500 Internal Server Error because the `Role` entity lacks a persistence-compatible constructor for Spring Data JDBC. The fix adds a `@PersistenceCreator`-annotated constructor to `Role.java` so Spring Data JDBC can properly instantiate the entity when loading from the database. Additionally, an `IllegalStateException` handler is added to `GlobalExceptionHandler` so that a missing default role returns a 503 Service Unavailable instead of a generic 500 error. Regression tests will verify the signup flow works end-to-end.

## Technical Context

**Language/Version**: Java 21 (domain), Kotlin 2.3.0 (infrastructure)
**Primary Dependencies**: Spring Boot 3.5.9, Spring Data JDBC, Spring Security
**Storage**: PostgreSQL 17 (via Spring Data JDBC for Role entity, event sourcing for aggregates)
**Testing**: JUnit 5, Spring Boot Test, Testcontainers 1.21.1, MockK
**Target Platform**: Linux server (Docker)
**Project Type**: Web application (backend + frontend)
**Performance Goals**: N/A (bug fix, no new performance requirements)
**Constraints**: Minimal change — fix must not alter the domain model's public API or business logic
**Scale/Scope**: Two file changes (Role.java, GlobalExceptionHandler.java) + regression tests

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Code Quality | PASS | Minimal change to one entity; follows existing codebase patterns; inline documentation preserved |
| II. Testing Discipline | PASS | Regression test required and planned; maps directly to Issue #17 |
| III. Consistent UX | PASS | Backend-only fix; no user-facing surface changes |
| IV. Performance Requirements | PASS | No performance impact; constructor annotation only affects instantiation metadata |
| Additional Constraints | PASS | No new dependencies; Spring Data `@PersistenceCreator` is part of existing `spring-data-commons` |
| Development Workflow | PASS | Tracked via Issue #17; change maps to documented requirement and test scenario |

**Pre-Phase 0 Gate: PASSED** — No violations detected.

## Project Structure

### Documentation (this feature)

```text
specs/009-fix-signup-role-error/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (no new contracts for bug fix)
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/worklog/
│   ├── domain/role/
│   │   └── Role.java                         # FIX: Add @PersistenceCreator annotation
│   └── api/
│       ├── AuthController.java               # Existing (no change needed)
│       └── GlobalExceptionHandler.java       # FIX: Add IllegalStateException handler
└── src/test/kotlin/com/worklog/
    ├── api/
    │   └── AuthControllerSignupTest.kt       # NEW: Signup integration tests
    └── infrastructure/persistence/
        └── RoleRepositoryTest.kt             # NEW: Role data integrity test
```

**Structure Decision**: Web application structure. This bug fix touches only the backend — two production files (`Role.java`, `GlobalExceptionHandler.java`) and adds two integration test files. No frontend changes required.

## Complexity Tracking

> No constitution violations. This section is intentionally left empty.
