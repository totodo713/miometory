---
name: new-endpoint
description: Use when adding a new REST API endpoint that requires scaffolding across all DDD layers (Domain, Application, API, Infrastructure, Migration, Tests)
disable-model-invocation: true
---

# New Endpoint Scaffold

Generate all files for a new REST API endpoint across DDD layers. Uses event sourcing, multi-tenant support, and delegates to existing skills for migrations and tests.

## Step 1: Requirements Gathering

Ask the user for:

1. **Aggregate name** (PascalCase, e.g. `Project`, `WorkLogEntry`)
2. **Fields** (name + type for each, e.g. `name: String`, `startDate: LocalDate`)
3. **Tenant-scoped?** (yes → URL under `/api/v1/tenants/{tenantId}/...`, no → `/api/v1/...`)
4. **Operations** (which to generate): `create`, `update`, `delete`, `findById`, `list`
5. **URL path** (default: pluralized snake_case of aggregate, e.g. `projects`)

Confirm all inputs before proceeding.

## Step 2: Domain Layer

Generate files in order. Follow templates in `references/aggregate-template.md`.

| Order | File | Path |
|-------|------|------|
| 1 | `{Aggregate}Id.java` | `domain/{aggregate}/` |
| 2 | `{Aggregate}Created.java` (+ events per operation) | `domain/{aggregate}/` |
| 3 | `{Aggregate}.java` | `domain/{aggregate}/` |

**Event mapping:**
- `create` → `{Aggregate}Created`
- `update` → `{Aggregate}Updated`
- `delete` → `{Aggregate}Deleted` (soft-delete via boolean flag)

## Step 3: Application Layer

Follow templates in `references/service-template.md`.

| Order | File | Path |
|-------|------|------|
| 4 | `Create{Aggregate}Command.java` (+ per operation) | `application/command/` |
| 5 | `{Aggregate}Service.java` | `application/service/` |

**Command mapping:**
- `create` → `Create{Aggregate}Command` (all fields)
- `update` → `Update{Aggregate}Command` (mutable fields only)
- `delete` → no command needed (just ID)

## Step 4: Infrastructure Layer

Follow templates in `references/repository-template.md`.

| Order | File | Path |
|-------|------|------|
| 6 | `{Aggregate}Repository.java` | `infrastructure/repository/` |

Repository must implement: `save()`, `findById()`, `updateProjection()`, `deserializeEvent()`.
If tenant-scoped, also add `findByTenantId()`.

## Step 5: API Layer

Follow templates in `references/controller-template.md`.

| Order | File | Path |
|-------|------|------|
| 7 | `{Aggregate}Controller.java` | `api/` |

**HTTP method mapping:**
| Operation | Method | Status | Return |
|-----------|--------|--------|--------|
| create | POST | 201 | `{ id, ...fields }` |
| findById | GET `/{id}` | 200 | `{ id, ...fields }` |
| list | GET | 200 | `[ { id, ...fields } ]` |
| update | PATCH `/{id}` | 204 | empty |
| delete | DELETE `/{id}` | 204 | empty |

DTOs are nested records inside the controller class.

## Step 6: Migration (delegate)

Provide the following context in the conversation, then invoke `/create-migration` (an interactive skill that will guide you through the migration creation):
- **Table name**: snake_case of aggregate (e.g. `project`)
- **Columns**: map fields using type table below + `tenant_id UUID REFERENCES tenant(id)` if scoped
- **Indexes**: `idx_{table}_tenant` for tenant_id, plus any FK columns
- **Seed data**: at least 2 rows using existing tenant UUIDs from `data-dev.sql`

**Type mapping:**

| Java Type | SQL Type |
|-----------|----------|
| String | VARCHAR(255) |
| UUID | UUID |
| LocalDate | DATE |
| LocalDateTime | TIMESTAMP |
| Instant | TIMESTAMP |
| int / Integer | INTEGER |
| long / Long | BIGINT |
| BigDecimal | DECIMAL(19,4) |
| boolean / Boolean | BOOLEAN |
| Enum (as String) | VARCHAR(50) |

## Step 7: Tests (delegate)

Invoke `/gen-test` twice:

1. **Domain unit test**: `/gen-test {Aggregate}.java`
   - Tests: create with valid params, reject invalid input, event generation
2. **API integration test**: `/gen-test {Aggregate}Controller.java`
   - Tests: POST creates (201), GET returns (200), PATCH updates (204), not-found (404)

## Step 8: Build Verification

```bash
cd backend && ./gradlew build
```

Fix any compilation errors before completing.

## Checklist

Before completing, verify:
- [ ] ID implements `EntityId` with `generate()`, `of(UUID)`, `of(String)` factories
- [ ] Events implement `DomainEvent` with `create()` factory and `eventType()`
- [ ] Aggregate extends `AggregateRoot`, has `apply()` switch for all events
- [ ] Commands are records with required fields
- [ ] Service uses `@Transactional` for write operations
- [ ] Repository handles: save (event append + projection), findById (event replay)
- [ ] Repository `deserializeEvent()` covers all event types
- [ ] Controller DTOs are nested records, HTTP status codes match the table above
- [ ] If tenant-scoped: URL includes `{tenantId}`, `tenant_id` FK in migration
- [ ] Migration and tests generated via `/create-migration` and `/gen-test`
- [ ] `./gradlew build` passes
