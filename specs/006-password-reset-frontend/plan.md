# Implementation Plan: Password Reset Frontend

**Branch**: `006-password-reset-frontend` | **Date**: 2026-02-16 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/006-password-reset-frontend/spec.md`

## Summary

The password reset frontend pages (request and confirm), supporting components (PasswordStrengthIndicator), validation utilities, rate limiting, and type definitions are **already fully implemented** on main. The remaining work focuses on:

1. **Unit tests**: Add comprehensive unit tests for all password reset pages, components, and utilities (currently only E2E accessibility tests exist)
2. **Login page integration**: Add a "forgot password" link from the login page to the password reset request page (FR-001)
3. **Biome compliance verification**: Ensure all new and modified files pass Biome lint/format checks

## Technical Context

**Language/Version**: TypeScript 5.x with React 19.x, Next.js 16.x (App Router)
**Primary Dependencies**: React Testing Library, Vitest, zod, @zxcvbn-ts/core, next-intl
**Storage**: localStorage (rate limiting state), sessionStorage (token backup)
**Testing**: Vitest + @testing-library/react (unit), Playwright + @axe-core/playwright (E2E)
**Target Platform**: Web (desktop + mobile, responsive down to 320px)
**Project Type**: Web application (frontend only — backend API already complete)
**Performance Goals**: Validation feedback < 1 second, form submission under 2 minutes total flow
**Constraints**: WCAG 2.1 AA compliance, Japanese localization for all user-facing text, Biome lint zero errors
**Scale/Scope**: 2 pages, 1 component, 3 utility modules, ~6 test files to create

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase 0 Check

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Code Quality | PASS | Existing implementation passes Biome checks. All files well-documented. |
| II. Testing Discipline | PARTIAL → PLAN | Unit tests missing for all password reset code. This plan addresses the gap. |
| III. Consistent UX | PASS | Pages follow established auth page patterns, Japanese localization complete, WCAG 2.1 AA E2E tests pass. |
| IV. Performance | PASS | zxcvbn debounced at 300ms, rate limiting with sliding window, no blocking operations. |
| Additional Constraints | PASS | All dependencies are current and license-compatible. |
| Development Workflow | PASS | Tied to Issue #4, automated linting/formatting via Biome. |

**Gate Result**: PASS with one planned remediation (testing). No violations require justification.

### Post-Phase 1 Re-check

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Code Quality | PASS | No changes to production code quality. Test code will follow Biome rules. |
| II. Testing Discipline | PASS (planned) | 5 unit test files planned covering all password reset code. Test pyramid: many unit + existing E2E. |
| III. Consistent UX | PASS | Login page link addition follows minimal existing patterns. No UX regression. |
| IV. Performance | PASS | No performance-impacting changes. Tests are isolated. |
| Additional Constraints | PASS | No new dependencies required. |
| Development Workflow | PASS | All work maps to Issue #4. Biome checks enforced. |

**Post-Phase 1 Gate Result**: ALL PASS. Ready for task generation.

## Project Structure

### Documentation (this feature)

```text
specs/006-password-reset-frontend/
├── plan.md              # This file
├── research.md          # Phase 0 output — codebase analysis findings
├── data-model.md        # Phase 1 output — frontend state/type model
├── quickstart.md        # Phase 1 output — developer quickstart
├── contracts/           # Phase 1 output — API contract documentation
│   └── api-contracts.md # Backend API endpoints used by frontend
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
frontend/
├── app/
│   ├── (auth)/
│   │   ├── login/
│   │   │   └── page.tsx                    # MODIFY: Add "forgot password" link
│   │   └── password-reset/
│   │       ├── request/
│   │       │   └── page.tsx                # EXISTS (437 lines)
│   │       └── confirm/
│   │           └── page.tsx                # EXISTS (714 lines)
│   ├── components/auth/
│   │   └── PasswordStrengthIndicator.tsx   # EXISTS (272 lines)
│   ├── lib/
│   │   ├── validation/
│   │   │   └── password.ts                 # EXISTS (311 lines)
│   │   ├── types/
│   │   │   └── password-reset.ts           # EXISTS (141 lines)
│   │   └── utils/
│   │       └── rate-limit.ts               # EXISTS (237 lines)
│   └── services/
│       └── api.ts                          # EXISTS — auth endpoints already defined
├── tests/
│   ├── unit/
│   │   ├── (auth)/
│   │   │   ├── login/
│   │   │   │   └── page.test.tsx           # EXISTS — may need update for new link
│   │   │   └── password-reset/
│   │   │       ├── request/
│   │   │       │   └── page.test.tsx       # CREATE
│   │   │       └── confirm/
│   │   │           └── page.test.tsx       # CREATE
│   │   ├── components/auth/
│   │   │   └── PasswordStrengthIndicator.test.tsx  # CREATE
│   │   └── lib/
│   │       ├── validation/
│   │       │   └── password.test.ts        # CREATE
│   │       └── utils/
│   │           └── rate-limit.test.ts      # CREATE
│   └── e2e/
│       └── password-reset-accessibility.spec.ts  # EXISTS (13 tests)
```

**Structure Decision**: Frontend-only feature. All new files are test files (5 created, 1 modified), plus one production file modification (login page link). Follows existing test directory mirroring pattern.
