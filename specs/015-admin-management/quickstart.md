# Quickstart: Admin Management

**Feature**: 015-admin-management
**Date**: 2026-02-20

## Prerequisites

- Docker and Docker Compose installed
- Java 21 JDK
- Node.js 20+ with npm
- PostgreSQL 17 running via Docker (dev environment)

## Setup

### 1. Start Development Environment

```bash
cd infra/docker && docker-compose -f docker-compose.dev.yml up -d
```

### 2. Run Backend

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Backend starts at http://localhost:8080. Dev profile auto-loads seed data including test admin users.

### 3. Run Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend starts at http://localhost:3000.

## Test Users (Dev Seed Data)

After implementing the admin permissions migration (V18), the following test users will be available:

### Miometry Tenant (Primary)

| User | Email | Role | Manager | Active |
|------|-------|------|---------|--------|
| System Admin | sysadmin@miometry.example.com | SYSTEM_ADMIN | — | yes |
| David Independent | david.independent@miometry.example.com | TENANT_ADMIN | — | yes |
| Alice Manager | alice.manager@miometry.example.com | SUPERVISOR | — | yes |
| Bob Engineer | bob.engineer@miometry.example.com | USER | Alice | yes |
| Charlie Engineer | charlie.engineer@miometry.example.com | USER | Alice | yes |
| Eve Frontend | eve.frontend@miometry.example.com | USER | Alice | yes |
| Frank Backend | frank.backend@miometry.example.com | USER | Alice | yes |
| Grace Designer | grace.designer@miometry.example.com | USER | Alice | **no** |
| Hank QA Lead | hank.qalead@miometry.example.com | SUPERVISOR | — | yes |
| Ivy Support | ivy.support@miometry.example.com | USER | Hank | yes |
| Kevin Intern | kevin.intern@miometry.example.com | USER | Hank | yes |

### ACME Tenant (Cross-tenant Testing)

| User | Email | Role | Manager | Active |
|------|-------|------|---------|--------|
| Jack ACME Admin | jack.admin@acme.example.com | TENANT_ADMIN | — | yes |
| Kim ACME Staff | kim.staff@acme.example.com | USER | Jack | yes |

Password for all dev users: `Password1` (bcrypt hash in seed data)

### E2E Verification Scenarios

| Scenario | Login As | What to Verify |
|----------|----------|----------------|
| Cross-tenant management | System Admin | Can see both MIOMETRY and ACME tenants |
| Member management | David (TENANT_ADMIN) | List 11 members (10 active + 1 inactive), search, pagination |
| Project management | David (TENANT_ADMIN) | List 6 projects (4 active + 2 inactive), create/edit/deactivate |
| Daily approval (many) | Alice (SUPERVISOR) | 4+ SUBMITTED entries from Bob/Eve/Frank pending approval |
| Daily approval (few) | Hank (SUPERVISOR) | 2 SUBMITTED entries from Ivy/Kevin pending approval |
| Inactive member | David (TENANT_ADMIN) | Grace shows as inactive, can be reactivated |
| Inactive project | David (TENANT_ADMIN) | RESEARCH-AI and LEGACY-SYS show as inactive |
| Tenant isolation | Jack (ACME TENANT_ADMIN) | Only sees ACME members/projects, not Miometry |
| User restrictions | Bob (USER) | Cannot access admin management pages |

## Key Development Paths

### Backend: Adding a New Admin Endpoint

1. Define request/response DTOs as Java records in `api/` package
2. Create controller with `@PreAuthorize("hasPermission(null, 'resource.action')")` annotation
3. Create command record in `application/command/`
4. Create service in `application/service/` with `@Service` and `@Transactional`
5. Wire repository (Spring Data JDBC interface) in `infrastructure/repository/`
6. Add tests: unit test for domain logic, integration test for API endpoint

### Frontend: Adding a New Admin Page

1. Create page at `app/admin/{feature}/page.tsx` with `"use client"` directive
2. Admin layout (`app/admin/layout.tsx`) auto-applies auth guard
3. Add API methods to `services/api.ts` under appropriate namespace
4. Create reusable components in `components/admin/`
5. Use `useCallback` + `useEffect` for data loading pattern
6. Add tests with Vitest + React Testing Library

### Database: Adding a Migration

```bash
# Create new migration file
touch backend/src/main/resources/db/migration/V14__daily_entry_approval.sql

# Run migrations (automatic on bootRun, or manually)
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
```

## Verification Commands

```bash
# Backend: build and test
cd backend && ./gradlew build

# Backend: format check
cd backend && ./gradlew checkFormat

# Backend: detekt static analysis
cd backend && ./gradlew detekt

# Frontend: lint and format check
cd frontend && npm run check:ci

# Frontend: unit tests
cd frontend && npm test -- --run
```

## Architecture Notes

- **DailyEntryApproval**: New Spring Data JDBC entity (not event-sourced). Located in `domain/dailyapproval/`.
- **InAppNotification**: New Spring Data JDBC entity. Located in `domain/notification/`.
- **Permission enforcement**: Uses `@PreAuthorize` annotations. Custom `PermissionEvaluator` resolves permissions from the `role_permissions` table.
- **Tenant scoping**: Service layer resolves tenant from `SecurityContext`. No tenant ID in URL paths for tenant-scoped operations.
- **Notification delivery**: Database-backed with 3-second client-side polling via `useNotifications` hook.
