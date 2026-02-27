# Frontend i18n Fix Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace all hardcoded strings in 11 frontend files with next-intl translation calls, completing the i18n coverage.

**Architecture:** Add new message keys to en.json/ja.json upfront, then migrate each component to use `useTranslations()` hook (or factory functions for Zod validation). Test wrappers use the existing `IntlWrapper` at `frontend/tests/helpers/intl.tsx`.

**Tech Stack:** Next.js 16.x, React 19.x, next-intl, Zod, Vitest, React Testing Library

---

### Task 1: Add New Message Keys to en.json

**Files:**
- Modify: `frontend/messages/en.json`

**Step 1: Add passwordReset.confirm new keys**

In `en.json`, inside the `passwordReset.confirm` object, add these keys after the existing ones:

```json
"loginNow": "Log in now",
"requestReset": "Request password reset",
"retryAriaLabel": "Clear error and retry",
"backToLogin": "Back to Login",
"redirectCountdown": "Redirecting to login in {seconds} seconds...",
"errors": {
  ... (keep existing error keys),
  "passwordValidation": "Password does not meet requirements.",
  "tokenRequired": "Token is required"
}
```

Note: The `errors` object already exists with keys like `tokenExpired`, `serverError`, etc. Merge the new keys into it.

**Step 2: Add passwordReset.request new keys**

In `en.json`, inside the `passwordReset.request` object, add:

```json
"submitting": "Sending...",
"rateLimitReached": "Request limit reached. Please retry in {minutes} minutes.",
"remainingAttempts": "Remaining attempts: {count}",
"toastSuccess": "Password reset email sent"
```

**Step 3: Add worklog.calendar new keys**

In `en.json`, inside the `worklog.calendar` object, add:

```json
"fiscalPeriod": "Fiscal Period: {start} to {end}",
"monthlyPeriod": "{fiscalYear} {fiscalPeriod} | Monthly Period: {start} - {end}",
"proxyEntryTitle": "Contains entries made by manager",
"proxyEntryLabel": "Proxy entry indicator",
"rejectionMonthly": "Monthly rejection: {reason}",
"rejectionDaily": "Daily rejection: {reason}",
"workLabel": "Work:"
```

**Step 4: Add worklog.memberSelector namespace**

In `en.json`, inside the `worklog` object, add a new `memberSelector` namespace:

```json
"memberSelector": {
  "label": "Select Team Member",
  "placeholder": "Choose a team member...",
  "noMembers": "No team members found. You can only enter time for your direct reports."
}
```

**Step 5: Add worklog.projectSelector namespace**

In `en.json`, inside the `worklog` object, add a new `projectSelector` namespace:

```json
"projectSelector": {
  "placeholder": "Select a project...",
  "loading": "Loading projects...",
  "noAssigned": "No projects assigned. Please contact your administrator.",
  "toggleList": "Toggle project list",
  "noMatching": "No matching projects found",
  "listLabel": "Projects"
}
```

**Step 6: Add auth.brandTagline key**

In `en.json`, inside the `auth` object (top level, not inside `login` or `signup`), add:

```json
"brandTagline": "Time Entry System"
```

**Step 7: Run key parity test to see it fail (ja.json not yet updated)**

Run: `cd frontend && npm test -- --run tests/unit/i18n/key-parity.test.ts`
Expected: FAIL — ja.json missing the new keys

**Step 8: Commit**

```bash
git add frontend/messages/en.json
git commit -m "feat(i18n): add new en.json message keys for i18n coverage (#46)"
```

---

### Task 2: Add New Message Keys to ja.json

**Files:**
- Modify: `frontend/messages/ja.json`

**Step 1: Add passwordReset.confirm new keys**

Mirror en.json structure. In `ja.json`, inside `passwordReset.confirm`:

```json
"loginNow": "今すぐログインする",
"requestReset": "パスワードリセットをリクエスト",
"retryAriaLabel": "エラーをクリアして再試行",
"backToLogin": "ログインに戻る",
"redirectCountdown": "{seconds}秒後にログインページにリダイレクトします...",
"errors": {
  ... (keep existing),
  "passwordValidation": "パスワードが要件を満たしていません。",
  "tokenRequired": "トークンが必要です"
}
```

**Step 2: Add passwordReset.request new keys**

