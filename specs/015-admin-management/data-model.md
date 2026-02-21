# Data Model: Admin Management

**Feature**: 015-admin-management
**Date**: 2026-02-20

## New Entities

### DailyEntryApproval

Tracks a supervisor's approval decision on an individual work log entry. Separate from the monthly approval workflow.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK | Unique identifier |
| work_log_entry_id | UUID | FK → work_log_entries_projection.id, NOT NULL | The entry being reviewed |
| member_id | UUID | FK → members.id, NOT NULL | The member who owns the entry |
| supervisor_id | UUID | FK → members.id, NOT NULL | The supervisor who made the decision |
| status | VARCHAR(20) | NOT NULL, CHECK (APPROVED, REJECTED, RECALLED) | Current approval status |
| comment | VARCHAR(500) | NULL | Optional comment (required for REJECTED) |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | When the decision was made |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Last status change |

**Indexes**:
- `idx_daily_approval_entry` ON (work_log_entry_id)
- `idx_daily_approval_member` ON (member_id)
- `idx_daily_approval_supervisor` ON (supervisor_id)
- `idx_daily_approval_status` ON (status)
- `UNIQUE idx_daily_approval_active` ON (work_log_entry_id) WHERE status != 'RECALLED' — enforces at most one active approval per entry

**State Transitions**:
```
(no record) → APPROVED   # Supervisor approves entry
(no record) → REJECTED   # Supervisor rejects entry
APPROVED    → RECALLED   # Supervisor recalls before monthly approval
REJECTED    → (member edits entry, new DailyEntryApproval created)
RECALLED    → (entry returns to unapproved, new approval can be created)
```

**Business Rules**:
- Only the supervisor who is the member's manager (member.manager_id = supervisor.id) can create approvals.
- Recall is only allowed if the corresponding monthly approval is not in APPROVED status.
- When an entry is rejected, the member can edit and save it, which effectively creates a new version for the supervisor to review.
- A RECALLED approval remains in the database for audit purposes but is treated as if no approval exists.

---

### InAppNotification

Stores notifications for approval events. Delivered to recipients via client-side polling.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK | Unique identifier |
| recipient_member_id | UUID | FK → members.id, NOT NULL | Who receives the notification |
| type | VARCHAR(30) | NOT NULL | Notification type (see enum below) |
| reference_id | UUID | NOT NULL | ID of the related entity (approval, entry, etc.) |
| title | VARCHAR(200) | NOT NULL | Short notification title |
| message | VARCHAR(500) | NOT NULL | Notification message body |
| is_read | BOOLEAN | NOT NULL, DEFAULT FALSE | Whether the recipient has read it |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | When the notification was created |

**Indexes**:
- `idx_notification_recipient` ON (recipient_member_id, is_read, created_at DESC)
- `idx_notification_created` ON (created_at)

**Notification Types**:
- `DAILY_APPROVED` — Daily entry approved by supervisor
- `DAILY_REJECTED` — Daily entry rejected by supervisor
- `DAILY_RECALLED` — Daily approval recalled by supervisor
- `MONTHLY_SUBMITTED` — Monthly record submitted for approval (sent to supervisor)
- `MONTHLY_APPROVED` — Monthly record approved by supervisor
- `MONTHLY_REJECTED` — Monthly record rejected by supervisor

---

## Modified Entities

### roles (seed data addition)

New predefined roles added via migration:

| name | description |
|------|-------------|
| SYSTEM_ADMIN | Global system administrator with cross-tenant access |
| TENANT_ADMIN | Tenant-scoped administrator for member, project, and assignment management |
| SUPERVISOR | Organization supervisor for daily/monthly approval and direct report management |

Note: The existing ADMIN and USER roles remain unchanged. New roles are additive.

### permissions (seed data addition)

New permissions following the `resource.action` pattern:

| name | description |
|------|-------------|
| tenant.view | View tenant details |
| tenant.create | Create new tenants |
| tenant.update | Edit tenant details |
| tenant.deactivate | Deactivate/activate tenants |
| user.view | View all user accounts globally |
| user.update_role | Change user roles |
| user.lock | Lock/unlock user accounts |
| user.reset_password | Initiate password resets |
| member.view | View members within tenant |
| member.create | Invite new members |
| member.update | Edit member details |
| member.deactivate | Deactivate/activate members |
| project.view | View projects within tenant |
| project.create | Create new projects |
| project.update | Edit project details |
| project.deactivate | Deactivate/activate projects |
| assignment.view | View member-project assignments |
| assignment.create | Create assignments |
| assignment.deactivate | Deactivate/activate assignments |
| daily_approval.view | View daily entries for approval |
| daily_approval.approve | Approve daily entries |
| daily_approval.reject | Reject daily entries |
| daily_approval.recall | Recall daily approvals |
| monthly_approval.view | View monthly records for approval |
| monthly_approval.approve | Approve monthly records |
| monthly_approval.reject | Reject monthly records |
| tenant_admin.assign | Assign Tenant Admin role to other members |

### role_permissions (seed data addition)

| Role | Permissions |
|------|------------|
| SYSTEM_ADMIN | tenant.*, user.*, member.view, project.view |
| TENANT_ADMIN | member.*, project.*, assignment.*, tenant_admin.assign |
| SUPERVISOR | assignment.view, assignment.create, assignment.deactivate, daily_approval.*, monthly_approval.view |

---

## Entity Relationship Diagram

```
┌──────────────┐     ┌──────────────────┐     ┌─────────────┐
│    roles     │────<│  role_permissions │>────│ permissions │
└──────────────┘     └──────────────────┘     └─────────────┘
       │
       │ role_id
       │
┌──────▼───────┐
│    users     │
└──────────────┘
       │ email
       │
┌──────▼───────┐     ┌───────────────────────┐     ┌──────────┐
│   members    │────<│ member_project_assign. │>────│ projects │
└──────┬───────┘     └───────────────────────┘     └──────────┘
       │
       │ member_id (owner)
       │
┌──────▼──────────────────┐
│ work_log_entries_proj.  │
└──────┬──────────────────┘
       │
       │ work_log_entry_id
       │
┌──────▼──────────────────┐
│  daily_entry_approval   │ (NEW)
└─────────────────────────┘

┌─────────────────────────┐
│  in_app_notifications   │ (NEW)
└─────────────────────────┘
       │
       │ recipient_member_id → members.id
```

---

## Migration Plan

### V14__daily_entry_approval.sql

Creates `daily_entry_approvals` table with indexes and constraints.

### V15__in_app_notifications.sql

Creates `in_app_notifications` table with indexes.

### V16__admin_permissions_seed.sql

Inserts predefined roles (SYSTEM_ADMIN, TENANT_ADMIN, SUPERVISOR), permissions (27 entries), and role_permission mappings. Uses `ON CONFLICT DO NOTHING` for idempotency.

### Dev Seed Data Update (data-dev.sql)

Update dev seed data to:
- Assign SYSTEM_ADMIN role to the existing test admin user
- Assign TENANT_ADMIN role to one test user per tenant
- Assign SUPERVISOR role to test users with manager relationships
- Create sample daily approvals for testing
- Create sample notifications for testing
