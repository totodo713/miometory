# Organization Domain Model Analysis - Miometry Project

## Overview
Miometry is a time entry management system using Event Sourcing + CQRS with DDD architecture. Organizations are a core entity in the hierarchy: Organization -> Tenant -> Members -> Projects.

---

## 1. ORGANIZATION DOMAIN MODEL

### Organization Aggregate Root
**File**: `/home/devman/repos/miometory/backend/src/main/java/com/worklog/domain/organization/Organization.java`

#### Key Properties:
- `id` (OrganizationId): Unique identifier
- `tenantId` (TenantId): Owning tenant reference
- `parentId` (OrganizationId): Parent organization for hierarchy (null for root)
- `code` (Code): Unique code within tenant (immutable)
- `name` (String): Display name (max 256 chars)
- `level` (int): Hierarchy level 1-6 (1=root, 6=deepest)
- `fiscalYearPatternId` (UUID): Reference to fiscal year pattern (nullable)
- `monthlyPeriodPatternId` (UUID): Reference to monthly period pattern (nullable)
- `status` (Status): ACTIVE or INACTIVE

#### Status Enum:
- `ACTIVE`: Organization is active
- `INACTIVE`: Organization is inactive

#### Invariants/Rules:
- Level must be 1-6 (enforced by MAX_HIERARCHY_LEVEL constant)
- Root organizations (level=1) cannot have a parent
- Non-root organizations must have a parent
- Code is immutable after creation
- Name cannot be empty (trimmed, max 256 chars)
- Cannot deactivate an already inactive organization
- Cannot activate an already active organization
- Parent consistency validation: level 1 must not have parent; level > 1 must have parent

#### Operations:
1. `create(id, tenantId, parentId, code, name)`: Creates root org (level auto-set to 1)
2. `create(id, tenantId, parentId, code, name, level)`: Creates org with explicit level
3. `update(newName)`: Updates name (only on ACTIVE orgs)
4. `deactivate()`: Sets status to INACTIVE
5. `activate()`: Sets status to ACTIVE
6. `assignPatterns(fiscalYearPatternId, monthlyPeriodPatternId)`: Assigns fiscal patterns

---

## 2. DOMAIN EVENTS

All events are records implementing `DomainEvent` interface:

### OrganizationCreated
```
Fields: eventId, occurredAt, aggregateId, tenantId, parentId, code, name, level
```

### OrganizationUpdated
```
Fields: eventId, occurredAt, aggregateId, name
```

### OrganizationDeactivated
```
Fields: eventId, occurredAt, aggregateId
```

### OrganizationActivated
```
Fields: eventId, occurredAt, aggregateId
```

### OrganizationPatternAssigned
```
Fields: eventId, occurredAt, aggregateId, fiscalYearPatternId, monthlyPeriodPatternId
```

---

## 3. MEMBER DOMAIN MODEL

**File**: `/home/devman/repos/miometory/backend/src/main/java/com/worklog/domain/member/Member.java`

### Key Properties:
- `id` (MemberId): Unique identifier
- `tenantId` (TenantId): Tenant reference
- `organizationId` (OrganizationId): Organization reference - MEMBER BELONGS TO AN ORGANIZATION
- `email` (String): Email address
- `displayName` (String): Display name
- `managerId` (MemberId): **MANAGER RELATIONSHIP** - nullable reference to manager member
- `isActive` (boolean): Active status
- `createdAt` (Instant): Creation timestamp
- `updatedAt` (Instant): Last update timestamp

### Key Methods:
- `create(tenantId, organizationId, email, displayName, managerId)`: Factory method
- `assignManager(managerId)`: Sets the manager
- `removeManager()`: Clears the manager
- `isManagedBy(otherMemberId)`: Checks if specific member is manager
- `hasManager()`: Checks if member has a manager

