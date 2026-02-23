# GUI Redesign: Gap-fill + UX Full Renovation

**Date**: 2026-02-24
**Approach**: B - Area-by-area full renovation
**Scope**: 5 feature gaps + comprehensive UX polish across all areas

## Background

Backend has 120+ API endpoints across 19 domains. Frontend has 18 pages covering most functionality but with 5 feature gaps and UX quality inconsistencies (responsive: 4/10, loading: 5/10, empty states: 6/10).

## Feature Gaps

| # | Feature | Gap |
|---|---------|-----|
| 1 | Fiscal Year Pattern CRUD | Only read in organization dropdown, no create GUI |
| 2 | Monthly Period Pattern CRUD | Same as above |
| 3 | Signup + Email Verification | Stub pages exist, not connected to API |
| 4 | Notification List Page | Only bell icon dropdown, no dedicated page |
| 5 | Organization date-info | API exists but not exposed in GUI |

## Decisions

- **Pattern management**: Integrated into organization management page (not separate admin page)
- **Signup**: Frontend-only (backend API already exists)
- **date-info**: Integrated into worklog calendar header
- **UX scope**: Full renovation (responsive, skeletons, empty states, toasts, confirmations, breadcrumbs, accessibility)

---

## Section 1: Shared UX Foundation Components

### 1.1 Toast Notification System

New files: `components/shared/Toast.tsx`, `components/shared/ToastProvider.tsx`

- Position: top-right, auto-dismiss 3s, manual close
- Variants: `success` (green), `error` (red), `warning` (yellow), `info` (blue)
- API: `useToast()` hook → `toast.success("保存しました")`
- Render via Portal, fade-in/slide-out animation
- Separate from `useNotifications` (backend polling) — toast is for UI operation feedback

### 1.2 Confirmation Dialog

New file: `components/shared/ConfirmDialog.tsx`

- Props: `title`, `message`, `confirmLabel`, `variant` (`danger` | `warning`), `onConfirm`, `onCancel`
- Modal overlay, Escape/backdrop-click to cancel, focus trap
- Apply to: member/project/tenant/org deactivate, entry/absence delete, user lock, password reset

### 1.3 Breadcrumbs

New file: `components/shared/Breadcrumbs.tsx`

- Props: `items: Array<{ label: string, href?: string }>`
- Last item is current page (no link), `/` separator
- Apply to all admin pages

### 1.4 Skeleton Loader

New file: `components/shared/Skeleton.tsx`

- Variants: `<Skeleton.Table rows={5} cols={4} />`, `<Skeleton.Card />`, `<Skeleton.Text lines={3} />`
- Style: Tailwind `animate-pulse` + `bg-gray-200 rounded`
- Replace all `読み込み中...` text across app

### 1.5 Enhanced Empty State

New file: `components/shared/EmptyState.tsx`

- Props: `icon`, `title`, `description`, `action?` (button)
- Distinguish "no data yet" vs "no search results"
- Replace all plain `text-gray-500` empty messages

---

## Section 2: Admin Panel Renovation

### 2.1 Pattern Management (Integrated in Organization Page)

New files: `components/admin/FiscalYearPatternForm.tsx`, `components/admin/MonthlyPeriodPatternForm.tsx`

Modified: `OrganizationForm.tsx` pattern assignment section

Design:
- Pattern dropdown + `[+ 新規作成]` button next to each dropdown
- Button opens modal dialog for pattern creation
- Fiscal year pattern form: start month (1-12), start day (1-28)
- Monthly period pattern form: start day (1-28)
- On success: dropdown auto-refreshes, new pattern selected, toast notification
- API: `POST /api/v1/tenants/{tenantId}/fiscal-year-patterns`, `POST /api/v1/tenants/{tenantId}/monthly-period-patterns`

### 2.2 Responsive Admin Layout

Modified: `AdminNav.tsx`, admin `layout.tsx`

New hook: `hooks/useMediaQuery.ts`

- Desktop (lg+): Current left sidebar (unchanged)
- Tablet (md-lg): Collapsed sidebar (icons only, expand on hover)
- Mobile (<md): Hamburger menu → slide-in drawer

### 2.3 Unified List Improvements

