# Research: Password Reset Frontend

**Date**: 2026-02-16
**Feature**: 006-password-reset-frontend

## Research Summary

This feature's implementation is already complete on main. Research focused on understanding existing code patterns, identifying gaps (unit tests, login page link), and confirming technical assumptions from the spec.

## R1: Existing Implementation Status

**Decision**: All password reset frontend code is production-ready on main branch.

**Rationale**: Codebase exploration confirmed the following files exist and pass Biome checks:

| File | Lines | Status |
|------|-------|--------|
| `(auth)/password-reset/request/page.tsx` | 437 | Complete |
| `(auth)/password-reset/confirm/page.tsx` | 714 | Complete |
| `components/auth/PasswordStrengthIndicator.tsx` | 272 | Complete |
| `lib/validation/password.ts` | 311 | Complete |
| `lib/types/password-reset.ts` | 141 | Complete |
| `lib/utils/rate-limit.ts` | 237 | Complete |
| `services/api.ts` (auth section) | — | Complete |

**Alternatives considered**: N/A — implementation already exists.

## R2: Unit Testing Gap Analysis

**Decision**: Create 5 new unit test files following existing patterns (Vitest + React Testing Library).

**Rationale**: Only E2E accessibility tests exist (13 tests in `password-reset-accessibility.spec.ts`). No unit tests exist for any password reset code. Constitution Principle II (Testing Discipline) requires automated assertions for every feature.

**Existing test patterns observed**:
- Import path alias: `@/(auth)/login/page` → maps to `app/(auth)/login/page`
- Libraries: `@testing-library/react` (render, screen, fireEvent), Vitest (describe, test, expect)
- Mocking: Standard Vitest `vi.mock()` for API client and Next.js router
- Structure: `describe` blocks per component/module, `test` per scenario
- File location: `tests/unit/` mirrors `app/` directory structure

**Test files to create**:

| File | Covers | Key Test Areas |
|------|--------|----------------|
| `tests/unit/(auth)/password-reset/request/page.test.tsx` | Request page | Form rendering, email validation, submission, success message, rate limiting UI, error handling, loading state, login link |
| `tests/unit/(auth)/password-reset/confirm/page.test.tsx` | Confirm page | Token extraction, password validation, mismatch detection, strength indicator integration, success redirect, error states (expired/invalid token, network), loading state |
| `tests/unit/components/auth/PasswordStrengthIndicator.test.tsx` | Strength indicator | Rendering for weak/medium/strong, feedback messages, debounce behavior, empty password (no render), onChange callback, ARIA attributes |
| `tests/unit/lib/validation/password.test.ts` | Validation utils | Email validation (valid/invalid formats), password schema (min length, uppercase, lowercase, digit, max length), confirm password matching, strength analysis, Japanese error messages |
| `tests/unit/lib/utils/rate-limit.test.ts` | Rate limiting | checkRateLimit (under/over limit), recordAttempt, expired attempts cleanup, getMinutesUntilReset, localStorage interaction, clearAttempts |

**Alternatives considered**: Could rely solely on E2E tests, but this violates the test pyramid principle (Principle II) and provides slower feedback.

## R3: Login Page Integration

**Decision**: Add a "パスワードをお忘れですか？" (Forgot your password?) link to the login page pointing to `/password-reset/request`.

**Rationale**: FR-001 requires the password reset request page to be "accessible from the login page." The current login page (`app/(auth)/login/page.tsx`, 44 lines) has no link to password reset. All other auth pages in the system include navigation links to related flows.

**Implementation approach**: Add a Next.js `<Link>` element below the login form, following the minimal inline style approach used by the existing login page. Keep the change small and focused.

**Alternatives considered**:
- Adding the link only in the signup page — rejected; users attempting to log in are the primary audience for password reset
- Adding a shared auth navigation component — rejected; over-engineering for a single link addition

## R4: Mocking Strategy for Page Tests

**Decision**: Mock `api.auth.requestPasswordReset` and `api.auth.confirmPasswordReset` via `vi.mock("@/services/api")`. Mock `next/navigation` for `useRouter` and `useSearchParams`.

**Rationale**: Unit tests must be isolated from backend API and Next.js router. The existing E2E tests cover real API integration. Mocking the API client at the module level is consistent with standard React Testing Library practices and allows testing all UI states (loading, success, error, rate limited) without backend dependencies.

**Key mocking needs**:
- `@/services/api` → mock `api.auth.requestPasswordReset()` and `api.auth.confirmPasswordReset()`
- `next/navigation` → mock `useRouter()` (for redirect assertions) and `useSearchParams()` (for token extraction)
- `localStorage` / `sessionStorage` → Vitest provides jsdom with these available
- `@zxcvbn-ts/core` → may need mock for PasswordStrengthIndicator tests to avoid heavy computation in unit tests

**Alternatives considered**:
- MSW (Mock Service Worker) — rejected for unit tests; more appropriate for integration/E2E tests
- Testing with real zxcvbn — acceptable if fast enough; mock only if tests become slow

## R5: Biome Compliance

**Decision**: All new test files must pass `npx biome check` with zero errors before commit.

**Rationale**: Existing password reset implementation files already pass Biome checks (verified). Biome config has relaxed rules for test files (`noExplicitAny: off`, `noConsole: off`), so test-specific patterns are allowed.

**Key Biome rules for test files**:
- Double quotes, semicolons, trailing commas
- 2-space indentation, 120 char line width
- `useConst: error` — use `const` where possible
- `noUnusedImports: error` — clean imports required

## R6: Japanese Localization in Tests

**Decision**: Test assertions for user-facing text must match Japanese strings used in the implementation.

**Rationale**: All validation error messages, UI labels, and feedback text are in Japanese (e.g., `"メールアドレスを入力してください"`, `"パスワードは8文字以上で入力してください"`). Tests must verify the actual displayed text, not English equivalents.

**Key Japanese strings to test**:
- Email validation: `"メールアドレスを入力してください"`, `"有効なメールアドレスを入力してください"`
- Password validation: `"パスワードは8文字以上で入力してください"`, `"パスワードは128文字以内で入力してください"`
- Password mismatch: `"パスワードが一致しません"`
- Token required: `"トークンが必要です"`
- Rate limit: `"リクエストが多すぎます。{N}分後に再試行してください。"`