### Important Notes:
- Members are NOT event-sourced (no aggregate root inheritance)
- Members belong to exactly one organization
- Each member has optional managerId for approval workflow
- Manager is just another member (no separate manager entity)

---

## 4. TENANT DOMAIN MODEL

**File**: `/home/devman/repos/miometory/backend/src/main/java/com/worklog/domain/tenant/Tenant.java`

### Key Properties:
- `id` (TenantId): Unique identifier
- `code` (Code): Unique tenant code
- `name` (String): Tenant display name
- `status` (Status): ACTIVE or INACTIVE

### Operations:
- `create(code, name)`: Creates new tenant
- `update(newName)`: Updates name
- `deactivate(reason)`: Deactivates tenant
- `activate()`: Activates tenant

### Relationship to Organization:
- Tenant is parent entity
- Organizations belong to a tenant
- Organizations have unique code per tenant (constraint: `uq_organization_tenant_code`)

---

## 5. MONTHLY APPROVAL WORKFLOW

**File**: `/home/devman/repos/miometory/backend/src/main/java/com/worklog/domain/approval/MonthlyApproval.java`

### Key Properties:
- `id` (MonthlyApprovalId): Unique identifier
- `memberId` (MemberId): Member whose time is being approved
- `fiscalMonth` (FiscalMonthPeriod): Fiscal month being approved
- `status` (ApprovalStatus): Current approval status
- `submittedAt` (Instant): When submitted
- `submittedBy` (MemberId): Who submitted
- `reviewedAt` (Instant): When reviewed
- `reviewedBy` (MemberId): Who reviewed (the manager/supervisor)
- `rejectionReason` (String): Reason if rejected (max 1000 chars)
- `workLogEntryIds` (Set<UUID>): Associated work log entries
- `absenceIds` (Set<UUID>): Associated absences

### ApprovalStatus Enum:
- `PENDING`: Initial state, member can edit
- `SUBMITTED`: Member submitted, waiting for manager review (read-only for member)
- `APPROVED`: Manager approved (permanently read-only)
- `REJECTED`: Manager rejected with reason (member can edit and resubmit)

### Status Transitions:
```
PENDING → SUBMITTED (member submits)
SUBMITTED → APPROVED (manager approves)
SUBMITTED → REJECTED (manager rejects with reason)
REJECTED → SUBMITTED (member resubmits)
```

### Key Methods:
- `create(memberId, fiscalMonth)`: Creates approval in PENDING status
- `submit(submittedBy, workLogEntryIds, absenceIds)`: Transitions to SUBMITTED
- `approve(reviewedBy)`: Transitions to APPROVED
- `reject(reviewedBy, rejectionReason)`: Transitions to REJECTED

### Important Notes:
- Manager permission validation is done at SERVICE layer (see ApprovalService)
- The `reviewedBy` member should be the `managerId` of the member being reviewed
- All associated work log entries and absences change status atomically

---

## 6. DAILY APPROVAL (SUPERVISOR FEEDBACK)

**File**: `/home/devman/repos/miometory/backend/src/main/java/com/worklog/domain/dailyapproval/DailyEntryApproval.java`

### Key Properties:
- `id` (DailyEntryApprovalId): Unique identifier
- `workLogEntryId` (UUID): Work log entry being approved
- `memberId` (MemberId): Member who created the entry
- `supervisorId` (MemberId): Supervisor who approved/rejected
- `status` (DailyApprovalStatus): APPROVED, REJECTED, or RECALLED
- `comment` (String): Comment on the approval (max 500 chars, required for REJECTED)
- `createdAt` (Instant): Creation timestamp
- `updatedAt` (Instant): Last update timestamp

### DailyApprovalStatus Enum:
- `APPROVED`: Supervisor approved the entry
- `REJECTED`: Supervisor rejected with comment
- `RECALLED`: Previously approved entry has been recalled

### Key Methods:
- `create(workLogEntryId, memberId, supervisorId, status, comment)`: Creates approval
- `reconstitute(...)`: Loads from database
- `recall()`: Transitions APPROVED → RECALLED