```json
"submitting": "送信中...",
"rateLimitReached": "リクエスト制限に達しました。{minutes}分後に再試行できます。",
"remainingAttempts": "残り試行回数: {count}",
"toastSuccess": "パスワードリセットメールを送信しました"
```

**Step 3: Add worklog.calendar new keys**

```json
"fiscalPeriod": "会計期間: {start} ～ {end}",
"monthlyPeriod": "{fiscalYear} {fiscalPeriod} | 月次期間: {start} - {end}",
"proxyEntryTitle": "マネージャーによる代理入力あり",
"proxyEntryLabel": "代理入力",
"rejectionMonthly": "月次差戻: {reason}",
"rejectionDaily": "日次差戻: {reason}",
"workLabel": "勤務:"
```

**Step 4: Add worklog.memberSelector namespace**

```json
"memberSelector": {
  "label": "チームメンバーを選択",
  "placeholder": "チームメンバーを選択...",
  "noMembers": "チームメンバーが見つかりません。直属の部下のみ代理入力できます。"
}
```

**Step 5: Add worklog.projectSelector namespace**

```json
"projectSelector": {
  "placeholder": "プロジェクトを選択...",
  "loading": "プロジェクト読み込み中...",
  "noAssigned": "プロジェクトが割り当てられていません。管理者にお問い合わせください。",
  "toggleList": "プロジェクト一覧を切替",
  "noMatching": "一致するプロジェクトが見つかりません",
  "listLabel": "プロジェクト"
}
```

**Step 6: Add auth.brandTagline key**

```json
"brandTagline": "勤怠管理システム"
```

**Step 7: Run key parity test to verify it passes**

Run: `cd frontend && npm test -- --run tests/unit/i18n/key-parity.test.ts`
Expected: PASS — both files now in sync

**Step 8: Commit**

```bash
git add frontend/messages/ja.json
git commit -m "feat(i18n): add new ja.json message keys for i18n coverage (#46)"
```

---

### Task 3: Migrate Password Reset Confirm Page (CRITICAL)

**Files:**
- Modify: `frontend/app/(auth)/password-reset/confirm/page.tsx`

**Context:** This file has ~25 hardcoded Japanese strings. It has NO i18n imports. The component structure has a default export `PasswordResetConfirmPage` wrapping `PasswordResetConfirmForm` in Suspense, plus a `PasswordResetLoading` fallback component.

**Step 1: Add i18n imports**

Add to the imports section:

```typescript
import { useTranslations } from "next-intl";
```

**Step 2: Add useTranslations hooks to components**

In `PasswordResetConfirmForm`, add at the top of the function body:

```typescript
const t = useTranslations("passwordReset.confirm");
const tc = useTranslations("passwordReset.common");
```

In `PasswordResetLoading` (Suspense fallback), add:

```typescript
const t = useTranslations("passwordReset.common");
```

**Step 3: Replace all hardcoded strings with t() calls**

Replace every hardcoded Japanese string with the corresponding translation key call. Key mappings:

| Hardcoded String | Replacement |
|------------------|-------------|
| `"パスワードは必須です"` | `t("errors.passwordRequired")` |
| `"パスワードの確認は必須です"` | `t("errors.confirmRequired")` |
| `"パスワードが一致しません"` | `t("errors.passwordMismatch")` |
| `"無効なリンクです。パスワードリセットを再度リクエストしてください。"` | `t("errors.invalidLink")` |
| `"パスワードを変更しました"` (toast) | `t("success")` |
| `"リンクの有効期限が切れています..."` | `t("errors.tokenExpired")` |
| `"パスワードが要件を満たしていません。"` | `t("errors.passwordValidation")` |
| `"サーバーエラーが発生しました..."` | `t("errors.serverError")` |
| `"ネットワークエラーが発生しました..."` | `t("errors.networkError")` |
| `"処理中..."` | `tc("loading")` |
| `"パスワードを変更しました"` (heading) | `t("successTitle")` |
| `"新しいパスワードでログインできます。"` | `t("successMessage")` |
| `"{redirectCountdown}秒後にログインページに..."` | `t("redirectCountdown", { seconds: redirectCountdown })` |
| `"今すぐログインする"` | `t("loginNow")` |
| `"新しいパスワードを設定"` | `t("title")` |
| `"新しいパスワードを入力してください。"` | `t("description")` |
| `"パスワードリセットをリクエスト"` | `t("requestReset")` |
| `"エラーをクリアして再試行"` | `t("retryAriaLabel")` |
| `"再試行"` | `tc("retry")` |
| `"新しいパスワード"` | `t("newPassword")` |
| `"8文字以上で入力してください"` | `t("newPasswordPlaceholder")` |
| `"パスワードの確認"` | `t("confirmPassword")` |
| `"もう一度入力してください"` | `t("confirmPasswordPlaceholder")` |
| `"パスワードを変更"` | `t("submitButton")` |
| `"ログインに戻る"` | `t("backToLogin")` |

