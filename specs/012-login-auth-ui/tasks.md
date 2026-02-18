# Tasks: Login Page Design, Auth Integration & Logout

**Input**: Design documents from `/specs/012-login-auth-ui/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/auth-api.md

**Tests**: Included per Constitution Principle II (Testing Discipline) and existing test update requirements from plan.md.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

- **Backend**: `backend/src/main/resources/`
- **Frontend**: `frontend/app/`
- **Frontend Tests**: `frontend/app/(auth)/login/__tests__/`, `frontend/tests/unit/hooks/`

---

## Phase 1: Setup

**Purpose**: Generate prerequisite artifacts needed before code changes

- [x] T001 Generate BCrypt hash of `Password1` for user seed data using Spring's BCryptPasswordEncoder or equivalent tool

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**CRITICAL**: No user story work can begin until this phase is complete. These tasks create the auth plumbing that all 4 user stories depend on.

- [x] T002 Add 4 user seed records (Bob, Alice, Charlie, David) to `backend/src/main/resources/db/migration/R__dev_seed_data.sql` after the existing roles/permissions section, with BCrypt hashed password, `account_status='active'`, and `email_verified_at` set per data-model.md
- [x] T003 Add `login()` and `logout()` methods to `api.auth` object in `frontend/app/services/api.ts` per contracts/auth-api.md (login: POST /api/v1/auth/login with skipAuth, logout: POST /api/v1/auth/logout returning void)
- [x] T004 Create `frontend/app/providers/AuthProvider.tsx` with React Context providing `{ user, isLoading, login, logout }`, sessionStorage persistence under key `miometry_auth_user`, and `useAuthContext()` hook export per research.md Decision 1
- [x] T005 Replace mock implementation in `frontend/app/hooks/useAuth.ts` with thin wrapper around `useAuthContext()` — keep `AuthUser` and `UseAuthResult` interfaces unchanged for backward compatibility
- [x] T006 Update `frontend/app/layout.tsx` to wrap children with `<AuthProvider>` as outermost provider (outside `<SessionProvider>`) per plan.md Step 8

**Checkpoint**: Auth infrastructure ready — `useAuth()` returns real state from AuthProvider, API methods available, seed data in place

---

## Phase 3: User Story 1 — Login with Credentials (Priority: P1) MVP

**Goal**: Users can enter email/password on a styled login page and be authenticated against the backend, redirecting to the worklog dashboard on success.

**Independent Test**: Navigate to `/login`, enter `bob.engineer@miometry.example.com` / `Password1`, verify redirect to `/worklog` with Bob's identity in API calls.

**Covers**: FR-001, FR-002, FR-003, FR-004, FR-010

### Implementation for User Story 1

- [x] T007 [US1] Restyle `frontend/app/(auth)/login/page.tsx` with Tailwind CSS: centered card layout (`min-h-screen bg-gray-50 flex items-center justify-center`), white card (`max-w-md w-full bg-white rounded-lg shadow p-8`), Miometry branding, styled form inputs with Japanese labels (メールアドレス/パスワード), remember-me checkbox, and submit button per plan.md Step 5
- [x] T008 [US1] Wire login form submission in `frontend/app/(auth)/login/page.tsx` to call `login()` from `useAuthContext()`, handle success redirect to `/worklog`, handle errors with Japanese messages per contracts/auth-api.md Frontend Error Mapping, add `isSubmitting` state to disable button and show loading indicator (ログイン中...)
- [x] T009 [US1] Add redirect logic in `frontend/app/(auth)/login/page.tsx`: if `user` is already set (already authenticated), `router.replace("/worklog")` on mount

**Checkpoint**: Login page is styled and functional. Users can log in with valid credentials and see errors for invalid ones.

---

## Phase 4: User Story 2 — Logout (Priority: P1)

**Goal**: Logged-in users see their display name and a logout button in a global header bar. Clicking logout ends the session and returns to login.

**Independent Test**: After logging in, verify header shows "Bob Engineer" and "ログアウト" button on all pages. Click logout, confirm redirect to `/login`.

**Covers**: FR-005, FR-006

### Implementation for User Story 2

- [x] T010 [US2] Create `frontend/app/components/shared/Header.tsx`: thin bar (`h-14 bg-white border-b border-gray-200`) with "Miometry" text left-aligned (`text-lg font-semibold`) and user `displayName` + "ログアウト" button right-aligned. Return `null` when `user` is null (hides on login page). Use `useAuthContext()` for `user` and `logout`.
- [x] T011 [US2] Add `<Header />` component to `frontend/app/layout.tsx` inside `<SessionProvider>` but above `{children}` per plan.md Step 8

**Checkpoint**: Header visible on authenticated pages with user name and working logout button. Hidden on login page.

---

## Phase 5: User Story 3 — Route Protection (Priority: P2)

**Goal**: Unauthenticated users cannot access worklog pages and are redirected to login. Root URL redirects based on auth state.

**Independent Test**: Clear session, navigate to `http://localhost:3000/worklog` — verify redirect to `/login`. Navigate to `http://localhost:3000/` — verify redirect to `/login`. Log in, navigate to `/` — verify redirect to `/worklog`.

**Covers**: FR-007, FR-008

### Implementation for User Story 3

