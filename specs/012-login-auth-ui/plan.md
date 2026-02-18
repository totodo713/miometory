# Implementation Plan: Login Page Design, Auth Integration & Logout

**Branch**: `012-login-auth-ui` | **Date**: 2026-02-18 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/012-login-auth-ui/spec.md`

## Summary

Replace the hardcoded mock authentication in the Miometry frontend with a working login/logout flow backed by the existing Spring Security session-based API. Style the login page with Tailwind CSS, add a global header with logout capability, protect worklog routes from unauthenticated access, and seed development user records in the database.

## Technical Context

**Language/Version**: TypeScript 5.x (frontend), Kotlin 2.3.0 / Java 21 (backend)
**Primary Dependencies**: Next.js 16.x, React 19.x, Tailwind CSS v4, Spring Boot 3.5.9
**Storage**: PostgreSQL 17 (users table via V11 migration, session-based auth via Spring Security)
**Testing**: Vitest + React Testing Library (frontend), JUnit 5 + Testcontainers (backend)
**Target Platform**: Web browser (desktop-primary, mobile-usable)
**Project Type**: Web application (frontend + backend)
**Performance Goals**: Login completes in <10 seconds (SC-001), logout in <3 seconds (SC-004)
**Constraints**: Session-based auth only (no JWT/OAuth), existing Tailwind design language, Biome linting
**Scale/Scope**: 4 development test users, single-tenant dev environment

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. Code Quality | PASS | Biome linting enforced, Spotless/detekt for backend. All new code reviewed. |
| II. Testing Discipline | PASS | Login page test updated, useAuth test updated, manual E2E verification plan. Unit tests for new components. |
| III. Consistent UX | PASS | Login page follows existing Tailwind design tokens (blue primary, white cards, Japanese labels). Header consistent across pages. Error/loading states defined. |
| IV. Performance | PASS | SC-001 (<10s login), SC-004 (<3s logout). No new heavy dependencies. |
| Additional Constraints | PASS | No new external dependencies added. All existing deps remain at current versions. |
| Development Workflow | PASS | Feature branch, spec-driven, tests mapped to requirements. |

No violations. Complexity Tracking not required.

## Project Structure

### Documentation (this feature)

```text
specs/012-login-auth-ui/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── auth-api.md      # Login/logout API contract
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 output (via /speckit.tasks)
```

### Source Code (repository root)

```text
backend/
└── src/main/resources/
    └── db/migration/
        └── R__dev_seed_data.sql          # MODIFIED: Add user records

frontend/
├── app/
│   ├── (auth)/login/
│   │   ├── page.tsx                      # MODIFIED: Styled login + API
│   │   └── __tests__/page.test.tsx       # MODIFIED: Mock AuthProvider
│   ├── components/shared/
│   │   ├── Header.tsx                    # NEW: Global header bar
│   │   └── AuthGuard.tsx                 # NEW: Route protection
│   ├── hooks/
│   │   └── useAuth.ts                    # MODIFIED: AuthContext consumer
│   ├── providers/
│   │   ├── AuthProvider.tsx              # NEW: Auth state management
│   │   └── SessionProvider.tsx           # MODIFIED: Fix timeout redirect
│   ├── services/
│   │   └── api.ts                        # MODIFIED: Add login/logout
│   ├── worklog/
│   │   └── layout.tsx                    # NEW: AuthGuard wrapper
│   ├── layout.tsx                        # MODIFIED: Add AuthProvider + Header
│   └── page.tsx                          # MODIFIED: Root redirect
└── tests/unit/hooks/
    └── useAuth.test.tsx                  # MODIFIED: Mock AuthContext
```

**Structure Decision**: Web application structure. Backend changes are minimal (seed data only). All functional changes are in the frontend Next.js app, following the existing App Router conventions (`app/` directory, `providers/`, `components/shared/`, `hooks/`).