Note: Some keys already exist in en.json/ja.json (e.g., `title`, `description`, `newPassword`, etc.). Verify each key exists before using it. If a key like `successTitle` or `successMessage` doesn't exist yet, check the actual key name in the messages file — it might be `success` with a nested structure.

**Step 4: Run the existing tests**

Run: `cd frontend && npm test -- --run tests/unit/\\(auth\\)/password-reset/confirm/page.test.tsx`
Expected: FAIL — tests need IntlWrapper (will be fixed in Task 9)

**Step 5: Commit**

```bash
git add frontend/app/\(auth\)/password-reset/confirm/page.tsx
git commit -m "feat(i18n): migrate password-reset confirm page to useTranslations (#46)"
```

---

### Task 4: Migrate Password Reset Request Page (CRITICAL)

**Files:**
- Modify: `frontend/app/(auth)/password-reset/request/page.tsx`

**Context:** ~20 hardcoded Japanese strings. No i18n imports. Default export `PasswordResetRequestPage`.

**Step 1: Add i18n imports**

```typescript
import { useTranslations } from "next-intl";
```

**Step 2: Add useTranslations hook**

At the top of `PasswordResetRequestPage` body:

```typescript
const t = useTranslations("passwordReset.request");
const tc = useTranslations("passwordReset.common");
```

**Step 3: Replace all hardcoded strings**

Key mappings:

| Hardcoded String | Replacement |
|------------------|-------------|
| `"メールアドレスは必須です"` | `t("errors.emailRequired")` |
| `"メールアドレスの形式が正しくありません"` | `t("errors.emailInvalid")` |
| `"リクエストが多すぎます。{minutes}分後..."` | `t("rateLimitReached", { minutes })` |
| `"パスワードリセットメールを送信しました"` | `t("toastSuccess")` |
| `"ネットワークエラーが発生しました..."` | `t("errors.networkError")` |
| `"サーバーエラーが発生しました..."` | `t("errors.serverError")` |
| `"予期しないエラーが発生しました。"` | `t("errors.unknownError")` |
| `"パスワードリセット"` | `t("title")` |
| `"メールを送信しました"` | `t("successTitle")` |
| `"パスワード再設定用のリンクを..."` | `t("successMessage")` |
| `"ログインに戻る"` | `tc("backToLogin")` |
| `"再試行"` | `tc("retry")` |
| `"リクエスト制限に達しました..."` | `t("rateLimitReached", { minutes })` |
| `"登録されたメールアドレスを..."` | `t("description")` |
| `"メールアドレス"` | `t("emailLabel")` |
| `"example@company.com"` | `t("emailPlaceholder")` |
| `"送信中..."` | `t("submitting")` |
| `"リセットリンクを送信"` | `t("submitButton")` |
| `"残り試行回数: {remainingAttempts}"` | `t("remainingAttempts", { count: remainingAttempts })` |

**Step 4: Run existing tests**

Run: `cd frontend && npm test -- --run tests/unit/\\(auth\\)/password-reset/request/page.test.tsx`
Expected: FAIL — tests need IntlWrapper

**Step 5: Commit**

```bash
git add frontend/app/\(auth\)/password-reset/request/page.tsx
git commit -m "feat(i18n): migrate password-reset request page to useTranslations (#46)"
```

---

### Task 5: Fix Calendar Hardcoded Strings (HIGH)

**Files:**
- Modify: `frontend/app/components/worklog/Calendar.tsx`

**Context:** Already uses `useTranslations("worklog.calendar")` at line 43. Seven remaining hardcoded strings need replacing.

**Step 1: Replace remaining hardcoded strings**

Key mappings (using existing `t` variable from line 43):

