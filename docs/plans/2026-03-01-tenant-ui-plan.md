# Tenant UI Implementation Plan — Issue #50

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add tenant waiting screens, multi-tenant selection, header tenant switching, and admin assignment UI.

**Architecture:** Separate `TenantProvider` manages tenant state (affiliation, memberships, selected tenant). `AuthGuard` extended to route based on tenant state. Header gains tenant switcher dropdown for multi-tenant users.

**Tech Stack:** Next.js 15, React 19, TypeScript, next-intl, Vitest, React Testing Library, Playwright

**Design Doc:** `docs/plans/2026-03-01-tenant-ui-design.md`

---

### Task 1: Add Tenant Types

**Files:**
- Create: `frontend/app/types/tenant.ts`

**Step 1: Create tenant type definitions**

```typescript
export type TenantAffiliationState =
  | "UNAFFILIATED"
  | "AFFILIATED_NO_ORG"
  | "FULLY_ASSIGNED";

export interface TenantMembership {
  memberId: string;
  tenantId: string;
  tenantName: string;
  organizationId: string | null;
  organizationName: string | null;
}

export interface UserStatusResponse {
  userId: string;
  email: string;
  state: TenantAffiliationState;
  memberships: TenantMembership[];
}
```

**Step 2: Commit**

```bash
git add frontend/app/types/tenant.ts
git commit -m "feat: add tenant type definitions"
```

---

### Task 2: Extend API Client

**Files:**
- Modify: `frontend/app/services/api.ts`

**Step 1: Add UserStatus and admin assignment API methods**

Add to the `auth.login` return type — extend the existing `login()` response to include `tenantAffiliationState` and `memberships`. Also add new API namespaces.

In the `login()` method, the response already includes these fields from the backend but the frontend type doesn't capture them. Update the type.

Add after the existing `auth` section (around line 768):

```typescript
userStatus: {
  async getStatus(): Promise<UserStatusResponse> {
    return get("/api/v1/user/status");
  },
  async selectTenant(tenantId: string): Promise<void> {
    return post("/api/v1/user/select-tenant", { tenantId });
  },
},
```

Add to the existing `admin.users` section:

```typescript
async searchForAssignment(email: string): Promise<{ users: Array<{ userId: string; email: string; name: string; isAlreadyInTenant: boolean }> }> {
  return get(`/api/v1/admin/users/search-for-assignment?email=${encodeURIComponent(email)}`);
},
```

Add to the existing `admin.members` section:

```typescript
async assignTenant(userId: string, displayName: string): Promise<void> {
  return post("/api/v1/admin/members/assign-tenant", { userId, displayName });
},
```

Also update the `login()` return type to include the new fields:

```typescript
// In the login method, update the response type to include:
tenantAffiliationState: string;
memberships: Array<{
  memberId: string;
  tenantId: string;
  tenantName: string;
  organizationId: string | null;
  organizationName: string | null;
}>;
```

**Step 2: Import the UserStatusResponse type**

Add import at the top of api.ts:

```typescript
import type { UserStatusResponse } from "@/types/tenant";
```

**Step 3: Commit**

```bash
git add frontend/app/services/api.ts
git commit -m "feat: add user status and tenant assignment API methods"
```

---

### Task 3: Add i18n Keys

**Files:**
- Modify: `frontend/messages/en.json`
- Modify: `frontend/messages/ja.json`

**Step 1: Add English keys**

Add these sections to `en.json`:

```json
"waiting": {
  "title": "Waiting for Tenant Assignment",
  "message": "An administrator needs to add you to a tenant before you can use the system.",
  "checking": "Checking status...",
  "logout": "Log Out"
},
"pendingOrganization": {
  "title": "Waiting for Organization Assignment",
  "message": "You have been added to a tenant. An administrator needs to assign you to an organization before you can start working.",
  "checking": "Checking status...",
  "logout": "Log Out"
},
"selectTenant": {
  "title": "Select a Tenant",
  "message": "You belong to multiple tenants. Please select which one to use.",
  "select": "Select",
  "organization": "Organization",
  "noOrganization": "No organization assigned"
},
"header": {
  ...existing keys,
  "switchTenant": "Switch Tenant",
  "currentTenant": "Current Tenant"
},
"admin": {
  ...existing keys,
  "members": {
    ...existing keys,
    "assignTenant": {
      "button": "Assign User to Tenant",
      "title": "Assign User to Tenant",
      "searchPlaceholder": "Search by email address",
      "search": "Search",
      "noResults": "No users found",
      "alreadyAssigned": "Already in tenant",
      "displayName": "Display Name",
      "assign": "Assign",
      "cancel": "Cancel",
      "success": "User assigned to tenant successfully",
      "error": "Failed to assign user to tenant"
    }
  }
}
```

