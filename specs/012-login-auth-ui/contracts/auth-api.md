# API Contract: Authentication

**Feature**: 012-login-auth-ui | **Date**: 2026-02-18

These endpoints already exist in the backend (`AuthController.java`). This contract documents the request/response shapes that the frontend must consume.

## POST /api/v1/auth/login

Authenticates a user with email and password. Creates an HTTP session.

### Request

```json
{
  "email": "bob.engineer@miometry.example.com",
  "password": "Password1",
  "rememberMe": false
}
```

| Field      | Type    | Required | Notes                           |
|------------|---------|----------|---------------------------------|
| email      | string  | yes      | Case-insensitive (normalized)   |
| password   | string  | yes      | Plaintext, validated server-side |
| rememberMe | boolean | yes      | Persistent login token if true  |

### Response (200 OK)

```json
{
  "user": {
    "id": "00000000-0000-0000-0000-000000000001",
    "email": "bob.engineer@miometry.example.com",
    "name": "Bob Engineer",
    "accountStatus": "ACTIVE"
  },
  "sessionExpiresAt": "2026-02-18T10:30:00Z",
  "rememberMeToken": null,
  "warning": null
}
```

| Field             | Type        | Notes                                  |
|-------------------|-------------|----------------------------------------|
| user.id           | string/UUID | Maps to member ID for worklog operations |
| user.email        | string      | Normalized email                       |
| user.name         | string      | Display name for Header component      |
| user.accountStatus | string     | ACTIVE, UNVERIFIED, LOCKED, DELETED    |
| sessionExpiresAt  | string/ISO  | 30 minutes from now                    |
| rememberMeToken   | string/null | Non-null if rememberMe was true        |
| warning           | string/null | e.g., "Account not verified"           |

### Error Responses

| Status | Condition              | Body                                            |
|--------|------------------------|-------------------------------------------------|
| 401    | Invalid credentials    | `{ "message": "..." }`                          |
| 401    | Account locked         | `{ "message": "..." }`                          |
| 400    | Missing required field | `{ "message": "...", "errors": [...] }`         |
| 503    | Service unavailable    | `{ "message": "..." }`                          |

### Frontend Error Mapping

| Status | Japanese Message                                             |
|--------|--------------------------------------------------------------|
| 401    | メールアドレスまたはパスワードが正しくありません。           |
| 400    | 入力内容を確認してください。                                 |
| 503    | サーバーエラーが発生しました。しばらくしてから再試行してください。 |
| 0      | ネットワークエラーが発生しました。接続を確認してください。   |

## POST /api/v1/auth/logout

Invalidates the current HTTP session.

### Request

No request body. Session cookie sent automatically via `credentials: "include"`.

### Response (204 No Content)

No response body.

### Error Responses

| Status | Condition         | Notes                           |
|--------|-------------------|---------------------------------|
| (any)  | Session not found | Silently succeeds (idempotent)  |

Frontend should ignore errors from this endpoint and always clear local state.
