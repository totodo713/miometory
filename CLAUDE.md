# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Language Rules

- **Respond in Japanese** unless explicitly asked otherwise
- **Think internally in English** for consistent reasoning
- GitHub Issues / PRs: Japanese (title + description)
- Documentation files (*.md), code comments, commit messages: English
- Commit messages: conventional commit format (`feat:`, `fix:`, `refactor:`, etc.)

## Project Overview

Miometry — a time entry management system with event sourcing, multi-tenant support, and DDD architecture.

- **Frontend**: `frontend/` — Next.js 16.x, React 19.x, TypeScript, Tailwind CSS, Biome
- **Backend**: `backend/` — Spring Boot 3.5.9, Kotlin 2.3.0 + Java 21, PostgreSQL, Flyway
- **Infra**: `infra/docker/` — Docker Compose (dev + prod)

## Commands

### Development Environment

```bash
cd infra/docker && docker-compose -f docker-compose.dev.yml up -d
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
cd frontend && npm install && npm run dev
```

Frontend: http://localhost:3000 | Backend API: http://localhost:8080

### Frontend (run from `frontend/`)

| Task | Command |
|------|---------|
| Dev server | `npm run dev` |
| Build | `npm run build` |
| Lint | `npm run lint` |
| Lint + auto-fix | `npm run lint:fix` |
| Format | `npm run format` |
| Format check (no write) | `npm run format:check` |
| CI check | `npm run check:ci` |
| Unit tests | `npm test -- --run` |
| Unit tests (watch) | `npm run test:watch` |
| E2E tests | `npm run test:e2e` |
| Single file lint | `npx biome check <file>` |
| Single file format | `npx biome format <file> --write` |

### Backend (run from `backend/`)

| Task | Command |
|------|---------|
| Build | `./gradlew build` |
| Run | `./gradlew bootRun` |
| All tests | `./gradlew test` |
| Single test class | `./gradlew test --tests "com.worklog.api.HealthControllerTest"` |
| Single test method | `./gradlew test --tests "com.worklog.api.HealthControllerTest.healthEndpointReturnsOK"` |
| Format all | `./gradlew formatAll` |
| Check format | `./gradlew checkFormat` |
| Detekt only | `./gradlew detekt` |

## Architecture

### Event Sourcing + CQRS

All domain state changes are stored as immutable events in a `domain_events` table. Read models are projections rebuilt from events. Optimistic locking via aggregate versioning.

Key aggregates: `WorkLogEntry`, `Absence`, `ApprovalWorkflow`, `Member`, `Tenant`, `Organization`, `FiscalYearPattern`, `MonthlyPeriodPattern`

### Backend Layers (`com.worklog`)

```
api/              # REST controllers, DTOs
application/      # Application services, commands, queries (CQRS)
domain/           # Aggregates, entities, value objects, domain events
  worklog/        # WorkLogEntry aggregate
  absence/        # Absence aggregate
  approval/       # ApprovalWorkflow aggregate
  member/         # Member aggregate
  tenant/         # Tenant aggregate
  fiscalyear/     # FiscalYearPattern
  monthlyperiod/  # MonthlyPeriodPattern
eventsourcing/    # Event store, snapshot store infrastructure
infrastructure/   # Spring config, persistence, projections (Kotlin in src/main/kotlin/)
shared/           # Cross-cutting: types, exceptions
```

Domain logic is in Java; infrastructure/config is in Kotlin.

### Frontend Structure (`frontend/app/`)

```
(auth)/           # Auth pages (login, signup, password-reset) — route group
components/
  auth/           # Auth components (PasswordStrengthIndicator, UnverifiedBanner)
  shared/         # ErrorBoundary, LoadingSpinner, SessionTimeoutDialog
  worklog/        # Calendar, DailyEntryForm, MonthlySummary, AbsenceForm, CsvUploader
hooks/            # Custom hooks (useAutoSave, useWorkLogEntry)
lib/              # Utils, types, validation helpers
services/         # API client layer
worklog/          # Worklog pages (calendar, approval)
```

Uses `@/` path alias for imports. API client in `services/api.ts`.

### Database

PostgreSQL 17 with JSONB for events. Flyway migrations in `backend/src/main/resources/db/migration/` (V1–V12 + dev seed data). Dev profile auto-loads `data-dev.sql` with test users and sample data.

### Key Design Decisions

- **Fiscal months**: 21st–20th pattern (configurable per tenant)
- **Time granularity**: 0.25-hour (15-minute) increments
- **Auto-save**: 60-second timer with unsaved changes tracking
- **Session timeout**: 30 minutes, warning at 25 minutes
- **Auth**: Basic Auth (backend), hardcoded mock UUIDs (frontend, see README for test users)

## Code Style

