# API Contracts: Password Reset Frontend

**Date**: 2026-02-16
**Feature**: 006-password-reset-frontend

## Overview

The frontend consumes two backend REST endpoints for password reset. Both endpoints are already implemented (PR #3) and integrated into the frontend API client (`frontend/app/services/api.ts`). Both use `skipAuth: true` (no authentication required).

## Endpoints

### POST /api/v1/auth/password-reset/request

**Purpose**: Request a password reset email.

**Request**:
```json
{
  "email": "user@example.com"
}
```

**Responses**:

| Status | Body | Condition |
|--------|------|-----------|
| 200 | `{ "message": "..." }` | Always returned (anti-enumeration) — whether email exists or not |

**Frontend behavior**: Always display success message. Never distinguish between existing and non-existing emails.

---

### POST /api/v1/auth/password-reset/confirm

**Purpose**: Confirm password reset with token and new password.

**Request**:
```json
{
  "token": "reset-token-from-email",
  "newPassword": "NewSecurePassword123"
}
```

**Responses**:

| Status | Body | Condition |
|--------|------|-----------|
| 200 | `{ "message": "..." }` | Password reset successful |
| 400 | `{ "message": "...", "code": "..." }` | Validation error (e.g., weak password) |
| 404 | `{ "message": "...", "code": "..." }` | Invalid or expired token |

**Frontend behavior**:
- 200: Show success message with 3-second countdown, then redirect to `/login`
- 400: Map to `ErrorState { type: "validation", isRetryable: true }`
- 404: Map to `ErrorState { type: "expired_token", isRetryable: false }`, show link to request new reset

## Error Handling

The API client (`ApiError` class) extracts `message`, `status`, and `code` from error responses. The frontend pages map these to typed `ErrorState` objects that determine:
- Error message displayed to user (Japanese)
- Whether a retry button is shown (`isRetryable`)
- Whether a "request new reset" link is shown (expired token)

## Security Notes

- CSRF protection: API client automatically includes `X-XSRF-TOKEN` header
- Token in URL: Frontend removes token from URL via `router.replace()` after extraction
- Token backup: Stored in sessionStorage to survive page refreshes during the flow
- Credentials: `credentials: "include"` for session cookie handling