Apply to ALL list components (MemberList, ProjectList, TenantList, UserList, OrganizationList):

| Aspect | Before | After |
|--------|--------|-------|
| Loading | `読み込み中...` text | `<Skeleton.Table>` |
| Empty state | `text-gray-500` text | `<EmptyState>` + action button |
| Destructive ops | Immediate execution | `<ConfirmDialog>` |
| Operation result | No feedback | Toast notification |
| Breadcrumbs | None | `<Breadcrumbs>` on page |
| Mobile table | Horizontal scroll | Card layout toggle |

### 2.4 Mobile Table → Card View

- `md+`: Current table display
- `<md`: Card list (each row becomes a card)
- Implementation: `<MobileCard>` variant in each list, `useMediaQuery` toggle

---

## Section 3: Worklog Area Renovation

### 3.1 date-info Calendar Integration

Modified: `Calendar.tsx`

New hook: `hooks/useDateInfo.ts`

- Display below month title: fiscal year and monthly period info
- Example: `会計年度: 2025年度 第11期 | 月次期間: 1/21 - 2/20`
- API: `POST /api/v1/tenants/{tenantId}/organizations/{orgId}/date-info`
- Member's organization ID from AdminProvider or api.members.getMember()
- Loading: skeleton text

### 3.2 Responsive Calendar

Modified: `Calendar.tsx`

- Desktop (md+): Current grid calendar
- Mobile (<md): Vertical list (collapsible date cards)
  - Each card shows total hours + status icon
  - Tap to expand → entry list → date link

### 3.3 Daily Entry Form Improvements

Modified: `DailyEntryForm.tsx`

| Aspect | Before | After |
|--------|--------|-------|
| Loading | Text | Skeleton (project selector, entry list) |
| Save result | No feedback | Toast notification |
| Delete | Immediate | Confirmation dialog |
| Submit/Recall | Immediate | Confirmation dialog (submit is critical) |
| Validation | Form-level only | Inline field errors (red border + message) |

### 3.4 CSV Import Progress (SSE)

Modified: `CsvUploader.tsx`, `/worklog/import/page.tsx`

- After upload, connect SSE for real-time progress
- Progress bar (overall %) + row count display
- Error summary (`N件中M件成功、K件エラー`)
- Completion toast notification
- Implementation: `EventSource` API

### 3.5 Monthly Summary Improvements

Modified: `MonthlySummary.tsx`, `MonthlyApprovalSummary.tsx`

- Add skeleton loaders
- Enhanced empty state with call-to-action

---

## Section 4: Auth Area Renovation

### 4.1 Signup Form

Modified: `/(auth)/signup/page.tsx`

New API method: `api.auth.signup(email, password)`

Form fields:
- Email: real-time Zod validation, inline error
- Password: PasswordStrengthIndicator (existing component), minimum requirements display
- Password confirmation: match check
- Submit button: LoadingSpinner during submission
- Success: navigate to signup confirmation page (new)
- Error: ApiError-aware display (email duplicate, etc.)
- Login link at bottom

New file: `/(auth)/signup/confirm/page.tsx` (email confirmation prompt)

### 4.2 Email Verification Page

Modified: `/(auth)/verify-email/page.tsx`

New API method: `api.auth.verifyEmail(token)`

- URL: `/verify-email?token={token}`
- Auto-call API on page load
- Three states: verifying (spinner), success (checkmark + login link), failure (error + resend link)

### 4.3 Form Validation UX Unification

Apply to ALL auth forms (login, signup, password reset):

| Aspect | Before | After |
|--------|--------|-------|
| Field errors | Form-level only | `border-red-500` + error message below field |
| Real-time check | None | `onBlur` validation |
| Submit button | Always enabled | Disabled when validation fails |
| Success feedback | Redirect only | Toast + redirect |
| Accessibility | Missing | `aria-invalid`, `aria-describedby` |

---

## Section 5: Notification Page + Global

### 5.1 Notification List Page

New file: `/app/notifications/page.tsx`

Structure:
- Breadcrumbs (Home / Notifications)
- Filter tabs: All, Unread, Read
- Bulk action: Mark all as read
- Paginated notification list
  - Notification card: type icon, title + message, relative timestamp, unread marker (blue dot)
  - Click: mark as read + navigate to related page