### Database Structure:
**Table**: `daily_entry_approvals`
- Supervisor can provide feedback on individual daily entries
- Separate from monthly approval workflow
- At most one active (non-RECALLED) approval per entry (unique index)
- Rejected entries require a comment

### Important Notes:
- Daily approvals are supervisor feedback on individual entries
- Different from monthly approval workflow (which is manager approval)
- Terminology: "supervisor" is used here; likely interchangeable with "manager" in context

---

## 7. APPROVAL SERVICE

**File**: `/home/devman/repos/miometory/backend/src/main/java/com/worklog/application/approval/ApprovalService.java`

### Responsibilities:
1. Coordinate approval workflow across multiple aggregates
2. Validate manager permissions
3. Update work log entry and absence statuses atomically
4. Provide approval detail queries

### Key Methods:

#### `submitMonth(SubmitMonthForApprovalCommand)`
- Finds or creates MonthlyApproval
- Finds all work log entries and absences for fiscal month
- Updates all entries to SUBMITTED status
- Validates proxy permission (if submitting on behalf of someone)

#### `approveMonth(ApproveMonthCommand)`
- Loads MonthlyApproval
- Updates all entries to APPROVED status (permanent lock)
- Note: Manager permission check is TODO

#### `rejectMonth(RejectMonthCommand)`
- Loads MonthlyApproval
- Updates all entries back to DRAFT status (editable again)
- Stores rejection reason

#### `getMonthlyApprovalDetail(MonthlyApprovalId)`
- Returns rich approval detail including:
  - Project breakdown with hours
  - Absence summary
  - Daily approval status summary
  - Unresolved daily rejections

### Manager Permission Validation:
- TODO in current code
- Should check if `reviewedBy` is the member's manager (member.managerId)

---

## 8. ORGANIZATION SERVICE

**File**: `/home/devman/repos/miometory/backend/src/main/java/com/worklog/application/service/OrganizationService.java`

### Responsibilities:
- Create, update, activate, deactivate organizations
- Manage pattern assignments
- Validate parent existence

### Key Methods:

#### `createOrganization(CreateOrganizationCommand)`
- Validates parent exists
- TODO: Validate circular reference
- Creates organization aggregate
- Assigns patterns if provided

#### `updateOrganization(organizationId, name)`
- Updates organization name

#### `deactivateOrganization(organizationId)`
- Deactivates organization

#### `activateOrganization(organizationId)`
- Activates organization

#### `assignPatterns(organizationId, fiscalYearPatternId, monthlyPeriodPatternId)`
- Assigns fiscal patterns to organization

#### `findById(organizationId)`
- Retrieves organization by ID

### TODO Items:
- Circular reference validation (would require loading entire parent chain)

---

## 9. ORGANIZATION REST API

**File**: `/home/devman/repos/miometory/backend/src/main/java/com/worklog/api/OrganizationController.java`

### Endpoints:

#### POST `/api/v1/tenants/{tenantId}/organizations`
- Create organization
- Request: CreateOrganizationRequest (parentId, code, name, level, fiscalYearPatternId, monthlyPeriodPatternId)
- Returns: Organization details with HTTP 201

#### GET `/api/v1/tenants/{tenantId}/organizations/{id}`
- Get organization by ID
- Returns: Organization details or 404

#### PATCH `/api/v1/tenants/{tenantId}/organizations/{id}`
- Update organization name
- Request: UpdateOrganizationRequest (name)
- Returns: HTTP 204

#### POST `/api/v1/tenants/{tenantId}/organizations/{id}/deactivate`
- Deactivate organization
- Returns: HTTP 204

#### POST `/api/v1/tenants/{tenantId}/organizations/{id}/activate`
- Activate organization
- Returns: HTTP 204

