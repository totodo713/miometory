# AGENTS.md – Development Guidelines for Miometry

Shared guidelines for all AI coding agents working in this repository.

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
- **Dev Environment**: `.devcontainer/` — Single container (Java 21 + Node 20) with PostgreSQL, Redis, Mailpit sidecars

## Commands

### Development Environment

Development uses devcontainer. Open the repository in VS Code and select "Reopen in Container", or start from CLI:

```bash
# Start devcontainer services (PostgreSQL, Redis, Mailpit + app container)
docker compose -f .devcontainer/docker-compose.yml up -d

# Inside devcontainer:
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
cd frontend && npm install && npm run dev
```

Claude Code hooks (`format-on-edit.sh`, `auto-test-on-edit.sh`, `typecheck-on-edit.sh`) automatically delegate build/test/lint commands to the devcontainer via `.claude/hooks/devcontainer-exec.sh`. If the container is not running, commands fall back to local execution.

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
admin/            # Admin panel (members, organizations, tenants, users, projects, assignments)
components/
  auth/           # Auth components (PasswordStrengthIndicator, UnverifiedBanner)
  shared/         # ErrorBoundary, LoadingSpinner, SessionTimeoutDialog
  worklog/        # Calendar, DailyEntryForm, MonthlySummary, AbsenceForm, CsvUploader
hooks/            # Custom hooks (useAutoSave, useWorkLogEntry)
lib/              # Utils, types, validation helpers
providers/        # React context providers (AdminProvider, AuthProvider, SessionProvider)
services/         # API client layer
types/            # Domain type definitions (absence, approval, worklog)
worklog/          # Worklog pages (calendar, approval)
```

Uses `@/` path alias for imports. API client in `services/api.ts`.

### Database

PostgreSQL 17 with JSONB for events. Flyway migrations in `backend/src/main/resources/db/migration/`. Dev seed data via `data-dev.sql` (Spring SQL init, dev profile only).

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

## Testing Stack

- **Frontend**: Vitest, React Testing Library, Playwright (E2E), @axe-core/playwright (a11y)
- **Backend**: JUnit 5, Spring Boot Test, Testcontainers 1.21.1, spring-security-test, MockK

### Testing Gotchas

Test templates and patterns → use `/gen-test` skill. Below are non-obvious gotchas only:

- **Integration tests**: Extend `IntegrationTestBase` (Testcontainers, `@BeforeEach` で FK 用ユーザー作成)
- **Seeded role IDs**: USER=`00000000-...-000000000002`, ADMIN=`00000000-...-000000000001` (V18 migration)
- **Test data uniqueness**: Use UUID-based unique values (not hardcoded strings) to prevent constraint violations with Testcontainers container reuse
- **Testcontainer reuse stale data**: Tenant-scoped data (holidays, calendars) persists across runs with `withReuse(true)`. Add `@BeforeEach` cleanup that `DELETE`s by `tenant_id`. Leverage `ON DELETE CASCADE` FKs — only delete parent rows. Use `IntegrationTestBase.TEST_TENANT_ID` (protected) in cleanup SQL
- **GlobalExceptionHandler error code mapping**: Error codes ending with `_NOT_FOUND` automatically return HTTP 404 (not 400). Test assertions must match: e.g. `MEMBER_NOT_FOUND` → `HttpStatus.NOT_FOUND`
- **Mockito + Kotlin varargs**: `contains()`/`eq()` matchers don't work with Java varargs methods (e.g. `JdbcTemplate.queryForObject`). Use `defaultAnswer` with SQL routing instead
- **Coverage target**: 80%+ LINE coverage per package. Run `./gradlew test jacocoTestReport` to generate reports
- **Fake timers + waitFor**: `vi.useFakeTimers()` breaks `waitFor()`. Use `act(() => vi.advanceTimersByTime(ms))` then assert synchronously
- **Fake timers + userEvent**: `userEvent.setup({ advanceTimers })` can still timeout. Use `fireEvent` for click/input in fake timer tests

## Database Implementation Rules

New entities require: domain model + migration + seed data in same PR. Use `/create-migration` skill for scaffolding with full checklist and conventions.

## PR Review Response Rules

When responding to PR review comments:
1. Reply to each review thread individually with commit hash and fix description
2. Do NOT post general summary comments
3. Update the PR Description (body) to reflect all changes after addressing feedback
4. Reply API: `gh api repos/{owner}/{repo}/pulls/{pr}/comments -X POST -f body="..." -F in_reply_to={comment_id}`

## Security Features

- **CSRF Protection**: Cookie-based for SPA (`SecurityConfig.kt`)
- **Rate Limiting**: Token bucket algorithm (`RateLimitConfig.kt`)
- **Request Logging**: With sensitive data masking (`LoggingConfig.kt`)
- **Session Management**: 30-minute timeout with secure cookies

## Accessibility (WCAG 2.1 AA)

- ARIA labels on all interactive elements
- Modal dialogs with proper `role="dialog"` and `aria-modal`
- Alert messages with `role="alert"` and `aria-live`
- Keyboard navigation support throughout

## Infrastructure

Development uses devcontainer exclusively. Production environment is TBD.