- Empty state, skeleton loaders
- API: `GET /api/v1/notifications?page=0&size=20&isRead=false`

### 5.2 NotificationBell Enhancement

Modified: `NotificationBell.tsx`

- Add "すべての通知を見る →" link at dropdown bottom → `/notifications`
- Unread count badge (show `99+` if >99)
- Use `EmptyState` in dropdown

### 5.3 Responsive Header

Modified: `Header.tsx`

- Desktop: Current layout (logo + nav tabs + notification + username + logout)
- Mobile: Logo + notification bell + hamburger menu (nav + user info in drawer)
- Username hidden on mobile (moved into hamburger)

### 5.4 Accessibility Improvements

Apply globally:

| Aspect | Action |
|--------|--------|
| Focus management | Focus trap/restore on modal open/close |
| ARIA | `aria-live="polite"` on toast, `aria-invalid` on form fields |
| Keyboard | `tabIndex`, `onKeyDown` on all interactive elements |
| Color contrast | Check `text-gray-500`, adjust for WCAG AA compliance |

---

## New Files Summary

```
frontend/app/
├── components/shared/
│   ├── Toast.tsx                    (NEW)
│   ├── ToastProvider.tsx            (NEW)
│   ├── ConfirmDialog.tsx            (NEW)
│   ├── Breadcrumbs.tsx              (NEW)
│   ├── Skeleton.tsx                 (NEW)
│   └── EmptyState.tsx               (NEW)
├── components/admin/
│   ├── FiscalYearPatternForm.tsx    (NEW)
│   └── MonthlyPeriodPatternForm.tsx (NEW)
├── hooks/
│   ├── useMediaQuery.ts             (NEW)
│   ├── useDateInfo.ts               (NEW)
│   └── useToast.ts                  (NEW)
├── notifications/
│   └── page.tsx                     (NEW)
└── (auth)/signup/confirm/
    └── page.tsx                     (NEW)
```

## Modified Files Summary

```
frontend/app/
├── components/shared/
│   ├── Header.tsx                   (responsive)
│   └── NotificationBell.tsx         (dropdown enhancement)
├── components/admin/
│   ├── AdminNav.tsx                 (responsive sidebar)
│   ├── MemberList.tsx               (skeleton, empty, confirm, mobile card)
│   ├── ProjectList.tsx              (skeleton, empty, confirm, mobile card)
│   ├── TenantList.tsx               (skeleton, empty, confirm, mobile card)
│   ├── UserList.tsx                 (skeleton, empty, confirm, mobile card)
│   ├── OrganizationList.tsx         (skeleton, empty, confirm, mobile card)
│   ├── OrganizationForm.tsx         (pattern creation buttons)
│   ├── MemberForm.tsx               (toast, validation UX)
│   ├── ProjectForm.tsx              (toast, validation UX)
│   └── TenantForm.tsx               (toast, validation UX)
├── components/worklog/
│   ├── Calendar.tsx                 (date-info, responsive)
│   ├── DailyEntryForm.tsx           (skeleton, confirm, toast, validation)
│   ├── CsvUploader.tsx              (SSE progress)
│   ├── MonthlySummary.tsx           (skeleton, empty state)
│   └── MonthlyApprovalSummary.tsx   (skeleton, empty state)
├── (auth)/
│   ├── signup/page.tsx              (implement form)
│   ├── verify-email/page.tsx        (implement verification)
│   ├── login/page.tsx               (validation UX)
│   └── password-reset/*/page.tsx    (validation UX)
├── admin/
│   ├── layout.tsx                   (responsive layout)
│   └── */page.tsx                   (breadcrumbs on all admin pages)
├── services/
│   └── api.ts                       (signup, verifyEmail, pattern create methods)
└── layout.tsx                       (wrap with ToastProvider)
```

## Implementation Order

1. Shared UX Foundation (toast, confirm, breadcrumbs, skeleton, empty state, useMediaQuery)
2. Admin Panel Renovation (patterns, responsive, list improvements)
3. Worklog Renovation (date-info, responsive calendar, entry form, CSV progress)
4. Auth Renovation (signup, email verification, validation UX)
5. Notification Page + Global (notification page, bell, header responsive, accessibility)