#### POST `/api/v1/tenants/{tenantId}/organizations/{id}/assign-patterns`
- Assign fiscal patterns
- Request: AssignPatternsRequest (fiscalYearPatternId, monthlyPeriodPatternId)
- Returns: HTTP 204

#### POST `/api/v1/tenants/{tenantId}/organizations/{id}/date-info`
- Calculate fiscal period info for a date
- Request: DateInfoRequest (date)
- Returns: Date info with fiscal year and monthly period boundaries
- Returns: 400 if patterns missing in hierarchy, 404 if not found

---

## 10. DATABASE SCHEMA

### Organizations Table
**File**: Migration V2__foundation.sql

```sql
CREATE TABLE organization (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenant(id),
    parent_id       UUID REFERENCES organization(id),
    code            VARCHAR(32) NOT NULL,
    name            VARCHAR(256) NOT NULL,
    level           INT NOT NULL DEFAULT 1,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_organization_tenant_code UNIQUE(tenant_id, code),
    CONSTRAINT chk_organization_level CHECK (level >= 1 AND level <= 6)
);

-- Indexes
CREATE INDEX idx_organization_tenant_id ON organization(tenant_id);
CREATE INDEX idx_organization_parent_id ON organization(parent_id);
CREATE INDEX idx_organization_status ON organization(status);
```

### Pattern References
**File**: Migration V3__add_pattern_refs_to_organization.sql

```sql
ALTER TABLE organization 
    ADD COLUMN fiscal_year_pattern_id UUID REFERENCES fiscal_year_pattern(id),
    ADD COLUMN monthly_period_pattern_id UUID REFERENCES monthly_period_pattern(id);

CREATE INDEX idx_organization_fiscal_year_pattern ON organization(fiscal_year_pattern_id);
CREATE INDEX idx_organization_monthly_period_pattern ON organization(monthly_period_pattern_id);
```

### Members Table
**File**: Migration V5__member_table.sql

```sql
CREATE TABLE members (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenant(id),
    organization_id UUID NOT NULL REFERENCES organization(id),
    email           VARCHAR(255) NOT NULL,
    display_name    VARCHAR(256) NOT NULL,
    manager_id      UUID REFERENCES members(id),
    is_active       BOOLEAN NOT NULL DEFAULT true,
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_member_tenant_email UNIQUE(tenant_id, email)
);
```

### Daily Entry Approvals Table
**File**: Migration V16__daily_entry_approval.sql

```sql
CREATE TABLE daily_entry_approvals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_log_entry_id UUID NOT NULL REFERENCES work_log_entries_projection(id),
    member_id UUID NOT NULL REFERENCES members(id),
    supervisor_id UUID NOT NULL REFERENCES members(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('APPROVED', 'REJECTED', 'RECALLED')),
    comment VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_rejected_comment CHECK (status != 'REJECTED' OR comment IS NOT NULL)
);

-- Enforce at most one active approval per entry
CREATE UNIQUE INDEX idx_daily_approval_active
    ON daily_entry_approvals(work_log_entry_id)
    WHERE status != 'RECALLED';
```

---

## 11. INFRASTRUCTURE LAYER

### OrganizationRepository
**File**: `/home/devman/repos/miometory/backend/src/main/java/com/worklog/infrastructure/repository/OrganizationRepository.java`

- Implements event sourcing pattern
- Persists organization aggregates via EventStore
- Reconstructs aggregates by replaying events
- Methods: `save()`, `findById()`, `existsById()`

### JdbcMemberRepository
**File**: `/home/devman/repos/miometory/backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberRepository.java`

- JDBC-based repository (NOT event sourced)
- Key methods:
  - `findById(MemberId)`
  - `findByEmail(TenantId, email)`
  - `findDirectSubordinates(managerId)` - Direct reports only
  - `findAllSubordinates(managerId)` - Recursive (direct + indirect)
  - `isSubordinateOf(managerId, memberId)` - Checks reporting chain

---

