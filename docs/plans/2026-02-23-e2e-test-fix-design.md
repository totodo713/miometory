# E2E Test Fix Design

## Problem

PR#33 CI has 25 failing E2E tests (48 pass). Root cause analysis with local reproduction identified two categories.

## Root Causes

### Category A: Real Test/Code Bugs (12 tests, reproduce locally)

| File | Failures | Root Cause |
|------|----------|------------|
| password-reset-accessibility.spec.ts | 6 | Confirm page renders Next.js error page (`<html id="__next_error__">`) with test token. `#new-password` form never mounts. Also `#email` lacks `aria-label` attribute. |
| accessibility.spec.ts | 3 | `bg-green-600` Submit button has contrast ratio 3.21 (needs 4.5:1). Keyboard nav focuses `NEXTJS-PORTAL`. Modal focus trap incomplete. |
| approval-workflow.spec.ts | 1 | After rejection, `input[id="project-0"]` stays `disabled` instead of becoming `enabled`. |
| auto-save.spec.ts | 1 | Existing entry loads with `project-0` disabled; test tries `clear()` which requires enabled input. |
| daily-entry.spec.ts | 1 | Save doesn't redirect from `/worklog/{date}` to `/worklog` (mock doesn't trigger redirect). |

### Category B: CI-Only Timeouts (13 tests, pass locally)

| File | CI Failures | Root Cause |
|------|------------|------------|
| absence-entry.spec.ts | 3 | `waitForSelector('input[id="project-0"]', { timeout: 5000 })` times out |
| multi-project-entry.spec.ts | 5 | Same selectors time out |
| daily-entry.spec.ts | 4 | Same selectors time out |
| approval-workflow.spec.ts | 1 | Element not found within timeout |

**Common cause**: ProjectSelector fetches `/api/v1/members/{memberId}/projects` from real backend (unmocked). Locally the response is fast; in CI Docker networking adds latency exceeding 5000ms timeouts.

## Design

### Phase 1: CI Stabilization (fixes 13 CI-only tests)

Add ProjectSelector API mock to all worklog E2E test `beforeEach` blocks:

```typescript
await page.route("**/api/v1/members/*/projects", async (route) => {
  await route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({
      projects: [
        { id: "project-1", code: "PROJ-001", name: "Project Alpha" },
        { id: "project-2", code: "PROJ-002", name: "Project Beta" },
        { id: "project-3", code: "PROJ-003", name: "Project Gamma" },
      ],
      count: 3,
    }),
  });
});
```

**Files to modify**: absence-entry.spec.ts, daily-entry.spec.ts, multi-project-entry.spec.ts, accessibility.spec.ts, approval-workflow.spec.ts, auto-save.spec.ts

### Phase 2: Test Bug Fixes (fixes 12 locally-reproducible tests)

#### 2a. Password Reset Tests (6 fixes)

- **Problem**: `/password-reset/confirm?token=TEST_TOKEN` shows error page because the token validation in `useEffect` likely throws or the page component errors during SSR
- **Fix**: Mock the token validation API endpoint so the confirm page renders properly. Add route mock for `/api/v1/auth/password-reset/validate-token` (or equivalent) to return success. Fix `aria-label` assertion (email input uses `<label for>` not `aria-label`).

#### 2b. Accessibility Tests (3 fixes)

- **Problem 1**: `bg-green-600` (#00a63e) with white text has contrast 3.21 (needs 4.5:1)
- **Fix 1**: Change Submit button color to `bg-green-700` (#15803d, contrast ~5.2:1)
- **Problem 2**: Tab focuses `NEXTJS-PORTAL` element
- **Fix 2**: Update test to handle Next.js portal elements in tab order
- **Problem 3**: Focus not trapped in modal after multiple Tab presses
- **Fix 3**: Investigate modal focus trap implementation; adjust test expectations or fix component

#### 2c. Approval Workflow (1 fix)

- **Problem**: After rejection, project input stays disabled
- **Fix**: Mock returns `approvalStatus: "REJECTED"` but form still uses read-only rendering. Either fix the form logic to check rejection status, or fix the mock to properly simulate the rejection flow.

#### 2d. Auto-Save (1 fix)

- **Problem**: Existing entry `project-0` is disabled. Test tries `clear()`.
- **Fix**: With Phase 1 mock providing projects, the combobox should be enabled. If still disabled, the test needs to use different approach (e.g., click to open dropdown, then select empty).

#### 2e. Daily Entry (1 fix)

- **Problem**: Save click doesn't redirect to `/worklog`. The mock POST returns 200 but no navigation occurs.
- **Fix**: Add navigation mock (intercept POST and then trigger redirect), or change test assertion to not expect redirect.

## Verification

After all fixes, run:
```bash
cd frontend && npx playwright test --project=chromium --workers=1 --reporter=line
```

All 73 tests should pass both locally and in CI.
