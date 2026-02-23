---
name: create-migration
description: Create a Flyway migration with auto-versioning, domain model stub, and dev seed data
disable-model-invocation: true
---

# Create Flyway Migration

Generate a new Flyway migration file with the correct version number, along with optional domain model and seed data stubs.

## Steps

### 1. Detect Next Version Number

Scan `backend/src/main/resources/db/migration/` for existing `V{N}__*.sql` files.
Find the highest N and use N+1 as the new version.

```bash
ls backend/src/main/resources/db/migration/V*.sql | sed 's/.*V\([0-9]*\)__.*/\1/' | sort -n | tail -1
```

### 2. Create Migration File

**Path**: `backend/src/main/resources/db/migration/V{N}__{description}.sql`

Use snake_case for `{description}` (e.g., `add_project_categories`).

**Template** (follow the pattern from `V5__member_table.sql`):

```sql
-- V{N}: {Title}
-- Description: {Brief description of what this migration does}
-- Feature: {feature reference if applicable}

-- ============================================================================
-- {Section Title}
-- ============================================================================
CREATE TABLE IF NOT EXISTS {table_name} (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    -- ... columns ...
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for common query patterns
CREATE INDEX idx_{table_name}_tenant ON {table_name}(tenant_id);

COMMENT ON TABLE {table_name} IS '{description}';
```

**Key conventions**:
- Always include `tenant_id` FK for multi-tenant tables
- Use `UUID` primary keys
- Include `created_at` / `updated_at` timestamps
- Add indexes for FK columns and common query patterns
- Add `COMMENT ON` for tables and non-obvious columns

### 3. Create Domain Model Stub (if creating a new aggregate)

**Aggregate** at `backend/src/main/java/com/worklog/domain/{aggregate}/`:
- `{Aggregate}.java` — aggregate root extending `AggregateRoot`
- `{Aggregate}Id.java` — typed ID value object

Follow existing patterns in `domain/worklog/`, `domain/member/`, `domain/absence/`.

### 4. Add Dev Seed Data

**File**: `backend/src/main/resources/data-dev.sql`

Use `INSERT ... ON CONFLICT (id) DO UPDATE SET ...` pattern to make seed data idempotent:

```sql
-- {Aggregate} seed data
INSERT INTO {table_name} (id, tenant_id, ...)
VALUES ('uuid-here', 'existing-tenant-uuid', ...)
ON CONFLICT (id) DO UPDATE SET
    column1 = EXCLUDED.column1,
    column2 = EXCLUDED.column2;
```

Reference existing tenant/organization UUIDs from seed data.

### 5. Checklist

Before completing, verify:
- [ ] Version number is sequential (no gaps, no duplicates)
- [ ] Migration file is idempotent (`IF NOT EXISTS`, `IF EXISTS`)
- [ ] Multi-tenant tables have `tenant_id` FK
- [ ] Indexes cover FK columns and common query patterns
- [ ] Domain model follows DDD aggregate pattern (if applicable)
- [ ] Seed data uses `ON CONFLICT` for idempotency
- [ ] Run `cd backend && ./gradlew build` to validate migration
