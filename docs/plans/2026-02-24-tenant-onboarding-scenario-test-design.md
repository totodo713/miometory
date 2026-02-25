# Tenant Onboarding Scenario Test Design

## Overview

Create an end-to-end scenario test that validates the complete workflow from new tenant creation through the first monthly approval cycle. This test uses real backend integration (no API mocking) with Playwright, exercising GUI operations as actual users would.

## Approach

**Serial Test** using Playwright's `test.describe.serial()` — each test step runs sequentially, inheriting state from previous steps. A single file contains all 15 steps organized into 3 phases.

## Test Organization

### File Location

```
frontend/tests/e2e/scenarios/
  tenant-onboarding.spec.ts
```

Scenario tests are placed in a `scenarios/` subdirectory to distinguish them from existing feature-level mock-based E2E tests.

### Personas

| Persona | Role | Scope | Created By |
|---|---|---|---|
| **sys-admin** | ADMIN | Tenant creation, tenant owner invitation | Test seed data (pre-existing) |
| **tenant-owner-1** | TENANT_ADMIN | Org A creation, pattern setup, admin invitation | sys-admin invites during test |
| **tenant-owner-2** | TENANT_ADMIN | Org B creation, pattern setup, admin invitation | sys-admin invites during test |
| **org-a-admin-1** | MODERATOR | Invite 3 members, assign manager | tenant-owner-1 invites during test |
| **org-a-admin-2** | MODERATOR | Secondary org A admin | tenant-owner-1 invites during test |
| **org-b-admin-1** | MODERATOR | Invite 3 members, assign manager | tenant-owner-2 invites during test |
| **org-b-admin-2** | MODERATOR | Secondary org B admin | tenant-owner-2 invites during test |
| **org-a-member-1~3** | USER | Work log entry, monthly submission | org-a-admin-1 invites during test |
| **org-b-member-1~3** | USER | Work log entry (proxy-entered by admin) | org-b-admin-1 invites during test |

### Fiscal Configuration

- **Fiscal year pattern**: January 1st start (calendar year)
- **Monthly period pattern**: 1st day start (end-of-month closing)

## Test Steps

### Phase 1: Organization Setup (Steps 1-9)

| # | Test Name | Actor | Action | Verification |
|---|---|---|---|---|
| 1 | Create tenant | sys-admin | Create tenant via `/admin/tenants` | Tenant appears in list, status ACTIVE |
| 2 | Configure fiscal patterns | sys-admin | Create fiscal year pattern (Jan start) and monthly period pattern (1st day) | Patterns created and selectable |
| 3 | Bootstrap orgs + invite tenant owners | sys-admin | Create Org A & B via API, assign patterns, invite tenant-owner-1, 2, assign TENANT_ADMIN role | Orgs created with patterns, tenant owners have temp passwords |
| 4 | Tenant owner 1 verifies Org A | tenant-owner-1 | Login with temp password -> Navigate to `/admin/organizations` -> Verify Org A and patterns | Organization visible with patterns linked |
| 5 | Tenant owner 2 verifies Org B | tenant-owner-2 | Same as above for Org B | Same verification |
| 6 | Invite Org A admins | tenant-owner-1 | Invite org-a-admin-1, 2 as MODERATOR via `/admin/members` | Invitation successful |
| 7 | Invite Org B admins | tenant-owner-2 | Invite org-b-admin-1, 2 as MODERATOR | Invitation successful |
| 8 | Org A: Invite members + assign manager | org-a-admin-1 | Login -> Invite 3 members -> Assign org-a-admin-1 as manager | Members in list, manager linked |
| 9 | Org B: Invite members + assign manager | org-b-admin-1 | Login -> Invite 3 members -> Assign org-b-admin-1 as manager | Members in list, manager linked |

### Phase 2: Work Log Entry (Steps 10-11)

| # | Test Name | Actor | Action | Verification |
|---|---|---|---|---|
| 10 | Org A: Member self-entry | org-a-member-1 | Login -> Enter work log for several days in current month via `/worklog` | Entries saved, calendar reflects data |
| 11 | Org B: Manager proxy entry | org-b-admin-1 | Login -> Enable proxy mode -> Enter work log for org-b-member-1 | Proxy banner shown, entries saved under member-1 |

### Phase 3: Monthly Approval (Steps 12-15)

| # | Test Name | Actor | Action | Verification |
|---|---|---|---|---|
| 12 | Org A: Member submits for approval | org-a-member-1 | Click "Submit for Approval" on `/worklog` | Status SUBMITTED, inputs become read-only |
| 13 | Org A: Manager approves | org-a-admin-1 | Check approval queue via `/worklog/approval` -> Approve | Status APPROVED, permanently locked |
| 14 | Org B: Manager proxy-submits | org-b-admin-1 | Submit org-b-member-1's month via proxy mode | Status SUBMITTED |
| 15 | Org B: Different admin approves | org-b-admin-2 | Approve from approval queue | Status APPROVED |

## Technical Implementation

### Authentication Helper

Dynamic login helper for users created during test execution:

```typescript
async function loginAs(page: Page, email: string, password: string): Promise<AuthUser> {
  const response = await page.request.post(`${API_BASE_URL}/api/v1/auth/login`, {
    data: { email, password, rememberMe: false },
  });
  if (!response.ok()) {
    throw new Error(`Login failed for ${email}: ${response.status()}`);
  }
  const body = await response.json();
  const authUser = { id: body.user.id, email: body.user.email, displayName: body.user.name };

  await page.addInitScript((user) => {
    window.sessionStorage.setItem("miometry_auth_user", JSON.stringify(user));
  }, authUser);

  return authUser;
}
```

### State Sharing Between Steps

Closure variables within `test.describe.serial()` scope:

```typescript
test.describe.serial("Tenant Onboarding Scenario", () => {
  let tenantId: string;
  let orgAId: string;
  let orgBId: string;
  let tenantOwner1: { email: string; password: string; id?: string };
  let tenantOwner2: { email: string; password: string; id?: string };
  // ... more shared state
});
```

### Prerequisites

- **sys-admin**: Pre-existing in test seed data (`R__test_seed_data.sql`)
- **All other users**: Created via GUI during test execution
- **Backend**: Running with test profile (`./gradlew bootRun --args='--spring.profiles.active=test'`)
- **Database**: PostgreSQL via Testcontainers or Docker Compose
- **External services**: MailHog for email (already in docker-compose)

### No API Mocking

- All requests go to the real backend
- No `page.route()` interception
- Only exception: external services use test doubles (MailHog for email)

### Cleanup

- Test containers are ephemeral — no explicit cleanup needed
- Each test run starts with a fresh database state (migrations + seed data)

### Estimated Execution Time

- Per step: 10-30 seconds
- Total (15 steps): ~3-7 minutes
- Playwright timeout: 60 seconds per step (sufficient)

## Test Configuration

The scenario test may require a separate Playwright project configuration to ensure:
- Serial execution (no parallelism)
- Longer timeout if needed
- Backend + frontend both running

## Constraints

- Test must be deterministic — no reliance on time-dependent behavior
- Use fixed dates where possible for work log entries
- All assertions should be explicit (no implicit waits beyond Playwright's auto-waiting)
