# Tasks: Password Reset Frontend

**Input**: Design documents from `/specs/005-password-reset-frontend/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api-contracts.yaml

**Tests**: Unit and E2E tests are included per Constitution Check requirements (Testing Discipline section).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

This is a **web application** (Next.js App Router):
- Frontend: `frontend/app/`, `frontend/tests/`
- Backend: Already implemented (no changes needed)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and dependency installation

- [ ] T001 Install new frontend dependencies: @zxcvbn-ts/core@^3.0.4, @zxcvbn-ts/language-common@^3.0.4, @zxcvbn-ts/language-en@^3.0.2, next-intl@^3.0.0 in frontend/package.json
- [ ] T002 Create directory structure: frontend/app/(auth)/password-reset/ with request/ and confirm/ subdirectories
- [ ] T003 Create directory structure: frontend/app/lib/validation/ and frontend/app/lib/utils/ directories
- [ ] T004 Create i18n configuration file frontend/messages/ja.json for Japanese translations

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core utilities and types that ALL user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T005 [P] Create TypeScript types file frontend/app/lib/types/password-reset.ts with all entities from data-model.md (PasswordResetRequestForm, PasswordResetConfirmForm, ValidationError, RateLimitState, PasswordStrengthResult, ApiResponse, ErrorState)
- [ ] T006 [P] Implement rate limiting utility in frontend/app/lib/utils/rate-limit.ts with sliding window algorithm (3 requests per 5 minutes, localStorage storage, cross-tab sync via Storage Events)
- [ ] T007 [P] Implement password validation utility in frontend/app/lib/validation/password.ts with Zod schema (min 8 chars, 1 uppercase, 1 lowercase, 1 number)
- [ ] T008 [P] Create or extend API client in frontend/app/lib/api/auth.ts with passwordResetRequest() and passwordResetConfirm() functions (fetch wrapper, CSRF token handling, error classification)
- [ ] T009 [P] Initialize zxcvbn-ts in frontend/app/lib/validation/password.ts (import zxcvbnOptions, load language-en, configure feedback messages)

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Request Password Reset (Priority: P1) 🎯 MVP

**Goal**: Users can submit their email address to request a password reset link

**Independent Test**: Navigate to `/password-reset/request`, enter email, submit form, verify success message appears regardless of email existence (anti-enumeration)

**Implementation Strategy**: This is the MVP increment. Complete this story first to deliver immediate value.

### Unit Tests for User Story 1

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T010 [P] [US1] Create unit test file frontend/tests/unit/password-reset-request.test.tsx with test suites: form rendering, email validation (valid/invalid formats), submit button disabled when errors exist, success message display, error handling (network/validation)
- [ ] T011 [P] [US1] Create unit test file frontend/tests/unit/rate-limit.test.ts with test suites: rate limit state initialization, attempt recording, limit enforcement (3 req/5min), cross-tab sync simulation, edge cases (clock adjustment, cleared storage)

### Implementation for User Story 1

- [ ] T012 [US1] Create password reset request page in frontend/app/(auth)/password-reset/request/page.tsx with form (email input, submit button), loading state, success/error message display, rate limit integration, link to login page
- [ ] T013 [US1] Implement form validation in T012 using Zod schema from T007 (email required, RFC 5322 format), display ValidationError[] in UI with i18n messages from ja.json
- [ ] T014 [US1] Integrate rate limiting in T012 using utility from T006 (check before submit, display remaining attempts warning, show blocked message with reset time)
- [ ] T015 [US1] Integrate API client in T012 using passwordResetRequest() from T008 (handle loading state, display success message, handle errors with manual retry button)
- [ ] T016 [US1] Add i18n strings to frontend/messages/ja.json for US1: form labels, validation errors, success message, rate limit messages, error messages

### E2E Tests for User Story 1

- [ ] T017 [US1] Create E2E test file frontend/tests/e2e/password-reset-request.spec.ts with scenarios: successful submission with valid email, validation error with invalid email format, rate limit enforcement after 3 requests, network error with retry button

**Checkpoint**: User Story 1 complete - users can now request password reset emails

---

## Phase 4: User Story 2 - Reset Password with Token (Priority: P2)

**Goal**: Users can set a new password using the token from their email

**Independent Test**: Navigate to `/password-reset/confirm?token=VALID_TOKEN`, enter new password (meeting requirements), enter matching confirmation, verify password reset success and redirect to /login

**Dependencies**: Requires US1 for full end-to-end flow, but can be tested independently with pre-generated token

### Unit Tests for User Story 2

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T018 [P] [US2] Create unit test file frontend/tests/unit/password-reset-confirm.test.tsx with test suites: form rendering, token extraction from URL, password validation (length, character requirements), confirmation mismatch detection, success redirect behavior, error handling (invalid/expired token)
- [ ] T019 [P] [US2] Create unit test file frontend/tests/unit/password-validation.test.ts with test suites: Zod schema validation (min 8 chars, uppercase/lowercase/digit requirements), edge cases (empty, whitespace, special chars), error message generation

### Implementation for User Story 2

- [ ] T020 [US2] Create password reset confirm page in frontend/app/(auth)/password-reset/confirm/page.tsx with token extraction (useSearchParams, sessionStorage backup, URL cleanup via router.replace), form (newPassword, confirmPassword inputs, submit button), loading state, success/error message display, automatic redirect to /login on success (3-second delay)
- [ ] T021 [US2] Implement form validation in T020 using Zod schema from T007 (password requirements, confirmation match), display ValidationError[] in UI with i18n messages from ja.json
- [ ] T022 [US2] Implement token extraction logic in T020 (extract from URL on mount, store in sessionStorage as backup, clean URL to prevent history exposure, handle missing token with error message)
- [ ] T023 [US2] Integrate API client in T020 using passwordResetConfirm() from T008 (send token + newPassword, handle loading state, display success message, handle errors: 404 invalid token, 400 validation, 500 server error, provide appropriate user actions)
- [ ] T024 [US2] Add i18n strings to frontend/messages/ja.json for US2: form labels, validation errors (length, missing requirements, mismatch), success message, token error messages (invalid/expired), network errors

### E2E Tests for User Story 2

- [ ] T025 [US2] Create E2E test file frontend/tests/e2e/password-reset-confirm.spec.ts with scenarios: successful password reset with valid token and valid password, validation errors (password too short, missing uppercase/lowercase/digit, confirmation mismatch), expired/invalid token error (404), network error with retry, redirect to login after success

**Checkpoint**: User Stories 1 AND 2 complete - full password reset flow now functional

---

## Phase 5: User Story 3 - View Password Strength Feedback (Priority: P3)

**Goal**: Users see real-time visual feedback about their password strength as they type

**Independent Test**: Type various passwords in the confirm page newPassword field and verify strength indicator updates in real-time: "pass" shows weak/red, "Password1" shows medium/yellow, "SecurePass123" shows strong/green

**Dependencies**: Requires US2 (confirm page) to exist, but is an enhancement to that page

### Unit Tests for User Story 3

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T026 [P] [US3] Create unit test file frontend/tests/unit/password-strength.test.tsx with test suites: component rendering, strength calculation with zxcvbn (weak/medium/strong mapping), debounce behavior (300ms), feedback message display, color/icon updates, accessibility (ARIA attributes, screen reader announcements)
- [ ] T027 [P] [US3] Create unit test file frontend/tests/unit/zxcvbn-integration.test.ts with test suites: zxcvbn scoring (score 0-1 = weak, 2-3 = medium, 4 = strong), performance benchmarks (< 10ms per check), feedback message localization, edge cases (empty password, very long password)

### Implementation for User Story 3

- [ ] T028 [US3] Create PasswordStrengthIndicator component in frontend/app/components/auth/PasswordStrengthIndicator.tsx with props (password: string, onChange: (result: PasswordStrengthResult) => void), zxcvbn calculation with 300ms debounce, visual indicator (progress bar with color: red/yellow/green), strength label (weak/medium/strong), feedback messages list, accessibility (role="status", aria-live="polite" for dynamic updates, aria-label for screen readers)
- [ ] T029 [US3] Implement password strength calculation in T028 using zxcvbn-ts from T009 (calculate score 0-4, map to PasswordStrength enum, extract feedback messages, format crackTimeDisplay, handle empty password gracefully)
- [ ] T030 [US3] Integrate PasswordStrengthIndicator component into password-reset/confirm/page.tsx from T020 (add below newPassword field, pass password value, update form state with PasswordStrengthResult, use result for additional validation or user guidance)
- [ ] T031 [US3] Add i18n strings to frontend/messages/ja.json for US3: strength labels (weak/medium/strong), feedback messages (generic tips for improving password), accessibility announcements

### E2E Tests for User Story 3

- [ ] T032 [US3] Create E2E test file frontend/tests/e2e/password-strength.spec.ts with scenarios: indicator appears when typing in newPassword field, weak password shows red indicator and "weak" label, medium password shows yellow indicator and "medium" label, strong password shows green indicator and "strong" label, feedback messages appear for common patterns (e.g., "avoid common passwords")

**Checkpoint**: All user stories complete - password reset feature fully functional with enhanced UX

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Non-functional requirements, performance optimization, accessibility audit

- [ ] T033 [P] Run Biome linter on all new files and fix any errors (npm run lint in frontend/)
- [ ] T034 [P] Verify WCAG 2.1 AA compliance with @axe-core/playwright (run accessibility tests from quickstart.md, fix any violations: missing labels, insufficient color contrast, missing ARIA attributes)
- [ ] T035 [P] Add JSDoc comments to all public functions and components (API client functions in auth.ts, validation utilities in password.ts, rate-limit.ts, PasswordStrengthIndicator component props/methods)
- [ ] T036 [P] Performance audit: verify password strength calculation < 10ms (use browser DevTools Performance tab, ensure debounce is working), verify page load times < 500ms (Lighthouse audit)
- [ ] T037 Run full E2E test suite (npm run test:e2e) and verify all tests pass, address any flaky tests or failures
- [ ] T038 Run unit test suite (npm run test) and verify code coverage meets 80%+ target for new code
- [ ] T039 Manual testing checklist from quickstart.md: test full flow with backend running, verify email received (check backend logs for token), test rate limiting, test expired token, test keyboard navigation, test mobile responsive (min 320px width)
- [ ] T040 Update AGENTS.md if any new patterns or conventions were introduced during implementation (though Phase 1 already updated with technologies)

**Checkpoint**: Feature complete, tested, accessible, and ready for code review

---

## Dependencies & Parallel Execution Strategy

### User Story Dependency Graph

```
Setup (Phase 1) → Foundational (Phase 2) → US1 (Phase 3) → US2 (Phase 4) → US3 (Phase 5) → Polish (Phase 6)
                                              ↓              ↓              ↓
                                           (MVP Ready)  (Core Complete) (Enhanced)