- [x] T012 [P] [US3] Create `frontend/app/components/shared/AuthGuard.tsx`: if `!isLoading && !user`, call `router.replace("/login")`. Render children only when authenticated. Use `useAuthContext()`.
- [x] T013 [P] [US3] Replace `frontend/app/page.tsx` with client component that checks auth state via `useAuthContext()`: if authenticated → `router.replace("/worklog")`, if not → `router.replace("/login")`, while loading → render nothing
- [x] T014 [US3] Create `frontend/app/worklog/layout.tsx` that wraps `{children}` in `<AuthGuard>` to protect all `/worklog/**` routes

**Checkpoint**: All worklog routes are protected. Root URL redirects correctly based on auth state.

---

## Phase 6: User Story 4 — Session Persistence & Timeout Fix (Priority: P2)

**Goal**: Login state survives page refresh. Session timeout properly logs out and redirects to login (fixing the broken `/api/auth/logout` redirect).

**Independent Test**: Log in, refresh page — still authenticated. Wait for timeout warning — logout redirects to `/login`.

**Covers**: FR-009, FR-012

### Implementation for User Story 4

- [x] T015 [US4] Update `frontend/app/providers/SessionProvider.tsx`: import `useAuthContext` for `user` and `logout`, change `enabled: true` to `enabled: user !== null`, replace `router.push("/api/auth/logout")` with `logout()`, remove unused `useRouter` import

**Checkpoint**: Page refresh preserves login. Session timeout correctly logs out and redirects to login.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Test updates, validation, and final quality checks

- [x] T016 [P] Update `frontend/app/(auth)/login/__tests__/page.test.tsx`: mock `useAuthContext` to return `{ user: null, isLoading: false, login: mockFn, logout: mockFn }`, mock `next/navigation` router, update button selector from `/log in/i` to `/ログイン/i`, add test for successful login API call
- [x] T017 [P] Update `frontend/tests/unit/hooks/useAuth.test.tsx`: mock `@/providers/AuthProvider` module's `useAuthContext`, verify `useAuth` correctly derives `isAuthenticated` and `userId` from context state (both authenticated and unauthenticated cases)
- [x] T018 Run `npm run check:ci` in `frontend/` to verify Biome lint compliance
- [x] T019 Run `npm run build` in `frontend/` to verify TypeScript compilation with no errors
- [x] T020 Run `npm test -- --run` in `frontend/` to verify all unit tests pass
- [x] T021 Run quickstart.md validation: start backend with dev profile, start frontend, execute all 8 verification steps from `specs/012-login-auth-ui/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on T001 (BCrypt hash) — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Phase 2 completion
- **User Story 2 (Phase 4)**: Depends on Phase 2 completion (and practically on US1 for manual testing)
- **User Story 3 (Phase 5)**: Depends on Phase 2 completion
- **User Story 4 (Phase 6)**: Depends on Phase 2 completion
- **Polish (Phase 7)**: Depends on all user stories being complete

### User Story Dependencies

- **US1 (Login)**: Can start after Phase 2 — no dependencies on other stories
- **US2 (Logout)**: Can start after Phase 2 — no code dependencies on US1, but needs login working for manual testing
- **US3 (Route Protection)**: Can start after Phase 2 — fully independent
- **US4 (Session Persistence)**: Can start after Phase 2 — fully independent

### Within Each User Story

- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- T003 and T004 can run in parallel (different files)
- T012 and T013 can run in parallel (different files, both [P])
- T016 and T017 can run in parallel (different test files, both [P])
- US1, US2, US3, US4 can all start in parallel after Phase 2 (if team capacity allows)

---

## Parallel Example: Foundational Phase

```bash
# These can run in parallel (different files):
Task T003: "Add login/logout API methods in frontend/app/services/api.ts"
Task T004: "Create AuthProvider in frontend/app/providers/AuthProvider.tsx"

# These depend on T004 and must run after:
Task T005: "Update useAuth hook" (depends on AuthProvider)
Task T006: "Update root layout" (depends on AuthProvider)
```

## Parallel Example: User Story 3

```bash
# These can run in parallel (different files):
Task T012: "Create AuthGuard in frontend/app/components/shared/AuthGuard.tsx"
Task T013: "Update root page in frontend/app/page.tsx"

# This depends on T012:
Task T014: "Create worklog layout with AuthGuard" (uses AuthGuard)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (generate BCrypt hash)
2. Complete Phase 2: Foundational (seed data, API, AuthProvider, useAuth, layout)
3. Complete Phase 3: User Story 1 (styled login page + API wiring)
4. **STOP and VALIDATE**: Test login with `bob.engineer@miometry.example.com` / `Password1`
5. Proceed to remaining stories

### Incremental Delivery

1. Setup + Foundational → Auth infrastructure ready
2. Add US1 (Login) → Test independently → Users can log in (MVP!)
3. Add US2 (Logout) → Test independently → Users can log out
4. Add US3 (Route Protection) → Test independently → Unauthorized access blocked
5. Add US4 (Session Fix) → Test independently → Session timeout works correctly
6. Polish → Tests updated, build verified, quickstart validated

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Commit after each phase completion
- Stop at any checkpoint to validate story independently
- All new TypeScript files must comply with Biome: double quotes, semicolons, 2-space indent, trailing commas
- Use `export function` (named exports) per project convention — no default exports for components
