# Tasks: Password Reset Frontend

**Input**: Design documents from `/specs/006-password-reset-frontend/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests are the PRIMARY deliverable for this feature. The password reset implementation already exists on main. Issue #4 explicitly requires unit tests (acceptance criterion: "すべてのユニットテストが合格").

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Web app**: `frontend/` prefix for all paths
- Tests mirror source: `frontend/tests/unit/` mirrors `frontend/app/`

---

## Phase 1: Setup

**Purpose**: Create test directory structure for password reset unit tests

- [x] T001 Create test directory structure: `frontend/tests/unit/(auth)/password-reset/request/`, `frontend/tests/unit/(auth)/password-reset/confirm/`, `frontend/tests/unit/lib/validation/`, `frontend/tests/unit/lib/utils/`

---

## Phase 2: Foundational (Shared Utility Tests)

**Purpose**: Unit tests for shared utilities that underpin multiple user stories. MUST complete before user story page tests to establish validated test infrastructure.

- [x] T002 [P] Create validation utility unit tests in `frontend/tests/unit/lib/validation/password.test.ts`. Test the following exported functions from `frontend/app/lib/validation/password.ts`:
  - `validateEmail()`: valid email formats (standard, subdomains), invalid formats (missing @, no domain, empty string), Japanese error messages (`"メールアドレスを入力してください"`, `"有効なメールアドレスを入力してください"`)
  - `passwordResetRequestSchema` (zod): parse valid input, reject invalid email
  - `passwordResetConfirmSchema` (zod): min 8 chars, max 128 chars, password match refinement, Japanese error messages (`"パスワードは8文字以上で入力してください"`, `"パスワードは128文字以内で入力してください"`, `"パスワードが一致しません"`, `"トークンが必要です"`)
  - **Password character type enforcement (FR-006)**: Verify that the system rejects passwords missing required character types. Test cases: `"abcdefgh1"` (no uppercase) → rejected or flagged as weak, `"ABCDEFGH1"` (no lowercase) → rejected or flagged as weak, `"Abcdefgh"` (no digit) → rejected or flagged as weak, `"Abcdefg1"` (all types, exactly 8 chars) → accepted. If character type checks are NOT in the zod schema, verify via `analyzePasswordStrength()` that these passwords score as weak/medium and document whether enforcement relies on zxcvbn scoring or backend validation.
  - `analyzePasswordStrength()`: weak password (score 0-1), medium password (score 2-3), strong password (score 4), empty password handling, return structure matches `PasswordStrengthResult` type
  - `meetsMinimumStrength()`: returns true/false for given threshold
  - Use `describe` blocks per function. Import from `@/lib/validation/password`. May need to mock `@zxcvbn-ts/core` if import fails in test environment.

- [x] T003 [P] Create rate-limit utility unit tests in `frontend/tests/unit/lib/utils/rate-limit.test.ts`. Test the following exported functions from `frontend/app/lib/utils/rate-limit.ts`:
  - `checkRateLimit()`: returns `RateLimitState` with `isAllowed: true` when under limit (< 3 attempts), `isAllowed: false` when at/over limit (>= 3 attempts within 5 min window), correctly counts `remainingAttempts`, sets `resetTime` when limited, cleans up expired attempts (> 5 min old)
  - `recordAttempt()`: adds current timestamp to localStorage, verify localStorage key `"password_reset_rate_limit"`
  - `getMinutesUntilReset(resetTime)`: returns correct minutes (rounded up), handles edge cases (time in past, exactly on boundary)
  - `clearAttempts()`: empties localStorage entry
  - Setup/teardown: clear localStorage before each test with `beforeEach(() => localStorage.clear())`
  - Test cross-tab sync: `setupStorageListener()` returns cleanup function, verify callback on storage event
  - Import from `@/lib/utils/rate-limit`. Import `RateLimitState` type from `@/lib/types/password-reset`.

**Checkpoint**: Shared utility tests complete. User story page tests can now begin.

---

## Phase 3: User Story 1 - Request Password Reset (Priority: P1) 🎯 MVP

**Goal**: Verify the password reset request page works correctly via unit tests, and add the "forgot password" entry point from the login page.

**Independent Test**: Run `npm test -- --run tests/unit/(auth)/password-reset/request/` and verify all tests pass. Navigate to `/login` and confirm the forgot password link is visible and navigates to `/password-reset/request`.

### Tests for User Story 1

- [x] T004 [P] [US1] Create request page unit tests in `frontend/tests/unit/(auth)/password-reset/request/page.test.tsx`. Mock `@/services/api` (vi.mock) with `api.auth.requestPasswordReset` returning `Promise<{ message: string }>`. Mock `next/navigation` for `useRouter` (push, replace). Test scenarios:
  - **Rendering**: form renders with email input (`#email`), submit button, login link
  - **Email validation**: submitting empty email shows validation error, submitting invalid email format shows error (`"有効なメールアドレスを入力してください"`), valid email proceeds to submission
  - **Submission success**: mock API resolves → success message displayed ("メールを確認してください" or similar), form hidden after success
  - **Anti-enumeration**: success message is identical regardless of API response (always 200)
  - **Loading state**: submit button disabled and shows loading indicator during API call, prevents double submission
  - **Error handling**: mock API rejects with network error → error message displayed with retry option
  - **Rate limiting UI**: when `checkRateLimit()` returns `isAllowed: false` → submit disabled, rate limit message shown
  - **Login navigation**: login link rendered and points to `/login`
  - Import component from `@/(auth)/password-reset/request/page`. Use `render`, `screen`, `fireEvent`, `waitFor` from `@testing-library/react`.

