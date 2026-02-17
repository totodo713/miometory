# Quickstart: Password Reset Integration & E2E Tests

**Branch**: `007-password-reset-tests` | **Date**: 2026-02-17

## Prerequisites

- Docker running (for Testcontainers)
- JDK 21+
- Backend project builds successfully: `cd backend && ./gradlew build`

## New Test Files

| File | Type | Pattern |
|------|------|---------|
| `backend/src/test/kotlin/com/worklog/infrastructure/persistence/PasswordResetTokenRepositoryTest.kt` | Repository IT | JdbcUserRepositoryTest |
| `backend/src/test/kotlin/com/worklog/api/PasswordResetCsrfTest.kt` | CSRF IT | @WebMvcTest + custom SecurityFilterChain |
| `backend/src/test/kotlin/com/worklog/e2e/PasswordResetE2ETest.kt` | E2E | IntegrationTestBase |

## Modified Test Files

| File | Change |
|------|--------|
| `backend/src/test/kotlin/com/worklog/api/AuthControllerTest.kt` | Add 4 validation test methods |

## Running Tests

```bash
# All new tests
cd backend

# Repository integration test
./gradlew test --tests "com.worklog.infrastructure.persistence.PasswordResetTokenRepositoryTest"

# Controller validation tests (existing + new)
./gradlew test --tests "com.worklog.api.AuthControllerTest"

# CSRF test
./gradlew test --tests "com.worklog.api.PasswordResetCsrfTest"

# E2E test
./gradlew test --tests "com.worklog.e2e.PasswordResetE2ETest"

# All tests
./gradlew test

# Format check
./gradlew checkFormat
```

## Key Implementation Notes

1. **Repository test setup**: Requires a test role in `roles` table + test user in `users` table before creating tokens (foreign key constraint)
2. **Token strings must be ≥32 characters** (validated in PasswordResetToken constructor)
3. **Expiration testing**: Manipulate `expires_at` via JdbcTemplate SQL UPDATE (same approach as JdbcUserRepositoryTest locked_until manipulation)
4. **CSRF test**: Uses custom TestConfiguration with SecurityFilterChain that enables CSRF, independent of profile-based SecurityConfig
5. **E2E token extraction**: Query `password_reset_tokens` table directly via JdbcTemplate after calling the request endpoint (MailHog email parsing not needed)
