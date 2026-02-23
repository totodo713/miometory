# PR#33 Code Review Fixes Design

## Context

PR#33 (devcontainer + CI pipeline) received a code review identifying 1 Critical, 5 Important, and 6 Minor issues. This document describes the fix approach for all actionable items (11 of 12; M-5 is intentional and kept as-is).

## Fixes

### Group A: Infrastructure/CI

**C-1: Unresolved merge conflict markers**
- File: `docs/plans/2026-02-22-devcontainer-ci-design.md` lines 601-622
- Fix: Keep feature branch content (Post-Implementation Notes), remove conflict markers and `No commit needed.` line

**I-2 + M-6: Coverage job condition**
- File: `.github/workflows/ci.yml` line 120-122
- Fix: Add `if: github.event_name == 'pull_request' && github.event.action != 'labeled'` to coverage job
- This also resolves M-6 (no separate fix needed)

**M-1: Pin Dockerfile base images**
- Files: `.devcontainer/backend/Dockerfile`, `.devcontainer/frontend/Dockerfile`
- Fix: Pin to specific minor versions (`eclipse-temurin:21.0.6-jdk`, `node:20-slim`)

### Group B: Backend

**I-1: Custom async thread pool**
- Add `AsyncConfig.java` in `com.worklog.config` package
- Configure: corePoolSize=2, maxPoolSize=4, queueCapacity=100, threadNamePrefix="async-audit-"
- Include `AsyncUncaughtExceptionHandler` for defense-in-depth logging

**M-3: toString() assertions**
- File: `CommandContractTest.kt`
- Fix: Change bare `cmd.toString()` calls to `assertNotNull(cmd.toString())`

### Group C: Frontend/E2E

**I-3: Restore i18n in PasswordStrengthIndicator**
- File: `frontend/app/components/auth/PasswordStrengthIndicator.tsx`
- Fix: Restore `useTranslations("passwordReset")` import and all translation key usage
- The project uses `next-intl` (package.json dependency); test file already mocks it

**I-4: Conditional reuseExistingServer**
- File: `frontend/playwright.config.ts` line 32
- Fix: Restore `reuseExistingServer: !process.env.CI`

**I-5: Externalize E2E test credentials**
- File: `frontend/tests/e2e/fixtures/auth.ts`
- Fix: Read from `process.env.E2E_TEST_EMAIL` / `process.env.E2E_TEST_PASSWORD` with current values as defaults

**M-2: Resilient focus assertion**
- File: `frontend/tests/e2e/accessibility.spec.ts` line 178
- Fix: Assert that `document.activeElement` exists and is not `document.body` instead of checking tag name list

**M-4: Extract projects API mock helper**
- File: `frontend/tests/e2e/fixtures/auth.ts`
- Fix: Add `mockProjectsApi(page)` helper, update 6 spec files to use it

## Non-Changes

**M-5: Playwright retry count (1 vs 2)** — Intentional for CI speed. Monitor after merge.
