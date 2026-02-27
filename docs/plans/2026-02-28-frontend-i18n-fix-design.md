# Frontend i18n Fix Design

**Issue**: #46 — fix(frontend): フロントエンド i18n 未対応箇所の修正
**Date**: 2026-02-28
**Status**: Draft

## Problem

The frontend has a fully configured next-intl i18n framework with 862 synchronized keys in `messages/en.json` and `messages/ja.json`. However, several components still use hardcoded strings, causing language switching to fail in those areas. The password reset pages are the most critical — translation keys already exist but are not being used.

## Approach

Single PR implementing all changes following the issue's 11-step plan. Message keys are added upfront (Step 1), then components are updated in priority order (CRITICAL → HIGH → MEDIUM → LOW), followed by test updates and verification.

## Scope

### Target Files (by priority)

| Priority | File | Hardcoded Count | Language |
|----------|------|-----------------|----------|
| CRITICAL | `app/(auth)/password-reset/confirm/page.tsx` | ~25 | Japanese |
| CRITICAL | `app/(auth)/password-reset/request/page.tsx` | ~20 | Japanese |
| HIGH | `app/components/worklog/Calendar.tsx` | 7 | English+Japanese |
| MEDIUM | `app/components/shared/ErrorBoundary.tsx` | 4 | English |
| MEDIUM | `app/components/worklog/MemberSelector.tsx` | 5 | English |
| MEDIUM | `app/components/worklog/ProjectSelector.tsx` | 6 | English |
| MEDIUM | `app/lib/validation/password.ts` | ~15 | Japanese |
| LOW | `app/page.tsx` | 1 | Japanese |
| LOW | `app/components/shared/AuthGuard.tsx` | 1 | Japanese |
| LOW | `app/(auth)/login/page.tsx` | 2 | English |
| LOW | `app/(auth)/signup/page.tsx` | 1 | English |

### Out of Scope

- `app/layout.tsx` static metadata (requires `generateMetadata()` conversion)
- `LoadingSpinner.tsx` itself (utility component, callers handle text)

## Design

### Step 1: Add New Message Keys

Add keys to both `messages/en.json` and `messages/ja.json` under existing namespaces:

| Namespace | New Keys | Content |
|-----------|----------|---------|
| `passwordReset.confirm` | 7 | loginNow, requestReset, retryAriaLabel, backToLogin, redirectCountdown, errors.passwordValidation, errors.tokenRequired |
| `passwordReset.request` | 4 | submitting, rateLimitReached, remainingAttempts, toastSuccess |
| `worklog.calendar` | 7 | fiscalPeriod, monthlyPeriod, proxyEntryTitle, proxyEntryLabel, rejectionMonthly, rejectionDaily, workLabel |
| `worklog.memberSelector` | 3 | label, placeholder, noMembers |
| `worklog.projectSelector` | 6 | placeholder, loading, noAssigned, toggleList, noMatching, listLabel |
| `auth` | 1 | brandTagline |

Parameterized keys use ICU MessageFormat: `{seconds}`, `{minutes}`, `{count}`, `{start}`, `{end}`, etc.

### Step 2-3: Password Reset Pages (CRITICAL)

**Pattern:**
1. Add `import { useTranslations } from "next-intl"`
2. Declare `const t = useTranslations("passwordReset.confirm")` (or `.request`)
3. Replace all hardcoded strings with `t("keyName")` or `t("keyName", { param: value })`
4. Suspense fallback components get their own `useTranslations` call

**confirm/page.tsx changes:**
- ~25 hardcoded Japanese strings → `t()` calls
- Redirect countdown: `t("redirectCountdown", { seconds: redirectCountdown })`
- Error messages: use existing `passwordReset.confirm.errors.*` keys + new ones

**request/page.tsx changes:**
- ~20 hardcoded Japanese strings → `t()` calls
- Rate limit: `t("rateLimitReached", { minutes })`
- Toast: `t("toastSuccess")`

### Step 4: Calendar (HIGH)

Already uses `useTranslations("worklog.calendar")`. Fix remaining hardcoded strings:
- Template literals building fiscal period strings → `t("fiscalPeriod", { start, end })`
- `t("monthlyPeriod", { fiscalYear, fiscalPeriod, start, end })`
- `title="Contains entries made by manager"` → `title={t("proxyEntryTitle")}`
- Rejection and work label strings

### Step 5: ErrorBoundary (MEDIUM)

- `ErrorBoundary` is a class component — cannot use hooks
- `ErrorFallback` and `ErrorMessage` are function components → add `useTranslations("errorBoundary")`
- Replace "Something went wrong", "Try Again", "Retry" with translation keys

### Step 6: MemberSelector (MEDIUM)

- Add `useTranslations("worklog.memberSelector")`
- Replace hardcoded props defaults with `label ?? t("label")` pattern
- Preserve props override capability

### Step 7: ProjectSelector (MEDIUM)

- Add `useTranslations("worklog.projectSelector")`
- Replace placeholder, loading, aria-label, and empty state strings

### Step 8: Zod Validation Layer (MEDIUM)

**Strategy: Factory function pattern**

Convert static Zod schemas to factory functions that receive translated messages:

```typescript
// Before
export const passwordResetRequestSchema = z.object({
  email: z.string().min(1, { message: "メールアドレスを入力してください" })
});

// After
export function createPasswordResetRequestSchema(messages: {
  emailRequired: string;
  emailInvalid: string;
}) {
  return z.object({
    email: z.string().min(1, { message: messages.emailRequired })
      .email({ message: messages.emailInvalid })
  });
}
```

**Changes:**
- `validateEmail()` → add messages parameter
- `validatePasswordConfirm()` → add messages parameter
- `analyzePasswordStrength()` → add locale parameter (skip `translateFeedback()` for English)
- `PasswordStrengthIndicator.tsx` → use `useLocale()` to pass locale

### Step 9: LOW Priority Files

| File | Change |
|------|--------|
| `app/page.tsx` | `useTranslations("common")` → `t("loading")` |
| `app/components/shared/AuthGuard.tsx` | Same pattern |
| `app/(auth)/login/page.tsx` | `"Time Entry System"` → `t("brandTagline")` |
| `app/(auth)/signup/page.tsx` | `"Miometry Time Entry System"` → `"Miometry"` + `{t("brandTagline")}` |

### Step 10: Test Updates

| Test Area | Change |
|-----------|--------|
| Password reset tests | Add `NextIntlClientProvider` wrapper |
| Calendar tests | Update English text assertions to use translated values |
| ErrorBoundary tests | Add IntlWrapper |
| MemberSelector tests | Add IntlWrapper |
| ProjectSelector tests | Add IntlWrapper |
| password.ts tests | Pass message arguments to factory functions |

### Step 11: Verification

1. `cd frontend && npm test -- --run tests/unit/i18n/key-parity.test.ts` — key parity check
2. `cd frontend && npm test -- --run` — all unit tests
3. `cd frontend && npx biome ci` — lint/format check

## Decision Log

| Decision | Choice | Rationale |
|----------|--------|-----------|
| PR strategy | Single PR | i18n consistency requires atomic delivery of keys + components |
| Zod i18n | Factory function pattern | Type-safe, testable, messages injected at call site |
| Implementation order | Keys first, then components by priority | Unblocks all component work upfront |
