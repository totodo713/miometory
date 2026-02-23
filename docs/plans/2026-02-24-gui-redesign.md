# GUI Redesign Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fill 5 feature gaps and renovate UX across all areas (responsive, skeletons, toasts, confirmations, breadcrumbs, empty states, accessibility).

**Architecture:** Area-by-area renovation — build shared UX foundation components first, then apply to Admin, Worklog, Auth, and Notification areas. Each area gets gap-fill + UX polish in a single pass.

**Tech Stack:** Next.js 16 + React 19, Tailwind CSS v4, Vitest + React Testing Library, Zod, TanStack React Query, Zustand

**Design Document:** `docs/plans/2026-02-24-gui-redesign-design.md`

**Test convention:** `frontend/tests/unit/**/*.test.tsx`, mock api with `vi.mock("@/services/api")`, use `@testing-library/react` + `userEvent`.

---

## Phase 1: Shared UX Foundation

### Task 1: useMediaQuery hook

**Files:**
- Create: `frontend/app/hooks/useMediaQuery.ts`
- Test: `frontend/tests/unit/hooks/useMediaQuery.test.ts`

**Step 1: Write the test**

```typescript
// frontend/tests/unit/hooks/useMediaQuery.test.ts
import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { useMediaQuery } from "@/hooks/useMediaQuery";

describe("useMediaQuery", () => {
  let listeners: Array<(e: { matches: boolean }) => void> = [];

  beforeEach(() => {
    listeners = [];
    Object.defineProperty(window, "matchMedia", {
      writable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        addEventListener: (_: string, fn: (e: { matches: boolean }) => void) => {
          listeners.push(fn);
        },
        removeEventListener: vi.fn(),
      })),
    });
  });

  it("returns false when media query does not match", () => {
    const { result } = renderHook(() => useMediaQuery("(min-width: 768px)"));
    expect(result.current).toBe(false);
  });

  it("updates when media query changes", () => {
    const { result } = renderHook(() => useMediaQuery("(min-width: 768px)"));
    act(() => {
      listeners.forEach((fn) => fn({ matches: true }));
    });
    expect(result.current).toBe(true);
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run tests/unit/hooks/useMediaQuery.test.ts`
Expected: FAIL — module not found

**Step 3: Implement**

```typescript
// frontend/app/hooks/useMediaQuery.ts
"use client";

import { useState, useEffect } from "react";

export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(false);

  useEffect(() => {
    const mql = window.matchMedia(query);
    setMatches(mql.matches);

    const handler = (e: MediaQueryListEvent | { matches: boolean }) => {
      setMatches(e.matches);
    };
    mql.addEventListener("change", handler);
    return () => mql.removeEventListener("change", handler);
  }, [query]);

  return matches;
}
```

**Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run tests/unit/hooks/useMediaQuery.test.ts`
Expected: PASS

**Step 5: Commit**

```bash
git add frontend/app/hooks/useMediaQuery.ts frontend/tests/unit/hooks/useMediaQuery.test.ts
git commit -m "feat(frontend): add useMediaQuery hook"
```

---

### Task 2: Toast notification system

**Files:**
- Create: `frontend/app/hooks/useToast.ts`
- Create: `frontend/app/components/shared/ToastProvider.tsx`
- Create: `frontend/app/components/shared/Toast.tsx`
- Test: `frontend/tests/unit/components/shared/Toast.test.tsx`

**Step 1: Write the test**

```typescript
// frontend/tests/unit/components/shared/Toast.test.tsx
import { render, screen, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, beforeEach, vi } from "vitest";
import { ToastProvider } from "@/components/shared/ToastProvider";
import { useToast } from "@/hooks/useToast";

function TestComponent() {
  const toast = useToast();
  return (
    <>
      <button onClick={() => toast.success("保存しました")}>success</button>
      <button onClick={() => toast.error("エラーが発生しました")}>error</button>
    </>
  );
}

describe("Toast", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  it("shows success toast and auto-dismisses after 3s", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(
      <ToastProvider>
        <TestComponent />
      </ToastProvider>
    );
    await user.click(screen.getByText("success"));
    expect(screen.getByText("保存しました")).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(3500);
    });
    await waitFor(() => {
      expect(screen.queryByText("保存しました")).not.toBeInTheDocument();
    });
  });

  it("shows error toast", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(
      <ToastProvider>
        <TestComponent />
      </ToastProvider>
    );
    await user.click(screen.getByText("error"));
    expect(screen.getByText("エラーが発生しました")).toBeInTheDocument();
  });

  it("can be manually dismissed", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(
      <ToastProvider>
        <TestComponent />
      </ToastProvider>
    );
    await user.click(screen.getByText("success"));
    const closeButton = screen.getByRole("button", { name: /close/i });
    await user.click(closeButton);
    expect(screen.queryByText("保存しました")).not.toBeInTheDocument();
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run tests/unit/components/shared/Toast.test.tsx`
Expected: FAIL

**Step 3: Implement Toast, ToastProvider, useToast**

```typescript
// frontend/app/hooks/useToast.ts
"use client";

import { useContext } from "react";
import { ToastContext } from "@/components/shared/ToastProvider";

export type ToastVariant = "success" | "error" | "warning" | "info";

export interface ToastItem {
  id: string;
  message: string;
  variant: ToastVariant;
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) throw new Error("useToast must be used within ToastProvider");
  return context;
}
```

```typescript
// frontend/app/components/shared/ToastProvider.tsx
"use client";

import { createContext, useCallback, useState, type ReactNode } from "react";
import { Toast } from "./Toast";
import type { ToastItem, ToastVariant } from "@/hooks/useToast";

interface ToastContextValue {
  success: (message: string) => void;
  error: (message: string) => void;
  warning: (message: string) => void;
  info: (message: string) => void;
}