### Implementation for User Story 1

- [x] T005 [US1] Add "パスワードをお忘れですか？" (Forgot your password?) link to login page in `frontend/app/(auth)/login/page.tsx`. Add `import Link from "next/link"` at top. Add a Next.js `<Link href="/password-reset/request">` element below the submit button, before the closing `</form>` tag. Use inline styles consistent with the existing login page (minimal approach). Link text: `"パスワードをお忘れですか？"`. Verify with `npx biome check frontend/app/(auth)/login/page.tsx`.

- [x] T006 [US1] Update login page tests in `frontend/tests/unit/(auth)/login/page.test.tsx` to verify the new forgot password link. Add test: renders "パスワードをお忘れですか？" link, link has `href="/password-reset/request"`. Keep existing tests unchanged (error when fields missing, remember me checkbox toggles). Run `npm test -- --run tests/unit/(auth)/login/page.test.tsx` to verify all tests pass.

**Checkpoint**: Request page unit tests pass. Login page has forgot password link. FR-001 satisfied.

---

## Phase 4: User Story 2 - Confirm Password Reset (Priority: P1)

**Goal**: Verify the password reset confirmation page works correctly via unit tests, covering token extraction, password validation, success redirect, and error states.

**Independent Test**: Run `npm test -- --run tests/unit/(auth)/password-reset/confirm/` and verify all tests pass.

### Tests for User Story 2

- [x] T007 [US2] Create confirm page unit tests in `frontend/tests/unit/(auth)/password-reset/confirm/page.test.tsx`. Mock `@/services/api` (vi.mock) with `api.auth.confirmPasswordReset` returning `Promise<{ message: string }>`. Mock `next/navigation` for `useRouter` (push, replace) and `useSearchParams` (returning token via `get("token")`). Note: the confirm page uses React `Suspense` boundary for `useSearchParams()` — wrap render in appropriate test setup. Test scenarios:
  - **Token extraction**: `useSearchParams` returns token → form renders with password fields (`#new-password`, `#confirm-password`), submit button
  - **Missing token**: `useSearchParams` returns null, no sessionStorage backup → error message displayed, link to request new reset shown
  - **Password validation**: submit with password < 8 chars → validation error (`"パスワードは8文字以上で入力してください"`), submit with mismatched passwords → mismatch error (`"パスワードが一致しません"`)
  - **Submission success**: mock API resolves → success message displayed, 3-second countdown shown, `router.push("/login")` called after countdown (use `vi.useFakeTimers()` / `vi.advanceTimersByTime(3000)`)
  - **Expired/invalid token error**: mock API rejects with status 404 → expired token error message, link to `/password-reset/request` shown, `isRetryable: false`
  - **Validation error from API**: mock API rejects with status 400 → validation error message, `isRetryable: true`
  - **Network error**: mock API rejects with TypeError (network) → network error message with retry option
  - **Loading state**: submit button disabled during API call, shows loading indicator
  - **Password strength integration**: typing password triggers PasswordStrengthIndicator (may need to mock the component or verify it renders)
  - Import component from `@/(auth)/password-reset/confirm/page`. Use `render`, `screen`, `fireEvent`, `waitFor`, `act` from `@testing-library/react`.