| Location | Hardcoded String | Replacement |
|----------|------------------|-------------|
| ~Line 105 | `"Fiscal Period: ${dates[0]?.date} to ${dates[dates.length - 1]?.date}"` | `t("fiscalPeriod", { start: dates[0]?.date, end: dates[dates.length - 1]?.date })` |
| ~Line 112 | Template literal with `月次期間:` | `t("monthlyPeriod", { fiscalYear, fiscalPeriod, start, end })` |
| ~Line 164 | `"Contains entries made by manager"` | `t("proxyEntryTitle")` |
| ~Line 165 | `"Proxy entry indicator"` | `t("proxyEntryLabel")` |
| ~Line 186 | `"Contains entries made by manager"` | `t("proxyEntryTitle")` |
| ~Line 269 | `"Contains entries made by manager"` | `t("proxyEntryTitle")` |
| ~Line 286 | `"Work:"` | `t("workLabel")` |

Also check for rejection-related strings and replace with `t("rejectionMonthly", { reason })` / `t("rejectionDaily", { reason })`.

**Step 2: Run Calendar tests**

Run: `cd frontend && npm test -- --run tests/unit/components/Calendar.test.tsx`
Expected: Some assertions may fail due to changed text. Note which ones.

**Step 3: Commit**

```bash
git add frontend/app/components/worklog/Calendar.tsx
git commit -m "feat(i18n): replace remaining hardcoded strings in Calendar (#46)"
```

---

### Task 6: Migrate ErrorBoundary (MEDIUM)

**Files:**
- Modify: `frontend/app/components/shared/ErrorBoundary.tsx`

**Context:** `ErrorBoundary` is a class component (cannot use hooks). `ErrorFallback` and `ErrorMessage` are function components. No existing unit tests.

**Step 1: Add i18n import**

```typescript
import { useTranslations } from "next-intl";
```

**Step 2: Add useTranslations to function components**

In `ErrorFallback`:
```typescript
const t = useTranslations("errorBoundary");
```

In `ErrorMessage`:
```typescript
const t = useTranslations("errorBoundary");
```

**Step 3: Replace hardcoded strings**

| Location | Hardcoded String | Replacement |
|----------|------------------|-------------|
| Line 89 | `"Something went wrong"` | `t("title")` |
| Line 90 | `"An unexpected error occurred"` | `t("defaultMessage")` |
| Line 96 | `"Retry loading the content"` | `t("retryAriaLabel")` |
| Line 98 | `"Try Again"` | `t("tryAgain")` |
| Line 126 | `"Retry"` (aria-label) | `t("retryAriaLabel")` |
| Line 128 | `"Retry"` (button text) | `t("retry")` |

Note: Check `errorBoundary` namespace in en.json/ja.json for exact key names. Existing keys include `title`, `defaultMessage`, `tryAgain`, `retry`.

**Step 4: Commit**

```bash
git add frontend/app/components/shared/ErrorBoundary.tsx
git commit -m "feat(i18n): migrate ErrorBoundary to useTranslations (#46)"
```

---

### Task 7: Migrate MemberSelector (MEDIUM)

**Files:**
- Modify: `frontend/app/components/worklog/MemberSelector.tsx`

**Context:** No i18n imports. Props interface has default string values for `label` and `placeholder`. No unit tests.

**Step 1: Add i18n import**

```typescript
import { useTranslations } from "next-intl";
```

**Step 2: Add useTranslations hook**

```typescript
const t = useTranslations("worklog.memberSelector");
```

**Step 3: Replace hardcoded strings**

| Location | Hardcoded String | Replacement |
|----------|------------------|-------------|
| Line 37 | `"Select Team Member"` (default prop) | Remove default, use `label ?? t("label")` in JSX |
| Line 38 | `"Choose a team member..."` (default prop) | Remove default, use `placeholder ?? t("placeholder")` in JSX |
| Line 56 | `"Failed to load team members"` | `t("loadError")` (add this key to en/ja.json if missing) |
| Line 101 | `"Loading..."` | `t("loading")` (add this key if missing) |
| Line 109 | `"No team members found..."` | `t("noMembers")` |

Note: Check if `loadError` and `loading` keys need to be added to the `worklog.memberSelector` namespace. If so, add them in this same step.

**Step 4: Commit**

```bash
git add frontend/app/components/worklog/MemberSelector.tsx
git commit -m "feat(i18n): migrate MemberSelector to useTranslations (#46)"
```

