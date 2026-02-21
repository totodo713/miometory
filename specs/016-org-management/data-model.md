# Data Model: Organization Management

**Feature**: 016-org-management
**Date**: 2026-02-21

## Entities

### Organization (Event-Sourced Aggregate)

**Table**: `organization` (projection, already exists in V2/V3)
**Event Store**: `event_store` (aggregate_type = "Organization")

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | UUID | PK | Aggregate ID |
| tenant_id | UUID | FK → tenant(id), NOT NULL | Tenant ownership |
| parent_id | UUID | FK → organization(id), NULLABLE | Parent org (null for root) |
| code | VARCHAR(32) | NOT NULL, UNIQUE(tenant_id, code) | Immutable after creation |
| name | VARCHAR(256) | NOT NULL | Display name |
| level | INT | NOT NULL, CHECK(1-6) | Hierarchy depth |
| status | VARCHAR(16) | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE / INACTIVE |
| version | BIGINT | NOT NULL, DEFAULT 0 | Optimistic locking |
| fiscal_year_pattern_id | UUID | FK → fiscal_year_pattern(id), NULLABLE | Pattern reference |
| monthly_period_pattern_id | UUID | FK → monthly_period_pattern(id), NULLABLE | Pattern reference |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**State Transitions**:
```
ACTIVE → INACTIVE  (deactivate)
INACTIVE → ACTIVE  (activate)
```

**Domain Events**:
- `OrganizationCreated(aggregateId, tenantId, parentId, code, name, level)` — existing
- `OrganizationUpdated(aggregateId, name)` — existing
- `OrganizationDeactivated(aggregateId)` — existing
- `OrganizationActivated(aggregateId)` — existing
- `OrganizationPatternAssigned(aggregateId, fiscalYearPatternId, monthlyPeriodPatternId)` — existing

**Validation Rules**:
- Code: 1-32 chars, alphanumeric + underscore, case-insensitive unique per tenant
- Name: 1-256 chars, non-blank
- Level: 1-6, must match parent level + 1
- Root orgs (level 1) must have no parent
- Non-root orgs must have a parent
- Cannot update inactive organizations

### Member (JDBC, Non-Event-Sourced)

**Table**: `members` (already exists in V5/V9)

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | UUID | PK | Member ID |
| tenant_id | UUID | FK → tenant(id), NOT NULL | Tenant ownership |
| organization_id | UUID | FK → organization(id), NOT NULL | **CHANGE: mutable** |
| email | VARCHAR(255) | NOT NULL | |
| display_name | VARCHAR(255) | NOT NULL | |
| manager_id | UUID | FK → members(id), NULLABLE | Supervisor reference |
| is_active | BOOLEAN | NOT NULL, DEFAULT true | |
| version | INTEGER | NOT NULL, DEFAULT 0 | Optimistic locking |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Key Change**: `organization_id` was previously `final` in the Java domain model. Changed to mutable to support FR-015 (member transfer between organizations).

**New Methods on Member**:
- `changeOrganization(OrganizationId newOrgId)` — Updates organizationId, clears managerId, updates timestamp

**Manager Relationship Rules**:
- A member can have at most one manager
- Manager must be an active member in the same tenant
- Self-assignment prohibited (member cannot be their own manager)
- Circular chains prohibited at any depth (A→B→C→...→A)
- Manager can be in a different organization than the subordinate
- Manager assignment is cleared when member is transferred to a different organization

## Relationships

```
Tenant (1) ──── (*) Organization
Organization (1) ──── (*) Member
Organization (0..1) ──── (*) Organization  [parent-child, max 6 levels]
Member (0..1) ──── (*) Member  [manager-subordinate]
Organization (0..1) ──── (0..1) FiscalYearPattern
Organization (0..1) ──── (0..1) MonthlyPeriodPattern
```

## Indexes (Existing)

- `idx_organization_tenant_id` — Tenant-scoped queries
- `idx_organization_parent_id` — Tree traversal
- `idx_organization_status` — Status filtering
- `idx_members_organization` — Members by organization
- `idx_members_manager` — Subordinates by manager
- `idx_members_manager_covering` — Covering index for manager lookups

## New Database Migration (V19)

```sql
-- V19__organization_management_permissions.sql

-- New permissions for organization management
INSERT INTO permissions (id, name, description) VALUES
  (gen_random_uuid(), 'organization.view', 'View organizations'),
  (gen_random_uuid(), 'organization.create', 'Create organizations'),
  (gen_random_uuid(), 'organization.update', 'Update organizations'),
  (gen_random_uuid(), 'organization.deactivate', 'Deactivate/reactivate organizations')
ON CONFLICT (name) DO NOTHING;

-- Grant to TENANT_ADMIN role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'TENANT_ADMIN'
  AND p.name IN ('organization.view', 'organization.create',
                  'organization.update', 'organization.deactivate')
ON CONFLICT DO NOTHING;

-- Grant view to SUPERVISOR role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SUPERVISOR'
  AND p.name = 'organization.view'
ON CONFLICT DO NOTHING;
```