**Step 2: Add Japanese keys**

Add corresponding Japanese translations to `ja.json`:

```json
"waiting": {
  "title": "テナント割り当て待ち",
  "message": "管理者があなたをテナントに追加するまでお待ちください。",
  "checking": "状態を確認中...",
  "logout": "ログアウト"
},
"pendingOrganization": {
  "title": "組織割り当て待ち",
  "message": "テナントに追加されました。管理者が組織に配属するまでお待ちください。",
  "checking": "状態を確認中...",
  "logout": "ログアウト"
},
"selectTenant": {
  "title": "テナントを選択",
  "message": "複数のテナントに所属しています。使用するテナントを選択してください。",
  "select": "選択",
  "organization": "組織",
  "noOrganization": "組織未配属"
},
"header": {
  ...existing keys,
  "switchTenant": "テナント切替",
  "currentTenant": "現在のテナント"
},
"admin": {
  ...existing keys,
  "members": {
    ...existing keys,
    "assignTenant": {
      "button": "ユーザーをテナントにアサイン",
      "title": "ユーザーをテナントにアサイン",
      "searchPlaceholder": "メールアドレスで検索",
      "search": "検索",
      "noResults": "ユーザーが見つかりません",
      "alreadyAssigned": "テナント所属済み",
      "displayName": "表示名",
      "assign": "アサイン",
      "cancel": "キャンセル",
      "success": "ユーザーをテナントにアサインしました",
      "error": "テナントへのアサインに失敗しました"
    }
  }
}
```

**Step 3: Commit**

```bash
git add frontend/messages/en.json frontend/messages/ja.json
git commit -m "feat: add i18n keys for tenant UI"
```

---

### Task 4: Create TenantProvider

**Files:**
- Create: `frontend/app/providers/TenantProvider.tsx`
- Create: `frontend/tests/unit/providers/TenantProvider.test.tsx`

**Step 1: Write TenantProvider tests**

Test file covers:
- Provides initial loading state
- Fetches status on mount when user is authenticated
- Does not fetch when user is null
- `selectTenant()` calls API and updates state
- Polling every 30s when UNAFFILIATED
- Polling every 30s when AFFILIATED_NO_ORG
- No polling when FULLY_ASSIGNED
- `refreshStatus()` fetches latest status

Mock `api.userStatus.getStatus()` and `api.userStatus.selectTenant()`.

**Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run tests/unit/providers/TenantProvider.test.tsx`
Expected: FAIL

**Step 3: Implement TenantProvider**

```typescript
"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import type { TenantAffiliationState, TenantMembership } from "@/types/tenant";
import { useAuthContext } from "./AuthProvider";
import { api } from "@/services/api";

interface TenantContextType {
  affiliationState: TenantAffiliationState | null;
  memberships: TenantMembership[];
  selectedTenantId: string | null;
  selectedTenantName: string | null;
  isLoading: boolean;
  selectTenant: (tenantId: string) => Promise<void>;
  refreshStatus: () => Promise<void>;
}

const TenantContext = createContext<TenantContextType | null>(null);

export function useTenantContext() {
  const context = useContext(TenantContext);
  if (!context) {
    throw new Error("useTenantContext must be used within a TenantProvider");
  }
  return context;
}

const POLLING_INTERVAL = 30_000;

