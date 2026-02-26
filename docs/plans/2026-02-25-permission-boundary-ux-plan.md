# Permission Boundary UX Implementation Plan

**Goal:** Remove tenant-scoped permissions from SYSTEM_ADMIN and add frontend AccessDenied UX for 403 responses.

**Architecture:** Backend Flyway migration removes `member.view`/`project.view` from SYSTEM_ADMIN. Frontend adds `ForbiddenError` to API client, `AccessDenied` display component, and `onForbidden` callback to List components. Dashboard grid and AdminNav get P1 UX improvements.

**Tech Stack:** Spring Boot (Flyway, MockMvc), Next.js (React, next-intl, Vitest, Testing Library)

**Design doc:** `docs/plans/2026-02-25-permission-boundary-ux-design.md`

---

### Task 1: Backend — Migration to remove SYSTEM_ADMIN tenant-scoped permissions

**Files:**
- Create: `backend/src/main/resources/db/migration/V22__remove_system_admin_tenant_scoped_permissions.sql`

**Step 1: Create the migration file**

```sql
-- Remove tenant-scoped read permissions from SYSTEM_ADMIN.
-- SYSTEM_ADMIN should only manage system-level resources (tenants, users).
-- Rollback:
--   INSERT INTO role_permissions (role_id, permission_id)
--     SELECT r.id, p.id FROM roles r, permissions p
--     WHERE r.name = 'SYSTEM_ADMIN' AND p.name IN ('member.view', 'project.view');

DELETE FROM role_permissions
WHERE role_id = (SELECT id FROM roles WHERE name = 'SYSTEM_ADMIN')
  AND permission_id IN (
    SELECT id FROM permissions WHERE name IN ('member.view', 'project.view')
  );
```

**Step 2: Verify migration applies cleanly**

Run: `cd backend && ./gradlew test --tests "com.worklog.api.HealthControllerTest"`
Expected: PASS (Flyway migration runs at startup without error)

**Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V22__remove_system_admin_tenant_scoped_permissions.sql
git commit -m "feat: remove member.view/project.view from SYSTEM_ADMIN (V22 migration)"
```

---

### Task 2: Backend — Permission boundary integration test

**Files:**
- Create: `backend/src/test/kotlin/com/worklog/api/AdminPermissionBoundaryTest.kt`

**Step 1: Write the boundary test**

```kotlin
package com.worklog.api

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Cross-role permission boundary tests.
 *
 * Verifies that SYSTEM_ADMIN cannot access tenant-scoped resources
 * (members, projects) but can access system-level resources (tenants, users).
 */
class AdminPermissionBoundaryTest : AdminIntegrationTestBase() {

