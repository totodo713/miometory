# Data Model: Password Reset Integration & E2E Tests

**Branch**: `007-password-reset-tests` | **Date**: 2026-02-17

## Existing Entities (No Changes Required)

This feature adds tests only — no data model modifications are needed. The following documents the existing schema for test design reference.

### PasswordResetToken

**Table**: `password_reset_tokens` (Migration: `V11__user_auth.sql`)

| Column      | Type           | Constraints                                              |
|-------------|----------------|----------------------------------------------------------|
| id          | UUID           | PK, DEFAULT gen_random_uuid()                            |
| user_id     | UUID           | NOT NULL, FK → users(id) ON DELETE CASCADE               |
| token       | VARCHAR(255)   | NOT NULL, UNIQUE (idx_password_reset_tokens_token)       |
| created_at  | TIMESTAMP      | NOT NULL, DEFAULT NOW()                                  |
| expires_at  | TIMESTAMP      | NOT NULL, CHECK (expires_at > created_at)                |
| used_at     | TIMESTAMP      | NULLABLE (NULL = unused, non-NULL = used)                |

**Indexes**:
- `idx_password_reset_tokens_token` — UNIQUE on `token`
- `idx_password_reset_tokens_user_id` — on `user_id`
- `idx_password_reset_tokens_expires_at` — on `expires_at`

**Domain class**: `com.worklog.domain.password.PasswordResetToken`
- Token minimum length: 32 characters (validated in constructor)
- Factory method: `PasswordResetToken.create(userId, token, validityMinutes)` — uses `Instant.now()` + `validityMinutes * 60` seconds
- State transitions: created (used=false, usedAt=null) → used (used=true, usedAt=Instant.now())

### User (referenced entity)

**Table**: `users` (Migration: `V11__user_auth.sql`)

| Key Column       | Type    | Notes                                  |
|------------------|---------|----------------------------------------|
| id               | UUID    | PK                                     |
| email            | VARCHAR | UNIQUE, used for password reset lookup |
| hashed_password  | VARCHAR | Updated on password reset              |
| role_id          | UUID    | FK → roles(id), needed for test setup  |

### Repository Methods Under Test

| Method                            | SQL Operation                                        | Key Behavior                         |
|-----------------------------------|------------------------------------------------------|--------------------------------------|
| `save(token)`                     | INSERT INTO password_reset_tokens                    | Fails on duplicate id/token          |
| `findByToken(token)`              | SELECT WHERE token = ?                               | Returns any matching token           |
| `findValidByToken(token)`         | SELECT WHERE token = ? AND used_at IS NULL AND expires_at > NOW() | Only unused + unexpired     |
| `markAsUsed(tokenId)`             | UPDATE SET used_at = NOW() WHERE id = ?              | Sets used_at timestamp               |
| `invalidateUnusedTokensForUser(userId)` | UPDATE SET used_at = NOW() WHERE user_id = ? AND used_at IS NULL | Bulk invalidation          |
| `deleteExpired()`                 | DELETE WHERE expires_at < NOW()                      | Removes expired tokens               |

### Validation Rules (Controller Layer)

| Command                         | Field        | Validation                | Annotation            |
|---------------------------------|--------------|---------------------------|-----------------------|
| PasswordResetRequestCommand     | email        | Not blank, valid email    | @NotBlank @Email      |
| PasswordResetConfirmCommand     | token        | Not blank                 | @NotBlank             |
| PasswordResetConfirmCommand     | newPassword  | Not blank, min 8 chars    | @NotBlank @Size(min=8)|

### API Endpoints

| Method | Path                              | Request Body                              | Success Response |
|--------|-----------------------------------|-------------------------------------------|-----------------|
| POST   | /api/v1/auth/password-reset/request | `{ "email": "..." }`                     | 200 + message    |
| POST   | /api/v1/auth/password-reset/confirm | `{ "token": "...", "newPassword": "..." }` | 200 + message  |

### Test Data Dependencies

Tests require the following prerequisite data:

1. **Role**: At least one role must exist in `roles` table (for user creation)
2. **User**: Created per-test with `User.create(email, name, hashedPassword, roleId)`
3. **Token**: Created via `PasswordResetToken.create(userId, tokenString, validityMinutes)` — tokenString must be ≥32 characters
