**Note**: Development now uses devcontainer. See [QUICKSTART.md](/QUICKSTART.md) for current setup instructions.

# Quickstart: Login Page Design, Auth Integration & Logout

**Feature**: 012-login-auth-ui | **Date**: 2026-02-18

## Prerequisites

- Docker running (for PostgreSQL via docker-compose)
- Node.js 20+ and npm installed
- Java 21+ and Gradle installed

## Setup

### 1. Start database

```bash
cd infra/docker && docker-compose -f docker-compose.dev.yml up -d
```

### 2. Start backend (loads seed data including new user records)

```bash
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
```

### 3. Start frontend

```bash
cd frontend && npm install && npm run dev
```

## Test Users

All development users share password: `Password1`

| User              | Email                                    | Role    |
|-------------------|------------------------------------------|---------|
| Bob Engineer      | bob.engineer@miometry.example.com        | USER    |
| Alice Manager     | alice.manager@miometry.example.com       | ADMIN   |
| Charlie Engineer  | charlie.engineer@miometry.example.com    | USER    |
| David Independent | david.independent@miometry.example.com   | USER    |

## Verification Steps

1. Open http://localhost:3000 → redirected to `/login`
2. See styled login page with Miometry branding
3. Login with `bob.engineer@miometry.example.com` / `Password1`
4. Redirected to `/worklog` dashboard
5. Header shows "Bob Engineer" and "ログアウト" button
6. Click "ログアウト" → redirected to `/login`
7. Try accessing http://localhost:3000/worklog directly → redirected to `/login`
8. Login with wrong password → Japanese error message shown

## Running Tests

```bash
# Frontend unit tests
cd frontend && npm test -- --run

# Frontend lint check
cd frontend && npm run check:ci

# Frontend build verification
cd frontend && npm run build
```

## Key Files

| File | Purpose |
|------|---------|
| `frontend/app/(auth)/login/page.tsx` | Styled login page |
| `frontend/app/providers/AuthProvider.tsx` | Auth state management |
| `frontend/app/components/shared/Header.tsx` | Global header with logout |
| `frontend/app/components/shared/AuthGuard.tsx` | Route protection |
| `frontend/app/services/api.ts` | Login/logout API methods |
| `backend/.../R__dev_seed_data.sql` | Dev user records |
