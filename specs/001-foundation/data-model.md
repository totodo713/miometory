# Data Model: Foundation Infrastructure

**Feature Branch**: `001-foundation`  
**Date**: 2026-01-01  
**Status**: Complete

## Overview

This document defines the data model for the foundation infrastructure, including domain entities, value objects, events, and database schema.

---

## 1. Domain Entities

### 1.1 Tenant (Aggregate Root)

マルチテナントの最上位単位。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Tenant identifier |
| code | String | UNIQUE, NOT NULL, max 50 | Tenant code (business key) |
| name | String | NOT NULL, max 200 | Tenant display name |
| isActive | Boolean | NOT NULL, DEFAULT true | Active status (soft delete) |
| createdAt | Instant | NOT NULL | Creation timestamp |
| updatedAt | Instant | NOT NULL | Last update timestamp |

**Validation Rules**:
- `code`: alphanumeric with hyphens, 1-50 characters
- `name`: non-empty, 1-200 characters
- Physical deletion NOT permitted (FR-001a)

**State Transitions**:
```
[Created] --> [Active] <--> [Inactive]
```

**Events**:
- `TenantCreated(tenantId, code, name)`
- `TenantUpdated(tenantId, name)`
- `TenantDeactivated(tenantId)`
- `TenantActivated(tenantId)`

---

### 1.2 Organization (Aggregate Root)

組織。階層構造を持つ（最大6レベル）。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Organization identifier |
| tenantId | UUID | FK(Tenant), NOT NULL | Parent tenant |
| parentId | UUID | FK(Organization), NULL | Parent organization (null = root) |
| code | String | NOT NULL, max 50 | Organization code |
| name | String | NOT NULL, max 200 | Organization display name |
| level | Integer | NOT NULL, 1-6 | Hierarchy level (1 = root) |
| fiscalYearPatternId | UUID | FK(FiscalYearPattern), NULL | Assigned fiscal year pattern |
| monthlyPeriodPatternId | UUID | FK(MonthlyPeriodPattern), NULL | Assigned monthly period pattern |
| isActive | Boolean | NOT NULL, DEFAULT true | Active status |
| createdAt | Instant | NOT NULL | Creation timestamp |
| updatedAt | Instant | NOT NULL | Last update timestamp |

**Validation Rules**:
- `code`: unique within tenant, alphanumeric with hyphens, 1-50 characters
- `level`: 1-6 (FR-006)
- Child organization: `level = parent.level + 1`
- `parentId` cannot reference self (edge case)
- Circular reference prohibited (edge case)
- Physical deletion NOT permitted (FR-004)
- Root organizations (level=1) MUST have fiscalYearPatternId and monthlyPeriodPatternId set (FR-012a)

**State Transitions**:
```
[Created] --> [Active] <--> [Inactive]
```

**Events**:
- `OrganizationCreated(organizationId, tenantId, parentId, code, name, level)`
- `OrganizationUpdated(organizationId, name)`
- `OrganizationPatternAssigned(organizationId, fiscalYearPatternId, monthlyPeriodPatternId)`
- `OrganizationDeactivated(organizationId)`
- `OrganizationActivated(organizationId)`

---

### 1.3 FiscalYearPattern (Entity)

年度パターン。組織ごとに異なる会計年度開始日を設定可能。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Pattern identifier |
| tenantId | UUID | FK(Tenant), NOT NULL | Owner tenant |
| name | String | NOT NULL, max 100 | Pattern name (e.g., "4月開始") |
| startMonth | Integer | NOT NULL, 1-12 | Fiscal year start month |
| startDay | Integer | NOT NULL, 1-31 | Fiscal year start day |
| createdAt | Instant | NOT NULL | Creation timestamp |

**Validation Rules**:
- `startMonth`: 1-12 (edge case: 0, 13 → error)
- `startDay`: 1-31, must be valid for startMonth
- Name unique within tenant

**Calculation Logic**:
```kotlin
fun getFiscalYear(date: LocalDate): Int {
    val fiscalYearStart = LocalDate.of(date.year, startMonth, startDay)
    return if (date.isBefore(fiscalYearStart)) date.year - 1 else date.year
}

fun getFiscalYearRange(fiscalYear: Int): Pair<LocalDate, LocalDate> {
    val start = LocalDate.of(fiscalYear, startMonth, startDay)
    val end = start.plusYears(1).minusDays(1)
    return Pair(start, end)
}
```

**Events**:
- `FiscalYearPatternCreated(patternId, tenantId, name, startMonth, startDay)`

---

### 1.4 MonthlyPeriodPattern (Entity)

月度パターン。組織ごとに異なる締め日を設定可能。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Pattern identifier |
| tenantId | UUID | FK(Tenant), NOT NULL | Owner tenant |
| name | String | NOT NULL, max 100 | Pattern name (e.g., "21日締め") |
| startDay | Integer | NOT NULL, 1-28 | Period start day (翌日が締め日) |
| createdAt | Instant | NOT NULL | Creation timestamp |

