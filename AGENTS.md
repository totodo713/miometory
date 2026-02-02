# AGENTS.md – Agentic Coding Rules for Miometry

This file provides agentic coding agents with complete guidelines for operating in this repository. It covers build, lint, and test commands, exhaustive style recommendations, and critical patterns for maintaining code standardization across backend (Spring Boot/Kotlin) and frontend (Next.js/TypeScript) projects.

## Table of Contents
1. [Language Preferences](#language-preferences)
2. [Repository Overview](#repository-overview)
3. [Directory Structure](#directory-structure)
4. [Build, Lint, and Test Commands](#build-lint-and-test-commands)
   - [Frontend](#frontend-nextjs)
   - [Backend](#backend-spring-boot-kotlin)
5. [Code Style Guidelines](#code-style-guidelines)
   - [Frontend: TypeScript/React](#frontend-typescriptreact)
   - [Backend: Kotlin/Java](#backend-kotlinjava)
6. [Specification Best Practices](#specification-best-practices)
7. [References](#references)
8. [Reserved: Copilot/Cursor Rules](#reserved-copilotcursor-rules)
9. [PR Review Response Rules](#pr-review-response-rules)

---

## Language Preferences

**Default Language: Japanese (日本語)**

Unless explicitly specified otherwise by the user, GitHub issues and pull requests should be created in Japanese.

### Thinking and Response Language Rule
- **Internal Thinking**: Always think and reason in English when processing user requests
- **Response to User**: Always respond in Japanese unless explicitly requested otherwise
- This ensures consistent logical processing while providing localized communication

### Guidelines:
- **GitHub Issues**: Write title and description in Japanese
- **Pull Requests**: Write title and description in Japanese
- **Documentation files** (*.md, README, etc.): Write in English
- **Commit messages**: Write in English (follows conventional commit format)
- **Code comments**: Write in English for inline/block comments; Japanese acceptable for complex business logic explanations
- **User-facing UI text**: Follow project's i18n strategy (currently Japanese)

### Exceptions:
- When user explicitly requests English (e.g., "Create PR in English")
- Technical terms and API names should remain in English (e.g., "React", "Spring Boot", "API endpoint")
- Code itself (variable names, function names, class names) must be in English

---

## Repository Overview

- **Frontend:** `frontend/` — Next.js 16.x, TypeScript, React 19.x, Biome for lint/format
- **Backend:** `backend/` — Spring Boot 3.x, Kotlin 2.3, Java 21, JUnit, Flyway, Gradle
- **Infra:** `infra/docker/` — Development Docker Compose

## Directory Structure

```
work-log/
  frontend/        # Next.js app
  backend/         # Spring Boot/Kotlin service
  infra/docker/    # Docker Compose config
  .specify/        # Specification and planning templates
```

---

## Build, Lint, and Test Commands

### Frontend (Next.js)

**Common Commands:**
- Start dev server        : `npm run dev` (or `yarn dev`, `pnpm dev`)
- Production build        : `npm run build`
- Run linter              : `npm run lint` (Biome)
- Format all files        : `npm run format`
- *Single-file format*    : `npx biome format <file>`

**Testing:**
- The project currently does **not** include unit tests by default. If you add testing (e.g., Jest, Playwright), use standard commands (`npm test`, etc.).

**Single Lint/Format:**
- Lint single file: `npx biome check <file>`
- Format single file: `npx biome format <file> --write`

### Backend (Spring Boot, Kotlin)

**Build/JAR:**
- Build service: `./gradlew build`
- Run service : `./gradlew bootRun`

**Testing:**
- Run all tests: `./gradlew test`
- Run single test (class): `./gradlew test --tests "com.worklog.api.HealthControllerTest"`
- Run single test (method): `./gradlew test --tests "com.worklog.api.HealthControllerTest.healthEndpointReturnsOK"`

**Linting/Formatting:**
- **[Biome not used for backend]**
- Use IDE formatting or configure Spotless/Ktlint plugins as per team convention.

---

## Code Style Guidelines

### Frontend (TypeScript/React)
- **Imports:** Prefer absolute paths via TSconfig (`@/module`). Group external then internal imports. 
- **Formatting:** Enforced via Biome; 2 spaces, no semicolons, single quotes preferred.
- **Naming:** Use camelCase for variables/functions; PascalCase for components.
- **Types:** Always type public functions/props/exports. Prefer interfaces for objects; use types for unions.
- **JSX:** Keep JSX expressions concise. Spread props only with clear intent. Use functional, stateless components where possible.
- **Error Handling:** Prefer explicit error boundaries at the component root. Use custom hooks for async errors.
- **React Rules:**
  - Avoid default exports for components.
  - Prefer function exports: `export function MyComponent()`
- **Linting:** Address all Biome warnings before PRs.

### Backend (Kotlin/Java)
- **Imports:** Group Java, third-party, and project imports separately; no wildcard imports.
- **Formatting:** 4 spaces for indentation. Curly braces on same line. Use idiomatic Kotlin—prefer `val` over `var` unless mutation is required.
- **Naming:** PascalCase for classes/types; camelCase for functions/params/local vars.
- **Types:** Explicitly declare return types for all public functions.
- **Null Safety:** Use Kotlin nullability. Prefer explicit handling with `?` and `?:` operators.
- **Error Handling:** Favor exceptions for programming errors, sealed classes for recoverable domain errors.
- **Annotations:** Annotate APIs with Spring `(e.g., @RestController, @GetMapping)`, validate inputs with javax/validation where applicable.
- **Testing:** Write JUnit 5 tests alongside production code in `backend/src/test/`.

---

## Specification Best Practices
- Use `.specify/templates/spec-template.md` as the core for feature specs.
- Always state priorities, edge cases, and measurable criteria as per template.
- Ensure user stories and requirements are testable and independently valuable.

---

## References
- [Next.js Docs](https://nextjs.org/docs)
- [React Docs](https://react.dev)
- [Biome Formatting/Linting](https://biomejs.dev/docs/)
- [Spring Boot + Kotlin](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html)

---

## Reserved: Copilot/Cursor Rules
_No special Copilot or Cursor agent instructions are present. If present, add them here._

---

*Last updated: 2026-02-01*

## Active Technologies
- Kotlin 2.3.0, Java 21 (backend); TypeScript 5.x (frontend) + Spring Boot 3.5.9, Spring Data JDBC, Spring Security, Flyway (backend); Next.js 16.1.1, React 19.x (frontend) (001-foundation)
- PostgreSQL with JSONB for events (001-foundation)
- PostgreSQL with JSONB for event sourcing, event store for domain events, projection tables for read models (002-work-log-entry)

## Architecture Patterns (002-work-log-entry)

### Domain-Driven Design (DDD)
- **Aggregates**: WorkLogEntry, Absence, ApprovalWorkflow, Member
- **Value Objects**: TimeRange, WorkHours, ProjectAllocation, AbsenceType
- **Domain Events**: EntryCreated, EntryUpdated, EntrySubmitted, EntryApproved, EntryRejected
- **Event Sourcing**: All state changes stored as immutable events in `domain_events` table
- **Projections**: Read models rebuilt from events (MonthlyCalendarProjection, ApprovalQueueProjection)

### Backend Layers
```
com.worklog/
  api/           # REST controllers, request/response DTOs
  application/   # Application services, commands, queries (CQRS)
  domain/        # Aggregates, entities, value objects, domain events
  infrastructure/
    config/      # Spring configuration (Security, CORS, Rate Limiting)
    persistence/ # Repository implementations, event store
    projection/  # Read model projections
```

### Frontend Architecture
```
frontend/app/
  components/
    shared/      # Reusable UI components (LoadingSpinner, ErrorBoundary)
    worklog/     # Domain-specific components (Calendar, DailyEntryForm)
  hooks/         # Custom React hooks (useAutoSave, useWorkLogEntry)
  lib/           # Utilities, API client, types
```

### Database Implementation Rules

When adding new domain entities, **always create both the domain model and database migration together** in the same commit/PR to prevent schema-model mismatches.

**Required Checklist for New Entities:**
- [ ] Domain model: `domain/xxx/Xxx.java`
- [ ] ID value object: `domain/xxx/XxxId.java`  
- [ ] Migration file: `db/migration/Vxx__xxx_table.sql`
- [ ] Seed data: Add test data to `data-dev.sql`
- [ ] Repository (if needed): `infrastructure/repository/XxxRepository.java`

**Foreign Key Rules:**
- Always add FK constraints for `*_id` columns referencing other tables
- Referenced tables must be created before referencing tables
- Use `ON CONFLICT` clauses in seed data for idempotency

**Verification Command:**
```bash
# Compare domain models vs database tables
ls backend/src/main/java/com/worklog/domain/*/
docker exec -i miometry-postgres psql -U worklog -d worklog -c "\dt"
```

### Key Design Decisions
- **Auto-save**: 3-second debounce with optimistic UI updates
- **Session timeout**: 30 minutes with warning at 25 minutes
- **Time granularity**: 15-minute increments for all time entries
- **Fiscal months**: 21st-20th pattern for month boundaries
- **CSV streaming**: Chunked processing for large file imports
- **Redis caching**: Optional projection caching with 5-minute TTL (enabled in production)
- **Rate limiting**: Token bucket algorithm with configurable RPS and burst size

### Performance Targets
| Metric | Target | Validation |
|--------|--------|------------|
| Calendar load | < 1 second | SC-006 |
| CSV import | 100 rows/second | SC-005 |
| Concurrent users | 100+ without degradation | SC-007 |
| Mobile entry time | < 2 minutes | SC-008 |
| Auto-save reliability | 99.9% | SC-011 |

### Infrastructure Configuration

**Development:**
```bash
# Start development stack
cd infra/docker && docker compose up -d

# Backend dev server
cd backend && ./gradlew bootRun

# Frontend dev server
cd frontend && npm run dev
```

**Production:**
```bash
# Production with TLS, Redis caching, and health checks
cd infra/docker && docker compose -f docker-compose.prod.yml --env-file prod.env up -d
```

**Environment Variables (Production):**
| Variable | Description | Default |
|----------|-------------|---------|
| POSTGRES_PASSWORD | Database password | (required) |
| REDIS_PASSWORD | Redis password | (optional) |
| CACHE_ENABLED | Enable Redis caching | true |
| RATE_LIMIT_ENABLED | Enable API rate limiting | true |
| RATE_LIMIT_RPS | Requests per second | 20 |
| RATE_LIMIT_BURST | Burst size | 50 |

### Security Features (Phase 10)
- **CSRF Protection**: Cookie-based for SPA (`SecurityConfig.kt`)
- **Rate Limiting**: Token bucket algorithm (`RateLimitConfig.kt`)
- **Request Logging**: With sensitive data masking (`LoggingConfig.kt`)
- **TLS/HTTPS**: Nginx reverse proxy with modern cipher suites
- **Session Management**: 30-minute timeout with secure cookies

### Accessibility (WCAG 2.1 AA)
- ARIA labels on all interactive elements
- Modal dialogs with proper `role="dialog"` and `aria-modal`
- Alert messages with `role="alert"` and `aria-live`
- Keyboard navigation support throughout

## Recent Changes
- 001-foundation: Added Kotlin 2.3.0, Java 21 (backend); TypeScript 5.x (frontend) + Spring Boot 3.5.9, Spring Data JDBC, Spring Security, Flyway (backend); Next.js 16.1.1, React 19.x (frontend)
- 002-work-log-entry: Implemented complete Miometry Entry System with 7 user stories (daily entry, multi-project, absences, approval workflow, CSV import/export, copy previous month, proxy entry)
- 002-work-log-entry (Phase 10): Added Redis caching, ARIA accessibility improvements, performance indices, rate limiting, CSRF protection, TLS configuration, API documentation (OpenAPI/Swagger)

## Test Results Summary (Phase 10 Validation)

### Performance Benchmarks ✅
| Benchmark | Target | Result | Status |
|-----------|--------|--------|--------|
| SC-006 Calendar Load | < 1 second | Avg 8.8ms, Max 13ms | ✅ PASSED |
| SC-007 100 Concurrent Users | 95%+ success | 100% (300/300 requests) | ✅ PASSED |
| SC-008 Mobile API Operations | < 2 seconds | 26ms total | ✅ PASSED |

### Unit Tests
| Component | Tests | Pass Rate |
|-----------|-------|-----------|
| Backend (JUnit 5) | All | 100% |
| Frontend Calendar (Vitest) | 32 | 100% |
| Frontend DailyEntryForm (Vitest) | 43 | 100% |

### E2E Tests (Chromium)
| Result | Count |
|--------|-------|
| Passed | 30 |
| Failed | 33 |
| **Note** | Failures are selector mismatches between tests and actual UI |

### Code Coverage
| Component | Coverage | Target | Status |
|-----------|----------|--------|--------|
| Backend | 76.1% | 85% | ⚠️ Below target |
| Frontend Unit | 100% | 80% | ✅ Above target |

### Security Scan (OWASP)
**Frontend (npm audit):**
- High: 1 (Next.js image optimizer)
- Moderate: 4 (esbuild, vite dependencies)
- Fix available: `npm audit fix --force`

**Backend:**
- OWASP Dependency Check plugin added to `build.gradle.kts`
- Run: `./gradlew dependencyCheckAnalyze`

---

## PR Review Response Rules

PRレビューコメントへの対応時は、以下のルールに従うこと。

### 1. 個別コメントへの返信
- レビューでindividual comment（スレッド形式のコメント）がある場合は、**必ずそのスレッドに対応内容を返信する**
- 返信には修正したコミットハッシュと、具体的にどう修正したかを簡潔に記載する
- 例: `Fixed in commit \`abc1234\`. Changed to use \`indexOf("=")\` to split only on the first \`=\`.`

### 2. サマリコメントは不要
- PRに対する一般的なサマリコメント（スレッド以外のコメント）は**投稿しない**
- 各スレッドへの個別返信で十分

### 3. PRのルートコメント（Description）の更新
- レビュー対応が完了したら、PRのbody（ルートコメント/Description）を更新する
- 対応内容を反映した統括的な変更内容としてPR Descriptionを更新する
- 更新には `github_update_pull_request` ツールの `body` パラメータを使用する

### 4. 対応の流れ
1. レビューコメントを確認（`github_pull_request_read` で `get_review_comments`）
2. コードを修正してコミット・プッシュ
3. 各レビュースレッドに返信（`gh api` で `in_reply_to` を使用）
4. PR Descriptionを更新して変更内容の全体像を反映

### 5. コメント返信のAPIコマンド
```bash
gh api repos/{owner}/{repo}/pulls/{pr_number}/comments \
  -X POST \
  -f body="Fixed in commit \`{commit_hash}\`. {description}" \
  -F in_reply_to={comment_id}
```

### 6. PR Description更新
```
github_update_pull_request(
  owner="...",
  repo="...",
  pullNumber=...,
  body="## Summary\n..."
)
```
