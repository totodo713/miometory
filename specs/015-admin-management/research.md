# Research: Admin Management

**Feature**: 015-admin-management
**Date**: 2026-02-20

## Decision 1: Daily Approval Domain Modeling

**Decision**: Create a separate `DailyEntryApproval` entity using Spring Data JDBC (not event-sourced).

**Rationale**:
- WorkLogEntry already has a status field (DRAFT/SUBMITTED/APPROVED/REJECTED) tied to the monthly approval workflow. Adding daily approval status to the same entity would create coupling between two independent workflows.
- DailyEntryApproval as a separate entity cleanly models the optional, supervisor-driven review process without interfering with the existing monthly submission flow.
- Spring Data JDBC (not event-sourced) is appropriate because daily approvals are lightweight operational records — the same pattern used for Member, Project, and MemberProjectAssignment entities.
- The entity references the WorkLogEntry by ID and tracks the supervisor's decision (APPROVED/REJECTED/RECALLED) with an optional comment.

**Alternatives considered**:
- *Extend WorkLogEntry with daily approval fields*: Rejected. Would complicate the existing aggregate and create ambiguity between daily and monthly approval statuses.
- *Event-source DailyEntryApproval*: Rejected. Unnecessary complexity for a simple approve/reject/recall lifecycle. The audit_logs table provides sufficient traceability.

## Decision 2: In-App Notification Mechanism

**Decision**: Implement notifications via a database-backed `in_app_notifications` table with client-side polling (3-second interval).

**Rationale**:
- The spec requires notifications for daily and monthly approval decisions (FR-063, SC-007: <10 seconds).
- A database-backed approach is consistent with the existing architecture (PostgreSQL + Spring Data JDBC).
- Client-side polling at 3-second intervals ensures worst-case delivery within ~6 seconds, well within the <10 second requirement.
- The notification bell component in the frontend header shows unread count and recent notifications.

**Alternatives considered**:
- *WebSocket (real-time push)*: Rejected. Requires additional infrastructure (WebSocket server, connection management, reconnection logic). Overkill for the expected notification volume (~10-50 per user per day).
- *Server-Sent Events (SSE)*: Rejected. Less complex than WebSocket but still requires keeping connections open. Polling is simpler and sufficient for the latency requirement.
- *Email notifications*: Explicitly out of scope per spec.

## Decision 3: Permission Enforcement Strategy

**Decision**: Use Spring Security `@PreAuthorize` annotations on controller methods with a custom `PermissionEvaluator` that checks role-permission mappings from the database.

**Rationale**:
- The existing codebase has `MethodSecurityConfig.kt` that enables `@PreAuthorize`, `@PostAuthorize`, `@PreFilter`, `@PostFilter`, `@Secured`, and `@RolesAllowed`. This infrastructure is already in place but not yet actively used.
- The `roles`, `permissions`, and `role_permissions` tables provide the data model for RBAC.
- A custom `PermissionEvaluator` maps the `resource.action` permission pattern (e.g., `tenant.create`, `member.update`) to the authenticated user's role-based permissions.
- Controller-level annotations provide clear, auditable access control per endpoint.
- Tenant-scoping is enforced at the service layer by comparing the authenticated user's tenant with the requested resource's tenant.

**Alternatives considered**:
- *URL-pattern-based security in SecurityConfig*: Rejected. Too coarse-grained for the mixed permission model (System Admin, Tenant Admin, Supervisor all access `/api/v1/admin/*` but with different scopes).
- *Custom filter chain*: Rejected. More complex than annotation-based approach. Harder to audit which endpoints require which permissions.

## Decision 4: Admin Frontend Routing

**Decision**: Create a new `/admin` route group in the Next.js App Router with a shared admin layout containing role-based navigation.

**Rationale**:
- Consistent with the existing pattern of route groups: `(auth)/` for login, `worklog/` for time entry.
- A shared `admin/layout.tsx` applies an admin-specific auth guard that checks the user's role before rendering.
- Admin navigation sidebar (`AdminNav.tsx`) dynamically shows menu items based on the user's role.
- Daily approval is placed under `/worklog/daily-approval` (not under `/admin`) because it's a supervisor workflow closely related to the worklog domain, similar to the existing `/worklog/approval` for monthly approvals.

**Alternatives considered**:
- *Separate admin application*: Rejected. Over-engineering for the current scale. A route group within the existing app is simpler and shares auth/API infrastructure.
- *All admin under `/admin` including daily approval*: Rejected. Daily approval is a worklog workflow, not an administrative function. Keeping it in `/worklog/` maintains domain consistency.

## Decision 5: Tenant-Scoped API Design

**Decision**: Use the authenticated user's context (session) to determine tenant scope. No tenant ID in URL paths for tenant-scoped admin operations.

**Rationale**:
- Consistent with the existing API pattern where `/api/v1/members/{id}` doesn't include tenant ID — tenant isolation is implicit via the authenticated user.
- Reduces URL complexity and eliminates a class of authorization bugs (user passing a different tenant ID in the URL).
- System Admin endpoints that operate across tenants use query parameters for tenant filtering (e.g., `GET /api/v1/admin/users?tenantId=...`).
- The service layer resolves the current user's tenant from the SecurityContext and enforces isolation.

**Alternatives considered**:
- *Tenant ID in URL path (e.g., `/api/v1/tenants/{tenantId}/members`)*: Rejected. Deviates from existing API conventions. Requires additional authorization checks to prevent cross-tenant access via URL manipulation.

## Decision 6: Admin Role Seeding Strategy

**Decision**: Use a Flyway migration to seed predefined admin roles and permissions. Three roles: SYSTEM_ADMIN, TENANT_ADMIN, SUPERVISOR. Each maps to specific `resource.action` permissions.

**Rationale**:
- The existing `roles` and `permissions` tables support this model.
- Flyway migration ensures roles exist consistently across all environments (dev, test, prod).
- Permissions follow the established `resource.action` pattern (e.g., `tenant.create`, `member.update`, `daily_approval.approve`).
- New permissions are additive — they don't modify existing role definitions.

**Permission mapping**:

| Role | Permissions |
|------|------------|
| SYSTEM_ADMIN | tenant.*, user.*, member.view, project.view |
| TENANT_ADMIN | member.*, project.*, assignment.*, tenant_admin.assign |
| SUPERVISOR | assignment.create (direct reports), daily_approval.*, monthly_approval.view |

Note: SUPERVISOR permissions are further scoped at the service layer to direct reports only.
