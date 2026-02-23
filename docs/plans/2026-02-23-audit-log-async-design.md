# Fix: AuditLogService REQUIRES_NEW Deadlock

## Problem

E2E tests fail with `CannotCreateTransactionException` caused by a self-deadlock in `AuditLogService`.

### Root Cause

`REQUIRES_NEW` combined with a FK constraint creates a deadlock:

1. `AuthServiceImpl.login()` acquires Connection 1, locks a `users` row (UPDATE)
2. `AuditLogService.logEvent()` uses `REQUIRES_NEW`, suspending Connection 1
3. Connection 2 tries `INSERT INTO audit_logs` with `user_id` FK referencing `users`
4. FK check blocks on the `users` row lock held by Connection 1
5. Connection 1 is suspended waiting for Connection 2 to finish -> **deadlock**

After a few deadlocked connections, HikariCP pool (10 connections in dev) is exhausted, causing `CannotCreateTransactionException` for all subsequent requests.

## Solution: @Async Audit Logging

Replace `REQUIRES_NEW` with `@Async` so audit log insertion runs in a separate thread with its own transaction. This eliminates the deadlock because the outer transaction commits first, releasing all locks before the audit log is persisted.

### Changes

1. **Add `@EnableAsync`** to backend application or a config class
2. **Modify `AuditLogService.logEvent()`**: Replace `@Transactional(propagation = REQUIRES_NEW)` with `@Async @Transactional`
3. **Update tests** to account for async behavior

### Trade-offs

- Audit log insertion is slightly delayed (ms) but this is acceptable for audit purposes
- Login response time improves (no longer waits for audit log INSERT)
- Audit log failures are already handled gracefully (caught and logged)