export function TenantProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuthContext();
  const [affiliationState, setAffiliationState] = useState<TenantAffiliationState | null>(null);
  const [memberships, setMemberships] = useState<TenantMembership[]>([]);
  const [selectedTenantId, setSelectedTenantId] = useState<string | null>(null);
  const [selectedTenantName, setSelectedTenantName] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const refreshStatus = useCallback(async () => {
    if (!user) return;
    try {
      const status = await api.userStatus.getStatus();
      setAffiliationState(status.state);
      setMemberships(status.memberships);
    } catch {
      // Silently fail — user might have been logged out
    }
  }, [user]);

  // Fetch status when user changes
  useEffect(() => {
    if (!user) {
      setAffiliationState(null);
      setMemberships([]);
      setSelectedTenantId(null);
      setSelectedTenantName(null);
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    refreshStatus().finally(() => setIsLoading(false));
  }, [user, refreshStatus]);

  // Polling for UNAFFILIATED and AFFILIATED_NO_ORG
  useEffect(() => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    if (
      affiliationState === "UNAFFILIATED" ||
      affiliationState === "AFFILIATED_NO_ORG"
    ) {
      intervalRef.current = setInterval(refreshStatus, POLLING_INTERVAL);
    }
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [affiliationState, refreshStatus]);

  const selectTenant = useCallback(async (tenantId: string) => {
    await api.userStatus.selectTenant(tenantId);
    setSelectedTenantId(tenantId);
    const membership = memberships.find((m) => m.tenantId === tenantId);
    setSelectedTenantName(membership?.tenantName ?? null);
  }, [memberships]);

  return (
    <TenantContext.Provider
      value={{
        affiliationState,
        memberships,
        selectedTenantId,
        selectedTenantName,
        isLoading,
        selectTenant,
        refreshStatus,
      }}
    >
      {children}
    </TenantContext.Provider>
  );
}
```

**Step 4: Run tests to verify they pass**

Run: `cd frontend && npx vitest run tests/unit/providers/TenantProvider.test.tsx`
Expected: PASS

**Step 5: Commit**

```bash
git add frontend/app/providers/TenantProvider.tsx frontend/tests/unit/providers/TenantProvider.test.tsx
git commit -m "feat: add TenantProvider with polling and tenant selection"
```

---

### Task 5: Integrate TenantProvider into Root Layout

**Files:**
- Modify: `frontend/app/layout.tsx`

**Step 1: Add TenantProvider to provider stack**

Import `TenantProvider` and wrap it around `SessionProvider` inside `AuthProvider`:

```typescript
import { TenantProvider } from "@/providers/TenantProvider";

// In the layout, change the provider nesting to:
<AuthProvider>
  <TenantProvider>
    <SessionProvider>
      <ToastProvider>
        <Header />
        {children}
      </ToastProvider>
    </SessionProvider>
  </TenantProvider>
</AuthProvider>
```

**Step 2: Commit**

```bash
git add frontend/app/layout.tsx
git commit -m "feat: integrate TenantProvider into root layout"
```

---

### Task 6: Extend AuthGuard with Tenant State Routing

**Files:**
- Modify: `frontend/app/components/shared/AuthGuard.tsx`
- Create: `frontend/tests/unit/components/AuthGuard.test.tsx`

**Step 1: Write AuthGuard tests**

Tests cover:
- Redirects to `/login` when user is null
- Redirects to `/waiting` when affiliationState is UNAFFILIATED
- Redirects to `/pending-organization` when affiliationState is AFFILIATED_NO_ORG
- Redirects to `/select-tenant` when FULLY_ASSIGNED with multiple memberships and no selectedTenantId
- Renders children when FULLY_ASSIGNED with selectedTenantId
- Renders children when FULLY_ASSIGNED with single membership (auto-selected by backend)
- Shows LoadingSpinner while auth or tenant loading

Mock both `useAuthContext` and `useTenantContext`.

**Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run tests/unit/components/AuthGuard.test.tsx`
Expected: FAIL

**Step 3: Implement AuthGuard extension**

```typescript
"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthContext } from "@/providers/AuthProvider";
import { useTenantContext } from "@/providers/TenantProvider";
import { LoadingSpinner } from "./LoadingSpinner";

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { user, isLoading: authLoading } = useAuthContext();
  const { affiliationState, memberships, selectedTenantId, isLoading: tenantLoading } = useTenantContext();

  useEffect(() => {
    if (authLoading || tenantLoading) return;

    if (!user) {
      router.replace("/login");
      return;
    }

    if (affiliationState === "UNAFFILIATED") {
      router.replace("/waiting");
      return;
    }

    if (affiliationState === "AFFILIATED_NO_ORG") {
      router.replace("/pending-organization");
      return;
    }

    if (affiliationState === "FULLY_ASSIGNED" && memberships.length > 1 && !selectedTenantId) {
      router.replace("/select-tenant");
      return;
    }
  }, [user, authLoading, tenantLoading, affiliationState, memberships, selectedTenantId, router]);

  if (authLoading || tenantLoading) {
    return <LoadingSpinner />;
  }

  if (!user || affiliationState !== "FULLY_ASSIGNED") {
    return <LoadingSpinner />;
  }

  if (memberships.length > 1 && !selectedTenantId) {
    return <LoadingSpinner />;
  }

  return <>{children}</>;
}
```

**Step 4: Run tests to verify they pass**

Run: `cd frontend && npx vitest run tests/unit/components/AuthGuard.test.tsx`
Expected: PASS

**Step 5: Commit**