```

**Key Insights**:
- US1, US2, US3 are **independent user stories** but build on each other sequentially
- US1 (Request) is the MVP - delivers immediate value
- US2 (Confirm) completes the core flow
- US3 (Strength Indicator) enhances UX but is not blocking

### Parallel Execution Opportunities

**Within Phase 2 (Foundational)**: All tasks (T005-T009) can run in parallel
- T005: TypeScript types (different file)
- T006: Rate limiting utility (different file)
- T007: Password validation (different file)
- T008: API client (different file)
- T009: zxcvbn initialization (can be done in T007 file)

**Within Phase 3 (US1)**: Unit tests (T010-T011) can run in parallel before implementation

**Within Phase 4 (US2)**: Unit tests (T018-T019) can run in parallel before implementation

**Within Phase 5 (US3)**: Unit tests (T026-T027) can run in parallel before implementation

**Within Phase 6 (Polish)**: Tasks T033-T036, T038, T040 can run in parallel (different concerns)

**Example Parallel Session**:
```bash
# After Phase 2 complete, start US1:
# Terminal 1: Write unit tests first
npm run test -- --watch tests/unit/password-reset-request.test.tsx

# Terminal 2: Implement request page (after tests fail)
# Edit frontend/app/(auth)/password-reset/request/page.tsx

