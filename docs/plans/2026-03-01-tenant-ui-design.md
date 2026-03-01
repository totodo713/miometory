# Tenant UI Design — Issue #50

## Overview

Add frontend UI for tenant-unaffiliated user waiting screens, multi-tenant selection, and admin tenant assignment. Depends on Phase 3 backend APIs (completed in commit 1b1187e).

## Architecture: Separate Context (Option B)

New `TenantProvider` manages tenant state independently from `AuthProvider`.

```
AuthProvider
  └─ TenantProvider (depends on AuthProvider user)
       └─ AuthGuard (uses both contexts)
            └─ children
```

## Data Model

```typescript
type TenantAffiliationState = "UNAFFILIATED" | "AFFILIATED_NO_ORG" | "FULLY_ASSIGNED";

interface TenantMembership {
  memberId: string;
  tenantId: string;
  tenantName: string;
  organizationId: string | null;
  organizationName: string | null;
}

interface TenantContext {
  affiliationState: TenantAffiliationState | null;
  memberships: TenantMembership[];
  selectedTenantId: string | null;
  selectedTenantName: string | null;
  isLoading: boolean;
  selectTenant: (tenantId: string) => Promise<void>;
  refreshStatus: () => Promise<void>;
}
```

## API Client Additions

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `userStatus.getStatus()` | `GET /api/v1/user/status` | Get tenant affiliation state and memberships |
| `userStatus.selectTenant(tenantId)` | `POST /api/v1/user/select-tenant` | Select active tenant |
| `admin.users.searchForAssignment(email)` | `GET /api/v1/admin/users/search-for-assignment?email=` | Search unassigned users |
| `admin.members.assignTenant(userId, displayName)` | `POST /api/v1/admin/members/assign-tenant` | Assign user to tenant |

## Routing

### AuthGuard State Machine

| State | Redirect |
|-------|----------|
| Not authenticated | `/login` |
| UNAFFILIATED | `/waiting` |
| AFFILIATED_NO_ORG | `/pending-organization` |
| FULLY_ASSIGNED + multi-tenant + not selected | `/select-tenant` |
| FULLY_ASSIGNED + selected | Render children |

### Screen Flow

```
Login success
  ├─ UNAFFILIATED → /waiting (30s polling)
  ├─ AFFILIATED_NO_ORG → /pending-organization (30s polling)
  └─ FULLY_ASSIGNED
       ├─ Multi-tenant + not selected → /select-tenant
       └─ Single or selected → /worklog
```

## New Pages

### `/waiting` — Tenant Waiting Screen
- Message: "Please wait for an administrator to add you to a tenant"
- 30-second polling via `GET /api/v1/user/status`
- State change triggers TenantProvider update → AuthGuard auto-redirects
- Logout button available

### `/pending-organization` — Organization Pending Screen
- Message: "You have been added to a tenant. Waiting for organization assignment."
- 30-second polling, same mechanism as waiting screen

### `/select-tenant` — Tenant Selection Screen
- Displayed only for multi-tenant users (single tenant auto-selected by backend)
- Card/list layout showing tenant name and organization name
- Selection calls `POST /api/v1/user/select-tenant` → redirects to `/worklog`

## Header Tenant Switcher

- Visible only for users with multiple tenant memberships
- Shows current tenant name in header
- Dropdown to switch to another tenant
- Switch calls `selectTenant()` → page refresh (AdminContext etc. need re-fetching)

## Admin: Tenant Assignment UI (`/admin/members`)

- "Assign User to Tenant" button at top of member list
- Permission: `member.assign_tenant` required
- Modal dialog flow:
  1. Email search input (partial match)
  2. Results list with "already in tenant" label
  3. Select user → enter display name → execute assignment

## i18n Keys

New namespaces in `en.json` / `ja.json`:
- `waiting.*` — Waiting screen messages
- `pendingOrganization.*` — Pending organization messages
- `selectTenant.*` — Tenant selection messages
- `admin.members.assignTenant.*` — Assignment dialog messages
- `header.tenantSwitcher.*` — Header tenant switcher

## Login Flow Changes

- `login/page.tsx`: After login, check `tenantAffiliationState` from response and redirect accordingly (instead of always → `/worklog`)
- `app/page.tsx`: Use TenantProvider state for redirect logic

## Testing

### Unit Tests (Vitest + RTL)
- TenantProvider: state management, selectTenant, polling
- AuthGuard: 3-state routing + tenant not-selected redirect
- /waiting: polling behavior, state change transition
- /pending-organization: polling behavior, state change transition
- /select-tenant: tenant list rendering, selection operation
- Assignment dialog: email search, results, assignment execution
- Header tenant switcher: dropdown display, selection

### E2E Tests (Playwright)
- Login → state-based screen transitions
- Admin tenant assignment flow

## Decisions

- **Approach B (Separate Context)**: TenantProvider separated from AuthProvider for clean separation of concerns
- **Waiting screen**: Simple message + polling only (no request-to-admin feature)
- **Tenant switching**: Available from header at any time (not just at login)
- **Assignment UI location**: `/admin/members` per issue specification
