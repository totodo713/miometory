# Permission Boundary UX Design

**Issue**: #41 — SYSTEM_ADMINからテナント固有リソースの参照権限を削除し、権限境界のUXを改善
**Date**: 2026-02-25

## Motivation

- SYSTEM_ADMINのダッシュボードに不要なメニュー（Members, Projects）が表示されていて紛らわしい（UX主）
- テナント固有リソースへのアクセスを最小権限の原則で制限する（セキュリティ副）
- 現在の防御はナビ非表示の1層のみ。URL直打ち・ブックマーク・ブラウザバックに無防備

## Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Primary motivation | UX (secondary: security) | Dashboard clarity is the main goal |
| Denied UX | Access denied message in content area | User understands what happened |
| Layout on deny | Admin nav preserved, content replaced | Easy navigation to correct page |
| Permission check | API-response-based (403 → ForbiddenError) | Backend is source of truth |
| Architecture | Page-level error handling + shared AccessDenied component | Extends existing patterns, no Context needed |
| P1 scope | Included (dashboard grid + nav tenant name) | Bundle together |
| Reusability | Generic onForbidden + AccessDenied | Ready for other pages |

## Changes

### S1: Backend — Migration

**Create**: `backend/src/main/resources/db/migration/V22__remove_system_admin_tenant_scoped_permissions.sql`

```sql
DELETE FROM role_permissions
WHERE role_id = (SELECT id FROM roles WHERE name = 'SYSTEM_ADMIN')
  AND permission_id IN (
    SELECT id FROM permissions WHERE name IN ('member.view', 'project.view')
  );
```

- Uses subqueries (no hardcoded UUIDs)
- Rollback SQL included as comment
- Flyway runs at app startup when permission cache is empty (60s TTL irrelevant)

### S2: Backend — Permission Boundary Test

**Create**: `backend/src/test/kotlin/com/worklog/api/AdminPermissionBoundaryTest.kt`

Extends `AdminIntegrationTestBase`. Tests:

| Test Case | Expected |
|---|---|
| `GET /api/v1/admin/members` as SYSTEM_ADMIN | 403 |
| `GET /api/v1/admin/projects` as SYSTEM_ADMIN | 403 |
| `GET /api/v1/admin/tenants` as SYSTEM_ADMIN | 200 |
| `GET /api/v1/admin/users` as SYSTEM_ADMIN | 200 |

### S3: Frontend — ForbiddenError class

**Edit**: `frontend/app/services/api.ts`

- Add `ForbiddenError extends ApiError` (status 403, code "FORBIDDEN")
- Add 403 check in `request()` after existing 401 check, before 412 check

### S4: Frontend — AccessDenied + Page Integration

#### S4a: AccessDenied component (presentational)

**Create**: `frontend/app/components/shared/AccessDenied.tsx`

- Stateless display component: lock icon + title + message + dashboard link
- Props: optional `title` and `message` overrides (defaults from i18n)
- Renders inside admin layout (nav is preserved by the parent page)

#### S4b: List components — onForbidden callback

**Edit**: `MemberList.tsx`, `ProjectList.tsx`

- Add optional `onForbidden?: () => void` prop
- In catch block: check `instanceof ForbiddenError` before generic error handling
- If ForbiddenError, call `onForbidden?.()` and return early
- Backward compatible (onForbidden is optional)

#### S4c: Page integration

**Edit**: `admin/members/page.tsx`, `admin/projects/page.tsx`

- Add `isForbidden` state
- Pass `onForbidden={() => setIsForbidden(true)}` to List component
- If `isForbidden`, render `<AccessDenied />` instead of normal content

#### S4 i18n

**Edit**: `frontend/messages/ja.json`, `frontend/messages/en.json`

```json
{
  "accessDenied": {
    "title": "アクセス権限がありません / Access Denied",
    "message": "このページを表示する権限がありません。... / You do not have permission...",
    "backToDashboard": "ダッシュボードに戻る / Back to Dashboard"
  }
}
```

### S5: Frontend — P1 UX Improvements

#### S5a: Dashboard dynamic grid

**Edit**: `frontend/app/admin/page.tsx`

- Refactor inline `hasPermission && <Card>` to data-driven card array
- Filter visible cards, determine grid class by count:
  - `<= 2` cards → `grid-cols-1 md:grid-cols-2`
  - `> 2` cards → `grid-cols-1 md:grid-cols-2 lg:grid-cols-3`

#### S5b: AdminNav tenant name

**Edit**: `frontend/app/components/admin/AdminNav.tsx`

- Show `adminContext.tenantName` below role name when available
- SYSTEM_ADMIN has no tenantName → role name only (implicit context for fewer menu items)
- `tenantName` already exists in `AdminContext` interface — no API changes needed

## Post-Change SYSTEM_ADMIN Permissions

| Remaining | Removed |
|---|---|
| tenant.view, tenant.create, tenant.update, tenant.deactivate | ~~project.view~~ |
| user.view, user.update_role, user.lock, user.reset_password | ~~member.view~~ |

## Test Strategy

### Backend

- `AdminPermissionBoundaryTest.kt` (new): 4 cross-role boundary tests
- Existing tests unaffected (member/project tests use TENANT_ADMIN)

### Frontend

- `MemberList.test.tsx` (add): API 403 → onForbidden called
- `ProjectList.test.tsx` (add): API 403 → onForbidden called
- `AccessDenied.test.tsx` (new): renders message + dashboard link
- Dashboard grid test (new or add): grid class switches by card count

### Manual Verification

1. Login as sysadmin → dashboard shows only Tenants/Users
2. Direct URL to `/admin/members` → AccessDenied message
3. TENANT_ADMIN login → all pages fully functional

## Files Changed

| File | Action |
|---|---|
| `backend/.../V22__remove_system_admin_tenant_scoped_permissions.sql` | Create |
| `backend/.../AdminPermissionBoundaryTest.kt` | Create |
| `frontend/app/services/api.ts` | Edit |
| `frontend/app/components/shared/AccessDenied.tsx` | Create |
| `frontend/app/components/admin/MemberList.tsx` | Edit |
| `frontend/app/components/admin/ProjectList.tsx` | Edit |
| `frontend/app/admin/members/page.tsx` | Edit |
| `frontend/app/admin/projects/page.tsx` | Edit |
| `frontend/messages/ja.json` | Edit |
| `frontend/messages/en.json` | Edit |
| `frontend/app/admin/page.tsx` | Edit |
| `frontend/app/components/admin/AdminNav.tsx` | Edit |
| `frontend/tests/unit/components/admin/MemberList.test.tsx` | Edit |
| `frontend/tests/unit/components/admin/ProjectList.test.tsx` | Edit |
| `frontend/tests/unit/components/shared/AccessDenied.test.tsx` | Create |