```bash
git add frontend/app/components/shared/AuthGuard.tsx frontend/tests/unit/components/AuthGuard.test.tsx
git commit -m "feat: extend AuthGuard with tenant state routing"
```

---

### Task 7: Create Waiting Page

**Files:**
- Create: `frontend/app/(auth)/waiting/page.tsx`
- Create: `frontend/tests/unit/pages/waiting.test.tsx`

**Step 1: Write tests for waiting page**

Tests:
- Renders waiting message
- Shows logout button
- Calls logout on button click
- Redirects away when affiliationState changes (via AuthGuard, so this test is integration-level — may skip)

**Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run tests/unit/pages/waiting.test.tsx`
Expected: FAIL

**Step 3: Implement waiting page**

The waiting page is outside AuthGuard protection (since UNAFFILIATED users are redirected here). It needs its own auth check (user must be logged in, but UNAFFILIATED).

```typescript
"use client";

import { useTranslations } from "next-intl";
import { useAuthContext } from "@/providers/AuthProvider";
import { useTenantContext } from "@/providers/TenantProvider";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { LoadingSpinner } from "@/components/shared/LoadingSpinner";

export default function WaitingPage() {
  const t = useTranslations("waiting");
  const { user, isLoading: authLoading, logout } = useAuthContext();
  const { affiliationState, isLoading: tenantLoading } = useTenantContext();
  const router = useRouter();

  useEffect(() => {
    if (authLoading || tenantLoading) return;
    if (!user) {
      router.replace("/login");
      return;
    }
    // If state changed from UNAFFILIATED, redirect appropriately
    if (affiliationState && affiliationState !== "UNAFFILIATED") {
      router.replace("/");
    }
  }, [user, authLoading, tenantLoading, affiliationState, router]);

  if (authLoading || tenantLoading) {
    return <LoadingSpinner />;
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="mx-auto max-w-md rounded-lg bg-white p-8 shadow-md text-center">
        <div className="mb-6">
          <svg className="mx-auto h-16 w-16 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
              d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>
        <h1 className="mb-4 text-2xl font-bold text-gray-900">{t("title")}</h1>
        <p className="mb-8 text-gray-600">{t("message")}</p>
        <p className="mb-6 text-sm text-gray-400">{t("checking")}</p>
        <button
          type="button"
          onClick={logout}
          className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
        >
          {t("logout")}
        </button>
      </div>
    </div>
  );
}
```

Note: TenantProvider handles the 30s polling automatically when state is UNAFFILIATED.

**Step 4: Run tests to verify they pass**

Run: `cd frontend && npx vitest run tests/unit/pages/waiting.test.tsx`
Expected: PASS

**Step 5: Commit**

```bash
git add frontend/app/\(auth\)/waiting/page.tsx frontend/tests/unit/pages/waiting.test.tsx
git commit -m "feat: add waiting page for unaffiliated users"
```

---

### Task 8: Create Pending Organization Page

**Files:**
- Create: `frontend/app/(auth)/pending-organization/page.tsx`
- Create: `frontend/tests/unit/pages/pending-organization.test.tsx`

**Step 1: Write tests**

Same pattern as waiting page tests but for `AFFILIATED_NO_ORG` state.

**Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run tests/unit/pages/pending-organization.test.tsx`
Expected: FAIL

**Step 3: Implement pending-organization page**

Same pattern as waiting page but different messages and redirects when state changes from `AFFILIATED_NO_ORG`:

```typescript
"use client";

import { useTranslations } from "next-intl";
import { useAuthContext } from "@/providers/AuthProvider";
import { useTenantContext } from "@/providers/TenantProvider";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { LoadingSpinner } from "@/components/shared/LoadingSpinner";

export default function PendingOrganizationPage() {
  const t = useTranslations("pendingOrganization");
  const { user, isLoading: authLoading, logout } = useAuthContext();
  const { affiliationState, isLoading: tenantLoading } = useTenantContext();
  const router = useRouter();

  useEffect(() => {
    if (authLoading || tenantLoading) return;
    if (!user) {
      router.replace("/login");
      return;
    }
    if (affiliationState && affiliationState !== "AFFILIATED_NO_ORG") {
      router.replace("/");
    }
  }, [user, authLoading, tenantLoading, affiliationState, router]);

  if (authLoading || tenantLoading) {
    return <LoadingSpinner />;
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="mx-auto max-w-md rounded-lg bg-white p-8 shadow-md text-center">
        <div className="mb-6">
          <svg className="mx-auto h-16 w-16 text-amber-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
              d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
          </svg>
        </div>
        <h1 className="mb-4 text-2xl font-bold text-gray-900">{t("title")}</h1>
        <p className="mb-8 text-gray-600">{t("message")}</p>
        <p className="mb-6 text-sm text-gray-400">{t("checking")}</p>
        <button
          type="button"
          onClick={logout}
          className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
        >
          {t("logout")}
        </button>
      </div>
    </div>
  );
}
```

