**Note**: Development now uses devcontainer. See [QUICKSTART.md](/QUICKSTART.md) for current setup instructions.

# Quickstart: AuditLog Persistence Bug Fix

**Feature Branch**: `010-fix-auditlog-persistence`
**Date**: 2026-02-17

## Prerequisites

- Java 21 + Kotlin 2.3.0
- Docker (for Testcontainers / dev PostgreSQL)
- Dev environment running: `cd infra/docker && docker-compose -f docker-compose.dev.yml up -d`

## Development Setup

```bash
# Switch to feature branch
git checkout 010-fix-auditlog-persistence

# Build and run tests
cd backend
./gradlew build

# Run only relevant test classes
./gradlew test --tests "com.worklog.infrastructure.persistence.AuditLogRepositoryTest"
./gradlew test --tests "com.worklog.application.audit.AuditLogServiceTest"
./gradlew test --tests "com.worklog.domain.audit.AuditLogTest"

# Format before commit
./gradlew formatAll
```

## Files to Create

| File | Purpose |
|------|---------|
| `infrastructure/persistence/JsonbToStringReadingConverter.java` | PGobject → String (jsonb read) |
| `infrastructure/persistence/InetToStringReadingConverter.java` | PGobject → String (inet read) |
| `application/audit/AuditLogService.java` | Transaction-isolated audit logging |

> **Note**: Writing converters were intentionally omitted. Global `String → PGobject` writing converters
> affect ALL String fields across all entities, breaking VARCHAR columns. Instead, `AuditLogRepository`
> uses a custom `@Query` INSERT with explicit `CAST(? AS jsonb)` / `CAST(? AS inet)` SQL casting.

## Files to Modify

| File | Change |
|------|--------|
| `domain/audit/AuditLog.java` | Add `Persistable<UUID>`, `@PersistenceCreator`, `@Transient isNew` |
| `infrastructure/persistence/PersistenceConfig.java` | Register 2 reading converters |
| `infrastructure/persistence/AuditLogRepository.java` | Add `@Query` INSERT with explicit SQL CAST |
| `application/auth/AuthServiceImpl.java` | Replace private `logAuditEvent()` with `AuditLogService` delegation |

## Files to Create (Tests)

| File | Purpose |
|------|---------|
| `test/.../domain/audit/AuditLogTest.kt` | Unit: isNew() behavior, Persistable contract |
| `test/.../infrastructure/persistence/AuditLogRepositoryTest.kt` | Integration: CRUD with JSONB/INET |
| `test/.../infrastructure/persistence/JsonbInetConverterTest.kt` | Unit: converter correctness |
| `test/.../application/audit/AuditLogServiceTest.kt` | Unit + Integration: transaction isolation |

## Verification Checklist

- [ ] `AuditLog.createUserAction()` → `repository.save()` succeeds (no PSQLException)
- [ ] Saved audit log has correct JSONB details (queryable, not corrupted)
- [ ] Saved audit log has correct INET ip_address (both IPv4 and IPv6)
- [ ] Multiple saves always INSERT (never UPDATE)
- [ ] Login succeeds even when audit log save fails
- [ ] Existing `AuthServiceTest` still passes
- [ ] `./gradlew test` all green
- [ ] `./gradlew checkFormat` passes