export const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const addToast = useCallback((message: string, variant: ToastVariant) => {
    const id = crypto.randomUUID();
    setToasts((prev) => [...prev, { id, message, variant }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 3000);
  }, []);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const value: ToastContextValue = {
    success: useCallback((msg: string) => addToast(msg, "success"), [addToast]),
    error: useCallback((msg: string) => addToast(msg, "error"), [addToast]),
    warning: useCallback((msg: string) => addToast(msg, "warning"), [addToast]),
    info: useCallback((msg: string) => addToast(msg, "info"), [addToast]),
  };

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        className="fixed top-4 right-4 z-50 flex flex-col gap-2"
        aria-live="polite"
      >
        {toasts.map((toast) => (
          <Toast key={toast.id} toast={toast} onClose={() => removeToast(toast.id)} />
        ))}
      </div>
    </ToastContext.Provider>
  );
}
```

```typescript
// frontend/app/components/shared/Toast.tsx
"use client";

import type { ToastItem } from "@/hooks/useToast";

const variantStyles: Record<string, string> = {
  success: "bg-green-50 border-green-200 text-green-800",
  error: "bg-red-50 border-red-200 text-red-800",
  warning: "bg-yellow-50 border-yellow-200 text-yellow-800",
  info: "bg-blue-50 border-blue-200 text-blue-800",
};

interface ToastProps {
  toast: ToastItem;
  onClose: () => void;
}

export function Toast({ toast, onClose }: ToastProps) {
  return (
    <div
      className={`flex items-center gap-3 px-4 py-3 rounded-lg border shadow-lg min-w-72 animate-in slide-in-from-right ${variantStyles[toast.variant]}`}
      role="status"
    >
      <span className="flex-1 text-sm font-medium">{toast.message}</span>
      <button
        onClick={onClose}
        className="text-current opacity-60 hover:opacity-100"
        aria-label="close"
      >
        ✕
      </button>
    </div>
  );
}
```

**Step 4: Run test**

Run: `cd frontend && npx vitest run tests/unit/components/shared/Toast.test.tsx`
Expected: PASS

**Step 5: Commit**

```bash
git add frontend/app/hooks/useToast.ts frontend/app/components/shared/ToastProvider.tsx frontend/app/components/shared/Toast.tsx frontend/tests/unit/components/shared/Toast.test.tsx
git commit -m "feat(frontend): add toast notification system"
```

---

### Task 3: ConfirmDialog component

**Files:**
- Create: `frontend/app/components/shared/ConfirmDialog.tsx`
- Test: `frontend/tests/unit/components/shared/ConfirmDialog.test.tsx`

**Step 1: Write the test**

```typescript
// frontend/tests/unit/components/shared/ConfirmDialog.test.tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";

describe("ConfirmDialog", () => {
  const defaultProps = {
    open: true,
    title: "確認",
    message: "本当に無効化しますか？",
    confirmLabel: "無効化",
    variant: "danger" as const,
    onConfirm: vi.fn(),
    onCancel: vi.fn(),
  };

  it("renders title and message when open", () => {
    render(<ConfirmDialog {...defaultProps} />);
    expect(screen.getByText("確認")).toBeInTheDocument();
    expect(screen.getByText("本当に無効化しますか？")).toBeInTheDocument();
  });

  it("does not render when closed", () => {
    render(<ConfirmDialog {...defaultProps} open={false} />);
    expect(screen.queryByText("確認")).not.toBeInTheDocument();
  });

  it("calls onConfirm when confirm button clicked", async () => {
    const user = userEvent.setup();
    render(<ConfirmDialog {...defaultProps} />);
    await user.click(screen.getByRole("button", { name: "無効化" }));
    expect(defaultProps.onConfirm).toHaveBeenCalled();
  });

  it("calls onCancel when cancel button clicked", async () => {
    const user = userEvent.setup();
    render(<ConfirmDialog {...defaultProps} />);
    await user.click(screen.getByRole("button", { name: "キャンセル" }));
    expect(defaultProps.onCancel).toHaveBeenCalled();
  });

  it("calls onCancel when Escape is pressed", async () => {
    const user = userEvent.setup();
    render(<ConfirmDialog {...defaultProps} />);
    await user.keyboard("{Escape}");
    expect(defaultProps.onCancel).toHaveBeenCalled();
  });
});
```

**Step 2: Run test → FAIL**

**Step 3: Implement**

```typescript
// frontend/app/components/shared/ConfirmDialog.tsx
"use client";

import { useEffect, useRef } from "react";

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  variant: "danger" | "warning";
  onConfirm: () => void;
  onCancel: () => void;
}

const confirmButtonStyles = {
  danger: "bg-red-600 hover:bg-red-700 text-white",
  warning: "bg-yellow-600 hover:bg-yellow-700 text-white",
};

export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  variant,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const cancelRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (open) cancelRef.current?.focus();
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") onCancel();
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [open, onCancel]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={onCancel}>
      <div
        className="bg-white rounded-lg shadow-xl p-6 max-w-sm w-full mx-4"
        onClick={(e) => e.stopPropagation()}
        role="alertdialog"
        aria-labelledby="confirm-title"
        aria-describedby="confirm-message"
      >
        <h3 id="confirm-title" className="text-lg font-semibold text-gray-900">{title}</h3>
        <p id="confirm-message" className="mt-2 text-sm text-gray-600">{message}</p>
        <div className="mt-4 flex justify-end gap-3">
          <button
            ref={cancelRef}
            onClick={onCancel}
            className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50"
          >
            キャンセル
          </button>
          <button
            onClick={onConfirm}
            className={`px-4 py-2 text-sm font-medium rounded-md ${confirmButtonStyles[variant]}`}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
```

**Step 4: Run test → PASS**

**Step 5: Commit**

```bash
git add frontend/app/components/shared/ConfirmDialog.tsx frontend/tests/unit/components/shared/ConfirmDialog.test.tsx
git commit -m "feat(frontend): add ConfirmDialog component"
```

---

### Task 4: Breadcrumbs component

**Files:**
- Create: `frontend/app/components/shared/Breadcrumbs.tsx`
- Test: `frontend/tests/unit/components/shared/Breadcrumbs.test.tsx`

**Step 1: Write the test**

```typescript
// frontend/tests/unit/components/shared/Breadcrumbs.test.tsx
import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { Breadcrumbs } from "@/components/shared/Breadcrumbs";

