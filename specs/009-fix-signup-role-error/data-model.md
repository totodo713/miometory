# Data Model: Fix Signup API Role Instantiation Error

**Date**: 2026-02-17
**Branch**: `009-fix-signup-role-error`

## Entities Affected

### Role (existing — modification only)

**Table**: `roles`
**Persistence**: Spring Data JDBC (`CrudRepository<Role, RoleId>`)

| Field | Type | Nullable | Final | Notes |
|-------|------|----------|-------|-------|
| id | RoleId (UUID) | No | Yes | Primary key, custom converter registered |
| name | String | No | No | Uppercase, max 50 chars |
| description | String | Yes | No | Free text |
| createdAt | Instant | No | Yes | Set on creation |
| updatedAt | Instant | No | No | Updated on mutation |

**Constructors** (current):
1. 4-arg: `Role(RoleId, String, String, Instant)` — creation, delegates to 5-arg
2. 5-arg: `Role(RoleId, String, String, Instant, Instant)` — rehydration from persistence

**Change required**: Annotate the 5-arg constructor with `@PersistenceCreator` so Spring Data JDBC uses it for entity instantiation when loading from the database.

**Validation rules** (unchanged):
- `id` — non-null (`Objects.requireNonNull`)
- `name` — non-null, non-blank, max 50 chars, stored as uppercase
- `createdAt` — non-null (`Objects.requireNonNull`)
- `updatedAt` — non-null (`Objects.requireNonNull`)

**State transitions**: None changed. Role remains mutable via `update(name, description)`.

### AuditLog (no change needed)

**Table**: `audit_logs`
**Persistence**: Spring Data JDBC (`CrudRepository<AuditLog, UUID>`)

AuditLog has a single constructor (7-arg), so Spring Data JDBC can unambiguously select it. No `@PersistenceCreator` annotation needed. Verified as not affected by this bug.

## Relationships

```
Role 1 ←──── * User
  (User.roleId references Role.id)
```

No relationship changes in this fix.

## Converters (existing, no changes)

| Converter | Direction | From | To |
|-----------|-----------|------|----|
| RoleIdToUuidConverter | @WritingConverter | RoleId | UUID |
| UuidToRoleIdConverter | @ReadingConverter | UUID | RoleId |
| UserIdToUuidConverter | @WritingConverter | UserId | UUID |
| UuidToUserIdConverter | @ReadingConverter | UUID | UserId |

All converters registered in `PersistenceConfig.java`. No new converters needed.