---

### Task 8: Migrate ProjectSelector (MEDIUM)

**Files:**
- Modify: `frontend/app/components/worklog/ProjectSelector.tsx`

**Context:** No i18n imports. Default `placeholder` prop. No unit tests.

**Step 1: Add i18n import**

```typescript
import { useTranslations } from "next-intl";
```

**Step 2: Add useTranslations hook**

```typescript
const t = useTranslations("worklog.projectSelector");
```

**Step 3: Replace hardcoded strings**

| Location | Hardcoded String | Replacement |
|----------|------------------|-------------|
| Line 42 | `"Select a project..."` | Remove default prop, use `placeholder ?? t("placeholder")` |
| Line 80 | `"Failed to load projects"` | `t("loadError")` (add key if missing) |
| Line 192 | `"Loading projects..."` | `t("loading")` |
| Line 213 | `"No projects assigned..."` | `t("noAssigned")` |
| Line 249 | `"Toggle project list"` | `t("toggleList")` |
| Line 277 | `"No matching projects found"` | `t("noMatching")` |

Also check for `aria-label="Projects"` or similar and replace with `t("listLabel")`.

**Step 4: Commit**

```bash
git add frontend/app/components/worklog/ProjectSelector.tsx
git commit -m "feat(i18n): migrate ProjectSelector to useTranslations (#46)"
```

---

### Task 9: Convert password.ts to Factory Functions (MEDIUM)

**Files:**
- Modify: `frontend/app/lib/validation/password.ts`

**Context:** Contains Zod schemas with hardcoded Japanese validation messages. Exports `validateEmail()`, `validatePasswordConfirm()`, `analyzePasswordStrength()`, `meetsMinimumStrength()`. Also has `translateFeedback()` that maps English zxcvbn messages to Japanese.

**Step 1: Define message type interfaces**

At the top of the file, add:

```typescript
export interface EmailValidationMessages {
  emailRequired: string;
  emailInvalid: string;
}

export interface PasswordConfirmMessages {
  tokenRequired: string;
  passwordMin: string;
  passwordMax: string;
  confirmRequired: string;
  passwordMismatch: string;
}
```

**Step 2: Convert static schemas to factory functions**

Replace `passwordResetRequestSchema`:

```typescript
// Before
export const passwordResetRequestSchema = z.object({ ... });

// After
export function createPasswordResetRequestSchema(messages: EmailValidationMessages) {
  return z.object({
    email: z.string()
      .min(1, { message: messages.emailRequired })
      .email({ message: messages.emailInvalid }),
  });
}
```

Replace `passwordResetConfirmSchema`:

```typescript
// Before
export const passwordResetConfirmSchema = z.object({ ... }).refine(...);

// After
export function createPasswordResetConfirmSchema(messages: PasswordConfirmMessages) {
  return z.object({
    token: z.string().min(1, { message: messages.tokenRequired }),
    newPassword: z.string()
      .min(8, { message: messages.passwordMin })
      .max(128, { message: messages.passwordMax }),
    confirmPassword: z.string()
      .min(1, { message: messages.confirmRequired }),
  }).refine((data) => data.newPassword === data.confirmPassword, {
    message: messages.passwordMismatch,
    path: ["confirmPassword"],
  });
}
```

**Step 3: Update validateEmail and validatePasswordConfirm**

Add messages parameter to `validateEmail`:

```typescript
export function validateEmail(email: string, messages: EmailValidationMessages): ValidationError | null {
  const schema = createPasswordResetRequestSchema(messages);
  // ... rest of function
}
```

Add messages parameter to `validatePasswordConfirm`:

```typescript
export function validatePasswordConfirm(
  newPassword: string,
  confirmPassword: string,
  token: string,
  messages: PasswordConfirmMessages
): Record<string, ValidationError> {
  const schema = createPasswordResetConfirmSchema(messages);
  // ... rest of function
}
```

**Step 4: Add locale parameter to analyzePasswordStrength**

```typescript
export function analyzePasswordStrength(password: string, locale: string = "ja"): PasswordStrengthResult {
  // ... existing logic
  // When building feedback, conditionally apply translateFeedback:
  const feedback = locale === "ja" ? translateFeedback(rawFeedback) : rawFeedback;
  // ...
}
```

