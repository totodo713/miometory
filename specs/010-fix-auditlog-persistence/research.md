# Research: AuditLog Persistence Bug Fix

**Feature Branch**: `010-fix-auditlog-persistence`
**Date**: 2026-02-17

## R1: Spring Data JDBC Custom Type Converters for PostgreSQL JSONB/INET

### Decision
Use `@WritingConverter` / `@ReadingConverter` with `PGobject` to map Java `String` ↔ PostgreSQL `JSONB` and `INET` types.

### Rationale
- Spring Data JDBC's `CrudRepository.save()` does not perform automatic PostgreSQL type casting (unlike raw SQL with `?::jsonb`)
- The existing codebase uses this exact pattern for `UserId ↔ UUID` and `RoleId ↔ UUID` (4 converters registered in `PersistenceConfig`)
- `PGobject` is the standard PostgreSQL JDBC driver class for custom types — it allows setting both the type name (`jsonb`, `inet`) and the value as a String
- Reading converters handle `PGobject` → `String` because Spring Data JDBC returns `PGobject` instances for JSONB/INET columns

### Alternatives Considered
1. **Raw SQL in repository with `?::jsonb` casts** — Already used in `JdbcAuditLogger` and `JdbcUserSessionRepository` but requires custom `@Query` for all operations including `save()`. This defeats the purpose of `CrudRepository`.
2. **Custom `RowMapper`** — Requires manual SQL for all queries. Excessive for a fix; the entity should work with `CrudRepository.save()`.
3. **Hibernate/JPA with `@Type` annotations** — Project uses Spring Data JDBC, not JPA. Not applicable.

---

## R2: Spring Data JDBC isNew() Detection for Pre-assigned IDs

### Decision
Implement `Persistable<UUID>` on `AuditLog` with a `@Transient boolean isNew` flag, and annotate the constructor with `@PersistenceCreator`.

### Rationale
- Spring Data JDBC determines INSERT vs UPDATE by checking if the `@Id` field is `null`. If non-null, it assumes UPDATE.
- `AuditLog` uses `UUID.randomUUID()` in factory methods — the ID is always non-null at creation time.
- `Persistable<UUID>` interface allows the entity to control the `isNew()` decision explicitly.
- `@PersistenceCreator` tells Spring Data JDBC which constructor to use for rehydration from database rows (sets `isNew = false`).
- Factory methods set `isNew = true` to force INSERT.
- This exact pattern was recently applied to `Role` entity (commit `181823e`) for the same reason.

### Alternatives Considered
1. **`@Version` field for optimistic locking** — AuditLog is append-only and immutable; versioning adds unnecessary complexity and schema changes.
2. **Set `@Id` to `null` and use database-generated UUIDs** — Requires changing the factory method pattern and losing the ability to generate IDs in domain code. Breaks the DDD pattern used across the project.
3. **Custom `BeforeSaveCallback`** — More complex, harder to test, and non-standard compared to `Persistable`.

---

## R3: Transaction Boundary Isolation for Audit Logging

### Decision
Extract audit logging into a separate `AuditLogService` Spring bean with `@Transactional(propagation = Propagation.REQUIRES_NEW)`.

### Rationale
- `AuthServiceImpl` has class-level `@Transactional`. The private `logAuditEvent()` method runs in the same transaction.
- When `auditLogRepository.save()` fails, Spring marks the transaction as rollback-only **before** the exception is thrown to the catch block. Even though the exception is caught, the transaction is already doomed.
- Spring AOP proxies only intercept calls through the proxy — a `private` method called internally bypasses the proxy entirely. Adding `@Transactional(propagation = REQUIRES_NEW)` to `logAuditEvent()` would have no effect.
- Extracting to a separate bean ensures Spring's AOP proxy manages the new transaction boundary.
- The `REQUIRES_NEW` propagation suspends the outer transaction, opens a new one for the audit log, and resumes the outer transaction regardless of audit log success/failure.

### Alternatives Considered
1. **Spring Application Events (`@TransactionalEventListener`)** — Elegant but changes the timing semantics: events fire after commit, meaning audit logs for failed operations (exceptions thrown after logging) would not be recorded. For login failures, we need the audit entry even when the outer transaction rolls back.
2. **`TransactionTemplate` for programmatic transaction management** — Works but mixes declarative (`@Transactional`) and programmatic styles. Less readable and harder to test.
3. **Self-injection (`@Lazy` self-reference)** — Allows calling a `@Transactional` method on the same class through the proxy. Works but is a well-known anti-pattern and confusing for maintainers.
4. **`@Async` with event listener** — Adds concurrency complexity, potential lost events on shutdown, and makes testing harder.

---

## R4: Existing Two-Table Audit Logging Architecture

### Decision
Maintain both audit logging systems as-is. This fix targets only the `audit_logs` table (V11 migration, auth-focused `AuditLog` entity).

### Rationale
- The project has two separate audit tables:
  - `audit_log` (V2 migration) — Used by `JdbcAuditLogger` for tenant/resource event sourcing audit trail
  - `audit_logs` (V11 migration) — Used by `AuditLogRepository` for auth-specific security events
- They serve different purposes and have different schemas
- Merging them is out of scope for this bug fix
- `JdbcAuditLogger` already handles JSONB via raw SQL (`?::jsonb`) and works correctly

### Alternatives Considered
1. **Unify to a single audit table** — Significant schema migration, affects event sourcing infrastructure. Out of scope.
2. **Port `AuditLog` to use `JdbcAuditLogger` approach** — Would require abandoning `CrudRepository` and writing manual SQL. Loses the benefit of Spring Data JDBC for the auth audit subsystem.
