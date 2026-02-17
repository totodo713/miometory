# Quickstart: Fix Signup API Role Instantiation Error

**Branch**: `009-fix-signup-role-error`

## Prerequisites

- Java 21
- Docker (for Testcontainers PostgreSQL)
- The dev database must have role seed data (already present in `data-dev.sql`)

## Files to Modify

### 1. `backend/src/main/java/com/worklog/domain/role/Role.java`

**Change**: Add `@PersistenceCreator` annotation to the 5-arg rehydration constructor.

```java
import org.springframework.data.annotation.PersistenceCreator;

// Add annotation to existing 5-arg constructor:
@PersistenceCreator
public Role(RoleId id, String name, String description, Instant createdAt, Instant updatedAt) {
    // ... existing body unchanged
}
```

### 2. `backend/src/test/java/com/worklog/api/AuthControllerSignupTest.java` (NEW)

**Change**: Add integration test verifying signup endpoint returns success.

Test should:
- Use `@SpringBootTest` with `WebEnvironment.RANDOM_PORT`
- Use Testcontainers for PostgreSQL
- POST to `/api/v1/auth/signup` with valid registration data
- Assert 200/201 response (not 500)
- Assert response body contains user ID, email, and name

## Verification

```bash
# Run the specific test
cd backend && ./gradlew test --tests "com.worklog.api.AuthControllerSignupTest"

# Run all tests to check for regressions
cd backend && ./gradlew test

# Manual verification
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "name": "Test User", "password": "Password1"}'
# Expected: 200/201 with user data (previously: 500 Internal Server Error)
```

## Format Check

```bash
cd backend && ./gradlew formatAll && ./gradlew checkFormat
```