**Step 5: Keep backward compatibility exports (temporary)**

To avoid breaking existing imports before callers are updated, optionally export default-message versions:

```typescript
// Backward-compatible exports for gradual migration
export const passwordResetRequestSchema = createPasswordResetRequestSchema({
  emailRequired: "メールアドレスを入力してください",
  emailInvalid: "有効なメールアドレスを入力してください",
});

export const passwordResetConfirmSchema = createPasswordResetConfirmSchema({
  tokenRequired: "トークンが必要です",
  passwordMin: "パスワードは8文字以上で入力してください",
  passwordMax: "パスワードは128文字以内で入力してください",
  confirmRequired: "確認用パスワードを入力してください",
  passwordMismatch: "パスワードが一致しません",
});
```

Note: These backward-compatible exports can be removed once all callers are updated in Tasks 3-4. If Tasks 3-4 were already completed, these exports may not be needed. Check whether the password reset pages import the schemas directly or call the validate functions.

**Step 6: Commit**

```bash
git add frontend/app/lib/validation/password.ts
git commit -m "feat(i18n): convert Zod schemas to factory functions for i18n (#46)"
```

---

### Task 10: Update PasswordStrengthIndicator for Locale

**Files:**
- Modify: `frontend/app/components/auth/PasswordStrengthIndicator.tsx`

**Context:** Already uses `useTranslations("passwordReset")`. Calls `analyzePasswordStrength()` which now accepts a locale parameter.

**Step 1: Add useLocale import**

```typescript
import { useLocale, useTranslations } from "next-intl";
```

**Step 2: Get locale and pass to analyzePasswordStrength**

In the component body:

```typescript
const locale = useLocale();
```

Update all calls to `analyzePasswordStrength(password)` to `analyzePasswordStrength(password, locale)`.

**Step 3: Replace hardcoded string**

Replace line 71 `"即座"` with the appropriate translation key if one exists, or add one.

**Step 4: Commit**

```bash
git add frontend/app/components/auth/PasswordStrengthIndicator.tsx
git commit -m "feat(i18n): pass locale to analyzePasswordStrength (#46)"
```

---

### Task 11: Migrate LOW Priority Files

**Files:**
- Modify: `frontend/app/page.tsx`
- Modify: `frontend/app/components/shared/AuthGuard.tsx`
- Modify: `frontend/app/(auth)/login/page.tsx`
- Modify: `frontend/app/(auth)/signup/page.tsx`

**Step 1: Migrate app/page.tsx**

Add import and hook:
```typescript
import { useTranslations } from "next-intl";
// In component:
const t = useTranslations("common");
```

Replace `"読み込み中..."` with `t("loading")`.

**Step 2: Migrate AuthGuard.tsx**

Same pattern: add `useTranslations("common")`, replace `"読み込み中..."` with `t("loading")`.

**Step 3: Migrate login/page.tsx**

Already uses `useTranslations("auth.login")`. Add:
```typescript
const ta = useTranslations("auth");
```

Replace:
- `"Time Entry System"` → `ta("brandTagline")`
- Keep `"Miometry"` as-is (brand name, not translated)

**Step 4: Migrate signup/page.tsx**

Already uses `useTranslations("auth.signup")`. Add:
```typescript
const ta = useTranslations("auth");
```

Replace:
- `"Miometry Time Entry System"` → `{"Miometry"} {ta("brandTagline")}`

**Step 5: Commit**

```bash
git add frontend/app/page.tsx frontend/app/components/shared/AuthGuard.tsx frontend/app/\(auth\)/login/page.tsx frontend/app/\(auth\)/signup/page.tsx
git commit -m "feat(i18n): migrate low-priority files to useTranslations (#46)"
```

---

### Task 12: Update Password Reset Confirm Tests

**Files:**
- Modify: `frontend/tests/unit/(auth)/password-reset/confirm/page.test.tsx`

**Context:** Currently uses `ToastProvider` wrapper with hardcoded Japanese text assertions. Needs `IntlWrapper` from `frontend/tests/helpers/intl.tsx`. After i18n migration, translations resolve from ja.json (IntlWrapper defaults to Japanese locale).

**Step 1: Add IntlWrapper import**

```typescript
import { IntlWrapper } from "../../../../helpers/intl";
```

**Step 2: Update renderWithProviders**

