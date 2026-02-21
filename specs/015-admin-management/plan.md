# Implementation Plan: Admin Management

**Branch**: `015-admin-management` | **Date**: 2026-02-20 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/015-admin-management/spec.md`

## Summary

Implement a role-based admin management system spanning three actor levels: System Admin (global tenant/user management), Tenant Admin (tenant-scoped member/project/assignment management), and Organization Supervisor (daily entry approval, monthly record approval, direct-report assignment management). Introduces a new DailyEntryApproval domain entity, in-app notification system, admin-specific API endpoints, permission enforcement via Spring Security, and a dedicated admin section in the frontend.

## Technical Context

**Language/Version**: Java 21 (domain), Kotlin 2.3.0 (infrastructure/config), TypeScript 5.x (frontend)
**Primary Dependencies**: Spring Boot 3.5.9, Spring Security, Spring Data JDBC, Next.js 16.x, React 19.x, Tailwind CSS v4, Zustand, Biome
**Storage**: PostgreSQL 17 with JSONB event store (event-sourced aggregates) + projection tables (Spring Data JDBC entities), Flyway migrations
**Testing**: JUnit 5, Testcontainers 1.21.1, MockK (backend); Vitest, React Testing Library (frontend)
**Target Platform**: Web application (Linux server + modern browsers)
**Project Type**: Web (frontend + backend)
**Performance Goals**: <200ms p95 for admin CRUD operations, <500ms for list views with pagination
**Constraints**: Session-based auth with CSRF protection (prod), multi-tenant data isolation, event sourcing for Tenant/Organization/MonthlyApproval aggregates
**Scale/Scope**: ~100 members per tenant, ~50 projects per tenant, ~10 direct reports per supervisor, 7 new admin screens

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. Code Quality | PASS | Plan includes detekt/Spotless for backend, Biome for frontend. All new code peer-reviewed via PR. Inline documentation required for all new services/controllers. |
| II. Testing Discipline | PASS | Unit tests for all domain logic (aggregates, services). Integration tests for API endpoints with Testcontainers. Frontend component tests with Vitest + RTL. Test pyramid respected. |
| III. Consistent UX | PASS | Admin section follows existing Tailwind design patterns. Error/loading/empty states defined per screen. Accessible form controls with proper labels and ARIA attributes. |
| IV. Performance Requirements | PASS | <200ms p95 target for CRUD, <500ms for paginated lists. Pagination prevents unbounded queries. No N+1 query patterns. |
| Additional Constraints | PASS | All dependencies already in use and up-to-date. No new external libraries required beyond existing stack. Flyway migrations for schema changes. |

**Post-Phase 1 Re-check**: All gates still pass. DailyEntryApproval uses Spring Data JDBC (not event-sourced) for simplicity — consistent with Member/Project/Assignment patterns. No new external dependencies introduced.

## Project Structure

### Documentation (this feature)

```text
specs/015-admin-management/
├── plan.md              # This file
├── research.md          # Phase 0: Research decisions
├── data-model.md        # Phase 1: Entity definitions and state machines
├── quickstart.md        # Phase 1: Developer setup guide
├── contracts/           # Phase 1: API endpoint contracts
│   └── api-contracts.md
└── tasks.md             # Phase 2: Task breakdown (created by /speckit.tasks)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/worklog/
│   ├── api/
│   │   ├── AdminTenantController.java          # System Admin: tenant CRUD
│   │   ├── AdminUserController.java            # System Admin: global user management
│   │   ├── AdminMemberController.java          # Tenant Admin: member management
│   │   ├── AdminProjectController.java         # Tenant Admin: project management
│   │   ├── AdminAssignmentController.java      # Tenant Admin + Supervisor: assignments
│   │   └── DailyApprovalController.java        # Supervisor: daily entry approval
│   ├── application/
│   │   ├── service/
│   │   │   ├── AdminTenantService.java
│   │   │   ├── AdminUserService.java
│   │   │   ├── AdminMemberService.java
│   │   │   ├── AdminProjectService.java
│   │   │   ├── AdminAssignmentService.java
│   │   │   ├── DailyApprovalService.java
│   │   │   └── NotificationService.java
│   │   └── command/
│   │       ├── InviteMemberCommand.java
│   │       ├── UpdateMemberCommand.java
│   │       ├── CreateProjectCommand.java
│   │       ├── UpdateProjectCommand.java
│   │       ├── CreateAssignmentCommand.java
│   │       ├── ApproveDailyEntryCommand.java
│   │       ├── RejectDailyEntryCommand.java
│   │       └── RecallDailyApprovalCommand.java
│   ├── domain/
│   │   ├── dailyapproval/
│   │   │   ├── DailyEntryApproval.java         # New entity
│   │   │   ├── DailyEntryApprovalId.java       # Value object
│   │   │   └── DailyApprovalStatus.java        # APPROVED, REJECTED, RECALLED
│   │   └── notification/
│   │       ├── InAppNotification.java           # New entity
│   │       ├── NotificationId.java              # Value object
│   │       └── NotificationType.java            # Enum
│   └── shared/
│       └── AdminRole.java                       # Enum: SYSTEM_ADMIN, TENANT_ADMIN, SUPERVISOR
├── src/main/kotlin/com/worklog/infrastructure/
│   ├── config/
│   │   └── SecurityConfig.kt                   # Modified: add role-based endpoint protection
│   └── repository/
│       ├── DailyEntryApprovalRepository.kt
│       └── InAppNotificationRepository.kt
├── src/main/resources/db/migration/
│   ├── V14__daily_entry_approval.sql
│   ├── V15__in_app_notifications.sql
│   └── V16__admin_permissions_seed.sql
└── src/test/
    ├── java/com/worklog/
    │   ├── domain/dailyapproval/DailyEntryApprovalTest.java
    │   └── api/
    │       ├── AdminTenantControllerTest.java
    │       ├── AdminMemberControllerTest.java
    │       ├── AdminProjectControllerTest.java
    │       ├── AdminAssignmentControllerTest.java
    │       └── DailyApprovalControllerTest.java
    └── kotlin/com/worklog/
        └── application/service/
            ├── DailyApprovalServiceTest.kt
            └── NotificationServiceTest.kt

