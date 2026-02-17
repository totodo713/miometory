# Contracts: AuditLog Persistence Bug Fix

No new API endpoints are introduced by this bug fix. All changes are internal to the backend persistence and application layers.

## Existing Contracts (Unchanged)

- `POST /api/v1/auth/login` — Login endpoint (existing, behavior unchanged)
- `POST /api/v1/auth/signup` — Signup endpoint (existing, behavior unchanged)
- `POST /api/v1/auth/verify-email` — Email verification endpoint (existing, behavior unchanged)

## Internal Contract Change

The `AuditLogRepository.save()` method (Spring Data JDBC `CrudRepository`) will now correctly persist `AuditLog` entities with JSONB `details` and INET `ip_address` fields. This is a **behavioral fix**, not an interface change.

## New Internal Service

`AuditLogService.logEvent(userId, eventType, ipAddress, details)` — Internal service for transaction-isolated audit logging. Not exposed via REST.
