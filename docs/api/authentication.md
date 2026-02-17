# Authentication API Reference

Base URL: `/api/v1/auth`

All authentication endpoints accept and return `application/json`.

---

## Endpoints Overview

| Method | Endpoint | Auth Required | Description |
|--------|----------|:---:|-------------|
| POST | `/signup` | No | Register new account |
| POST | `/login` | No | Authenticate user |
| POST | `/logout` | Yes | Invalidate session |
| POST | `/verify-email` | No | Verify email address |
| POST | `/password-reset/request` | No | Request password reset |
| POST | `/password-reset/confirm` | No | Confirm password reset |

---

## POST /signup

Register a new user account. Sends a verification email to the provided address.

### Request

```json
{
  "email": "user@example.com",
  "name": "John Doe",
  "password": "Password1"
}
```

| Field | Type | Required | Constraints |
|-------|------|:---:|-------------|
| `email` | string | Yes | Valid email format |
| `name` | string | Yes | Non-blank |
| `password` | string | Yes | Min 8 chars, 1 lowercase, 1 uppercase, 1 digit |

### Response — 201 Created

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "name": "John Doe",
  "accountStatus": "UNVERIFIED",
  "message": "Account created successfully. Please check your email to verify your account."
}
```

### Errors

| Status | Error Code | Cause |
|--------|------------|-------|
| 400 | `validation_error` | Missing or invalid fields, weak password |
| 409 | `duplicate_email` | Email already registered |

### Example

```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "name": "John Doe", "password": "Password1"}'
```

---

## POST /login

Authenticate user and create an HTTP session. Optionally issues a remember-me token.

### Request

```json
{
  "email": "user@example.com",
  "password": "Password1",
  "rememberMe": false
}
```

| Field | Type | Required | Description |
|-------|------|:---:|-------------|
| `email` | string | Yes | Registered email |
| `password` | string | Yes | Account password |
| `rememberMe` | boolean | Yes | Issue persistent remember-me token (30-day validity) |

### Response — 200 OK

```json
{
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "name": "John Doe",
    "accountStatus": "ACTIVE"
  },
  "sessionExpiresAt": "2026-02-17T11:30:00Z",
  "rememberMeToken": null,
  "warning": null
}
```

### Errors

| Status | Error Code | Cause |
|--------|------------|-------|
| 401 | `INVALID_CREDENTIALS` | Wrong email or password |
| 401 | `ACCOUNT_LOCKED` | Too many failed attempts (5 failures = 15-minute lock) |

### Side Effects

- Previous HTTP session invalidated (session fixation prevention)
- New session created with 30-minute timeout
- `last_login_at` updated on user record
- Failed login attempts tracked; account locked after 5 consecutive failures

### Example

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "Password1", "rememberMe": false}'
```

---

## POST /logout

Invalidate the current HTTP session.

### Request

No request body required.

### Response — 204 No Content

Empty response body.

### Example

```bash
curl -X POST http://localhost:8080/api/v1/auth/logout
```

---

## POST /verify-email

Verify user's email address using the token sent during signup.

### Request

```json
{
  "token": "abc123..."
}
```

| Field | Type | Required | Description |
|-------|------|:---:|-------------|
| `token` | string | Yes | Verification token from email |

### Response — 200 OK

```json
{
  "message": "Email verified successfully. Your account is now active."
}
```

### Errors

| Status | Error Code | Cause |
|--------|------------|-------|
| 400 | `validation_error` | Token is blank or missing |
| 404 | `INVALID_TOKEN` | Token invalid, expired, or already used |

### Side Effects

- User `account_status` changed from `UNVERIFIED` to `ACTIVE`
- `email_verified_at` timestamp set

### Example

```bash
curl -X POST http://localhost:8080/api/v1/auth/verify-email \
  -H "Content-Type: application/json" \
  -d '{"token": "abc123..."}'
```

---

## POST /password-reset/request

Request a password reset email. **Always returns 200 OK** regardless of whether the email exists (anti-enumeration security measure).

### Request

```json
{
  "email": "user@example.com"
}
```