describe("Breadcrumbs", () => {
  it("renders items with links except the last one", () => {
    render(
      <Breadcrumbs
        items={[
          { label: "管理", href: "/admin" },
          { label: "メンバー管理" },
        ]}
      />
    );
    const link = screen.getByRole("link", { name: "管理" });
    expect(link).toHaveAttribute("href", "/admin");
    expect(screen.getByText("メンバー管理")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "メンバー管理" })).not.toBeInTheDocument();
  });

  it("renders separator between items", () => {
    const { container } = render(
      <Breadcrumbs
        items={[
          { label: "管理", href: "/admin" },
          { label: "テナント管理" },
        ]}
      />
    );
    expect(container.textContent).toContain("/");
  });
});
```

**Step 2: Run test → FAIL**

**Step 3: Implement**

```typescript
// frontend/app/components/shared/Breadcrumbs.tsx
import Link from "next/link";

interface BreadcrumbItem {
  label: string;
  href?: string;
}

interface BreadcrumbsProps {
  items: BreadcrumbItem[];
}

export function Breadcrumbs({ items }: BreadcrumbsProps) {
  return (
    <nav aria-label="パンくずリスト" className="mb-4">
      <ol className="flex items-center gap-2 text-sm text-gray-500">
        {items.map((item, index) => (
          <li key={index} className="flex items-center gap-2">
            {index > 0 && <span aria-hidden="true">/</span>}
            {item.href ? (
              <Link href={item.href} className="hover:text-gray-700 hover:underline">
                {item.label}
              </Link>
            ) : (
              <span className="text-gray-900 font-medium">{item.label}</span>
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}
```

**Step 4: Run test → PASS**

**Step 5: Commit**

```bash
git add frontend/app/components/shared/Breadcrumbs.tsx frontend/tests/unit/components/shared/Breadcrumbs.test.tsx
git commit -m "feat(frontend): add Breadcrumbs component"
```

---

### Task 5: Skeleton loader component

**Files:**
- Create: `frontend/app/components/shared/Skeleton.tsx`
- Test: `frontend/tests/unit/components/shared/Skeleton.test.tsx`

**Step 1: Write the test**

```typescript
// frontend/tests/unit/components/shared/Skeleton.test.tsx
import { render } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { Skeleton } from "@/components/shared/Skeleton";

describe("Skeleton", () => {
  it("renders table skeleton with correct rows and cols", () => {
    const { container } = render(<Skeleton.Table rows={3} cols={4} />);
    const rows = container.querySelectorAll("tr");
    expect(rows).toHaveLength(3);
    rows.forEach((row) => {
      expect(row.querySelectorAll("td")).toHaveLength(4);
    });
  });

  it("renders text skeleton with correct lines", () => {
    const { container } = render(<Skeleton.Text lines={3} />);
    const bars = container.querySelectorAll('[data-testid="skeleton-line"]');
    expect(bars).toHaveLength(3);
  });

  it("renders card skeleton", () => {
    const { container } = render(<Skeleton.Card />);
    expect(container.querySelector('[data-testid="skeleton-card"]')).toBeInTheDocument();
  });
});
```

**Step 2: Run test → FAIL**

**Step 3: Implement**

```typescript
// frontend/app/components/shared/Skeleton.tsx
const pulse = "animate-pulse bg-gray-200 rounded";

function Table({ rows, cols }: { rows: number; cols: number }) {
  return (
    <table className="w-full">
      <tbody>
        {Array.from({ length: rows }).map((_, r) => (
          <tr key={r} className="border-b border-gray-100">
            {Array.from({ length: cols }).map((_, c) => (
              <td key={c} className="px-4 py-3">
                <div className={`h-4 ${pulse}`} style={{ width: `${60 + Math.random() * 30}%` }} />
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function Text({ lines }: { lines: number }) {
  return (
    <div className="space-y-2">
      {Array.from({ length: lines }).map((_, i) => (
        <div
          key={i}
          data-testid="skeleton-line"
          className={`h-4 ${pulse}`}
          style={{ width: i === lines - 1 ? "60%" : "100%" }}
        />
      ))}
    </div>
  );
}

function Card() {
  return (
    <div data-testid="skeleton-card" className="border rounded-lg p-4 space-y-3">
      <div className={`h-5 w-1/3 ${pulse}`} />
      <div className={`h-4 w-2/3 ${pulse}`} />
      <div className={`h-4 w-1/2 ${pulse}`} />
    </div>
  );
}

export const Skeleton = { Table, Text, Card };
```

**Step 4: Run test → PASS**

**Step 5: Commit**

```bash
git add frontend/app/components/shared/Skeleton.tsx frontend/tests/unit/components/shared/Skeleton.test.tsx
git commit -m "feat(frontend): add Skeleton loader component"
```

---

### Task 6: EmptyState component

**Files:**
- Create: `frontend/app/components/shared/EmptyState.tsx`
- Test: `frontend/tests/unit/components/shared/EmptyState.test.tsx`

**Step 1: Write the test**

```typescript
// frontend/tests/unit/components/shared/EmptyState.test.tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { EmptyState } from "@/components/shared/EmptyState";

describe("EmptyState", () => {
  it("renders title and description", () => {
    render(<EmptyState title="データなし" description="まだデータがありません" />);
    expect(screen.getByText("データなし")).toBeInTheDocument();
    expect(screen.getByText("まだデータがありません")).toBeInTheDocument();
  });

  it("renders action button when provided", async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();
    render(
      <EmptyState
        title="プロジェクトなし"
        description="プロジェクトを作成してください"
        action={{ label: "新規作成", onClick }}
      />
    );
    await user.click(screen.getByRole("button", { name: "新規作成" }));
    expect(onClick).toHaveBeenCalled();
  });

  it("does not render action button when not provided", () => {
    render(<EmptyState title="なし" description="なし" />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
```

**Step 2: Run test → FAIL**

**Step 3: Implement**

```typescript
// frontend/app/components/shared/EmptyState.tsx
import type { ReactNode } from "react";

interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description: string;
  action?: {
    label: string;
    onClick: () => void;
  };
}

export function EmptyState({ icon, title, description, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-12 text-center">
      {icon && <div className="mb-4 text-gray-400">{icon}</div>}
      <h3 className="text-lg font-medium text-gray-900">{title}</h3>
      <p className="mt-1 text-sm text-gray-500">{description}</p>
      {action && (
        <button
          onClick={action.onClick}
          className="mt-4 px-4 py-2 text-sm font-medium text-blue-600 bg-blue-50 rounded-md hover:bg-blue-100"
        >
          {action.label}
        </button>
      )}
    </div>
  );
}
```

**Step 4: Run test → PASS**

**Step 5: Commit**

```bash
git add frontend/app/components/shared/EmptyState.tsx frontend/tests/unit/components/shared/EmptyState.test.tsx
git commit -m "feat(frontend): add EmptyState component"
```

---

### Task 7: Wire ToastProvider into root layout

**Files:**
- Modify: `frontend/app/layout.tsx`

**Step 1: Add ToastProvider**

Wrap `{children}` with `<ToastProvider>` inside `<SessionProvider>`:

```tsx
// In layout.tsx, add import:
import { ToastProvider } from "@/components/shared/ToastProvider";

// Wrap in provider tree (inside SessionProvider, around children):
<SessionProvider>
  <ToastProvider>
    <Header />
    {children}
  </ToastProvider>
</SessionProvider>
```

**Step 2: Verify build**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

**Step 3: Commit**

```bash
git add frontend/app/layout.tsx
git commit -m "feat(frontend): wire ToastProvider into root layout"
```

---

## Phase 2: Admin Panel Renovation

### Task 8: API methods for pattern creation

**Files:**
- Modify: `frontend/app/services/api.ts`

**Step 1: Add pattern creation methods**

Add to the `patterns` section of api.ts:

```typescript
// Add to api.admin.patterns:
createFiscalYearPattern: (tenantId: string, data: { name: string; startMonth: number; startDay: number }) =>
  apiClient.post<FiscalYearPatternOption>(`/api/v1/tenants/${tenantId}/fiscal-year-patterns`, data),

createMonthlyPeriodPattern: (tenantId: string, data: { name: string; startDay: number }) =>
  apiClient.post<MonthlyPeriodPatternOption>(`/api/v1/tenants/${tenantId}/monthly-period-patterns`, data),
```

Also add auth methods if not present:

```typescript
// Add to api.auth if not present:
signup: (email: string, password: string) =>
  apiClient.post<void>("/api/v1/auth/signup", { email, password }),

verifyEmail: (token: string) =>
  apiClient.post<void>("/api/v1/auth/verify-email", { token }),
```

Also add date-info method to organizations:

```typescript
// Add to organization or as new namespace:
getDateInfo: (tenantId: string, orgId: string, date: string) =>
  apiClient.post<DateInfoResponse>(`/api/v1/tenants/${tenantId}/organizations/${orgId}/date-info`, { date }),
```

**Step 2: Type check**

Run: `cd frontend && npx tsc --noEmit`

**Step 3: Commit**

```bash
git add frontend/app/services/api.ts
git commit -m "feat(frontend): add API methods for pattern creation, signup, date-info"
```

---

### Task 9: FiscalYearPatternForm

**Files:**
- Create: `frontend/app/components/admin/FiscalYearPatternForm.tsx`
- Test: `frontend/tests/unit/components/admin/FiscalYearPatternForm.test.tsx`

**Step 1: Write the test**

```typescript
// frontend/tests/unit/components/admin/FiscalYearPatternForm.test.tsx
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@/services/api", () => ({
  api: {
    admin: {
      patterns: {
        createFiscalYearPattern: vi.fn(),
      },
    },
  },
}));

import { FiscalYearPatternForm } from "@/components/admin/FiscalYearPatternForm";
import { api } from "@/services/api";

describe("FiscalYearPatternForm", () => {
  const defaultProps = {
    tenantId: "tenant-1",
    open: true,
    onClose: vi.fn(),
    onCreated: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    (api.admin.patterns.createFiscalYearPattern as any).mockResolvedValue({
      id: "new-pattern-id",
      name: "4月1日開始",
      startMonth: 4,
      startDay: 1,
    });
  });

  it("renders form fields", () => {
    render(<FiscalYearPatternForm {...defaultProps} />);
    expect(screen.getByLabelText(/名前/)).toBeInTheDocument();
    expect(screen.getByLabelText(/開始月/)).toBeInTheDocument();
    expect(screen.getByLabelText(/開始日/)).toBeInTheDocument();
  });

  it("submits form and calls onCreated", async () => {
    const user = userEvent.setup();
    render(<FiscalYearPatternForm {...defaultProps} />);

    await user.clear(screen.getByLabelText(/名前/));
    await user.type(screen.getByLabelText(/名前/), "4月1日開始");
    await user.click(screen.getByRole("button", { name: /作成/ }));

    await waitFor(() => {
      expect(api.admin.patterns.createFiscalYearPattern).toHaveBeenCalledWith(
        "tenant-1",
        expect.objectContaining({ name: "4月1日開始" })
      );
      expect(defaultProps.onCreated).toHaveBeenCalled();
    });
  });
});
```

**Step 2: Run test → FAIL**

**Step 3: Implement**

The form should follow the existing modal pattern (TenantForm, ProjectForm). Key fields: name (text), startMonth (number select 1-12), startDay (number select 1-28). Use the same modal overlay pattern: `fixed inset-0 bg-black/50` with centered white form. Escape key closes, onClose callback. onCreated callback with the new pattern data.

**Step 4: Run test → PASS**

**Step 5: Commit**

```bash
git add frontend/app/components/admin/FiscalYearPatternForm.tsx frontend/tests/unit/components/admin/FiscalYearPatternForm.test.tsx
git commit -m "feat(frontend): add FiscalYearPatternForm component"
```

---

### Task 10: MonthlyPeriodPatternForm

Same pattern as Task 9 but simpler — only `name` and `startDay` fields.

**Files:**
- Create: `frontend/app/components/admin/MonthlyPeriodPatternForm.tsx`
- Test: `frontend/tests/unit/components/admin/MonthlyPeriodPatternForm.test.tsx`

Follow exact same pattern as FiscalYearPatternForm. Test and implement.

**Commit:** `feat(frontend): add MonthlyPeriodPatternForm component`

---

### Task 11: Integrate pattern forms into OrganizationForm

**Files:**
- Modify: `frontend/app/components/admin/OrganizationForm.tsx`

**Step 1: Read current OrganizationForm** to understand the exact pattern dropdown section.

**Step 2: Add `[+ 新規作成]` buttons next to each pattern dropdown.**

```tsx
// Next to fiscal year pattern dropdown:
<div className="flex items-center gap-2">
  <select ...>{/* existing dropdown */}</select>
  <button
    type="button"
    onClick={() => setShowFiscalYearForm(true)}
    className="text-sm text-blue-600 hover:text-blue-800 whitespace-nowrap"
  >
    + 新規作成
  </button>
</div>

// Same for monthly period pattern dropdown

// Add form modals:
{showFiscalYearForm && (
  <FiscalYearPatternForm
    tenantId={tenantId}
    open={showFiscalYearForm}
    onClose={() => setShowFiscalYearForm(false)}
    onCreated={(pattern) => {
      setFiscalYearPatterns((prev) => [...prev, pattern]);
      setSelectedFiscalYearPatternId(pattern.id);
      setShowFiscalYearForm(false);
    }}
  />
)}
```

**Step 3: Type check and manual verify**

**Step 4: Commit**

```bash
git add frontend/app/components/admin/OrganizationForm.tsx
git commit -m "feat(frontend): integrate pattern creation into OrganizationForm"
```

---

### Task 12: Responsive AdminNav

**Files:**
- Modify: `frontend/app/components/admin/AdminNav.tsx`
- Modify: `frontend/app/admin/layout.tsx`

**Step 1: Read current AdminNav.tsx and admin layout.tsx**

**Step 2: Add responsive behavior using useMediaQuery**

AdminNav changes:
- Import `useMediaQuery` from `@/hooks/useMediaQuery`
- `const isMobile = useMediaQuery("(max-width: 767px)")`
- `const isTablet = useMediaQuery("(min-width: 768px) and (max-width: 1023px)")`
- Mobile: render hamburger button + slide-in drawer (`fixed inset-y-0 left-0 w-64 bg-white shadow-xl z-40 transform transition`)
- Tablet: collapsed sidebar (icons only, `w-16`, expand on hover to `w-64`)
- Desktop: current sidebar unchanged

Admin layout changes:
- Pass mobile state to layout to adjust main content padding

**Step 3: Type check**

**Step 4: Commit**

```bash
git add frontend/app/components/admin/AdminNav.tsx frontend/app/admin/layout.tsx
git commit -m "feat(frontend): make AdminNav responsive (mobile drawer, tablet collapse)"
```

---

### Task 13-17: Admin List Renovations

Apply identical UX improvements to each list. Do one list at a time.

**Pattern for each list (TenantList, ProjectList, MemberList, UserList, OrganizationList):**

**Files per list:**
- Modify: `frontend/app/components/admin/{List}.tsx`
- Modify: `frontend/app/admin/{resource}/page.tsx` (add breadcrumbs, replace `confirm()` with ConfirmDialog)

**Changes per list:**

1. **Replace loading text with Skeleton:**
   ```tsx
   // Before:
   if (isLoading) return <div className="text-center py-8 text-gray-500">読み込み中...</div>;

   // After:
   if (isLoading) return <Skeleton.Table rows={5} cols={columnCount} />;
   ```

2. **Replace empty text with EmptyState:**
   ```tsx
   // Before:
   if (items.length === 0) return <div className="text-center py-8 text-gray-500">見つかりません</div>;

   // After:
   if (items.length === 0) return (
     <EmptyState
       title="{resource}が見つかりません"
       description={hasFilters ? "検索条件を変更してください" : "まだ{resource}がありません"}
       action={!hasFilters ? { label: "新規作成", onClick: onCreate } : undefined}
     />
   );
   ```

3. **Add toast notifications (in page component):**
   ```tsx
   const toast = useToast();
   const handleDeactivate = async (id: string) => {
     try {
       await api.admin.{resource}.deactivate(id);
       toast.success("{resource}を無効化しました");
       setRefreshKey((k) => k + 1);
     } catch {
       toast.error("無効化に失敗しました");
     }
   };
   ```

4. **Replace confirm() with ConfirmDialog (in page component):**
   ```tsx
   const [confirmTarget, setConfirmTarget] = useState<{ id: string; action: string } | null>(null);

   // Instead of: if (!confirm("無効化しますか？")) return;
   // Show: setConfirmTarget({ id, action: "deactivate" });

   <ConfirmDialog
     open={confirmTarget !== null}
     title="確認"
     message={`この{resource}を${confirmTarget?.action === "deactivate" ? "無効化" : "有効化"}しますか？`}
     confirmLabel={confirmTarget?.action === "deactivate" ? "無効化" : "有効化"}
     variant={confirmTarget?.action === "deactivate" ? "danger" : "warning"}
     onConfirm={() => { executeAction(confirmTarget); setConfirmTarget(null); }}
     onCancel={() => setConfirmTarget(null)}
   />
   ```

5. **Add breadcrumbs (in page component):**
   ```tsx
   <Breadcrumbs items={[{ label: "管理", href: "/admin" }, { label: "{resource}管理" }]} />
   ```

6. **Add mobile card view:**
   ```tsx
   const isMobile = useMediaQuery("(max-width: 767px)");

   // In render:
   {isMobile ? <MobileCardView items={items} ... /> : <TableView items={items} ... />}
   ```

**Commit per list:** `feat(frontend): renovate {Resource}List with skeleton, empty state, confirm, toast, mobile card`

**Order:**
- Task 13: TenantList + tenants page (simplest — start here, establish the pattern)
- Task 14: ProjectList + projects page
- Task 15: MemberList + members page
- Task 16: UserList + users page
- Task 17: OrganizationList + organizations page

---

### Task 18: Admin form toast integration

**Files:**
- Modify: `frontend/app/components/admin/MemberForm.tsx`
- Modify: `frontend/app/components/admin/ProjectForm.tsx`
- Modify: `frontend/app/components/admin/TenantForm.tsx`
- Modify: `frontend/app/components/admin/OrganizationForm.tsx`

**Changes:** In each form's submit handler, add toast notifications:

```tsx
const toast = useToast();

// In try block after API success:
toast.success(isEdit ? "{resource}を更新しました" : "{resource}を作成しました");
onSaved();

// In catch block:
toast.error(error instanceof ApiError ? error.message : "保存に失敗しました");
```

**Commit:** `feat(frontend): add toast notifications to admin forms`

---

## Phase 3: Worklog Renovation

### Task 19: useDateInfo hook + API integration

**Files:**
- Create: `frontend/app/hooks/useDateInfo.ts`
- Test: `frontend/tests/unit/hooks/useDateInfo.test.ts`

**Step 1: Write the test**

```typescript
// frontend/tests/unit/hooks/useDateInfo.test.ts
import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@/services/api", () => ({
  api: {
    admin: {
      organizations: {
        getDateInfo: vi.fn(),
      },
    },
  },
}));

import { useDateInfo } from "@/hooks/useDateInfo";
import { api } from "@/services/api";

describe("useDateInfo", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (api.admin.organizations.getDateInfo as any).mockResolvedValue({
      fiscalYear: "2025年度",
      fiscalPeriod: "第11期",
      monthlyPeriodStart: "2026-01-21",
      monthlyPeriodEnd: "2026-02-20",
    });
  });

  it("fetches date info for given parameters", async () => {
    const { result } = renderHook(() =>
      useDateInfo("tenant-1", "org-1", 2026, 2)
    );
    await waitFor(() => {
      expect(result.current.data).toBeDefined();
      expect(result.current.data?.fiscalYear).toBe("2025年度");
    });
  });

  it("returns isLoading while fetching", () => {
    const { result } = renderHook(() =>
      useDateInfo("tenant-1", "org-1", 2026, 2)
    );
    expect(result.current.isLoading).toBe(true);
  });
});
```

**Step 2: Run → FAIL**

**Step 3: Implement**

```typescript
// frontend/app/hooks/useDateInfo.ts
"use client";

import { useState, useEffect } from "react";
import { api } from "@/services/api";

interface DateInfo {
  fiscalYear: string;
  fiscalPeriod: string;
  monthlyPeriodStart: string;
  monthlyPeriodEnd: string;
}

export function useDateInfo(tenantId: string | undefined, orgId: string | undefined, year: number, month: number) {
  const [data, setData] = useState<DateInfo | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!tenantId || !orgId) return;
    setIsLoading(true);
    const date = `${year}-${String(month).padStart(2, "0")}-01`;
    api.admin.organizations
      .getDateInfo(tenantId, orgId, date)
      .then(setData)
      .catch(() => setData(null))
      .finally(() => setIsLoading(false));
  }, [tenantId, orgId, year, month]);

  return { data, isLoading };
}
```

**Step 4: Run → PASS**

**Step 5: Commit**

```bash
git add frontend/app/hooks/useDateInfo.ts frontend/tests/unit/hooks/useDateInfo.test.ts
git commit -m "feat(frontend): add useDateInfo hook for calendar integration"
```

---

### Task 20: Calendar date-info + responsive

**Files:**
- Modify: `frontend/app/components/worklog/Calendar.tsx`

**Step 1: Read current Calendar.tsx**

**Step 2: Add date-info display below month header**

```tsx
import { useDateInfo } from "@/hooks/useDateInfo";
import { Skeleton } from "@/components/shared/Skeleton";
import { useMediaQuery } from "@/hooks/useMediaQuery";

// Inside component:
const { data: dateInfo, isLoading: dateInfoLoading } = useDateInfo(tenantId, orgId, year, month);
const isMobile = useMediaQuery("(max-width: 767px)");

// In header section, below month title:
{dateInfoLoading ? (
  <Skeleton.Text lines={1} />
) : dateInfo ? (
  <p className="text-xs text-gray-500 mt-1">
    {dateInfo.fiscalYear} {dateInfo.fiscalPeriod} | 月次期間: {formatDate(dateInfo.monthlyPeriodStart)} - {formatDate(dateInfo.monthlyPeriodEnd)}
  </p>
) : null}
```

**Step 3: Add mobile card view**

```tsx
{isMobile ? (
  <div className="divide-y">
    {dates.map((day) => (
      <button
        key={day.date}
        onClick={() => onDateSelect?.(day.date)}
        className="w-full p-3 flex items-center justify-between hover:bg-gray-50"
      >
        <div>
          <span className="font-medium">{formatDay(day.date)}</span>
          <span className="ml-2 text-sm text-gray-500">{getDayOfWeek(day.date)}</span>
        </div>
        <div className="flex items-center gap-2">
          {day.totalHours > 0 && <span className="text-sm">{day.totalHours}h</span>}
          <StatusBadge status={day.status} />
        </div>
      </button>
    ))}
  </div>
) : (
  // Existing grid calendar
)}
```

**Step 4: Type check**

**Step 5: Commit**

```bash
git add frontend/app/components/worklog/Calendar.tsx
git commit -m "feat(frontend): add date-info display and responsive calendar"
```

---

### Task 21: DailyEntryForm improvements

**Files:**
- Modify: `frontend/app/components/worklog/DailyEntryForm.tsx`

**Changes:**

1. **Skeleton loading:** Replace text loading with `<Skeleton.Table rows={3} cols={3} />` for entries and `<Skeleton.Text lines={1} />` for project selector.

2. **Toast notifications:** `useToast()` for save/delete/submit results.

3. **Confirm dialogs for delete and submit:**
   ```tsx
   const [confirmAction, setConfirmAction] = useState<{ type: string; id?: string } | null>(null);

   // Delete: setConfirmAction({ type: "delete", id: entryId })
   // Submit: setConfirmAction({ type: "submit" })

   <ConfirmDialog
     open={confirmAction !== null}
     title={confirmAction?.type === "delete" ? "削除確認" : "提出確認"}
     message={confirmAction?.type === "delete" ? "このエントリを削除しますか？" : "本日分のエントリを提出しますか？提出後は編集できません。"}
     confirmLabel={confirmAction?.type === "delete" ? "削除" : "提出"}
     variant="danger"
     onConfirm={...}
     onCancel={() => setConfirmAction(null)}
   />
   ```

4. **Inline field validation:**
   ```tsx
   // Add border-red-500 to invalid fields:
   <input
     className={`... ${fieldErrors.hours ? "border-red-500" : "border-gray-300"}`}
     aria-invalid={!!fieldErrors.hours}
     aria-describedby={fieldErrors.hours ? "hours-error" : undefined}
   />
   {fieldErrors.hours && (
     <p id="hours-error" className="text-xs text-red-600 mt-1">{fieldErrors.hours}</p>
   )}
   ```

**Commit:** `feat(frontend): improve DailyEntryForm with skeleton, confirm, toast, validation`

---

### Task 22: CSV import SSE progress

**Files:**
- Modify: `frontend/app/components/worklog/CsvUploader.tsx`

**Step 1: Read current CsvUploader.tsx** — check if SSE is already partially implemented.

**Step 2: Ensure EventSource connection after upload**

```tsx
// After successful upload returns importId:
const eventSource = new EventSource(`/api/v1/worklog/csv/import/${importId}/progress`);

eventSource.onmessage = (event) => {
  const data = JSON.parse(event.data);
  setProgress({ current: data.processedRows, total: data.totalRows, errors: data.errorCount });
};

eventSource.addEventListener("complete", (event) => {
  const data = JSON.parse(event.data);
  setResult({ success: data.successCount, errors: data.errorCount, details: data.errors });
  toast.success(`${data.successCount}件のインポートが完了しました`);
  eventSource.close();
});

eventSource.onerror = () => {
  toast.error("インポートの進捗取得に失敗しました");
  eventSource.close();
};
```

**Step 3: Add progress bar UI**

```tsx
{progress && (
  <div className="mt-4">
    <div className="flex justify-between text-sm text-gray-600 mb-1">
      <span>{progress.current} / {progress.total} 件処理中</span>
      <span>{Math.round((progress.current / progress.total) * 100)}%</span>
    </div>
    <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
      <div
        className="h-full bg-blue-600 rounded-full transition-all"
        style={{ width: `${(progress.current / progress.total) * 100}%` }}
      />
    </div>
  </div>
)}
```

**Commit:** `feat(frontend): add SSE progress tracking to CSV import`

---

### Task 23: Monthly summary improvements

**Files:**
- Modify: `frontend/app/components/worklog/MonthlySummary.tsx`
- Modify: `frontend/app/components/worklog/MonthlyApprovalSummary.tsx`

**Changes:** Add skeleton loader and enhanced empty state. Same pattern as admin lists.

**Commit:** `feat(frontend): improve monthly summaries with skeleton and empty state`

---

## Phase 4: Auth Renovation

### Task 24: Signup form implementation

**Files:**
- Modify: `frontend/app/(auth)/signup/page.tsx`
- Create: `frontend/app/(auth)/signup/confirm/page.tsx`
- Test: `frontend/tests/unit/pages/signup.test.tsx`

**Step 1: Write the test**

```typescript
// frontend/tests/unit/pages/signup.test.tsx
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@/services/api", () => ({
  api: { auth: { signup: vi.fn() } },
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/hooks/useToast", () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn() }),
}));

import SignupPage from "../../../app/(auth)/signup/page";
import { api } from "@/services/api";

describe("SignupPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (api.auth.signup as any).mockResolvedValue(undefined);
  });

  it("renders signup form", () => {
    render(<SignupPage />);
    expect(screen.getByLabelText(/メールアドレス/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^パスワード$/)).toBeInTheDocument();
    expect(screen.getByLabelText(/パスワード確認/)).toBeInTheDocument();
  });

  it("validates password match", async () => {
    const user = userEvent.setup();
    render(<SignupPage />);
    await user.type(screen.getByLabelText(/^パスワード$/), "Password1!");
    await user.type(screen.getByLabelText(/パスワード確認/), "Different1!");
    await user.tab();
    expect(screen.getByText(/パスワードが一致しません/)).toBeInTheDocument();
  });

  it("submits form successfully", async () => {
    const user = userEvent.setup();
    render(<SignupPage />);
    await user.type(screen.getByLabelText(/メールアドレス/), "test@example.com");
    await user.type(screen.getByLabelText(/^パスワード$/), "Password1!");
    await user.type(screen.getByLabelText(/パスワード確認/), "Password1!");
    await user.click(screen.getByRole("button", { name: /登録/ }));
    await waitFor(() => {
      expect(api.auth.signup).toHaveBeenCalledWith("test@example.com", "Password1!");
    });
  });
});
```

**Step 2: Run → FAIL**

**Step 3: Implement signup page** — full form with email, password, password confirmation. Use `PasswordStrengthIndicator` (existing component). Zod schema for validation. `onBlur` validation with inline field errors (`border-red-500` + error message). Submit calls `api.auth.signup()`. Success navigates to `/signup/confirm`.

**Step 4: Implement confirmation page** — simple static page: "認証メールを送信しました。メールをご確認ください。" with link back to login.

**Step 5: Run → PASS**

**Step 6: Commit**

```bash
git add frontend/app/\(auth\)/signup/ frontend/tests/unit/pages/signup.test.tsx
git commit -m "feat(frontend): implement signup form with validation and confirmation page"
```

---

### Task 25: Email verification page

**Files:**
- Modify: `frontend/app/(auth)/verify-email/page.tsx`
- Test: `frontend/tests/unit/pages/verify-email.test.tsx`

**Step 1: Write the test**

```typescript
// frontend/tests/unit/pages/verify-email.test.tsx
import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@/services/api", () => ({
  api: { auth: { verifyEmail: vi.fn() } },
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams("token=valid-token"),
}));

