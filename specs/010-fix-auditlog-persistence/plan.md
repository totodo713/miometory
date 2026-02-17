# Implementation Plan: AuditLog Persistence Bug Fix

**Branch**: `010-fix-auditlog-persistence` | **Date**: 2026-02-17 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/010-fix-auditlog-persistence/spec.md`

## Summary

Fix three bugs preventing `AuditLog` entities from being persisted via Spring Data JDBC:
1. **JSONB/INET type mapping** — Add custom converters for PostgreSQL `JSONB` and `INET` column types (FR-001, FR-002)
2. **isNew() detection** — Implement `Persistable<UUID>` to ensure new entities are always INSERTed (FR-003)
3. **Transaction rollback propagation** — Extract audit logging into a separate service bean with `REQUIRES_NEW` transaction propagation (FR-004)

No schema changes, no new API endpoints, no frontend changes.

## Technical Context

**Language/Version**: Java 21 (domain), Kotlin 2.3.0 (infrastructure/tests)
**Primary Dependencies**: Spring Boot 3.5.9, Spring Data JDBC, PostgreSQL JDBC Driver
**Storage**: PostgreSQL 17 (`audit_logs` table with JSONB + INET columns)
**Testing**: JUnit 5, MockK, Testcontainers (PostgreSQL 16), Spring Boot Test
**Target Platform**: Linux server (Docker)
**Project Type**: Web application (backend-only change)
**Performance Goals**: Audit log persistence must not add perceptible latency to login operations
**Constraints**: No schema changes to `audit_logs` table; maintain backward compatibility with existing audit log data
**Scale/Scope**: 5 new files, 3 modified files, 4 new test files

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Research Check

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. Code Quality | PASS | Bug fix follows existing converter/service patterns. No new abstractions beyond what's standard in Spring Data JDBC. |
| II. Testing Discipline | PASS | Plan includes unit tests for converters, entity behavior, and service; integration tests for repository and transaction isolation. Follows test pyramid. |
| III. Consistent UX | PASS | No user-facing changes. Fix ensures audit logging works transparently. |
| IV. Performance | PASS | `REQUIRES_NEW` propagation adds minimal overhead (new DB connection). Audit log persistence is not on the critical latency path. |
| Dependencies | PASS | No new external dependencies. Uses existing PostgreSQL JDBC driver `PGobject` class. |
| Workflow | PASS | Issue #15 documents the bug. Each fix maps to testable requirements (FR-001 through FR-004). |

### Post-Design Check

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. Code Quality | PASS | 4 converter classes follow existing `UserIdToUuidConverter` pattern exactly. `AuditLogService` follows standard Spring `@Service` pattern. `Persistable<UUID>` follows `Role` entity precedent. |
| II. Testing Discipline | PASS | Unit tests for: converters (null, valid, edge cases), AuditLog isNew() contract, AuditLogService mocked behavior. Integration tests for: repository CRUD with real PostgreSQL, transaction isolation. |
| III. Consistent UX | PASS | No UX impact. |
| IV. Performance | PASS | Converter overhead is negligible (String wrapping). `REQUIRES_NEW` uses a second connection from the pool — acceptable for audit logging frequency. |

## Project Structure

### Documentation (this feature)

```text
specs/010-fix-auditlog-persistence/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0: research decisions
├── data-model.md        # Phase 1: entity model changes
├── quickstart.md        # Phase 1: development setup guide
├── contracts/           # Phase 1: no new API contracts (bug fix)
│   └── README.md
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
backend/src/main/java/com/worklog/
├── domain/audit/
│   └── AuditLog.java                              # MODIFY: add Persistable<UUID>, @PersistenceCreator
├── application/audit/
│   └── AuditLogService.java                        # CREATE: transaction-isolated audit logging
├── application/auth/
│   └── AuthServiceImpl.java                        # MODIFY: delegate to AuditLogService
└── infrastructure/persistence/
    ├── PersistenceConfig.java                      # MODIFY: register 2 reading converters
    ├── AuditLogRepository.java                     # MODIFY: add @Query INSERT with SQL CAST
    ├── JsonbToStringReadingConverter.java           # CREATE
    └── InetToStringReadingConverter.java            # CREATE

backend/src/test/kotlin/com/worklog/
├── domain/audit/
│   └── AuditLogTest.kt                             # CREATE: unit tests
├── application/audit/
│   └── AuditLogServiceTest.kt                      # CREATE: unit + integration tests
└── infrastructure/persistence/
    ├── AuditLogRepositoryTest.kt                   # CREATE: integration tests
    └── JsonbInetConverterTest.kt                   # CREATE: unit tests
```

**Structure Decision**: Backend-only change following existing package conventions. New `application/audit/` package for the extracted service, following the pattern of `application/auth/`. All converters co-located with existing converters in `infrastructure/persistence/`.

## Complexity Tracking

No constitution violations. All changes follow existing patterns:
- Converters: Same pattern as `UserIdToUuidConverter` / `UuidToUserIdConverter`
- `Persistable<UUID>`: Same pattern as `Role` entity's `@PersistenceCreator`
- Service extraction: Standard Spring `@Service` + `@Transactional` pattern