### Frontend
- Biome enforces: 2 spaces, double quotes, semicolons always, trailing commas, lineWidth 120, LF
- See `frontend/biome.json` for full config including lint rules
- `noUnusedImports: error`, `useConst: error`, `noExplicitAny: warn`, `noConsole: warn`
- Test files have relaxed rules (`noExplicitAny: off`, `noConsole: off`)
- Prefer `export function` over default exports for components
- Use `catch (error: unknown)` with type guards, not `catch (error: any)`
- Use `biome-ignore` (not eslint-disable) to suppress warnings, placed on the line before the flagged statement

### Backend
- Spotless: palantir-java-format (Java), ktlint intellij_idea style (Kotlin). 4 spaces, 120-char lines
- Detekt for Kotlin static analysis. Baseline file suppresses existing violations
- No wildcard imports. Run `./gradlew formatAll` before committing
- New entities require: domain model + migration + seed data in same PR (see AGENTS.md checklist)

## PR Review Response Rules

When responding to PR review comments:
1. Reply to each review thread individually with commit hash and fix description
2. Do NOT post general summary comments
3. Update the PR Description (body) to reflect all changes after addressing feedback
4. Reply API: `gh api repos/{owner}/{repo}/pulls/{pr}/comments -X POST -f body="..." -F in_reply_to={comment_id}`

## Active Technologies
- TypeScript 5.x with React 19.x, Next.js 16.x (App Router) + React Testing Library, Vitest, zod, @zxcvbn-ts/core, next-intl (006-password-reset-frontend)
- localStorage (rate limiting state), sessionStorage (token backup) (006-password-reset-frontend)
- Kotlin 2.3.0 + Java 21 (Spring Boot 3.5.9) + Spring Boot Test, Testcontainers 1.21.1, spring-security-test, MockK, JUnit 5 (007-password-reset-tests)
- PostgreSQL 16 (via Testcontainers), Flyway migrations (V11__user_auth.sql) (007-password-reset-tests)
- Java 21 (domain), Kotlin 2.3.0 (infrastructure) + Spring Boot 3.5.9, Spring Data JDBC, Spring Security (009-fix-signup-role-error)
- PostgreSQL 16 (via Spring Data JDBC for Role entity, event sourcing for aggregates) (009-fix-signup-role-error)
- Java 21 (domain), Kotlin 2.3.0 (infrastructure/tests) + Spring Boot 3.5.9, Spring Data JDBC, PostgreSQL JDBC Driver (010-fix-auditlog-persistence)
- PostgreSQL 17 (`audit_logs` table with JSONB + INET columns) (010-fix-auditlog-persistence)
- Kotlin 2.3.0 (infrastructure/tests), Java 21 (domain) + Spring Boot 3.5.9, detekt (static analysis), SLF4J (logging) (011-fix-detekt-baseline)
- N/A (no storage changes) (011-fix-detekt-baseline)
- TypeScript 5.x (frontend), Kotlin 2.3.0 / Java 21 (backend) + Next.js 16.x, React 19.x, Tailwind CSS v4, Spring Boot 3.5.9 (012-login-auth-ui)
- PostgreSQL 17 (users table via V11 migration, session-based auth via Spring Security) (012-login-auth-ui)
- Java 21 (domain), Kotlin 2.3.0 (infrastructure), TypeScript 5.x (frontend) + Spring Boot 3.5.9, Next.js 16.x, React 19.x, Tailwind CSS, Zustand (013-submit-worklog-entry)
- PostgreSQL 17 with JSONB event store (event sourcing) (013-submit-worklog-entry)
- Java 21 (domain), Kotlin 2.3.0 (infrastructure/tests), TypeScript 5.x (frontend) + Spring Boot 3.5.9, Spring Data JDBC, Next.js 16.x, React 19.x, Tailwind CSS, Zustand (014-edit-rejected-entry)
- PostgreSQL 17 with JSONB event store + projection tables, Flyway migrations (014-edit-rejected-entry)
- Java 21 (domain), Kotlin 2.3.0 (infrastructure/config), TypeScript 5.x (frontend) + Spring Boot 3.5.9, Spring Security, Spring Data JDBC, Next.js 16.x, React 19.x, Tailwind CSS v4, Zustand, Biome (015-admin-management)
- PostgreSQL 17 with JSONB event store (event-sourced aggregates) + projection tables (Spring Data JDBC entities), Flyway migrations (015-admin-management)
- Java 21 (domain layer), Kotlin 2.3.0 (infrastructure/config), TypeScript 5.x (frontend) + Spring Boot 3.5.9, Spring Data JDBC, Spring Security, Next.js 16.x, React 19.x, Tailwind CSS (016-org-management)
- PostgreSQL 17 with JSONB event store (event sourcing for Organization), direct JDBC (Member projection) (016-org-management)

## Recent Changes
- 006-password-reset-frontend: Added TypeScript 5.x with React 19.x, Next.js 16.x (App Router) + React Testing Library, Vitest, zod, @zxcvbn-ts/core, next-intl