    private lateinit var sysAdminEmail: String

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        sysAdminEmail = "sysadmin-boundary-$suffix@test.com"
        createUser(sysAdminEmail, SYSTEM_ADMIN_ROLE_ID, "Boundary Test SysAdmin")
    }

    @Test
    fun `SYSTEM_ADMIN cannot access members endpoint`() {
        mockMvc.perform(get("/api/v1/admin/members").with(user(sysAdminEmail)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `SYSTEM_ADMIN cannot access projects endpoint`() {
        mockMvc.perform(get("/api/v1/admin/projects").with(user(sysAdminEmail)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `SYSTEM_ADMIN can access tenants endpoint`() {
        mockMvc.perform(get("/api/v1/admin/tenants").with(user(sysAdminEmail)))
            .andExpect(status().isOk)
    }

    @Test
    fun `SYSTEM_ADMIN can access users endpoint`() {
        mockMvc.perform(get("/api/v1/admin/users").with(user(sysAdminEmail)))
            .andExpect(status().isOk)
    }
}
```

**Step 2: Run the tests**

Run: `cd backend && ./gradlew test --tests "com.worklog.api.AdminPermissionBoundaryTest"`
Expected: All 4 tests PASS

**Step 3: Run all backend tests to verify no regressions**

Run: `cd backend && ./gradlew test`
Expected: All tests PASS (member/project tests use TENANT_ADMIN, unaffected)

**Step 4: Commit**

```bash
git add backend/src/test/kotlin/com/worklog/api/AdminPermissionBoundaryTest.kt
git commit -m "test: add SYSTEM_ADMIN permission boundary integration tests"
```

---

### Task 3: Frontend — Add ForbiddenError class to API client

**Files:**
- Modify: `frontend/app/services/api.ts`

**Step 1: Add ForbiddenError class**

After `UnauthorizedError` class (around line 123), add:

```typescript
export class ForbiddenError extends ApiError {
  constructor(message = "Access denied") {
    super(message, 403, "FORBIDDEN");
    this.name = "ForbiddenError";
  }
}
```

**Step 2: Add 403 check in request() method**

In the `request()` method, after the 401 check (`if (response.status === 401)` block, around line 195) and before the 412 check, add:

```typescript
      if (response.status === 403) {
        throw new ForbiddenError();
      }
```

**Step 3: Verify frontend still builds**

Run: `cd frontend && npx tsc --noEmit`
Expected: No type errors

**Step 4: Commit**

```bash
git add frontend/app/services/api.ts
git commit -m "feat: add ForbiddenError class for 403 API responses"
```

---

### Task 4: Frontend — i18n keys for AccessDenied

**Files:**
- Modify: `frontend/messages/ja.json`
- Modify: `frontend/messages/en.json`

**Step 1: Add i18n keys to ja.json**

Add under the top-level object:

```json
"accessDenied": {
  "title": "アクセス権限がありません",
  "message": "このページを表示する権限がありません。管理者にお問い合わせください。",
  "backToDashboard": "ダッシュボードに戻る"
}
```

**Step 2: Add i18n keys to en.json**

```json
"accessDenied": {
  "title": "Access Denied",
  "message": "You do not have permission to view this page. Please contact your administrator.",
  "backToDashboard": "Back to Dashboard"
}
```

**Step 3: Run i18n key parity test**

Run: `cd frontend && npm test -- --run tests/unit/i18n/key-parity.test.ts`
Expected: PASS (both locales have matching keys)

**Step 4: Commit**

```bash
git add frontend/messages/ja.json frontend/messages/en.json
git commit -m "feat: add i18n keys for AccessDenied component"
```

---

### Task 5: Frontend — AccessDenied component + test (TDD)

**Files:**
- Create: `frontend/tests/unit/components/shared/AccessDenied.test.tsx`
- Create: `frontend/app/components/shared/AccessDenied.tsx`

**Step 1: Write the failing test**

```tsx
import { render, screen } from "@testing-library/react";
import { AccessDenied } from "@/components/shared/AccessDenied";
import { IntlWrapper } from "../../../helpers/intl";

function renderWithIntl(ui: React.ReactElement) {
  return render(<IntlWrapper>{ui}</IntlWrapper>);
}

describe("AccessDenied", () => {
  test("renders default title and message from i18n", () => {
    renderWithIntl(<AccessDenied />);
    expect(screen.getByText("アクセス権限がありません")).toBeInTheDocument();
    expect(
      screen.getByText("このページを表示する権限がありません。管理者にお問い合わせください。"),
    ).toBeInTheDocument();
  });

  test("renders dashboard link", () => {
    renderWithIntl(<AccessDenied />);
    const link = screen.getByRole("link", { name: "ダッシュボードに戻る" });
    expect(link).toHaveAttribute("href", "/admin");
  });

  test("renders lock icon with accessible role", () => {
    renderWithIntl(<AccessDenied />);
    expect(screen.getByRole("img", { hidden: true })).toBeInTheDocument();
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- --run tests/unit/components/shared/AccessDenied.test.tsx`
Expected: FAIL (module not found)

**Step 3: Write AccessDenied component**

```tsx
"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";

export function AccessDenied() {
  const t = useTranslations("accessDenied");

  return (
    <div className="flex flex-col items-center justify-center py-16 px-4">
      <svg
        className="w-16 h-16 text-gray-400 mb-4"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        role="img"
        aria-hidden="true"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={1.5}
          d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
        />
      </svg>
      <h2 className="text-xl font-semibold text-gray-900 mb-2">{t("title")}</h2>
      <p className="text-sm text-gray-600 mb-6 text-center max-w-md">{t("message")}</p>
      <Link
        href="/admin"
        className="px-4 py-2 text-sm text-blue-600 bg-blue-50 rounded-md hover:bg-blue-100 transition-colors"
      >
        {t("backToDashboard")}
      </Link>
    </div>
  );
}
```

**Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- --run tests/unit/components/shared/AccessDenied.test.tsx`
Expected: PASS

**Step 5: Commit**

```bash
git add frontend/app/components/shared/AccessDenied.tsx frontend/tests/unit/components/shared/AccessDenied.test.tsx
git commit -m "feat: add AccessDenied component with tests"
```

---

### Task 6: Frontend — MemberList onForbidden callback + test (TDD)

**Files:**
- Modify: `frontend/tests/unit/components/admin/MemberList.test.tsx`
- Modify: `frontend/app/components/admin/MemberList.tsx`

**Step 1: Write the failing test**

Add to the existing `describe("MemberList")` block in `MemberList.test.tsx`. At the top of the file, the mock already imports `api` — also need to import `ForbiddenError`:

Add to the mock at the top of the file (around line 24):

```typescript
vi.mock("@/services/api", () => ({
  api: {
    admin: {
      members: {
        list: (...args: unknown[]) => mockListMembers(...args),
      },
    },
  },
  ApiError: class ApiError extends Error {
    constructor(message: string, public status: number, public code?: string) {
      super(message);
      this.name = "ApiError";
    }
  },
  ForbiddenError: class ForbiddenError extends Error {
    constructor(message = "Access denied") {
      super(message);
      this.name = "ForbiddenError";
      this.status = 403;
    }
    status: number;
  },
}));
```

Note: The mock must export `ForbiddenError` because the component will use `instanceof` checks. If the existing mock structure differs, adapt to match. The key is that `ForbiddenError` must be available from the mock.

Add the test at the end of the describe block:

```typescript
test("calls onForbidden when API returns 403", async () => {
  const { ForbiddenError } = await import("@/services/api");
  mockListMembers.mockRejectedValue(new ForbiddenError());
  const onForbidden = vi.fn();
  renderWithProviders(
    <MemberList {...defaultProps} onForbidden={onForbidden} />,
  );
  await waitFor(() => {
    expect(onForbidden).toHaveBeenCalledTimes(1);
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- --run tests/unit/components/admin/MemberList.test.tsx`
Expected: FAIL (onForbidden prop not expected / ForbiddenError not handled)

**Step 3: Modify MemberList component**

In `frontend/app/components/admin/MemberList.tsx`:

1. Update import (line 8):
```typescript
import { ApiError, ForbiddenError, api } from "@/services/api";
```

2. Add `onForbidden` to props interface (around line 20):
```typescript
interface MemberListProps {
  onEdit: (member: MemberRow) => void;
  onDeactivate: (id: string) => void;
  onActivate: (id: string) => void;
  refreshKey: number;
  onForbidden?: () => void;
}
```

3. Destructure `onForbidden` in function params (line 27):
```typescript
export function MemberList({ onEdit, onDeactivate, onActivate, refreshKey, onForbidden }: MemberListProps) {
```

4. Update catch block in `loadMembers` (around line 60):
```typescript
    } catch (err: unknown) {
      if (err instanceof ForbiddenError) {
        onForbidden?.();
        return;
      }
      setLoadError(err instanceof ApiError ? err.message : tc("fetchError"));
    }
```

**Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- --run tests/unit/components/admin/MemberList.test.tsx`
Expected: All tests PASS (including existing tests — onForbidden is optional)

**Step 5: Commit**

```bash
git add frontend/app/components/admin/MemberList.tsx frontend/tests/unit/components/admin/MemberList.test.tsx
git commit -m "feat: add onForbidden callback to MemberList for 403 handling"
```

---

### Task 7: Frontend — ProjectList onForbidden callback + test (TDD)

**Files:**
- Modify: `frontend/tests/unit/components/admin/ProjectList.test.tsx`
- Modify: `frontend/app/components/admin/ProjectList.tsx`

Same pattern as Task 6 but for ProjectList. The changes are identical in structure:

**Step 1: Write the failing test**

Update the mock in `ProjectList.test.tsx` to export `ForbiddenError` (same pattern as Task 6). Add test:

```typescript
test("calls onForbidden when API returns 403", async () => {
  const { ForbiddenError } = await import("@/services/api");
  mockListProjects.mockRejectedValue(new ForbiddenError());
  const onForbidden = vi.fn();
  renderWithProviders(
    <ProjectList {...defaultProps} onForbidden={onForbidden} />,
  );
  await waitFor(() => {
    expect(onForbidden).toHaveBeenCalledTimes(1);
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- --run tests/unit/components/admin/ProjectList.test.tsx`
Expected: FAIL

**Step 3: Modify ProjectList component**

Same changes as Task 6:
1. Import `ForbiddenError` from api
2. Add `onForbidden?: () => void` to `ProjectListProps`
3. Destructure in function params
4. Add `instanceof ForbiddenError` check in catch block

**Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- --run tests/unit/components/admin/ProjectList.test.tsx`
Expected: All tests PASS

**Step 5: Commit**

```bash
git add frontend/app/components/admin/ProjectList.tsx frontend/tests/unit/components/admin/ProjectList.test.tsx
git commit -m "feat: add onForbidden callback to ProjectList for 403 handling"
```

---

### Task 8: Frontend — Integrate AccessDenied into members and projects pages

**Files:**
- Modify: `frontend/app/admin/members/page.tsx`
- Modify: `frontend/app/admin/projects/page.tsx`

**Step 1: Edit members/page.tsx**

1. Add import:
```typescript
import { AccessDenied } from "@/components/shared/AccessDenied";
```

2. Add state (after existing useState declarations):
```typescript
const [isForbidden, setIsForbidden] = useState(false);
```

3. Add early return before the main JSX return:
```typescript
if (isForbidden) {
  return <AccessDenied />;
}
```

4. Add `onForbidden` prop to `<MemberList>`:
```tsx
<MemberList
  onEdit={handleEdit}
  onDeactivate={handleDeactivate}
  onActivate={handleActivate}
  refreshKey={refreshKey}
  onForbidden={() => setIsForbidden(true)}
/>
```

**Step 2: Edit projects/page.tsx**

Identical pattern:
1. Import `AccessDenied`
2. Add `isForbidden` state
3. Add early return with `<AccessDenied />`
4. Add `onForbidden` prop to `<ProjectList>`

**Step 3: Verify build**

Run: `cd frontend && npx tsc --noEmit`
Expected: No type errors

**Step 4: Commit**

```bash
git add frontend/app/admin/members/page.tsx frontend/app/admin/projects/page.tsx
git commit -m "feat: integrate AccessDenied into members and projects pages"
```

---

### Task 9: Frontend — Dashboard dynamic grid (P1)

**Files:**
- Modify: `frontend/app/admin/page.tsx`

**Step 1: Refactor dashboard to data-driven cards**

Replace the current inline conditional rendering with a card definition array:

```tsx
"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { useAdminContext } from "@/providers/AdminProvider";

interface CardDef {
  permission: string;
  titleKey: string;
  descriptionKey: string;
  href: string;
}

const CARDS: CardDef[] = [
  { permission: "member.view", titleKey: "members", descriptionKey: "cards.membersDescription", href: "/admin/members" },
  { permission: "project.view", titleKey: "projects", descriptionKey: "cards.projectsDescription", href: "/admin/projects" },
  { permission: "assignment.view", titleKey: "assignments", descriptionKey: "cards.assignmentsDescription", href: "/admin/assignments" },
  { permission: "daily_approval.view", titleKey: "dailyApproval", descriptionKey: "cards.dailyApprovalDescription", href: "/worklog/daily-approval" },
  { permission: "organization.view", titleKey: "organizations", descriptionKey: "cards.organizationsDescription", href: "/admin/organizations" },
  { permission: "tenant.view", titleKey: "tenants", descriptionKey: "cards.tenantsDescription", href: "/admin/tenants" },
  { permission: "user.view", titleKey: "users", descriptionKey: "cards.usersDescription", href: "/admin/users" },
];

export default function AdminDashboard() {
  const t = useTranslations("admin.dashboard");
  const tn = useTranslations("admin.nav");
  const { adminContext, hasPermission } = useAdminContext();

  if (!adminContext) return null;

  const visibleCards = CARDS.filter((c) => hasPermission(c.permission));
  const gridClass =
    visibleCards.length <= 2
      ? "grid grid-cols-1 md:grid-cols-2 gap-4"
      : "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4";

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">{t("title")}</h1>
      <div className={gridClass}>
        {visibleCards.map((card) => (
          <DashboardCard
            key={card.href}
            title={tn(card.titleKey)}
            description={t(card.descriptionKey)}
            href={card.href}
          />
        ))}
      </div>
    </div>
  );
}

function DashboardCard({ title, description, href }: { title: string; description: string; href: string }) {
  return (
    <Link
      href={href}
      className="block p-6 bg-white rounded-lg border border-gray-200 hover:border-blue-300 hover:shadow-sm transition-all"
    >
      <h2 className="text-lg font-semibold text-gray-900 mb-2">{title}</h2>
      <p className="text-sm text-gray-600">{description}</p>
    </Link>
  );
}
```

**Step 2: Verify build**

Run: `cd frontend && npx tsc --noEmit`
Expected: No type errors

**Step 3: Commit**

```bash
git add frontend/app/admin/page.tsx
git commit -m "feat: dynamic dashboard grid based on visible card count"
```

---

### Task 10: Frontend — AdminNav tenant name display (P1)

**Files:**
- Modify: `frontend/app/components/admin/AdminNav.tsx`

**Step 1: Add tenant name below role name**

In the `navContent` function, the desktop/tablet header section (around line 76-88) currently shows:

```tsx
<p className="mt-1 text-sm text-gray-700 truncate">{adminContext.role.replace(/_/g, " ")}</p>
```

Change to:

```tsx
<p className="mt-1 text-sm text-gray-700 truncate">{adminContext.role.replace(/_/g, " ")}</p>
{adminContext.tenantName && (
  <p className="text-xs text-gray-500 truncate">{adminContext.tenantName}</p>
)}
```

Also apply the same change in the mobile drawer section (around line 168-172), after the existing role display:

```tsx
<p className="text-sm text-gray-700 truncate">{adminContext.role.replace(/_/g, " ")}</p>
{adminContext.tenantName && (
  <p className="text-xs text-gray-500 truncate">{adminContext.tenantName}</p>
)}
```

**Step 2: Verify build**

Run: `cd frontend && npx tsc --noEmit`
Expected: No type errors

**Step 3: Commit**

```bash
git add frontend/app/components/admin/AdminNav.tsx
git commit -m "feat: show tenant name in AdminNav for tenant-scoped admins"
```

---

### Task 11: Full test suite verification

**Step 1: Run all backend tests**

Run: `cd backend && ./gradlew test`
Expected: All tests PASS

**Step 2: Run all frontend unit tests**

Run: `cd frontend && npm test -- --run`
Expected: All tests PASS

**Step 3: Run frontend lint/format check**

Run: `cd frontend && npm run check:ci`
Expected: No errors

**Step 4: Run backend format check**

Run: `cd backend && ./gradlew checkFormat`
Expected: No errors

---

### Task Summary

| Task | Description | Type |
|------|-------------|------|
| 1 | Migration V22 — remove permissions | Backend |
| 2 | Permission boundary integration test | Backend test |
| 3 | ForbiddenError class in api.ts | Frontend |
| 4 | i18n keys for AccessDenied | Frontend i18n |
| 5 | AccessDenied component + test (TDD) | Frontend |
| 6 | MemberList onForbidden + test (TDD) | Frontend |
| 7 | ProjectList onForbidden + test (TDD) | Frontend |
| 8 | Page integration (members + projects) | Frontend |
| 9 | Dashboard dynamic grid (P1) | Frontend |
| 10 | AdminNav tenant name (P1) | Frontend |
| 11 | Full test suite verification | Verification |
