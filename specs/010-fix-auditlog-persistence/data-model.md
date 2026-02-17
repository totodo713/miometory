# Data Model: AuditLog Persistence Bug Fix

**Feature Branch**: `010-fix-auditlog-persistence`
**Date**: 2026-02-17

## Entity: AuditLog (Updated)

### Current State (Broken)

```
AuditLog
├── @Id UUID id                    ← isNew() always returns false (non-null ID)
├── UserId userId                  ← Converter exists (UserIdToUuidConverter)
├── String eventType               ← Works (VARCHAR)
├── String ipAddress               ← FAILS: no String→INET converter
├── Instant timestamp              ← Works (TIMESTAMP)
├── String details                 ← FAILS: no String→JSONB converter
└── int retentionDays              ← Works (INTEGER)
```

### Target State (Fixed)

```
AuditLog implements Persistable<UUID>
├── @Id UUID id                    ← isNew() controlled by Persistable
├── UserId userId                  ← Converter exists (UserIdToUuidConverter)
├── String eventType               ← Works (VARCHAR)
├── String ipAddress               ← SQL CAST via @Query INSERT + InetToStringReadingConverter
├── Instant timestamp              ← Works (TIMESTAMP)
├── String details                 ← SQL CAST via @Query INSERT + JsonbToStringReadingConverter
├── int retentionDays              ← Works (INTEGER)
└── @Transient boolean isNew       ← NEW: controls INSERT vs UPDATE
```

### Changes Required

| Field | Current | Target | Change |
|-------|---------|--------|--------|
| Class declaration | `class AuditLog` | `class AuditLog implements Persistable<UUID>` | Add interface |
| Constructor | No annotation | `@PersistenceCreator` (sets `isNew = false`) | Add annotation |
| Factory methods | Sets id via `UUID.randomUUID()` | Same, but also sets `isNew = true` | Add flag |
| `isNew` field | Does not exist | `@Transient private boolean isNew` | New field |
| `getId()` | Returns `UUID` | Same (satisfies `Persistable<UUID>`) | No change |
| `isNew()` | Does not exist | Returns `this.isNew` (satisfies `Persistable<UUID>`) | New method |

### Database Schema (Unchanged)

Table `audit_logs` (from V11__user_auth.sql):

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | UUID | NOT NULL | gen_random_uuid() | Primary key |
| user_id | UUID | YES | — | FK → users(id) ON DELETE SET NULL |
| event_type | VARCHAR(100) | NOT NULL | — | |
| ip_address | INET | YES | — | Requires custom converter |
| timestamp | TIMESTAMP | NOT NULL | NOW() | |
| details | JSONB | YES | — | Requires custom converter |
| retention_days | INTEGER | NOT NULL | 90 | |

**No schema changes required.** All fixes are at the application layer.

## New Components

### Type Converters (2 reading converters + SQL CAST)

| Component | Direction | From | To | Purpose |
|-----------|-----------|------|----|---------|
| JsonbToStringReadingConverter | Read | PGobject | String | Read details field |
| InetToStringReadingConverter | Read | PGobject | String | Read ip_address field |
| AuditLogRepository `@Query` INSERT | Write | String | JSONB/INET | Explicit `CAST(? AS jsonb)` / `CAST(? AS inet)` |

> **Design decision**: Global writing converters (`String → PGobject`) were rejected because they
> affect ALL String fields across all entities, breaking VARCHAR columns. Instead, writing is handled
> by explicit SQL CAST in the repository's custom `@Query` INSERT method.

Location: `backend/src/main/java/com/worklog/infrastructure/persistence/`

### AuditLogService (1 new class)

| Class | Purpose | Key Annotation |
|-------|---------|----------------|
| AuditLogService | Isolate audit log transactions from callers | `@Transactional(propagation = REQUIRES_NEW)` |

Location: `backend/src/main/java/com/worklog/application/audit/AuditLogService.java`

### Dependency Graph

```
AuthServiceImpl
  └── AuditLogService (NEW - replaces private logAuditEvent())
        └── AuditLogRepository
              └── AuditLog (updated: Persistable<UUID>)
                    └── PersistenceConfig (updated: 4 new converters)
                          ├── JsonbToStringReadingConverter (NEW)
                          └── InetToStringReadingConverter (NEW)
```
