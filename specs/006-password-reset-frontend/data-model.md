# Data Model: Password Reset Frontend

**Date**: 2026-02-16
**Feature**: 006-password-reset-frontend

## Overview

This document describes the frontend state model and TypeScript types used by the password reset feature. All types are defined in `frontend/app/lib/types/password-reset.ts`.

## Entities

### ValidationError

Represents a field-level validation error with categorized type.

| Field | Type | Description |
|-------|------|-------------|
| field | string | Name of the form field with the error |
| message | string | User-facing error message (Japanese) |
| type | `"required" \| "format" \| "mismatch" \| "length" \| "strength"` | Error category |

**Usage**: Returned by validation functions in `lib/validation/password.ts`. Displayed inline near form fields.

### PasswordStrengthResult

Result of zxcvbn password strength analysis.

| Field | Type | Description |
|-------|------|-------------|
| strength | `"weak" \| "medium" \| "strong"` | Overall strength level |
| score | number (0–4) | Raw zxcvbn score |
| feedback | string[] | Improvement suggestions (Japanese) |
| crackTimeDisplay | string | Human-readable crack time estimate |

**Usage**: Computed by `analyzePasswordStrength()`, consumed by `PasswordStrengthIndicator` component via `onChange` callback.

### RateLimitState

Client-side rate limiting state for the request page.

| Field | Type | Description |
|-------|------|-------------|
| attempts | number[] | Timestamps of recent attempts (within sliding window) |
| isAllowed | boolean | Whether the user can make another request |
| remainingAttempts | number | Requests remaining before limit hit |
| resetTime | number \| null | Epoch ms when rate limit resets (null if not limited) |

**Configuration**: 3 requests per 5-minute sliding window. Persisted in localStorage key `"password_reset_rate_limit"`. Synchronized across browser tabs via Storage Event API.

### ErrorState

Page-level error state for API and system errors.

| Field | Type | Description |
|-------|------|-------------|
| type | `"network" \| "validation" \| "expired_token" \| "rate_limit" \| "server" \| null` | Error category |
| message | string | User-facing error message (Japanese) |
| isRetryable | boolean | Whether the user can retry the action |
| errorCode | string? | Optional backend error code |

**Usage**: Set after API call failures. Determines UI presentation (retry button shown if `isRetryable`).

## State Diagrams

### Request Page State Flow

```
[Initial] → user enters email → [Email Entered]
  → submit → [Loading]
    → API success → [Success Message] (terminal: shows "check email")
    → API error (network) → [Error: retryable]
    → Rate limit hit → [Error: rate limited]
  → invalid email format → [Validation Error] → user corrects → [Email Entered]
```

### Confirm Page State Flow

```
[Loading: Token Extraction] → token found in URL/sessionStorage → [Form Ready]
                            → no token found → [Error: missing token] (terminal)
[Form Ready] → user fills passwords → [Passwords Entered]
  → submit → [Loading: Submission]
    → API success → [Success + Countdown] → 3 seconds → [Redirect to Login]
    → API 404 → [Error: expired/invalid token]
    → API 400 → [Error: validation]
    → Network error → [Error: retryable]
  → client validation fails → [Validation Errors] → user corrects → [Passwords Entered]
```

## Validation Schemas (Zod)

### passwordResetRequestSchema

```
email: string, min 1 char, valid email format
```

### passwordResetConfirmSchema

```
token: string, min 1 char
newPassword: string, min 8 chars, max 128 chars
confirmPassword: string, min 1 char
+ refinement: newPassword === confirmPassword
```