import VerifyEmailPage from "../../../app/(auth)/verify-email/page";
import { api } from "@/services/api";

describe("VerifyEmailPage", () => {
  it("shows success state when verification succeeds", async () => {
    (api.auth.verifyEmail as any).mockResolvedValue(undefined);
    render(<VerifyEmailPage />);
    await waitFor(() => {
      expect(screen.getByText(/認証完了/)).toBeInTheDocument();
    });
    expect(api.auth.verifyEmail).toHaveBeenCalledWith("valid-token");
  });

  it("shows error state when verification fails", async () => {
    (api.auth.verifyEmail as any).mockRejectedValue(new Error("invalid"));
    render(<VerifyEmailPage />);
    await waitFor(() => {
      expect(screen.getByText(/トークンが無効/)).toBeInTheDocument();
    });
  });
});
```

**Step 2: Run → FAIL**

**Step 3: Implement** — Three states: loading (spinner + "メールアドレスを確認中..."), success (checkmark + "認証完了" + login link), failure (error icon + "トークンが無効です" + resend link). Auto-call `api.auth.verifyEmail(token)` on mount.

**Step 4: Run → PASS**

**Step 5: Commit**

```bash
git add frontend/app/\(auth\)/verify-email/ frontend/tests/unit/pages/verify-email.test.tsx
git commit -m "feat(frontend): implement email verification page"
```

---

### Task 26: Auth form validation UX

**Files:**
- Modify: `frontend/app/(auth)/login/page.tsx`
- Modify: `frontend/app/(auth)/password-reset/request/page.tsx`
- Modify: `frontend/app/(auth)/password-reset/confirm/page.tsx`

**Changes per form:**

1. Add `onBlur` validation to each field
2. Add `border-red-500` + error message below invalid fields
3. Add `aria-invalid` and `aria-describedby` attributes
4. Disable submit button when form is invalid
5. Add toast on success (where applicable)

Follow the same inline validation pattern established in the signup form (Task 24).

**Commit:** `feat(frontend): improve auth form validation UX with inline errors`

---

## Phase 5: Notification + Global

### Task 27: Notification list page

**Files:**
- Create: `frontend/app/notifications/page.tsx`
- Test: `frontend/tests/unit/pages/notifications.test.tsx`

**Step 1: Write the test**

```typescript
// frontend/tests/unit/pages/notifications.test.tsx
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@/services/api", () => ({
  api: {
    notification: {
      list: vi.fn(),
      markRead: vi.fn(),
      markAllRead: vi.fn(),
    },
  },
}));

