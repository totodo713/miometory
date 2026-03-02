# Profile API Design (GET/PUT /api/v1/profile)

**Issue**: #80
**Date**: 2026-03-02

## Overview

Backend API for the "My Page" feature. Authenticated users can view and update their own profile (display name, email, organization name, manager name) without going through the admin panel.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/profile` | Fetch authenticated user's profile with organization/manager names via JOIN |
| PUT | `/api/v1/profile` | Update display name and/or email (self only) |

## New Files

| File | Layer | Purpose |
|------|-------|---------|
| `ProfileController.java` | api | REST controller, no `@PreAuthorize` (self-only, `authenticated()` suffices) |
| `ProfileResponse.java` | api/dto | `id, email, displayName, organizationName, managerName, isActive` |
| `UpdateProfileRequest.java` | api/dto | `@NotBlank @Email email`, `@NotBlank @Size(max=100) displayName` |
| `ProfileService.java` | application/service | Business logic: JOIN query for GET, validation + dual-table update for PUT |

## Modified Files

| File | Change |
|------|--------|
| `SecurityConfig.kt` | Add `/api/v1/profile/**` to `.authenticated()` matcher |

## Data Flow

### GET /api/v1/profile

1. Extract email from `Authentication.getName()`
2. Single JOIN query: `members` LEFT JOIN `organizations` (name) LEFT JOIN `members` (manager display_name)
3. Return `ProfileResponse`

### PUT /api/v1/profile

1. Resolve member ID and tenant ID via `UserContextService`
2. Validate email uniqueness: tenant-scoped (members) + global (users)
3. Update `members` table (display name + email)
4. If email changed: update `users` table in same transaction + invalidate session via `SecurityContextLogoutHandler`
5. Response: `204 No Content` (no email change) or `200 OK` with `{ "emailChanged": true }` (email changed)

## Response DTOs

```java
public record ProfileResponse(
    UUID id, String email, String displayName,
    String organizationName, String managerName, boolean isActive) {}

public record UpdateProfileRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(max = 100) String displayName) {}

public record UpdateProfileResponse(boolean emailChanged) {}
```

## Error Handling

| Case | Error Code | HTTP Status |
|------|-----------|-------------|
| Member not found | `MEMBER_NOT_FOUND` | 404 |
| Duplicate email in tenant (members) | `DUPLICATE_EMAIL` | 409 |
| Duplicate email globally (users) | `DUPLICATE_EMAIL` | 409 |
| Validation failure | `VALIDATION_FAILED` | 400 |

## Security

- No URL-embedded ID (IDOR prevention): member resolved from authentication context
- Email uniqueness enforced at both tenant (members) and global (users) level
- Session invalidation on email change via `SecurityContextLogoutHandler` (cookie cleanup included)
- `SecurityConfig.kt`: `/api/v1/profile/**` added to `.authenticated()` block

## Dependencies

- `UserContextService` for member/tenant resolution
- `JdbcMemberRepository` for member CRUD
- `JdbcUserRepository` for user email sync
- `SecurityContextLogoutHandler` for session invalidation