```typescript
function renderWithProviders(ui: ReactElement) {
  return render(
    <IntlWrapper>
      <ToastProvider>{ui}</ToastProvider>
    </IntlWrapper>
  );
}
```

**Step 3: Verify text assertions still match**

Since IntlWrapper uses ja.json messages and the hardcoded strings were Japanese, most assertions should still pass. Check each assertion against the ja.json translation values:

- `getByLabelText(/新しいパスワード/)` — should match `ja.json passwordReset.confirm.newPassword`
- `getByRole("button", { name: /パスワードを変更/ })` — should match `ja.json passwordReset.confirm.submitButton`
- etc.

If any assertion texts don't match the ja.json values, update them.

**Step 4: Run tests**

Run: `cd frontend && npm test -- --run tests/unit/\\(auth\\)/password-reset/confirm/page.test.tsx`
Expected: PASS

**Step 5: Commit**

```bash
git add frontend/tests/unit/\(auth\)/password-reset/confirm/page.test.tsx
git commit -m "test(i18n): add IntlWrapper to password-reset confirm tests (#46)"
```

---

### Task 13: Update Password Reset Request Tests

**Files:**
- Modify: `frontend/tests/unit/(auth)/password-reset/request/page.test.tsx`

**Step 1: Add IntlWrapper import**

```typescript
import { IntlWrapper } from "../../../../helpers/intl";
```

**Step 2: Update renderWithProviders**

Same pattern as Task 12: wrap with `<IntlWrapper>`.

**Step 3: Verify text assertions**

Check all hardcoded Japanese assertions against ja.json values. Update any that don't match.

**Step 4: Run tests**

Run: `cd frontend && npm test -- --run tests/unit/\\(auth\\)/password-reset/request/page.test.tsx`
Expected: PASS

**Step 5: Commit**

```bash
git add frontend/tests/unit/\(auth\)/password-reset/request/page.test.tsx
git commit -m "test(i18n): add IntlWrapper to password-reset request tests (#46)"
```

---

### Task 14: Update Calendar Tests

**Files:**
- Modify: `frontend/tests/unit/components/Calendar.test.tsx`

**Context:** Already uses `IntlWrapper`. Some assertions reference English hardcoded strings that were replaced.

**Step 1: Update text assertions**

Replace assertions for strings that changed:
- `getByText(/Fiscal Period:/i)` → check if this now resolves to ja.json value `"会計期間:"`. Update regex accordingly.
- Any other English text assertions that now resolve to Japanese translations.

Note: Since IntlWrapper uses ja.json by default, all translated text will appear in Japanese in tests.

**Step 2: Run tests**

Run: `cd frontend && npm test -- --run tests/unit/components/Calendar.test.tsx`
Expected: PASS (or identify remaining failures)

**Step 3: Fix any remaining failures and commit**

```bash
git add frontend/tests/unit/components/Calendar.test.tsx
git commit -m "test(i18n): update Calendar test assertions for translated strings (#46)"
```

---

### Task 15: Update password.ts Validation Tests

**Files:**
- Modify: `frontend/tests/unit/lib/validation/password.test.ts`

**Context:** Tests import `passwordResetRequestSchema`, `passwordResetConfirmSchema`, `validateEmail`, `validatePasswordConfirm` directly. After Task 9, schemas are factory functions. Backward-compatible exports may exist.

**Step 1: Update imports**

If backward-compatible exports were kept, existing imports continue to work. If not:

```typescript
import {
  createPasswordResetRequestSchema,
  createPasswordResetConfirmSchema,
  analyzePasswordStrength,
  meetsMinimumStrength,
  validateEmail,
  validatePasswordConfirm,
} from "@/lib/validation/password";
```

**Step 2: Create test message objects**

```typescript
const testEmailMessages = {
  emailRequired: "メールアドレスを入力してください",
  emailInvalid: "有効なメールアドレスを入力してください",
};

const testConfirmMessages = {
  tokenRequired: "トークンが必要です",
  passwordMin: "パスワードは8文字以上で入力してください",
  passwordMax: "パスワードは128文字以内で入力してください",
  confirmRequired: "確認用パスワードを入力してください",
  passwordMismatch: "パスワードが一致しません",
};
```

**Step 3: Update schema test calls**

Replace `passwordResetRequestSchema.parse(...)` with `createPasswordResetRequestSchema(testEmailMessages).parse(...)`.

