# E2E Test: User Registration → Tenant Assign → Worklog Flow

**Issue:** #51
**Date:** 2026-03-02
**Approach:** User-side UI + Admin-side API (Hybrid)

## Overview

Add an E2E test covering the full user lifecycle: signup → email verification → login → waiting screen → tenant assignment → org assignment → worklog access → multi-tenant selection.

## Infrastructure Changes

### Mailpit (Email Interception)

Add Mailpit to `docker-compose.dev.yml`:

```yaml
mailpit:
  image: axllent/mailpit:latest
  ports:
    - "8025:8025"   # Web UI & REST API
    - "1025:1025"   # SMTP
  environment:
    MP_SMTP_AUTH_ACCEPT_ANY: 1
    MP_SMTP_AUTH_ALLOW_INSECURE: 1
```

Backend already sends SMTP to `localhost:1025` (configured in `application.yaml`).

### Playwright Helper: `tests/e2e/helpers/mailpit.ts`

```typescript
const MAILPIT_API = "http://localhost:8025/api/v1";

getLatestEmail(to: string): Promise<MailpitMessage>
extractVerificationLink(email: MailpitMessage): Promise<string>
clearMailbox(): Promise<void>
```

## Test File

**Location:** `frontend/tests/e2e/scenarios/user-registration-flow.spec.ts`
**Pattern:** `test.describe.serial()` — same as `tenant-onboarding.spec.ts`

## Test Steps

### Step 1: Signup

- Navigate to `/signup`
- Fill form: unique email (`e2e-user-${runId}@test.example.com`), password, name
- Submit → verify redirect to `/signup/confirm`
- Verify confirmation message displayed

### Step 2: Email Verification

- Query Mailpit API for verification email
- Extract verification link (`/verify-email?token=xxx`)
- Navigate to the verification URL
- Verify success message displayed

### Step 3: Login → Waiting Screen

- Navigate to `/login`, enter credentials
- Verify redirect to `/waiting` (affiliationState = UNAFFILIATED)
- Verify waiting screen UI elements (message, logout button)

### Step 4: Admin Assigns Tenant (API)

- Call `POST /api/v1/admin/members/assign-tenant` with sysadmin auth
- User reloads page → verify redirect to `/pending-organization`
- Verify pending organization screen UI

### Step 5: Admin Assigns Organization (API)

- Call `PUT /api/v1/admin/members/{memberId}/organization` with sysadmin auth
- User reloads page → verify redirect to `/worklog`

### Step 6: Worklog Access Verification

- Verify calendar is displayed
- Click a date → verify entry form appears
- Confirm basic worklog functionality is accessible

### Step 7: Multi-Tenant Setup (API)

- Assign user to ACME tenant (`550e8400-e29b-41d4-a716-446655440002`) via API
- Assign organization within ACME tenant via API

### Step 8: Multi-Tenant Selection

- User logs out → re-login
- Verify redirect to `/select-tenant`
- Verify both tenants listed (Miometry Corporation, ACME Corporation)
- Select a tenant → verify redirect to `/worklog`

## Test Data

```
NEW_USER:
  email: e2e-user-${runId}@test.example.com
  password: TestPassword1!
  name: E2E User ${runId}

ADMIN:
  email: sysadmin@miometry.example.com
  password: Password1

TENANT_1 (existing seed): Miometry Corporation (550e8400-...-440001)
TENANT_2 (existing seed): ACME Corporation (550e8400-...-440002)
ORG_1 (existing seed): Engineering Department (880e8400-...-440001)
```

## Design Decisions

1. **User operations via UI, admin operations via API** — focuses test on user experience; admin UI already tested in `tenant-onboarding.spec.ts`
2. **Mailpit for email interception** — lightweight, maintained, REST API for Playwright integration
3. **Page reload for state transitions** — simpler than waiting for 30s polling; tests the redirect logic
4. **Existing seed data for tenants/orgs** — avoids complex setup; ACME tenant already exists for multi-tenant testing
5. **Single serial test file** — steps share state (user ID, member ID, session); matches existing `tenant-onboarding.spec.ts` pattern
