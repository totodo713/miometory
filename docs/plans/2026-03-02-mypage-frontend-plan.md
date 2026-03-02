# MyPage Frontend Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Header に UserMenu ドロップダウンを追加し、マイページ画面でプロフィール閲覧・編集を可能にする。

**Architecture:** 既存の NotificationBell.tsx のドロップダウンパターンを踏襲した UserMenu コンポーネントを作成。AuthProvider に updateUser を追加してプロフィール更新後の即時反映を実現。Backend Profile API (#80) は実装済み。

**Tech Stack:** Next.js (App Router), React, next-intl, TypeScript, Vitest, Playwright

---

### Task 1: i18n 翻訳キー追加 (#84)

**Files:**
- Modify: `frontend/messages/en.json` (header セクション + mypage セクション追加)
- Modify: `frontend/messages/ja.json` (同上)

**Step 1: en.json に header キーを追加**

`header` セクションの `"currentTenant": "Current Tenant"` の後に追加:

```json
"myPage": "My Page",
"userMenu": "User menu"
```

**Step 2: en.json に mypage セクションを追加**

`"header"` セクションの閉じ括弧の後（`"breadcrumbs"` の前）に追加:

```json
"mypage": {
  "title": "My Page",
  "profile": {
    "title": "Profile",
    "displayName": "Display Name",
    "email": "Email",
    "organization": "Organization",
    "manager": "Manager",
    "noOrganization": "Not assigned",
    "noManager": "Not assigned",
    "edit": "Edit Profile",
    "editTitle": "Edit Profile",
    "save": "Save",
    "cancel": "Cancel",
    "updated": "Profile updated",
    "updateError": "Failed to update profile",
    "displayNameRequired": "Display name is required",
    "emailRequired": "Email is required",
    "emailInvalid": "Please enter a valid email address",
    "loadError": "Failed to load profile",
    "emailChangedTitle": "Email Changed",
    "emailChangedMessage": "Your email has been changed. Please log in again with your new email.",
    "emailChangedConfirm": "Log out"
  },
  "projects": {
    "title": "Assigned Projects",
    "code": "Code",
    "name": "Project Name",
    "noProjects": "No projects assigned"
  }
}
```

**Step 3: ja.json に同様のキーを追加**

`header` セクションに追加:
```json
"myPage": "マイページ",
"userMenu": "ユーザーメニュー"
```

`mypage` セクション:
```json
"mypage": {
  "title": "マイページ",
  "profile": {
    "title": "プロフィール",
    "displayName": "表示名",
    "email": "メールアドレス",
    "organization": "所属組織",
    "manager": "マネージャー",
    "noOrganization": "未割当",
    "noManager": "未割当",
    "edit": "プロフィールを編集",
    "editTitle": "プロフィール編集",
    "save": "保存",
    "cancel": "キャンセル",
    "updated": "プロフィールを更新しました",
    "updateError": "プロフィールの更新に失敗しました",
    "displayNameRequired": "表示名を入力してください",
    "emailRequired": "メールアドレスを入力してください",
    "emailInvalid": "有効なメールアドレスを入力してください",
    "loadError": "プロフィールの読み込みに失敗しました",
    "emailChangedTitle": "メールアドレスが変更されました",
    "emailChangedMessage": "メールアドレスが変更されました。新しいメールアドレスで再度ログインしてください。",
    "emailChangedConfirm": "ログアウト"
  },
  "projects": {
    "title": "担当プロジェクト",
    "code": "コード",
    "name": "プロジェクト名",
    "noProjects": "プロジェクトが割り当てられていません"
  }
}
```

**Step 4: Commit**
```bash
git add frontend/messages/en.json frontend/messages/ja.json
git commit -m "feat(i18n): add mypage and userMenu translation keys (#84)"
```

---

### Task 2: AuthProvider に updateUser 追加 + API クライアント拡張 (#83)

**Files:**
- Modify: `frontend/app/providers/AuthProvider.tsx`
- Modify: `frontend/app/services/api.ts`

**Step 1: AuthProvider に updateUser メソッド追加**

`AuthProvider.tsx` の `AuthContextValue` インターフェースに追加:
```typescript
updateUser: (updates: Partial<AuthUser>) => void;
```

`AuthProvider` 関数内、`logout` の後に追加:
```typescript
const updateUser = useCallback((updates: Partial<AuthUser>) => {
  setUser((prev) => {
    if (!prev) return prev;
    const updated = { ...prev, ...updates };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
    return updated;
  });
}, []);
```

`value` の `useMemo` に `updateUser` を追加:
```typescript
const value = useMemo(
  () => ({ user, isLoading, login, logout, updateUser }),
  [user, isLoading, login, logout, updateUser],
);
```

**Step 2: API クライアントに profile セクション追加**

`api.ts` の `api` オブジェクト内、`members` セクションの前に追加:

```typescript
/**
 * Profile endpoints (current user)
 */
profile: {
  get: () =>
    apiClient.get<{
      id: string;
      email: string;
      displayName: string;
      organizationName: string | null;
      managerName: string | null;
      isActive: boolean;
    }>("/api/v1/profile"),
  update: (data: { email: string; displayName: string }) =>
    apiClient.put<{ emailChanged: boolean }>("/api/v1/profile", data),
},
```

**Step 3: Commit**
```bash
git add frontend/app/providers/AuthProvider.tsx frontend/app/services/api.ts
git commit -m "feat(frontend): add updateUser to AuthProvider and profile API client (#83)"
```

---

### Task 3: UserMenu ドロップダウンコンポーネント (#81)

**Files:**
- Create: `frontend/app/components/shared/UserMenu.tsx`
- Modify: `frontend/app/components/shared/Header.tsx`

**Step 1: UserMenu.tsx を作成**

NotificationBell.tsx のドロップダウンパターンを踏襲:

```tsx
"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { useEffect, useRef, useState } from "react";
import { useAuthContext } from "@/providers/AuthProvider";

export function UserMenu() {
  const { user, logout } = useAuthContext();
  const t = useTranslations("header");
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  if (!user) return null;

  return (
    <div ref={dropdownRef} className="relative">
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center gap-1 text-sm text-gray-700 hover:text-gray-900"
        aria-label={t("userMenu")}
        aria-expanded={isOpen}
        aria-haspopup="true"
      >
        <span>{user.displayName}</span>
        <svg
          className={`w-4 h-4 transition-transform ${isOpen ? "rotate-180" : ""}`}
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>
      {isOpen && (
        <div className="absolute right-0 mt-2 w-48 bg-white rounded-lg shadow-lg border border-gray-200 z-50 py-1">
          <Link
            href="/mypage"
            onClick={() => setIsOpen(false)}
            className="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
          >
            {t("myPage")}
          </Link>
          <button
            type="button"
            onClick={() => {
              setIsOpen(false);
              logout();
            }}
            className="block w-full text-left px-4 py-2 text-sm text-gray-500 hover:bg-gray-50 hover:text-gray-700"
          >
            {t("logout")}
          </button>
        </div>
      )}
    </div>
  );
}
```

**Step 2: Header.tsx デスクトップ部分を修正**

import に `UserMenu` を追加。
L198-201 の `<span>` + `<button>` を `<UserMenu />` に置換:

Before:
```tsx
<span className="text-sm text-gray-700">{user.displayName}</span>
<button type="button" onClick={logout} className="text-sm text-gray-500 hover:text-gray-700">
  {t("logout")}
</button>
```

After:
```tsx
<UserMenu />
```

**Step 3: Header.tsx モバイルドロワーにマイページリンク追加**

モバイルドロワーの `<nav>` セクション（L123-143 の `</nav>` の前）に追加:

```tsx
<Link
  href="/mypage"
  onClick={closeDrawer}
  className={`block px-3 py-2 text-sm rounded-md ${
    pathname === "/mypage" ? "bg-gray-100 text-gray-900 font-medium" : "text-gray-600 hover:bg-gray-50"
  }`}
>
  {t("myPage")}
</Link>
```

**Step 4: Commit**
```bash
git add frontend/app/components/shared/UserMenu.tsx frontend/app/components/shared/Header.tsx
git commit -m "feat(frontend): create UserMenu dropdown component (#81)"
```

---

### Task 4: マイページ画面の実装 (#82)

**Files:**
- Create: `frontend/app/mypage/layout.tsx`
- Create: `frontend/app/mypage/page.tsx`

**Step 1: layout.tsx を作成**

```tsx
"use client";

import { AuthGuard } from "@/components/shared/AuthGuard";

export default function MypageLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard>{children}</AuthGuard>;
}
```

**Step 2: page.tsx を作成**

プロフィール表示 + 担当プロジェクト一覧 + 編集モーダル:

```tsx
"use client";

import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { useAuthContext } from "@/providers/AuthProvider";
import { api } from "@/services/api";
import { useToast } from "@/hooks/useToast";

interface ProfileData {
  id: string;
  email: string;
  displayName: string;
  organizationName: string | null;
  managerName: string | null;
  isActive: boolean;
}

interface AssignedProject {
  id: string;
  code: string;
  name: string;
}

export default function MypagePage() {
  const t = useTranslations("mypage");
  const { user, logout, updateUser } = useAuthContext();
  const toast = useToast();

  const [profile, setProfile] = useState<ProfileData | null>(null);
  const [projects, setProjects] = useState<AssignedProject[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Edit modal state
  const [isEditing, setIsEditing] = useState(false);
  const [editDisplayName, setEditDisplayName] = useState("");
  const [editEmail, setEditEmail] = useState("");
  const [editErrors, setEditErrors] = useState<{ displayName?: string; email?: string }>({});
  const [saving, setSaving] = useState(false);

  // Email changed dialog
  const [showEmailChanged, setShowEmailChanged] = useState(false);

  const loadProfile = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const profileData = await api.profile.get();
      setProfile(profileData);

      if (user?.memberId) {
        const projectData = await api.members.getAssignedProjects(user.memberId);
        setProjects(projectData.projects);
      }
    } catch {
      setError(t("profile.loadError"));
    } finally {
      setLoading(false);
    }
  }, [user?.memberId, t]);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

  const openEditModal = () => {
    if (!profile) return;
    setEditDisplayName(profile.displayName);
    setEditEmail(profile.email);
    setEditErrors({});
    setIsEditing(true);
  };

  const validateForm = (): boolean => {
    const errors: { displayName?: string; email?: string } = {};
    if (!editDisplayName.trim()) {
      errors.displayName = t("profile.displayNameRequired");
    }
    if (!editEmail.trim()) {
      errors.email = t("profile.emailRequired");
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(editEmail)) {
      errors.email = t("profile.emailInvalid");
    }
    setEditErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSave = async () => {
    if (!validateForm()) return;
    try {
      setSaving(true);
      const result = await api.profile.update({
        email: editEmail.trim(),
        displayName: editDisplayName.trim(),
      });
      setIsEditing(false);

      if (result.emailChanged) {
        setShowEmailChanged(true);
      } else {
        updateUser({ displayName: editDisplayName.trim(), email: editEmail.trim() });
        toast.success(t("profile.updated"));
        loadProfile();
      }
    } catch {
      toast.error(t("profile.updateError"));
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <p className="text-gray-500">{t("profile.title")}...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <p className="text-red-500">{error}</p>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto p-6 space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">{t("title")}</h1>

      {/* Profile Section */}
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-gray-900">{t("profile.title")}</h2>
          <button
            type="button"
            onClick={openEditModal}
            className="text-sm text-blue-600 hover:text-blue-800"
          >
            {t("profile.edit")}
          </button>
        </div>
        <dl className="space-y-3">
          <div className="flex">
            <dt className="w-40 text-sm text-gray-500">{t("profile.displayName")}</dt>
            <dd className="text-sm text-gray-900">{profile?.displayName}</dd>
          </div>
          <div className="flex">
            <dt className="w-40 text-sm text-gray-500">{t("profile.email")}</dt>
            <dd className="text-sm text-gray-900">{profile?.email}</dd>
          </div>
          <div className="flex">
            <dt className="w-40 text-sm text-gray-500">{t("profile.organization")}</dt>
            <dd className="text-sm text-gray-900">
              {profile?.organizationName ?? t("profile.noOrganization")}
            </dd>
          </div>
          <div className="flex">
            <dt className="w-40 text-sm text-gray-500">{t("profile.manager")}</dt>
            <dd className="text-sm text-gray-900">
              {profile?.managerName ?? t("profile.noManager")}
            </dd>
          </div>
        </dl>
      </div>

      {/* Assigned Projects Section */}
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">{t("projects.title")}</h2>
        {projects.length === 0 ? (
          <p className="text-sm text-gray-500">{t("projects.noProjects")}</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200">
                <th className="text-left py-2 text-gray-500 font-medium">{t("projects.code")}</th>
                <th className="text-left py-2 text-gray-500 font-medium">{t("projects.name")}</th>
              </tr>
            </thead>
            <tbody>
              {projects.map((project) => (
                <tr key={project.id} className="border-b border-gray-100">
                  <td className="py-2 text-gray-900">{project.code}</td>
                  <td className="py-2 text-gray-900">{project.name}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Edit Modal */}
      {isEditing && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          {/* biome-ignore lint/a11y/useKeyWithClickEvents: backdrop click to close */}
          {/* biome-ignore lint/a11y/noStaticElementInteractions: backdrop click to close */}
          <div className="absolute inset-0 bg-black/50" onClick={() => setIsEditing(false)} />
          <div className="relative bg-white rounded-lg shadow-xl p-6 w-full max-w-md mx-4">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">{t("profile.editTitle")}</h3>
            <div className="space-y-4">
              <div>
                <label htmlFor="edit-displayName" className="block text-sm font-medium text-gray-700 mb-1">
                  {t("profile.displayName")}
                </label>
                <input
                  id="edit-displayName"
                  type="text"
                  value={editDisplayName}
                  onChange={(e) => setEditDisplayName(e.target.value)}
                  className={`w-full px-3 py-2 border rounded-md text-sm ${
                    editErrors.displayName ? "border-red-500" : "border-gray-300"
                  } focus:outline-none focus:ring-2 focus:ring-blue-500`}
                />
                {editErrors.displayName && (
                  <p className="text-xs text-red-500 mt-1">{editErrors.displayName}</p>
                )}
              </div>
              <div>
                <label htmlFor="edit-email" className="block text-sm font-medium text-gray-700 mb-1">
                  {t("profile.email")}
                </label>
                <input
                  id="edit-email"
                  type="email"
                  value={editEmail}
                  onChange={(e) => setEditEmail(e.target.value)}
                  className={`w-full px-3 py-2 border rounded-md text-sm ${
                    editErrors.email ? "border-red-500" : "border-gray-300"
                  } focus:outline-none focus:ring-2 focus:ring-blue-500`}
                />
                {editErrors.email && (
                  <p className="text-xs text-red-500 mt-1">{editErrors.email}</p>
                )}
              </div>
            </div>
            <div className="flex justify-end gap-3 mt-6">
              <button
                type="button"
                onClick={() => setIsEditing(false)}
                className="px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-md"
              >
                {t("profile.cancel")}
              </button>
              <button
                type="button"
                onClick={handleSave}
                disabled={saving}
                className="px-4 py-2 text-sm text-white bg-blue-600 hover:bg-blue-700 rounded-md disabled:opacity-50"
              >
                {saving ? "..." : t("profile.save")}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Email Changed Dialog */}
      {showEmailChanged && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/50" />
          <div className="relative bg-white rounded-lg shadow-xl p-6 w-full max-w-md mx-4">
            <h3 className="text-lg font-semibold text-gray-900 mb-2">{t("profile.emailChangedTitle")}</h3>
            <p className="text-sm text-gray-600 mb-4">{t("profile.emailChangedMessage")}</p>
            <div className="flex justify-end">
              <button
                type="button"
                onClick={() => logout()}
                className="px-4 py-2 text-sm text-white bg-blue-600 hover:bg-blue-700 rounded-md"
              >
                {t("profile.emailChangedConfirm")}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
```

**Step 3: Commit**
```bash
git add frontend/app/mypage/layout.tsx frontend/app/mypage/page.tsx
git commit -m "feat(frontend): implement mypage screen with profile and projects (#82)"
```

---

### Task 5: Frontend テスト (#85 - Frontend部分)

**Files:**
- Create: `frontend/app/components/shared/__tests__/UserMenu.test.tsx`
- Create: `frontend/app/mypage/__tests__/page.test.tsx`

**Step 1: UserMenu.test.tsx を作成**

```tsx
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { UserMenu } from "../UserMenu";

// Mock next-intl
vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => {
    const translations: Record<string, string> = {
      userMenu: "User menu",
      myPage: "My Page",
      logout: "Logout",
    };
    return translations[key] ?? key;
  },
}));

// Mock next/link
vi.mock("next/link", () => ({
  default: ({ children, href, onClick, ...props }: any) => (
    <a href={href} onClick={onClick} {...props}>{children}</a>
  ),
}));

const mockLogout = vi.fn();
const mockUser = { id: "1", email: "test@example.com", displayName: "Test User" };

vi.mock("@/providers/AuthProvider", () => ({
  useAuthContext: () => ({
    user: mockUser,
    logout: mockLogout,
  }),
}));

describe("UserMenu", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("displays user name", () => {
    render(<UserMenu />);
    expect(screen.getByText("Test User")).toBeInTheDocument();
  });

  it("opens dropdown on click", () => {
    render(<UserMenu />);
    fireEvent.click(screen.getByRole("button", { name: "User menu" }));
    expect(screen.getByText("My Page")).toBeInTheDocument();
    expect(screen.getByText("Logout")).toBeInTheDocument();
  });

  it("closes dropdown on second click", () => {
    render(<UserMenu />);
    const trigger = screen.getByRole("button", { name: "User menu" });
    fireEvent.click(trigger);
    expect(screen.getByText("My Page")).toBeInTheDocument();
    fireEvent.click(trigger);
    expect(screen.queryByText("My Page")).not.toBeInTheDocument();
  });

  it("has mypage link pointing to /mypage", () => {
    render(<UserMenu />);
    fireEvent.click(screen.getByRole("button", { name: "User menu" }));
    const link = screen.getByText("My Page");
    expect(link.closest("a")).toHaveAttribute("href", "/mypage");
  });

  it("calls logout on logout button click", () => {
    render(<UserMenu />);
    fireEvent.click(screen.getByRole("button", { name: "User menu" }));
    fireEvent.click(screen.getByText("Logout"));
    expect(mockLogout).toHaveBeenCalled();
  });
});
```

**Step 2: Run UserMenu tests**

Run: `cd frontend && npm test -- --run app/components/shared/__tests__/UserMenu.test.tsx`
Expected: All 5 tests PASS

**Step 3: page.test.tsx を作成**

基本的な表示テストとモーダルテスト。詳細は既存テストパターン(admin pages)に合わせる。

**Step 4: Run all frontend tests**

Run: `cd frontend && npm test -- --run`
Expected: All tests PASS

**Step 5: Commit**
```bash
git add frontend/app/components/shared/__tests__/UserMenu.test.tsx frontend/app/mypage/__tests__/page.test.tsx
git commit -m "test(frontend): add UserMenu and mypage tests (#85)"
```

---

### Task 6: E2E テスト (#85 - E2E部分)

**Files:**
- Create: `frontend/tests/e2e/mypage.spec.ts`

**Step 1: E2E テストを作成**

ログイン → UserMenu → マイページ遷移 → プロフィール表示 → 編集のフローをテスト。
既存の E2E パターン (`frontend/tests/e2e/`) を踏襲。

**Step 2: Run E2E tests**

Run: `cd frontend && npx playwright test --project=chromium mypage.spec.ts`
Expected: All tests PASS

**Step 3: Commit**
```bash
git add frontend/tests/e2e/mypage.spec.ts
git commit -m "test(e2e): add mypage E2E tests (#85)"
```

---

### Task 7: 全体検証 + format

**Step 1: Biome lint/format チェック**
Run: `cd frontend && npx biome ci .`

**Step 2: TypeScript 型チェック**
Run: `cd frontend && npx tsc --noEmit`

**Step 3: 全テスト実行**
Run: `cd frontend && npm test -- --run`

**Step 4: E2E テスト**
Run: `cd frontend && npx playwright test --project=chromium`

**Step 5: 問題があれば修正して追加コミット**