import NotificationsPage from "../../../app/notifications/page";
import { api } from "@/services/api";

describe("NotificationsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (api.notification.list as any).mockResolvedValue({
      content: [
        { id: "1", type: "APPROVAL", title: "承認依頼", message: "1月分の承認", isRead: false, createdAt: new Date().toISOString() },
        { id: "2", type: "REJECTION", title: "却下通知", message: "1月分が却下", isRead: true, createdAt: new Date().toISOString() },
      ],
      totalElements: 2,
      unreadCount: 1,
    });
  });

  it("renders notification list", async () => {
    render(<NotificationsPage />);
    await waitFor(() => {
      expect(screen.getByText("承認依頼")).toBeInTheDocument();
      expect(screen.getByText("却下通知")).toBeInTheDocument();
    });
  });

  it("marks all as read", async () => {
    const user = userEvent.setup();
    (api.notification.markAllRead as any).mockResolvedValue(undefined);
    render(<NotificationsPage />);
    await waitFor(() => {
      expect(screen.getByText("承認依頼")).toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: /すべて既読/ }));
    expect(api.notification.markAllRead).toHaveBeenCalled();
  });
});
```

**Step 2: Run → FAIL**

**Step 3: Implement**

Page structure:
- Breadcrumbs: `ホーム / 通知`
- Filter tabs: すべて | 未読 | 既読
- "すべて既読にする" button
- Paginated list using `api.notification.list({ page, size: 20, isRead: filterValue })`
- Each notification card: type icon, title, message, relative timestamp, unread dot
- Click → `api.notification.markRead(id)` + navigate if referenceId exists
- Skeleton loading, EmptyState when no notifications

**Step 4: Run → PASS**

**Step 5: Commit**

```bash
git add frontend/app/notifications/ frontend/tests/unit/pages/notifications.test.tsx
git commit -m "feat(frontend): add notifications list page"
```

---

### Task 28: NotificationBell enhancement

**Files:**
- Modify: `frontend/app/components/shared/NotificationBell.tsx`

**Changes:**
1. Add "すべての通知を見る →" link at bottom of dropdown → `/notifications`
2. Improve unread badge: show count, cap at `99+`
3. Use EmptyState in dropdown when no notifications

**Commit:** `feat(frontend): enhance NotificationBell with full page link and badge count`

---

### Task 29: Responsive Header

**Files:**
- Modify: `frontend/app/components/shared/Header.tsx`

**Changes:**
- Import `useMediaQuery`
- `const isMobile = useMediaQuery("(max-width: 767px)")`
- Desktop: current layout unchanged
- Mobile: logo + notification bell + hamburger button
  - Hamburger opens slide-in drawer with: nav links, user name, logout button
  - Same drawer pattern as AdminNav mobile

**Commit:** `feat(frontend): make Header responsive with mobile hamburger menu`

---

### Task 30: Accessibility improvements

**Files:**
- Modify: `frontend/app/components/shared/ConfirmDialog.tsx` (already has aria, verify focus trap)
- Modify: `frontend/app/components/shared/Toast.tsx` (verify aria-live)
- Modify: All form components (add `aria-invalid`, `aria-describedby`)

**Changes:**
1. Verify ConfirmDialog focus trap works (focus should cycle within dialog)
2. Verify Toast has `aria-live="polite"`
3. Scan all input fields touched in this plan — ensure they have `aria-invalid` when errors present
4. Check `text-gray-500` instances for WCAG AA contrast (4.5:1 ratio). If needed, change to `text-gray-600`

**Commit:** `fix(frontend): improve accessibility (ARIA attributes, color contrast, focus management)`

---

### Task 31: Final verification

**Step 1: Run all tests**

```bash
cd frontend && npm test -- --run
```
Expected: All pass

**Step 2: Type check**

```bash
cd frontend && npx tsc --noEmit
```
Expected: No errors

**Step 3: Build**

```bash
cd frontend && npm run build
```
Expected: Build succeeds

**Step 4: Commit any remaining fixes**

---

## Summary

| Phase | Tasks | New Files | Modified Files |
|-------|-------|-----------|----------------|
| 1. Shared Foundation | 1-7 | 8 | 1 |
| 2. Admin Renovation | 8-18 | 4 | ~15 |
| 3. Worklog Renovation | 19-23 | 2 | ~5 |
| 4. Auth Renovation | 24-26 | 2 | ~4 |
| 5. Notification + Global | 27-31 | 1 | ~3 |
| **Total** | **31** | **17** | **~28** |
