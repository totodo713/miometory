# Fix: CI Lint Error and E2E AuditLog JSON Error

## Problem

PR#33 CI has two failures:

1. **Lint & Format Check**: `auth.ts` uses tabs instead of spaces (Biome formatter mismatch)
2. **E2E Tests**: `AuditLogService` passes plain text strings to a `CAST(:details AS jsonb)` column, causing `invalid input syntax for type json` errors on every login

## Fix A: Lint (`auth.ts`)

Run `npx biome format --write` on `frontend/tests/e2e/fixtures/auth.ts` to auto-fix formatting.

## Fix B: AuditLogService JSON Wrapping

### Root Cause

- `AuditLogRepository.insertAuditLog()` uses `CAST(:details AS jsonb)` in SQL
- `AuthServiceImpl` passes plain text like `"User logged in successfully. User-Agent: ..."`
- PostgreSQL rejects non-JSON strings with `ERROR: invalid input syntax for type json`

### Approach: ObjectMapper in AuditLogService

Inject `ObjectMapper` into `AuditLogService` and use it to serialize the details string as a JSON object `{"message": "..."}` before passing to the repository.

**Changes:**
- `AuditLogService.java`: Add `ObjectMapper` dependency, add `toJsonDetails()` private method
- Single change point covers all 7+ callers in `AuthServiceImpl`

### Design

```java
// In AuditLogService constructor
private final ObjectMapper objectMapper;

public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
    this.auditLogRepository = auditLogRepository;
    this.objectMapper = objectMapper;
}

// New private method
private String toJsonDetails(String details) {
    if (details == null) return null;
    try {
        Map<String, String> wrapper = Map.of("message", details);
        return objectMapper.writeValueAsString(wrapper);
    } catch (Exception e) {
        log.warn("Failed to serialize audit details to JSON, using null", e);
        return null;
    }
}
```

Usage in `logEvent()`: replace `details` with `toJsonDetails(details)` when calling repository.

## Verification

1. `cd frontend && npx biome check tests/e2e/fixtures/auth.ts` (lint passes)
2. `cd backend && ./gradlew test --tests '*AuditLog*'` (if tests exist)
3. Push and verify CI passes