Replace `passwordResetConfirmSchema.parse(...)` with `createPasswordResetConfirmSchema(testConfirmMessages).parse(...)`.

Update `validateEmail(email)` calls to `validateEmail(email, testEmailMessages)`.

Update `validatePasswordConfirm(pw, confirm, token)` calls to `validatePasswordConfirm(pw, confirm, token, testConfirmMessages)`.

**Step 4: Run tests**

Run: `cd frontend && npm test -- --run tests/unit/lib/validation/password.test.ts`
Expected: PASS

**Step 5: Commit**

```bash
git add frontend/tests/unit/lib/validation/password.test.ts
git commit -m "test(i18n): update password validation tests for factory functions (#46)"
```

---

### Task 16: Update PasswordStrengthIndicator Tests

**Files:**
- Modify: `frontend/tests/unit/components/auth/PasswordStrengthIndicator.test.tsx`

**Context:** Mocks `next-intl` directly. Now `analyzePasswordStrength` accepts locale parameter. The mock for `useLocale` may need to be added.

**Step 1: Update next-intl mock**

Ensure the mock includes `useLocale`:

```typescript
vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => {
    // existing mock implementation
  },
  useLocale: () => "ja",
}));
```

**Step 2: Run tests**

Run: `cd frontend && npm test -- --run tests/unit/components/auth/PasswordStrengthIndicator.test.tsx`
Expected: PASS

**Step 3: Commit if changes were needed**

```bash
git add frontend/tests/unit/components/auth/PasswordStrengthIndicator.test.tsx
git commit -m "test(i18n): update PasswordStrengthIndicator tests for locale param (#46)"
```

---

### Task 17: Add Missing Message Keys (if needed)

**Files:**
- Modify: `frontend/messages/en.json`
- Modify: `frontend/messages/ja.json`

**Context:** During implementation of Tasks 6-8, some keys may have been found missing (e.g., `worklog.memberSelector.loading`, `worklog.memberSelector.loadError`, `worklog.projectSelector.loadError`, `errorBoundary.retryAriaLabel`).

**Step 1: Audit for missing keys**

Review any keys used in Tasks 6-8 that don't exist in the message files. Common candidates:

For `worklog.memberSelector`:
- `loading` — en: "Loading..." / ja: "読み込み中..."
- `loadError` — en: "Failed to load team members" / ja: "チームメンバーの読み込みに失敗しました"

For `worklog.projectSelector`:
- `loadError` — en: "Failed to load projects" / ja: "プロジェクトの読み込みに失敗しました"

For `errorBoundary`:
- Check existing keys: `title`, `defaultMessage`, `tryAgain`, `retry`. Add `retryAriaLabel` if missing.

**Step 2: Add any missing keys to both en.json and ja.json**

**Step 3: Run key parity test**

Run: `cd frontend && npm test -- --run tests/unit/i18n/key-parity.test.ts`
Expected: PASS

**Step 4: Commit**

```bash
git add frontend/messages/en.json frontend/messages/ja.json
git commit -m "feat(i18n): add missing message keys discovered during migration (#46)"
```

---

### Task 18: Final Verification

**Files:** None (verification only)

**Step 1: Run all unit tests**

Run: `cd frontend && npm test -- --run`
Expected: ALL PASS

**Step 2: Run key parity test**

Run: `cd frontend && npm test -- --run tests/unit/i18n/key-parity.test.ts`
Expected: PASS

**Step 3: Run lint/format check**

Run: `cd frontend && npx biome ci`
Expected: PASS (no errors)

**Step 4: Fix any failures**

If any tests or lint checks fail, fix them and create a new commit.

**Step 5: Verify no remaining hardcoded strings**

Search for remaining hardcoded UI strings in the modified files:

```bash
cd frontend && grep -rn '"[ぁ-ん]' app/(auth)/password-reset/ app/components/shared/ErrorBoundary.tsx app/components/worklog/MemberSelector.tsx app/components/worklog/ProjectSelector.tsx app/lib/validation/password.ts app/page.tsx app/components/shared/AuthGuard.tsx
```

Expected: No matches (all Japanese strings migrated)

**Step 6: Final commit if needed**

```bash
git commit -m "fix(i18n): address remaining issues from i18n migration (#46)"
```