frontend/app/
├── admin/
│   ├── layout.tsx                    # Admin auth guard (requires admin role)
│   ├── page.tsx                      # Admin dashboard
│   ├── tenants/page.tsx              # System Admin: tenant list + CRUD
│   ├── users/page.tsx                # System Admin: global user management
│   ├── members/page.tsx              # Tenant Admin: member management
│   ├── projects/page.tsx             # Tenant Admin: project management
│   └── assignments/page.tsx          # Tenant Admin + Supervisor: assignments
├── worklog/
│   └── daily-approval/page.tsx       # Supervisor: daily approval dashboard
├── components/
│   ├── admin/
│   │   ├── AdminNav.tsx              # Admin sidebar navigation
│   │   ├── TenantList.tsx
│   │   ├── TenantForm.tsx
│   │   ├── UserList.tsx
│   │   ├── MemberList.tsx
│   │   ├── MemberForm.tsx
│   │   ├── ProjectList.tsx
│   │   ├── ProjectForm.tsx
│   │   ├── AssignmentManager.tsx
│   │   └── DailyApprovalDashboard.tsx
│   └── shared/
│       └── NotificationBell.tsx      # In-app notification indicator
├── services/
│   └── api.ts                        # Extended: admin.*, dailyApproval.*, notification.*
└── hooks/
    └── useNotifications.ts           # Notification polling hook
```

**Structure Decision**: Web application structure following the existing `backend/` + `frontend/` split. Backend follows the established hexagonal architecture (api → application → domain → infrastructure). Frontend follows Next.js App Router conventions with a new `/admin` route group. All new files integrate into existing directory conventions.

## Complexity Tracking

> No constitution violations. All patterns follow existing codebase conventions.

| Decision | Rationale | Alternative Considered |
|----------|-----------|----------------------|
| DailyEntryApproval as separate entity (not event-sourced) | Simpler model, consistent with Member/Project/Assignment patterns. Daily approval is a lightweight operational record, not a core business aggregate requiring full audit trail via events. | Extending WorkLogEntry status would create coupling between daily and monthly workflows. Event-sourcing adds unnecessary complexity for a simple approve/reject/recall lifecycle. |
| In-app notifications via polling (not WebSocket) | Lower implementation complexity. Acceptable latency (<5s via 5-second polling interval). No additional infrastructure (WebSocket server). Consistent with existing request-response architecture. | WebSocket would provide instant delivery but requires additional infrastructure, connection management, and reconnection logic. Overkill for notification volume in this feature. |
| Admin endpoints under `/api/v1/admin/*` prefix | Clear separation of admin operations from user-facing endpoints. Simplifies security configuration (entire prefix can be protected by role check). | Mixing admin operations into existing resource endpoints would make permission enforcement more complex and less auditable. |