**Validation Rules**:
- `startDay`: 1-28 (edge case: 0, 29-32 → error; 28 max to handle February)
- Name unique within tenant

**Calculation Logic**:
```kotlin
fun getMonthlyPeriod(date: LocalDate): MonthlyPeriod {
    val periodStart = if (date.dayOfMonth >= startDay) {
        date.withDayOfMonth(startDay)
    } else {
        date.minusMonths(1).withDayOfMonth(startDay)
    }
    val periodEnd = periodStart.plusMonths(1).minusDays(1)
    
    return MonthlyPeriod(
        startDate = periodStart,
        endDate = periodEnd,
        displayMonth = periodEnd.month,  // 表示用の月
        displayYear = periodEnd.year
    )
}
```

**Events**:
- `MonthlyPeriodPatternCreated(patternId, tenantId, name, startDay)`

---

## 2. Event Sourcing Infrastructure

### 2.1 EventStore (Infrastructure)

イベント永続化テーブル。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGSERIAL | PK | Auto-increment event ID |
| aggregateId | UUID | NOT NULL, INDEX | Aggregate identifier |
| aggregateType | String | NOT NULL | Aggregate type name |
| version | Integer | NOT NULL | Event version for this aggregate |
| eventType | String | NOT NULL | Event type name |
| payload | JSONB | NOT NULL | Event data |
| createdAt | Timestamp | NOT NULL | Event timestamp |

**Constraints**:
- UNIQUE(aggregateId, version) - ensures event ordering
- Events are immutable (append-only)
- Retention: indefinite (FR-013a)

### 2.2 Aggregate Registry

楽観的ロック用のAggregate管理テーブル。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK | Aggregate identifier |
| aggregateType | String | NOT NULL | Aggregate type name |
| version | Integer | NOT NULL, DEFAULT 0 | Current version |
| createdAt | Timestamp | NOT NULL | Creation timestamp |

### 2.3 Snapshot (Infrastructure)

スナップショット保存テーブル（パフォーマンス最適化用）。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| aggregateId | UUID | PK (composite) | Aggregate identifier |
| version | Integer | PK (composite) | Snapshot version |
| state | JSONB | NOT NULL | Aggregate state |
| createdAt | Timestamp | NOT NULL | Snapshot timestamp |

### 2.4 AuditLog (Infrastructure)

監査ログテーブル。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGSERIAL | PK | Log entry ID |
| tenantId | UUID | NULL, INDEX | Tenant context |
| userId | String | NULL | User performing action |
| action | String | NOT NULL | Action type (CREATE, UPDATE, etc.) |
| resourceType | String | NOT NULL | Resource type name |
| resourceId | UUID | NULL | Resource identifier |
| details | JSONB | NULL | Additional details |
| createdAt | Timestamp | NOT NULL | Action timestamp |

---

## 3. Entity Relationships

```
┌─────────────────────────────────────────────────────────────────┐
│                           Tenant                                 │
│  (id, code, name, isActive)                                      │
└─────────────────────────────────────────────────────────────────┘
        │                    │                    │
        │ 1:N                │ 1:N                │ 1:N
        ▼                    ▼                    ▼
┌──────────────┐    ┌─────────────────┐    ┌────────────────────┐
│ Organization │    │FiscalYearPattern│    │MonthlyPeriodPattern│
│ (tenantId)   │    │   (tenantId)    │    │    (tenantId)      │
└──────────────┘    └─────────────────┘    └────────────────────┘
        │                    ▲                    ▲
        │ self-ref           │ 0..1               │ 0..1
        ▼ (parentId)         │                    │
┌──────────────┐             │                    │
│ Organization │─────────────┴────────────────────┘
│   (child)    │  fiscalYearPatternId, monthlyPeriodPatternId
└──────────────┘

Organization Hierarchy (max 6 levels):
  Level 1: Root Organization (parentId = NULL)
  Level 2-6: Child Organizations (parentId = parent.id)
```

---

## 4. Database Schema (Flyway Migration)

### V2__foundation_tables.sql

