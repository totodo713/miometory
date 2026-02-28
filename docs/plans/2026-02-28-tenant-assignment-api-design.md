# Design: Tenant Assignment Service & User Status Endpoints

**Issue:** #48
**Date:** 2026-02-28
**Status:** Approved
**Depends on:** #47 (nullable organization_id — completed)

## Goal

Add tenant assignment functionality and user tenant status endpoints. This enables:
1. Authenticated users to check their own tenant affiliation status
2. Users to select a tenant when belonging to multiple tenants
3. Admins to search for users and assign them to their tenant

## Architecture: DDD Layer Separation (Approach C)

Domain logic (status enum, session state) lives in the domain layer. API coordination and orchestration live in the application layer. This separates pure business rules from infrastructure concerns.

## Domain Layer

### TenantAffiliationStatus enum (NEW)

**File:** `domain/member/TenantAffiliationStatus.java`

```java
public enum TenantAffiliationStatus {
    UNAFFILIATED,      // User exists in users table but has no member records
    AFFILIATED_NO_ORG, // Has member record(s) but all have organization_id=null
    FULLY_ASSIGNED     // Has at least one member record with organization_id set
}
```

**Determination logic:**
- memberships count = 0 -> UNAFFILIATED
- All memberships have org=null -> AFFILIATED_NO_ORG
- At least 1 membership has org set -> FULLY_ASSIGNED

### UserSession extension

**File:** `domain/session/UserSession.java`

Add `selectedTenantId` (nullable TenantId) field with:
- `selectTenant(TenantId)` — sets the selected tenant
- `getSelectedTenantId()` — returns current selection
- `hasSelectedTenant()` — null check

## Application Layer

### TenantAssignmentService (NEW)

**File:** `application/service/TenantAssignmentService.java`

Admin-facing tenant assignment operations.

| Method | Description |
|--------|-------------|
| `searchUsersForAssignment(String email, UUID tenantId)` | Search users table by email (partial match), return with `isAlreadyInTenant` flag |
| `assignUserToTenant(UUID userId, UUID tenantId, String displayName)` | Create Member via `Member.createForTenant()` with org=null. Throws DomainException if already in tenant |

**Dependencies:** UserRepository, MemberRepository
**Transaction:** Class-level `@Transactional`, read-only override for queries

### UserStatusService (NEW)

**File:** `application/service/UserStatusService.java`

Authenticated user's own status operations.

| Method | Description |
|--------|-------------|
| `getUserStatus(String email)` | Get User + all memberships across tenants, determine TenantAffiliationStatus |
| `selectTenant(String email, UUID tenantId, UUID sessionId)` | Validate membership exists for tenant, update UserSession.selectedTenantId |

**Dependencies:** UserRepository, MemberRepository, UserSessionRepository

## API Layer

### UserStatusController (NEW)

**File:** `api/UserStatusController.java`
**Base path:** `/api/v1/user`
**Auth:** `@PreAuthorize("isAuthenticated()")` — authentication only, no permission check

| Endpoint | Description |
|----------|-------------|
| `GET /api/v1/user/status` | Returns user status + memberships list |
| `POST /api/v1/user/select-tenant` | Body: `{ "tenantId": "uuid" }`. Saves selection to session |

**GET /api/v1/user/status response:**
```json
{
  "userId": "uuid",
  "email": "user@example.com",
  "state": "UNAFFILIATED",
  "memberships": [
    {
      "memberId": "uuid",
      "tenantId": "uuid",
      "tenantName": "Acme Corp",
      "organizationId": null,
      "organizationName": null
    }
  ]
}
```

### AdminMemberController extension

**File:** `api/AdminMemberController.java` (existing)

| Endpoint | Permission | Description |
|----------|------------|-------------|
| `GET /api/v1/admin/users/search-for-assignment?email=xxx` | `member.assign_tenant` | Search users system-wide by email |
| `POST /api/v1/admin/members/assign-tenant` | `member.assign_tenant` | Assign user to admin's tenant |

**GET search-for-assignment response:**
```json
{
  "users": [
    {
      "userId": "uuid",
      "email": "user@example.com",
      "name": "User Name",
      "isAlreadyInTenant": false
    }
  ]
}
```

**POST assign-tenant request:**
```json
{
  "userId": "uuid",
  "displayName": "Display Name"
}
```

### TenantStatusFilter modification

**File:** `infrastructure/config/TenantStatusFilter.kt`

Add `/api/v1/user/**` to the filter's excluded paths. This allows UNAFFILIATED users to access the status endpoint without triggering the tenant-active check.

## Infrastructure Layer

### DB Migration

**File:** `V28__user_session_selected_tenant.sql`

```sql
ALTER TABLE user_sessions ADD COLUMN selected_tenant_id UUID
    REFERENCES tenants(id);
```

### Repository Changes

| Repository | Change |
|-----------|--------|
| `JdbcUserSessionRepository` | Save/load `selected_tenant_id` column |
| `JdbcMemberRepository` | Add `findAllByEmail(String email)` — cross-tenant membership lookup |
| `JdbcUserRepository` | Add `searchByEmail(String emailPartial)` — partial match search |

## Test Plan

| Target | Type | Coverage |
|--------|------|----------|
| `TenantAffiliationStatus` determination | Unit | 3 status patterns |
| `UserSession.selectTenant()` | Unit | Select, clear, guard method |
| `TenantAssignmentService` | Unit | Search, assign, duplicate check |
| `UserStatusService` | Unit | Status retrieval, tenant selection |
| `UserStatusController` | Integration | Auth-only access, response format |
| `AdminMemberController` extension | Integration | Permission checks, assign flow |

## Error Handling

| Error Code | HTTP Status | Condition |
|-----------|-------------|-----------|
| `USER_NOT_FOUND` | 404 | User ID doesn't exist |
| `MEMBER_NOT_FOUND` | 404 | No membership for given tenant |
| `DUPLICATE_TENANT_ASSIGNMENT` | 409 | User already assigned to tenant |
| `INVALID_TENANT_SELECTION` | 422 | User has no membership for selected tenant |

## Impact Analysis

- **New files:** 4 (TenantAffiliationStatus, TenantAssignmentService, UserStatusService, UserStatusController)
- **Modified files:** 5 (UserSession, AdminMemberController, TenantStatusFilter, JdbcUserSessionRepository, JdbcMemberRepository + JdbcUserRepository)
- **Migration:** 1 (V28)
- **Backward compatible:** Yes — no existing behavior changes
