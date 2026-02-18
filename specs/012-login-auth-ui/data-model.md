# Data Model: Login Page Design, Auth Integration & Logout

**Feature**: 012-login-auth-ui | **Date**: 2026-02-18

## Entities

### User (Authentication) — Existing

The `users` table already exists (V11 migration). This feature adds **seed data** only; no schema changes.

| Attribute          | Type         | Constraints                              | Notes                        |
|--------------------|--------------|------------------------------------------|------------------------------|
| id                 | UUID (PK)    | NOT NULL, matches members.id             | Shared identity with Member  |
| email              | VARCHAR(255) | NOT NULL, UNIQUE                         | Login identifier             |
| hashed_password    | VARCHAR(255) | NOT NULL                                 | BCrypt encoded               |
| name               | VARCHAR(255) | NOT NULL                                 | Display name                 |
| role_id            | UUID (FK)    | NOT NULL, references roles.id            | ADMIN or USER                |
| account_status     | VARCHAR(50)  | CHECK: active, unverified, locked, deleted | Must be 'active' for login  |
| failed_login_attempts | INTEGER   | 0-10                                     | Backend brute-force protection |
| locked_until       | TIMESTAMP    | nullable                                 | Account lock expiry          |
| email_verified_at  | TIMESTAMP    | nullable                                 | Must be set for login to work |
| last_login_at      | TIMESTAMP    | nullable                                 | Updated on successful login  |

### Session (Server-Side) — Existing

Managed by Spring Security. No schema changes needed.

| Attribute       | Type         | Notes                          |
|-----------------|--------------|--------------------------------|
| session_id      | VARCHAR(255) | Spring HttpSession ID          |
| user_id         | UUID (FK)    | Set in session attributes      |
| expires_at      | TIMESTAMP    | 30-minute sliding window       |

### AuthUser (Frontend State) — New

Client-side representation stored in React Context and sessionStorage. Not a database entity.

| Attribute    | Type   | Source                        |
|--------------|--------|-------------------------------|
| id           | string | LoginResponse.user.id         |
| email        | string | LoginResponse.user.email      |
| displayName  | string | LoginResponse.user.name       |

## Relationships

```
User (auth) 1:1 Member (worklog)  — shared UUID
User 1:N Session                  — one active session per browser
User N:1 Role                     — ADMIN, USER, MODERATOR
```

## State Transitions

### User Authentication State (Frontend)

```
[Not Authenticated] --login()--> [Authenticated]
[Authenticated] --logout()--> [Not Authenticated]
[Authenticated] --session timeout--> [Not Authenticated]
[Authenticated] --401 response--> [Not Authenticated]
[Page Load] --sessionStorage has user--> [Authenticated]
[Page Load] --sessionStorage empty--> [Not Authenticated]
```

## Seed Data (Development)

4 user records matching existing members, all with password `Password1`:

| UUID (suffix) | Email                                    | Name              | Role  |
|---------------|------------------------------------------|-------------------|-------|
| ...0001       | bob.engineer@miometry.example.com        | Bob Engineer      | USER  |
| ...0002       | alice.manager@miometry.example.com       | Alice Manager     | ADMIN |
| ...0003       | charlie.engineer@miometry.example.com    | Charlie Engineer  | USER  |
| ...0004       | david.independent@miometry.example.com   | David Independent | USER  |