```sql
-- Tenant
CREATE TABLE tenant (
    id              UUID        PRIMARY KEY,
    code            VARCHAR(50) NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_tenant_code ON tenant(code);

-- Fiscal Year Pattern
CREATE TABLE fiscal_year_pattern (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenant(id),
    name            VARCHAR(100) NOT NULL,
    start_month     INTEGER     NOT NULL CHECK (start_month BETWEEN 1 AND 12),
    start_day       INTEGER     NOT NULL CHECK (start_day BETWEEN 1 AND 31),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name)
);
CREATE INDEX idx_fyp_tenant ON fiscal_year_pattern(tenant_id);

-- Monthly Period Pattern
CREATE TABLE monthly_period_pattern (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenant(id),
    name            VARCHAR(100) NOT NULL,
    start_day       INTEGER     NOT NULL CHECK (start_day BETWEEN 1 AND 28),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name)
);
CREATE INDEX idx_mpp_tenant ON monthly_period_pattern(tenant_id);

-- Organization
CREATE TABLE organization (
    id                          UUID        PRIMARY KEY,
    tenant_id                   UUID        NOT NULL REFERENCES tenant(id),
    parent_id                   UUID        REFERENCES organization(id),
    code                        VARCHAR(50) NOT NULL,
    name                        VARCHAR(200) NOT NULL,
    level                       INTEGER     NOT NULL CHECK (level BETWEEN 1 AND 6),
    fiscal_year_pattern_id      UUID        REFERENCES fiscal_year_pattern(id),
    monthly_period_pattern_id   UUID        REFERENCES monthly_period_pattern(id),
    is_active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, code),
    CHECK (
        (level = 1 AND parent_id IS NULL) OR
        (level > 1 AND parent_id IS NOT NULL)
    )
);
CREATE INDEX idx_org_tenant ON organization(tenant_id);
CREATE INDEX idx_org_parent ON organization(parent_id);

-- Event Sourcing: Aggregate Registry
CREATE TABLE es_aggregate (
    id              UUID        PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,
    version         INTEGER     NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_es_aggregate_type ON es_aggregate(aggregate_type);

-- Event Sourcing: Event Store
CREATE TABLE es_event (
    id              BIGSERIAL   PRIMARY KEY,
    aggregate_id    UUID        NOT NULL REFERENCES es_aggregate(id),
    version         INTEGER     NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB       NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (aggregate_id, version)
);
CREATE INDEX idx_es_event_aggregate ON es_event(aggregate_id);
CREATE INDEX idx_es_event_type ON es_event(event_type);

-- Event Sourcing: Snapshots
CREATE TABLE es_snapshot (
    aggregate_id    UUID        NOT NULL REFERENCES es_aggregate(id),
    version         INTEGER     NOT NULL,
    state           JSONB       NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (aggregate_id, version)
);

-- Audit Log
CREATE TABLE audit_log (
    id              BIGSERIAL   PRIMARY KEY,
    tenant_id       UUID        REFERENCES tenant(id),
    user_id         VARCHAR(100),
    action          VARCHAR(50) NOT NULL,
    resource_type   VARCHAR(100) NOT NULL,
    resource_id     UUID,
    details         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_tenant ON audit_log(tenant_id);
CREATE INDEX idx_audit_resource ON audit_log(resource_type, resource_id);
CREATE INDEX idx_audit_created ON audit_log(created_at);
```

---

## 5. Pattern Inheritance Logic

Organizations inherit fiscal year and monthly period patterns from their parent if not explicitly set.

```kotlin
fun Organization.resolveFiscalYearPattern(repository: OrganizationRepository): FiscalYearPattern? {
    // If this org has a pattern, use it
    fiscalYearPatternId?.let { return fiscalYearPatternRepository.findById(it) }
    
    // Otherwise, inherit from parent (recursively)
    parentId?.let {
        val parent = repository.findById(it)
        return parent?.resolveFiscalYearPattern(repository)
    }
    
    // Root org without pattern - should not happen (validation rule)
    return null
}
```

---

## 6. Test Data Examples

### Dataset: tenants.yml
```yaml
TENANT:
  - ID: "550e8400-e29b-41d4-a716-446655440001"
    CODE: "acme"
    NAME: "ACME Corporation"
    IS_ACTIVE: true
    CREATED_AT: "2026-01-01 00:00:00"
    UPDATED_AT: "2026-01-01 00:00:00"
```

### Dataset: organizations.yml
```yaml
FISCAL_YEAR_PATTERN:
  - ID: "660e8400-e29b-41d4-a716-446655440001"
    TENANT_ID: "550e8400-e29b-41d4-a716-446655440001"
    NAME: "4月開始"
    START_MONTH: 4
    START_DAY: 1

MONTHLY_PERIOD_PATTERN:
  - ID: "770e8400-e29b-41d4-a716-446655440001"
    TENANT_ID: "550e8400-e29b-41d4-a716-446655440001"
    NAME: "21日締め"
    START_DAY: 21

ORGANIZATION:
  - ID: "880e8400-e29b-41d4-a716-446655440001"
    TENANT_ID: "550e8400-e29b-41d4-a716-446655440001"
    PARENT_ID: null
    CODE: "headquarters"
    NAME: "本社"
    LEVEL: 1
    FISCAL_YEAR_PATTERN_ID: "660e8400-e29b-41d4-a716-446655440001"
    MONTHLY_PERIOD_PATTERN_ID: "770e8400-e29b-41d4-a716-446655440001"
    IS_ACTIVE: true
```