**Checkpoint**: Confirm page unit tests pass. Full password reset flow (US1 + US2) is verified.

---

## Phase 5: User Story 3 - Password Strength Feedback (Priority: P2)

**Goal**: Verify the PasswordStrengthIndicator component provides correct visual feedback for different password strengths.

**Independent Test**: Run `npm test -- --run tests/unit/components/auth/PasswordStrengthIndicator.test.tsx` and verify all tests pass.

### Tests for User Story 3

- [x] T008 [US3] Create PasswordStrengthIndicator unit tests in `frontend/tests/unit/components/auth/PasswordStrengthIndicator.test.tsx`. The component is at `frontend/app/components/auth/PasswordStrengthIndicator.tsx`. It uses `@zxcvbn-ts/core` for strength calculation with 300ms debounce. May need to mock `@zxcvbn-ts/core` and `@zxcvbn-ts/language-common`/`@zxcvbn-ts/language-en` if imports fail in test environment. Test scenarios:
  - **Empty password**: component renders nothing (null return) when `password=""` prop is passed
  - **Weak password**: pass `password="abc"` → after debounce, renders red strength bar (#d32f2f), strength label shows "弱い" or equivalent, bar width ~33%
  - **Medium password**: pass `password="TestPass1"` → after debounce, renders orange strength bar (#f57c00), bar width ~66%
  - **Strong password**: pass `password="C0mpl3x!P@ssw0rd#2024"` → after debounce, renders green strength bar (#388e3c), bar width 100%
  - **onChange callback**: pass `onChange` mock function → verify it is called with `PasswordStrengthResult` object containing `strength`, `score`, `feedback`, `crackTimeDisplay`
  - **Feedback messages**: when feedback exists, renders list of improvement suggestions in Japanese
  - **ARIA attributes**: strength label has `aria-live="polite"`, visual bar has `aria-hidden="true"`
  - **Debounce behavior**: rapidly changing password prop → onChange called only after 300ms delay (use `vi.useFakeTimers()`, `vi.advanceTimersByTime(300)`)
  - **Loading state**: during calculation, shows pulsing animation
  - Import `PasswordStrengthIndicator` from `@/components/auth/PasswordStrengthIndicator`. Import `PasswordStrengthResult` from `@/lib/types/password-reset`. Use `render`, `screen`, `act`, `waitFor` from `@testing-library/react`.

**Checkpoint**: All three user stories have comprehensive unit tests.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final validation across all changes

- [x] T009 [P] Run Biome lint and format check on all new and modified files: `cd frontend && npx biome check tests/unit/(auth)/password-reset/ tests/unit/components/auth/PasswordStrengthIndicator.test.tsx tests/unit/lib/validation/password.test.ts tests/unit/lib/utils/rate-limit.test.ts app/(auth)/login/page.tsx`. Fix any errors with `npx biome check --write <file>`. All files must pass with zero errors.

- [x] T010 Run full unit test suite: `cd frontend && npm test -- --run`. Verify ALL tests pass (existing + new). Expected: existing tests (login, signup, verify-email, Calendar, DailyEntryForm, UnverifiedBanner, useAuth) continue to pass. New tests (5 files) all pass. Zero failures.

- [x] T011 [P] Validate quickstart.md instructions in `specs/006-password-reset-frontend/quickstart.md`. Verify all test commands listed actually work. Verify all file paths in "Key Files Reference" table are correct. Update if any paths changed during implementation.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — creates directory structure only
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS page test confidence
- **User Story 1 (Phase 3)**: Depends on Phase 2 for validated utility tests. T005→T006 sequential dependency.
- **User Story 2 (Phase 4)**: Depends on Phase 2. Independent of Phase 3.
- **User Story 3 (Phase 5)**: Depends on Phase 1. Independent of Phases 3-4.
- **Polish (Phase 6)**: Depends on all previous phases

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Phase 2 — no dependency on other stories
- **User Story 2 (P1)**: Can start after Phase 2 — no dependency on other stories. US1 and US2 can run in parallel.
- **User Story 3 (P2)**: Can start after Phase 1 — only depends on component existing (it does). Can run in parallel with US1/US2.

### Within Each User Story

- US1: T004 [P] can run alongside T005. T006 depends on T005.
- US2: T007 is a single task.
- US3: T008 is a single task.

### Task Dependency Graph

```
T001 (setup)
  ├── T002 [P] (validation tests)
  ├── T003 [P] (rate-limit tests)
  │
  ├── T004 [P] [US1] (request page tests) ── depends on T002
  ├── T005 [US1] (login page mod) ── independent
  │     └── T006 [US1] (login page test update) ── depends on T005
  │
  ├── T007 [US2] (confirm page tests) ── depends on T002
  │
  └── T008 [US3] (strength indicator tests) ── independent

  ── All of T004-T008 complete ──
        │
        ├── T009 [P] (Biome check) ── depends on T004-T008
        ├── T010 (full test suite) ── depends on T004-T008
        └── T011 [P] (quickstart validation) ── depends on T005
```

### Parallel Opportunities

- **Phase 2**: T002 and T003 run in parallel (different files, no dependencies)
- **Phase 3-5**: T004, T005, T007, T008 can all run in parallel after Phase 2 (different files, independent stories)
- **Phase 6**: T009 and T011 can run in parallel

---

## Parallel Example: After Phase 2

```bash
# Launch all user story test tasks in parallel (different files, no dependencies):
Task: T004 "Request page tests in tests/unit/(auth)/password-reset/request/page.test.tsx"
Task: T005 "Login page modification in app/(auth)/login/page.tsx"
Task: T007 "Confirm page tests in tests/unit/(auth)/password-reset/confirm/page.test.tsx"
Task: T008 "PasswordStrengthIndicator tests in tests/unit/components/auth/PasswordStrengthIndicator.test.tsx"

# Then sequentially:
Task: T006 "Login page test update" (after T005 completes)
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

1. Complete Phase 1: Setup (T001)
2. Complete Phase 2: Foundational utility tests (T002, T003)
3. Complete Phase 3: US1 — Request page tests + login link (T004, T005, T006)
4. Complete Phase 4: US2 — Confirm page tests (T007)
5. **STOP and VALIDATE**: Run `npm test -- --run` — all request + confirm tests pass
6. This covers the complete P1 flow

### Incremental Delivery

1. Setup + Foundational → Utility tests validated
2. Add US1 (request page tests + login link) → Test independently → P1 entry point verified
3. Add US2 (confirm page tests) → Test independently → Full P1 flow verified
4. Add US3 (strength indicator tests) → Test independently → P2 UX enhancement verified
5. Polish → All tests pass, Biome clean

### Parallel Strategy (for maximum speed)

After Phase 2:
- Run T004, T005, T007, T008 simultaneously (4 different files)
- Then T006 (depends on T005)
- Then T009, T010, T011 (final validation)

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- All test assertions for user-facing text MUST use Japanese strings (see research.md R6)
- Mock strategy: `vi.mock("@/services/api")` for API, `vi.mock("next/navigation")` for router (see research.md R4)
- Biome relaxed rules apply to test files: `noExplicitAny: off`, `noConsole: off`
- The only production code change is T005 (login page link). All other tasks are test files.
- SC-003 (validation < 1 second) is not verifiable in jsdom unit tests; this is covered at the E2E level by the existing Playwright accessibility tests which implicitly validate page responsiveness. Consider adding explicit E2E performance assertions in a future iteration.
- Commit after each completed phase or logical task group