| Field | Type | Required | Constraints |
|-------|------|:---:|-------------|
| `email` | string | Yes | `@NotBlank`, `@Email` |

### Response — 200 OK

```json
{
  "message": "If the email exists, a password reset link has been sent."
}
```

### Side Effects (when email exists)

- All previous unused reset tokens for the user are invalidated
- New token generated (32-byte secure random, Base64 URL-encoded)
- Token valid for 24 hours
- Password reset email sent with link to frontend reset page

### Example

```bash
curl -X POST http://localhost:8080/api/v1/auth/password-reset/request \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com"}'
```

---

## POST /password-reset/confirm

Reset password using a valid token. Invalidates all existing sessions for the user.

### Request

```json
{
  "token": "dGhpcyBpcyBhIHRlc3Q...",
  "newPassword": "NewPassword1"
}
```

| Field | Type | Required | Constraints |
|-------|------|:---:|-------------|
| `token` | string | Yes | `@NotBlank`, must be valid and unused |
| `newPassword` | string | Yes | `@NotBlank`, `@Size(min=8)`, must contain 1 lowercase, 1 uppercase, 1 digit |

### Response — 200 OK

```json
{
  "message": "Password reset successfully. You may now log in with your new password."
}
```

### Errors

| Status | Error Code | Cause |
|--------|------------|-------|
| 400 | `validation_error` | Blank fields, password too weak |
| 404 | `INVALID_TOKEN` | Token invalid, expired, or already used |

### Side Effects

- Password updated (BCrypt hashed)
- Token marked as used
- All user sessions deleted (forces re-login on all devices)

### Example

```bash
curl -X POST http://localhost:8080/api/v1/auth/password-reset/confirm \
  -H "Content-Type: application/json" \
  -d '{"token": "dGhpcyBpcyBhIHRlc3Q...", "newPassword": "NewPassword1"}'
```

---

## Error Response Format

All errors follow a consistent format:

```json
{
  "errorCode": "INVALID_CREDENTIALS",
  "message": "Invalid email or password",
  "timestamp": "2026-02-17T10:30:00Z",
  "details": {}
}
```

| Field | Type | Description |
|-------|------|-------------|
| `errorCode` | string | Machine-readable error code |
| `message` | string | Human-readable description |
| `timestamp` | string | ISO 8601 timestamp |
| `details` | object | Additional context (usually empty) |

---

## Rate Limiting

Authentication endpoints have stricter rate limits to prevent brute force attacks:

| Setting | Default | Description |
|---------|---------|-------------|
| `auth-requests-per-second` | 3 | Max requests/sec for auth endpoints |
| `auth-burst-size` | 5 | Burst allowance for auth endpoints |

When rate limited, the server returns `429 Too Many Requests`.

Rate limiting can be configured via environment variables:
- `AUTH_RATE_LIMIT_RPS` — Requests per second (default: 3)
- `AUTH_RATE_LIMIT_BURST` — Burst size (default: 5)

---

## Password Requirements

Passwords must meet all of the following:
- Minimum 8 characters
- At least 1 lowercase letter (`a-z`)
- At least 1 uppercase letter (`A-Z`)
- At least 1 digit (`0-9`)

Validation is enforced on both signup and password reset confirm endpoints.

---

## Testing with MailHog

In development, emails are captured by MailHog (SMTP on port 1025, UI on port 8025).

```bash
# Start MailHog
docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog

# View captured emails
open http://localhost:8025
```

After calling `/signup` or `/password-reset/request`, check MailHog to retrieve verification/reset tokens.

---

## Source Files

| Component | File |
|-----------|------|
| Controller | `backend/src/main/java/com/worklog/api/AuthController.java` |
| Auth Service | `backend/src/main/java/com/worklog/application/auth/AuthService.java` |
| Password Reset Service | `backend/src/main/java/com/worklog/application/password/PasswordResetService.java` |
| Password Validator | `backend/src/main/java/com/worklog/application/validation/PasswordValidator.java` |
| Email Service | `backend/src/main/java/com/worklog/infrastructure/email/EmailServiceImpl.java` |
| Security Config | `backend/src/main/java/com/worklog/infrastructure/config/SecurityConfig.kt` |