**Step 4: Run tests to verify they pass**

Run: `cd frontend && npx vitest run tests/unit/pages/pending-organization.test.tsx`
Expected: PASS

**Step 5: Commit**

```bash
git add frontend/app/\(auth\)/pending-organization/page.tsx frontend/tests/unit/pages/pending-organization.test.tsx
git commit -m "feat: add pending-organization page for users awaiting org assignment"
```

---

### Task 9: Create Tenant Selection Page

**Files:**
- Create: `frontend/app/(auth)/select-tenant/page.tsx`
- Create: `frontend/tests/unit/pages/select-tenant.test.tsx`

**Step 1: Write tests**

Tests:
- Renders list of tenants from TenantContext memberships
- Shows tenant name and organization name for each
- Shows "No organization assigned" when organizationName is null
- Calls `selectTenant()` and redirects to `/worklog` on selection
- Redirects to `/login` if not authenticated

**Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run tests/unit/pages/select-tenant.test.tsx`
Expected: FAIL

**Step 3: Implement select-tenant page**

```typescript
"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { useAuthContext } from "@/providers/AuthProvider";
import { useTenantContext } from "@/providers/TenantProvider";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { LoadingSpinner } from "@/components/shared/LoadingSpinner";

export default function SelectTenantPage() {
  const t = useTranslations("selectTenant");
  const { user, isLoading: authLoading } = useAuthContext();
  const { memberships, selectTenant, isLoading: tenantLoading } = useTenantContext();
  const router = useRouter();
  const [selecting, setSelecting] = useState<string | null>(null);

  useEffect(() => {
    if (authLoading || tenantLoading) return;
    if (!user) {
      router.replace("/login");
    }
  }, [user, authLoading, tenantLoading, router]);

  const handleSelect = async (tenantId: string) => {
    setSelecting(tenantId);
    try {
      await selectTenant(tenantId);
      router.replace("/worklog");
    } catch {
      setSelecting(null);
    }
  };

  if (authLoading || tenantLoading) {
    return <LoadingSpinner />;
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="mx-auto w-full max-w-lg rounded-lg bg-white p-8 shadow-md">
        <h1 className="mb-2 text-2xl font-bold text-gray-900">{t("title")}</h1>
        <p className="mb-6 text-gray-600">{t("message")}</p>
        <div className="space-y-3">
          {memberships.map((m) => (
            <button
              key={m.tenantId}
              type="button"
              disabled={selecting !== null}
              onClick={() => handleSelect(m.tenantId)}
              className="w-full rounded-lg border border-gray-200 p-4 text-left transition hover:border-blue-500 hover:bg-blue-50 disabled:opacity-50"
            >
              <div className="font-medium text-gray-900">{m.tenantName}</div>
              <div className="mt-1 text-sm text-gray-500">
                {m.organizationName
                  ? `${t("organization")}: ${m.organizationName}`
                  : t("noOrganization")}
              </div>
              {selecting === m.tenantId && (
                <div className="mt-2">
                  <LoadingSpinner size="sm" />
                </div>
              )}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
```

**Step 4: Run tests to verify they pass**

Run: `cd frontend && npx vitest run tests/unit/pages/select-tenant.test.tsx`
Expected: PASS

**Step 5: Commit**

```bash
git add frontend/app/\(auth\)/select-tenant/page.tsx frontend/tests/unit/pages/select-tenant.test.tsx
git commit -m "feat: add tenant selection page for multi-tenant users"
```

---

### Task 10: Update Login Page Redirect Logic

**Files:**
- Modify: `frontend/app/(auth)/login/page.tsx`

**Step 1: Update login to check tenantAffiliationState from response**

Currently the login page always redirects to `/worklog` after successful login. Change to inspect the login response:

In the `handleSubmit` function, after `await login(email, password, rememberMe)`:

The `login()` function in AuthProvider returns void currently. We need to either:
(a) Return the login response from `login()` so the login page can inspect it, or
(b) Rely on TenantProvider + AuthGuard to do the redirect.

**Approach:** Change the redirect from `/worklog` to `/` (root page). The root page + AuthGuard will handle routing based on tenant state. This is the simplest change.

Replace:
```typescript
router.replace("/worklog");
```
With:
```typescript
router.replace("/");
```

The root page (`app/page.tsx`) will be updated in the next task to use TenantContext for routing.

**Step 2: Commit**

```bash
git add frontend/app/\(auth\)/login/page.tsx
git commit -m "feat: redirect to root after login for tenant-aware routing"
```

---

### Task 11: Update Root Page with Tenant-Aware Routing

**Files:**
- Modify: `frontend/app/page.tsx`

**Step 1: Update root page to route based on tenant state**

Current logic: authenticated → `/worklog`, else → `/login`

New logic:
```typescript
"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthContext } from "@/providers/AuthProvider";
import { useTenantContext } from "@/providers/TenantProvider";
import { LoadingSpinner } from "@/components/shared/LoadingSpinner";

export default function Home() {
  const router = useRouter();
  const { user, isLoading: authLoading } = useAuthContext();
  const { affiliationState, memberships, selectedTenantId, isLoading: tenantLoading } = useTenantContext();

  useEffect(() => {
    if (authLoading || tenantLoading) return;

    if (!user) {
      router.replace("/login");
      return;
    }

    switch (affiliationState) {
      case "UNAFFILIATED":
        router.replace("/waiting");
        break;
      case "AFFILIATED_NO_ORG":
        router.replace("/pending-organization");
        break;
      case "FULLY_ASSIGNED":
        if (memberships.length > 1 && !selectedTenantId) {
          router.replace("/select-tenant");
        } else {
          router.replace("/worklog");
        }
        break;
      default:
        // Still loading or unknown state
        break;
    }
  }, [user, authLoading, tenantLoading, affiliationState, memberships, selectedTenantId, router]);

  return (
    <div className="flex min-h-screen items-center justify-center">
      <LoadingSpinner />
    </div>
  );
}
```

**Step 2: Commit**

```bash
git add frontend/app/page.tsx
git commit -m "feat: add tenant-aware routing to root page"
```

---

### Task 12: Add Header Tenant Switcher

**Files:**
- Create: `frontend/app/components/shared/TenantSwitcher.tsx`
- Modify: `frontend/app/components/shared/Header.tsx`
- Create: `frontend/tests/unit/components/TenantSwitcher.test.tsx`

**Step 1: Write TenantSwitcher tests**

Tests:
- Returns null when user has 0 or 1 memberships
- Shows current tenant name when user has multiple memberships
- Opens dropdown on click
- Calls selectTenant and reloads on selection
- Closes dropdown on outside click / Escape

**Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run tests/unit/components/TenantSwitcher.test.tsx`
Expected: FAIL

**Step 3: Implement TenantSwitcher**

```typescript
"use client";

import { useState, useRef, useEffect } from "react";
import { useTranslations } from "next-intl";
import { useTenantContext } from "@/providers/TenantProvider";

export function TenantSwitcher() {
  const t = useTranslations("header");
  const { memberships, selectedTenantId, selectedTenantName, selectTenant } = useTenantContext();
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Only show for multi-tenant users
  if (memberships.length <= 1) return null;

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === "Escape") setIsOpen(false);
    };
    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside);
      document.addEventListener("keydown", handleEscape);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [isOpen]);

  const handleSelect = async (tenantId: string) => {
    setIsOpen(false);
    if (tenantId === selectedTenantId) return;
    await selectTenant(tenantId);
    window.location.reload(); // Full reload to refresh all contexts (AdminContext etc.)
  };

  return (
    <div className="relative" ref={dropdownRef}>
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center gap-1 rounded-md px-2 py-1 text-sm text-gray-700 hover:bg-gray-100"
        aria-label={t("switchTenant")}
      >
        <span className="max-w-[120px] truncate">{selectedTenantName}</span>
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>
      {isOpen && (
        <div className="absolute right-0 z-50 mt-1 w-56 rounded-md bg-white shadow-lg ring-1 ring-black ring-opacity-5">
          <div className="py-1" role="menu">
            {memberships.map((m) => (
              <button
                key={m.tenantId}
                type="button"
                role="menuitem"
                onClick={() => handleSelect(m.tenantId)}
                className={`w-full px-4 py-2 text-left text-sm hover:bg-gray-100 ${
                  m.tenantId === selectedTenantId ? "bg-blue-50 font-medium text-blue-700" : "text-gray-700"
                }`}
              >
                {m.tenantName}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
```

**Step 4: Integrate into Header**

In `Header.tsx`, import `TenantSwitcher` and add it before the nav links (in the desktop nav area). Place between the logo/brand and the nav links:

```typescript
import { TenantSwitcher } from "./TenantSwitcher";

// In the header's desktop nav section, add:
<TenantSwitcher />
```

Also add `TenantSwitcher` in the mobile drawer if appropriate.

**Step 5: Run tests to verify they pass**

Run: `cd frontend && npx vitest run tests/unit/components/TenantSwitcher.test.tsx`
Expected: PASS

**Step 6: Commit**

```bash
git add frontend/app/components/shared/TenantSwitcher.tsx frontend/app/components/shared/Header.tsx frontend/tests/unit/components/TenantSwitcher.test.tsx
git commit -m "feat: add tenant switcher to header for multi-tenant users"
```

---

### Task 13: Create Tenant Assignment Dialog Component

**Files:**
- Create: `frontend/app/components/admin/AssignTenantDialog.tsx`
- Create: `frontend/tests/unit/components/AssignTenantDialog.test.tsx`

**Step 1: Write tests**

Tests:
- Renders search input and search button
- Calls searchForAssignment API on search
- Displays search results with email and name
- Shows "Already in tenant" badge for assigned users
- Disables assign button for already-assigned users
- Shows displayName input when user is selected
- Calls assignTenant API on assign
- Calls onAssigned callback on success
- Closes on cancel
- Closes on Escape key

**Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run tests/unit/components/AssignTenantDialog.test.tsx`
Expected: FAIL

**Step 3: Implement AssignTenantDialog**

```typescript
"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { api } from "@/services/api";
import { LoadingSpinner } from "@/components/shared/LoadingSpinner";

interface AssignTenantDialogProps {
  onClose: () => void;
  onAssigned: () => void;
}

interface SearchResult {
  userId: string;
  email: string;
  name: string;
  isAlreadyInTenant: boolean;
}

export function AssignTenantDialog({ onClose, onAssigned }: AssignTenantDialogProps) {
  const t = useTranslations("admin.members.assignTenant");
  const [searchEmail, setSearchEmail] = useState("");
  const [results, setResults] = useState<SearchResult[]>([]);
  const [searching, setSearching] = useState(false);
  const [selectedUser, setSelectedUser] = useState<SearchResult | null>(null);
  const [displayName, setDisplayName] = useState("");
  const [assigning, setAssigning] = useState(false);
  const [searched, setSearched] = useState(false);

  const handleSearch = async () => {
    if (!searchEmail.trim()) return;
    setSearching(true);
    setSelectedUser(null);
    try {
      const response = await api.admin.users.searchForAssignment(searchEmail);
      setResults(response.users);
      setSearched(true);
    } catch {
      setResults([]);
    } finally {
      setSearching(false);
    }
  };

  const handleSelectUser = (user: SearchResult) => {
    if (user.isAlreadyInTenant) return;
    setSelectedUser(user);
    setDisplayName(user.name || "");
  };

  const handleAssign = async () => {
    if (!selectedUser || !displayName.trim()) return;
    setAssigning(true);
    try {
      await api.admin.members.assignTenant(selectedUser.userId, displayName.trim());
      onAssigned();
    } catch {
      setAssigning(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") onClose();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      onKeyDown={handleKeyDown}
      role="dialog"
      aria-modal="true"
      aria-label={t("title")}
    >
      <div className="mx-4 w-full max-w-lg rounded-lg bg-white p-6 shadow-xl">
        <h2 className="mb-4 text-lg font-semibold text-gray-900">{t("title")}</h2>

        {/* Search */}
        <div className="mb-4 flex gap-2">
          <input
            type="email"
            value={searchEmail}
            onChange={(e) => setSearchEmail(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSearch()}
            placeholder={t("searchPlaceholder")}
            className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm"
            autoFocus
          />
          <button
            type="button"
            onClick={handleSearch}
            disabled={searching || !searchEmail.trim()}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {searching ? <LoadingSpinner size="sm" /> : t("search")}
          </button>
        </div>

        {/* Results */}
        {searched && results.length === 0 && (
          <p className="mb-4 text-sm text-gray-500">{t("noResults")}</p>
        )}
        {results.length > 0 && (
          <div className="mb-4 max-h-48 overflow-y-auto rounded-md border border-gray-200">
            {results.map((user) => (
              <button
                key={user.userId}
                type="button"
                disabled={user.isAlreadyInTenant}
                onClick={() => handleSelectUser(user)}
                className={`w-full border-b border-gray-100 px-4 py-3 text-left last:border-0 ${
                  selectedUser?.userId === user.userId
                    ? "bg-blue-50"
                    : user.isAlreadyInTenant
                      ? "cursor-not-allowed opacity-50"
                      : "hover:bg-gray-50"
                }`}
              >
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-sm font-medium text-gray-900">{user.name || user.email}</div>
                    <div className="text-xs text-gray-500">{user.email}</div>
                  </div>
                  {user.isAlreadyInTenant && (
                    <span className="rounded-full bg-gray-100 px-2 py-1 text-xs text-gray-500">
                      {t("alreadyAssigned")}
                    </span>
                  )}
                </div>
              </button>
            ))}
          </div>
        )}

        {/* Display Name + Assign */}
        {selectedUser && (
          <div className="mb-4">
            <label className="mb-1 block text-sm font-medium text-gray-700">
              {t("displayName")}
            </label>
            <input
              type="text"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
          </div>
        )}

        {/* Actions */}
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            {t("cancel")}
          </button>
          {selectedUser && (
            <button
              type="button"
              onClick={handleAssign}
              disabled={assigning || !displayName.trim()}
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {assigning ? <LoadingSpinner size="sm" /> : t("assign")}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
```

**Step 4: Run tests to verify they pass**

Run: `cd frontend && npx vitest run tests/unit/components/AssignTenantDialog.test.tsx`
Expected: PASS

**Step 5: Commit**

```bash
git add frontend/app/components/admin/AssignTenantDialog.tsx frontend/tests/unit/components/AssignTenantDialog.test.tsx
git commit -m "feat: add tenant assignment dialog for admin members page"
```

---

### Task 14: Integrate Assignment Dialog into Admin Members Page

**Files:**
- Modify: `frontend/app/admin/members/page.tsx`

**Step 1: Add "Assign User to Tenant" button and dialog**

Import `AssignTenantDialog` and `useAdminContext`:

```typescript
import { AssignTenantDialog } from "@/components/admin/AssignTenantDialog";
```

Add state for dialog visibility:
```typescript
const [showAssignDialog, setShowAssignDialog] = useState(false);
```

Add button next to the existing "Invite" button (only if user has `member.assign_tenant` permission):

```typescript
{hasPermission("member.assign_tenant") && (
  <button
    type="button"
    onClick={() => setShowAssignDialog(true)}
    className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
  >
    {t("assignTenant.button")}
  </button>
)}
```

Add dialog render at the end of the component:
```typescript
{showAssignDialog && (
  <AssignTenantDialog
    onClose={() => setShowAssignDialog(false)}
    onAssigned={() => {
      setShowAssignDialog(false);
      setRefreshKey((k) => k + 1);
      // Show success toast
    }}
  />
)}
```

**Step 2: Commit**

```bash
git add frontend/app/admin/members/page.tsx
git commit -m "feat: add assign user to tenant button on admin members page"
```

---

### Task 15: Manual Integration Testing

**Step 1: Run all frontend tests**

Run: `cd frontend && npm test -- --run`
Expected: All tests PASS

**Step 2: Run lint and format check**

Run: `cd frontend && npx biome ci`
Expected: No errors

**Step 3: Test login flow manually (if dev server available)**

- Log in as UNAFFILIATED user → should see `/waiting` page
- Log in as AFFILIATED_NO_ORG user → should see `/pending-organization` page
- Log in as multi-tenant user → should see `/select-tenant` page
- Log in as single-tenant user → should go to `/worklog` directly
- Test tenant switcher in header for multi-tenant users
- Test admin assignment dialog

**Step 4: Run E2E tests**

Run: `cd frontend && npx playwright test --project=chromium`
Expected: All existing tests still PASS (new pages shouldn't break existing flows)

---

### Task 16: Write E2E Tests

**Files:**
- Create: `frontend/tests/e2e/tenant-flow.spec.ts`

**Step 1: Write E2E tests for tenant flow**

Tests require test data setup (mock users in different states). These tests should cover:
- Login as UNAFFILIATED user → redirected to `/waiting`
- Login as multi-tenant user → redirected to `/select-tenant` → select → arrives at `/worklog`
- Admin assigns user → user's waiting page transitions
- Tenant switcher works for multi-tenant user

Note: E2E tests depend on backend test data. Consult `frontend/tests/e2e/fixtures/` for existing patterns.

**Step 2: Run E2E tests**

Run: `cd frontend && npx playwright test --project=chromium tests/e2e/tenant-flow.spec.ts`

**Step 3: Commit**

```bash
git add frontend/tests/e2e/tenant-flow.spec.ts
git commit -m "test: add E2E tests for tenant assignment flow"
```