# Terminal 3: Add i18n strings concurrently
# Edit frontend/messages/ja.json
```

---

## Implementation Strategy

### MVP First Approach

**Minimum Viable Product (MVP) = Phase 1 + Phase 2 + Phase 3 (US1)**

**Rationale**: US1 alone allows users to request password reset emails. While they can't complete the flow yet (need US2), this provides immediate value:
- Users can trigger the email (backend already sends it)
- Team can validate rate limiting and UI patterns
- Provides early user feedback

**Recommended Delivery Sequence**:
1. **Sprint 1** (MVP): Complete Phase 1 + Phase 2 + Phase 3 (US1)
   - Deliverable: Users can request password reset emails
   - Testing: Unit tests + manual testing with backend logs
   - Estimated: 2-3 hours

2. **Sprint 2** (Core Complete): Complete Phase 4 (US2)
   - Deliverable: Full password reset flow functional
   - Testing: Unit tests + E2E tests (full flow)
   - Estimated: 2-3 hours

3. **Sprint 3** (Enhanced UX): Complete Phase 5 (US3) + Phase 6 (Polish)
   - Deliverable: Password strength feedback + production-ready
   - Testing: Accessibility audit, performance audit, full regression
   - Estimated: 1-2 hours

**Total Estimated Time**: 5-8 hours (matches plan.md estimate)

---

## Task Checklist Summary

| Phase | Task Range | Total Tasks | Parallelizable | Story | Status |
|-------|-----------|-------------|----------------|-------|--------|
| Setup | T001-T004 | 4 | T001 | - | ⏳ Pending |
| Foundational | T005-T009 | 5 | All (T005-T009) | - | ⏳ Pending |
| US1 (P1) | T010-T017 | 8 | T010-T011 (tests) | US1 | ⏳ Pending |
| US2 (P2) | T018-T025 | 8 | T018-T019 (tests) | US2 | ⏳ Pending |
| US3 (P3) | T026-T032 | 7 | T026-T027 (tests) | US3 | ⏳ Pending |
| Polish | T033-T040 | 8 | T033-T036, T038, T040 | - | ⏳ Pending |
| **TOTAL** | **T001-T040** | **40 tasks** | **19 parallelizable** | **3 stories** | **0% complete** |

---

## Format Validation

✅ **All tasks follow checklist format**: `- [ ] [TaskID] [P?] [Story?] Description with file path`

**Verification**:
- ✅ All tasks start with `- [ ]` (markdown checkbox)
- ✅ All tasks have sequential IDs (T001-T040)
- ✅ Parallelizable tasks marked with [P]
- ✅ User story tasks marked with [US1], [US2], or [US3]
- ✅ All tasks include specific file paths
- ✅ Setup/Foundational/Polish tasks have NO story label
- ✅ Each user story phase has clear goal and independent test criteria

---

## References

- [Feature Spec](./spec.md): User stories and acceptance criteria
- [Implementation Plan](./plan.md): Technical context and project structure
- [Data Model](./data-model.md): TypeScript type definitions
- [API Contracts](./contracts/api-contracts.yaml): Backend endpoint specifications
- [Quickstart Guide](./quickstart.md): Development setup and testing procedures
- [Research](./research.md): Technical decisions and alternatives

---

**Last Updated**: 2026-02-15  
**Status**: Ready for implementation  
**Next Step**: Begin Phase 1 (Setup) tasks