## 12. EVENT SOURCING INFRASTRUCTURE

### Event Store
- Single `event_store` table with JSONB payload
- Stores all domain events immutably
- Indexed by aggregate_id, aggregate_type, and version
- Snapshot store for performance optimization

### Event Types Supported:
- OrganizationCreated
- OrganizationUpdated
- OrganizationDeactivated
- OrganizationActivated
- OrganizationPatternAssigned

---

## 13. RELATIONSHIPS SUMMARY

```
Tenant (top-level boundary)
  ├─ Organization (hierarchical, 1-6 levels, event-sourced)
  │   ├─ FiscalYearPattern (for date calculations)
  │   └─ MonthlyPeriodPattern (for date calculations)
  │
  ├─ Member (belongs to one Organization, NOT event-sourced)
  │   ├─ Manager: References another Member (for approval workflow)
  │   └─ Subordinates: Other Members with this member as manager
  │
  ├─ Project (assigned to members)
  │
  └─ ApprovalWorkflow
      ├─ MonthlyApproval (monthly approval per member)
      │   └─ reviewedBy: Manager member
      │
      └─ DailyEntryApproval (per-entry supervisor feedback)
          └─ supervisorId: Supervisor member
```

---

## 14. KEY DESIGN PATTERNS

### Event Sourcing
- All organization state changes are immutable events
- Aggregates reconstructed by replaying events
- Snapshot store for optimization

### CQRS (Command Query Responsibility Segregation)
- Commands: CreateOrganizationCommand, etc.
- Queries: Separate read models/projections

### DDD (Domain-Driven Design)
- Domain aggregates: Organization, Tenant, MonthlyApproval, DailyEntryApproval
- Value objects: OrganizationId, Code, TimeAmount, FiscalMonthPeriod
- Services: OrganizationService, ApprovalService

### Manager/Supervisor Model
- Members have optional `managerId` field
- Manager is just another member (no separate role entity)
- Used for approval workflows
- Proxy entry permission: Check with `JdbcMemberRepository.isSubordinateOf()`

---

## 15. MIGRATION FILES SUMMARY

| File | Purpose |
|------|---------|
| V1__init.sql | Event store and snapshot store tables |
| V2__foundation.sql | Tenant, Organization, Patterns (fiscal year, monthly period) |
| V3__add_pattern_refs_to_organization.sql | Add pattern references to organization |
| V4__work_log_entry_tables.sql | Work log entries |
| V5__member_table.sql | Members table with manager_id field |
| V6-V10 | Projects, assignments, performance indices |
| V11__user_auth.sql | User authentication |
| V12__email_verification_tokens.sql | Email verification |
| V13__backfill_members_for_users.sql | Backfill members from users |
| V14__reconcile_projection_dates.sql | Date reconciliation |
| V15__daily_rejection_log.sql | Daily rejection tracking |
| V16__daily_entry_approval.sql | Daily entry approval/supervisor feedback |
| V17__in_app_notifications.sql | In-app notifications |
| V18__admin_permissions_seed.sql | Admin permissions |
| R__dev_seed_data.sql | Development seed data (member-project assignments) |

---

## 16. FRONTEND STATUS

No specific organization UI components found in current frontend structure.
Admin pages exist but no organization management page yet.
Directory structure: `/frontend/app/admin/`
- members/
- projects/
- users/
- tenants/
- assignments/

---

## 17. CRITICAL GAPS & TODOs

1. **Circular Reference Validation** - OrganizationService has TODO for preventing circular hierarchies
2. **Manager Permission Validation** - ApprovalService has TODO for validating reviewedBy is member's manager
3. **Organization UI** - No frontend admin UI for organization management
4. **Supervisor Hierarchy** - Concept of organizational supervisors not yet modeled (daily approval uses supervisor concept loosely)
5. **Org-level Supervisors** - No explicit mapping of supervisors to organizations (concept exists via Member.managerId but no org-level definition)

