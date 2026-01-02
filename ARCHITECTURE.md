# Architecture Documentation

## Overview

Work-Log follows a **Domain-Driven Design (DDD)** architecture with **Event Sourcing** pattern for all domain aggregates. The system is designed as a multi-tenant application with strict data isolation and hierarchical organization structures.

## Table of Contents

1. [Architecture Principles](#architecture-principles)
2. [System Architecture](#system-architecture)
3. [Domain Model](#domain-model)
4. [Event Sourcing](#event-sourcing)
5. [Data Flow](#data-flow)
6. [Security](#security)
7. [Testing Strategy](#testing-strategy)

---

## Architecture Principles

### 1. Domain-Driven Design (DDD)

The codebase is organized around domain concepts, not technical layers:

```
backend/src/main/java/com/worklog/
├── domain/              # Domain layer (pure business logic)
│   ├── tenant/          # Tenant aggregate
│   ├── organization/    # Organization aggregate
│   ├── fiscalyear/      # FiscalYearPattern aggregate
│   ├── monthlyperiod/   # MonthlyPeriodPattern aggregate
│   └── shared/          # Shared domain concepts
├── application/         # Application services (orchestration)
├── infrastructure/      # Technical implementations
└── api/                 # REST API controllers
```

### 2. Event Sourcing

**All state changes are recorded as immutable events.**

Benefits:
- Complete audit trail
- Time-travel debugging (replay events to any point)
- Easy to add new projections without data migration
- Natural fit for DDD aggregates

### 3. Hexagonal Architecture (Ports & Adapters)

- **Domain** is the center, isolated from external concerns
- **Ports** are interfaces defined by the domain
- **Adapters** implement those ports (REST API, JDBC, etc.)

---

## System Architecture

### High-Level Architecture

```
┌─────────────────┐
│   Frontend      │
│   (Next.js)     │
└────────┬────────┘
         │ HTTP/REST
         │
┌────────▼────────────────────────────┐
│   Backend (Spring Boot)             │
│                                     │
│  ┌──────────────────────────────┐  │
│  │   API Layer (Controllers)    │  │
│  └──────────┬───────────────────┘  │
│             │                       │
│  ┌──────────▼───────────────────┐  │
│  │   Application Layer          │  │
│  │   (Services, DTOs)           │  │
│  └──────────┬───────────────────┘  │
│             │                       │
│  ┌──────────▼───────────────────┐  │
│  │   Domain Layer               │  │
│  │   (Aggregates, Events,       │  │
│  │    Value Objects)            │  │
│  └──────────┬───────────────────┘  │
│             │                       │
│  ┌──────────▼───────────────────┐  │
│  │   Infrastructure Layer       │  │
│  │   (Repositories, EventStore) │  │
│  └──────────┬───────────────────┘  │
└─────────────┼───────────────────────┘
              │
     ┌────────▼─────────┐
     │   PostgreSQL     │
     │   (Event Store,  │
     │    Projections)  │
     └──────────────────┘
```

### Component Interaction

```
┌──────────────┐
│  Controller  │ ← REST request
└──────┬───────┘
       │
┌──────▼───────┐
│   Service    │ ← Orchestrates domain operations
└──────┬───────┘
       │
┌──────▼───────┐
│  Aggregate   │ ← Business logic + raises events
└──────┬───────┘
       │ events
┌──────▼───────┐
│  Repository  │ ← Saves events to EventStore
└──────┬───────┘
       │
┌──────▼───────┐
│  EventStore  │ ← Appends to append-only log
└──────────────┘
```

---

## Domain Model

### Core Aggregates

#### 1. Tenant
**Purpose**: Multi-tenant isolation  
**Invariants**:
- Unique code per tenant
- Code is immutable after creation
- Deactivated tenants cannot be reactivated

**Events**:
- `TenantCreated`
- `TenantRenamed`
- `TenantDeactivated`

#### 2. Organization
**Purpose**: Hierarchical organization structure  
**Invariants**:
- Maximum 6 levels of hierarchy
- Unique code within tenant
- Root organizations have `parentId = null`
- Cannot set parent to self
- Cannot create circular references

**Events**:
- `OrganizationCreated`
- `OrganizationRenamed`
- `OrganizationDeactivated`

#### 3. FiscalYearPattern
**Purpose**: Define fiscal year start date  
**Invariants**:
- `startMonth` ∈ [1, 12]
- `startDay` ∈ [1, 31]
- Name is trimmed and 1-100 characters

**Events**:
- `FiscalYearPatternCreated`

**Business Logic**:
```java
// For April 1 start pattern:
// - March 31, 2025 → FY 2024
// - April 1, 2025 → FY 2025
public int getFiscalYear(LocalDate date) {
    LocalDate fiscalYearStart = LocalDate.of(date.getYear(), startMonth, startDay);
    
    if (date.isBefore(fiscalYearStart)) {
        return date.getYear() - 1;
    } else {
        return date.getYear();
    }
}
```

#### 4. MonthlyPeriodPattern
**Purpose**: Define monthly closing date  
**Invariants**:
- `startDay` ∈ [1, 28] (restricted to handle February)
- Name is trimmed and 1-100 characters

**Events**:
- `MonthlyPeriodPatternCreated`

**Business Logic**:
```java
// For 21st day pattern:
// - Jan 15 → Period: Dec 21 - Jan 20
// - Jan 25 → Period: Jan 21 - Feb 20
public MonthlyPeriod getMonthlyPeriod(LocalDate date) {
    LocalDate periodStart;
    
    if (date.getDayOfMonth() >= startDay) {
        periodStart = LocalDate.of(date.getYear(), date.getMonth(), startDay);
    } else {
        periodStart = date.minusMonths(1).withDayOfMonth(startDay);
    }
    
    LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);
    
    return new MonthlyPeriod(periodStart, periodEnd, 
                              periodEnd.getMonthValue(), 
                              periodEnd.getYear());
}
```

### Value Objects

- `TenantId` - UUID wrapper
- `OrganizationId` - UUID wrapper
- `FiscalYearPatternId` - UUID wrapper
- `MonthlyPeriodPatternId` - UUID wrapper
- `MonthlyPeriod` - start/end dates with display month/year

### Domain Services

#### DateInfoService
**Purpose**: Calculate fiscal year and monthly period for an organization on a specific date

**Algorithm**:
1. Resolve fiscal year pattern (from org or parent hierarchy)
2. Resolve monthly period pattern (from org or parent hierarchy)
3. Calculate fiscal year using pattern
4. Calculate monthly period using pattern
5. Return `DateInfo` with all calculated values

**Pattern Inheritance**:
- Organizations inherit patterns from parent if not set
- Traverses up the hierarchy until pattern is found
- Throws exception if no pattern found in hierarchy (must be set at root)

---

## Event Sourcing

### Event Store Architecture

```
┌─────────────────────────────────────┐
│         Event Store                 │
│  (Append-Only, Immutable)           │
│                                     │
│  ┌─────────────────────────────┐   │
│  │  event_store table          │   │
│  │  ├─ event_id (PK)           │   │
│  │  ├─ aggregate_id            │   │
│  │  ├─ event_type              │   │
│  │  ├─ event_data (JSONB)      │   │
│  │  ├─ version                 │   │
│  │  ├─ occurred_at             │   │
│  │  └─ UNIQUE(aggregate_id,    │   │
│  │           version)           │   │
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │  snapshot_store table       │   │
│  │  ├─ aggregate_id (PK)       │   │
│  │  ├─ state_data (JSONB)      │   │
│  │  ├─ version                 │   │
│  │  └─ created_at              │   │
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │  audit_log table            │   │
│  │  ├─ log_id (PK)             │   │
│  │  ├─ event_id (FK)           │   │
│  │  ├─ aggregate_type          │   │
│  │  ├─ user_id                 │   │
│  │  ├─ ip_address              │   │
│  │  └─ logged_at               │   │
│  └─────────────────────────────┘   │
└─────────────────────────────────────┘
```

### Event Flow

```
1. Command Received
   ↓
2. Load Aggregate (from EventStore or Snapshot)
   ↓
3. Execute Business Logic
   ↓
4. Raise Domain Events (uncommittedEvents)
   ↓
5. Save Events to EventStore (with optimistic lock check)
   ↓
6. Update Projection Tables (synchronously)
   ↓
7. Log to AuditLog
   ↓
8. Return Response
```

### Optimistic Locking

Events are versioned to prevent concurrent modifications:

```sql
INSERT INTO event_store (
  event_id, aggregate_id, event_type, event_data, version, occurred_at
) VALUES (?, ?, ?, ?::jsonb, ?, ?)
ON CONFLICT (aggregate_id, version) DO NOTHING
RETURNING event_id
```

If `RETURNING` is empty → another transaction already wrote that version → `OptimisticLockException`

### Projection Tables

For query performance, we maintain projection tables:

```
event_store (append-only)
     ↓ (synchronized update)
projection tables:
  ├─ tenant
  ├─ organization
  ├─ fiscal_year_pattern
  └─ monthly_period_pattern
```

**Synchronization Strategy**: Write to event store, then update projection in same transaction.

**Trade-offs**:
- ✅ Simple to implement
- ✅ No eventual consistency issues
- ❌ Slightly slower writes (but still fast with proper indexing)

---

## Data Flow

### Example: Create Tenant

```
┌──────────────────────────────────────────────────────────┐
│ POST /api/v1/tenants                                     │
│ { "code": "ACME", "name": "Acme Corp" }                │
└────────────────┬─────────────────────────────────────────┘
                 │
      ┌──────────▼──────────┐
      │  TenantController   │
      │  @PostMapping       │
      └──────────┬──────────┘
                 │
      ┌──────────▼──────────┐
      │  TenantService      │
      │  (if needed)        │
      └──────────┬──────────┘
                 │
      ┌──────────▼──────────┐
      │  Tenant.create()    │  ← Factory method
      │  - Validate code    │
      │  - Trim name        │
      │  - Raise event      │
      └──────────┬──────────┘
                 │
                 │ TenantCreated event
                 │
      ┌──────────▼──────────┐
      │  TenantRepository   │
      │  .save(tenant)      │
      └──────────┬──────────┘
                 │
      ┌──────────▼──────────┐
      │  EventStore         │
      │  .append(events)    │  ← Optimistic lock check
      └──────────┬──────────┘
                 │
      ┌──────────▼──────────┐
      │  AuditLogger        │
      │  .log(event)        │
      └──────────┬──────────┘
                 │
      ┌──────────▼──────────┐
      │  Update Projection  │
      │  INSERT INTO tenant │
      └──────────┬──────────┘
                 │
      ┌──────────▼──────────┐
      │  Return Response    │
      │  { "id": "uuid",    │
      │    "code": "ACME",  │
      │    "name": "..." }  │
      └─────────────────────┘
```

### Example: Calculate Date Info

```
┌──────────────────────────────────────────────────────────┐
│ POST /api/v1/tenants/{tid}/organizations/{id}/date-info │
│ { "date": "2025-01-15" }                                │
└────────────────┬─────────────────────────────────────────┘
                 │
      ┌──────────▼──────────────┐
      │  OrganizationController │
      └──────────┬────────────────┘
                 │
      ┌──────────▼──────────────┐
      │  DateInfoService        │
      │  .getDateInfo()         │
      └──────────┬────────────────┘
                 │
                 ├─► Load Organization
                 │
                 ├─► Resolve FiscalYearPattern
                 │   (traverse parent hierarchy if needed)
                 │
                 ├─► Resolve MonthlyPeriodPattern
                 │   (traverse parent hierarchy if needed)
                 │
                 ├─► Calculate fiscal year
                 │   pattern.getFiscalYear(date)
                 │
                 ├─► Calculate monthly period
                 │   pattern.getMonthlyPeriod(date)
                 │
                 └─► Build DateInfo DTO
                     └─► Return response
```

---

## Security

### Authentication & Authorization

**Current Implementation**: Spring Security with Basic Auth

```yaml
spring:
  security:
    user:
      name: user
      password: password
```

**All API endpoints require authentication:**
```
Authorization: Basic dXNlcjpwYXNzd29yZA==
```

**Future Enhancements** (not yet implemented):
- JWT-based authentication
- Role-based access control (RBAC)
- Tenant-specific permissions
- API key authentication for service-to-service calls

### Data Isolation

**Tenant Isolation**: All queries filter by `tenant_id`

```sql
SELECT * FROM organization 
WHERE tenant_id = ? AND id = ?
```

**No cross-tenant access**: Even with valid credentials, users cannot access data from other tenants.

---

## Testing Strategy

### Test Pyramid

```
         ┌────────────┐
         │  E2E Tests │  ← Manual (not automated yet)
         └────────────┘
        ┌──────────────┐
        │ API Tests    │  ← 31 tests (Testcontainers)
        └──────────────┘
      ┌──────────────────┐
      │ Integration      │  ← 21 tests (Testcontainers)
      │ Tests            │
      └──────────────────┘
    ┌──────────────────────┐
    │ Domain Unit Tests    │  ← 81 tests (no external deps)
    └──────────────────────┘
```

### Test Categories

#### 1. Domain Unit Tests (81 tests)
**No external dependencies** (no DB, no Docker)

```kotlin
class FiscalYearPatternTest {
    @Test
    fun `create should generate valid pattern`() {
        val pattern = FiscalYearPattern.create(
            tenantId, "4月開始", 4, 1
        )
        
        assertThat(pattern.uncommittedEvents).hasSize(1)
        val event = pattern.uncommittedEvents.first()
        assertThat(event).isInstanceOf(FiscalYearPatternCreated::class.java)
    }
}
```

#### 2. Integration Tests (21 tests)
**Requires Testcontainers** (PostgreSQL Docker container)

```kotlin
@DataJdbcTest
@Testcontainers
class DateInfoServiceTest : IntegrationTestBase() {
    @Test
    fun `should calculate correct fiscal year`() {
        // Given: organization with fiscal year pattern
        // When: call getDateInfo()
        // Then: correct fiscal year returned
    }
}
```

#### 3. API Tests (31 tests)
**Full Spring Boot context** with MockMvc

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
class FiscalYearPatternControllerTest : IntegrationTestBase() {
    @Test
    fun `POST should create pattern`() {
        mockMvc.post("/api/v1/tenants/{tid}/fiscal-year-patterns") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"April","startMonth":4,"startDay":1}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { exists() }
        }
    }
}
```

### Test Infrastructure

- **Testcontainers**: Spins up real PostgreSQL in Docker
- **Flyway**: Applies all migrations before tests
- **Database Rider**: Loads test datasets from YAML
- **Instancio**: Generates random test data
- **JUnit 5**: Test framework
- **AssertJ**: Fluent assertions

### Running Tests

```bash
# All tests (requires Docker)
./gradlew test

# Domain tests only (no Docker)
./gradlew test --tests "*Test" --tests "!*IntegrationTest*"

# Specific test class
./gradlew test --tests "FiscalYearPatternTest"

# With coverage report
./gradlew test jacocoTestReport
```

---

## Database Schema

### Entity-Relationship Diagram

```
┌─────────────┐
│   tenant    │
│─────────────│
│ id (PK)     │
│ code        │◄─────┐
│ name        │      │
│ is_active   │      │
└─────────────┘      │
                     │
┌────────────────────┴────┐
│   organization          │
│─────────────────────────│
│ id (PK)                 │
│ tenant_id (FK)          │
│ parent_id (FK, self)    │
│ code                    │
│ name                    │
│ level                   │
│ fiscal_year_pattern_id  │──┐
│ monthly_period_pattern_id│─┼─┐
└─────────────────────────┘  │ │
                             │ │
   ┌─────────────────────────┘ │
   │                           │
┌──▼──────────────────┐  ┌────▼────────────────────┐
│ fiscal_year_pattern │  │ monthly_period_pattern  │
│─────────────────────│  │─────────────────────────│
│ id (PK)             │  │ id (PK)                 │
│ tenant_id (FK)      │  │ tenant_id (FK)          │
│ name                │  │ name                    │
│ start_month         │  │ start_day               │
│ start_day           │  │ created_at              │
│ created_at          │  │ updated_at              │
│ updated_at          │  └─────────────────────────┘
└─────────────────────┘
```

### Event Sourcing Tables

```
┌─────────────────┐
│  event_store    │
│─────────────────│
│ event_id (PK)   │
│ aggregate_id    │◄──┐
│ event_type      │   │
│ event_data      │   │ JSONB
│ version         │   │
│ occurred_at     │   │
│ UNIQUE(aggr_id, │   │
│        version) │   │
└─────────────────┘   │
                      │
┌─────────────────────┤
│  snapshot_store     │
│─────────────────────│
│ aggregate_id (PK)   │
│ state_data          │  JSONB
│ version             │
│ created_at          │
└─────────────────────┘
                      │
┌─────────────────────┤
│  audit_log          │
│─────────────────────│
│ log_id (PK)         │
│ event_id (FK)       │
│ aggregate_type      │
│ user_id             │
│ ip_address          │
│ logged_at           │
└─────────────────────┘
```

---

## Performance Considerations

### Indexing Strategy

```sql
-- Fast lookups by aggregate_id
CREATE INDEX idx_event_store_aggregate 
ON event_store(aggregate_id, version);

-- Fast tenant isolation
CREATE INDEX idx_organization_tenant 
ON organization(tenant_id);

-- Fast hierarchy queries
CREATE INDEX idx_organization_parent 
ON organization(parent_id) WHERE parent_id IS NOT NULL;
```

### Snapshot Optimization

For aggregates with many events (>100), use snapshots:

```java
// Every 50 events, save a snapshot
if (version % 50 == 0) {
    snapshotStore.save(aggregateId, state, version);
}

// On load, start from latest snapshot
Snapshot snapshot = snapshotStore.load(aggregateId);
List<Event> events = eventStore.load(aggregateId, snapshot.version);
```

### Query Optimization

- **Projection tables** for fast reads (no event replay needed)
- **Eager loading** of related entities where appropriate
- **Connection pooling** (HikariCP default in Spring Boot)
- **Prepared statements** for all JDBC queries

---

## Future Enhancements

### Planned Improvements

1. **CQRS Pattern**
   - Separate read models from write models
   - Use dedicated read store (e.g., Elasticsearch)

2. **Event Bus**
   - Publish domain events to message queue (e.g., Kafka)
   - Enable async event handlers
   - Support event-driven microservices

3. **API Gateway**
   - Centralized authentication
   - Rate limiting
   - Request routing

4. **Monitoring**
   - Prometheus metrics
   - Grafana dashboards
   - Distributed tracing (OpenTelemetry)

5. **Advanced Security**
   - OAuth2/OIDC integration
   - Multi-factor authentication
   - Fine-grained permissions

---

## References

- [Domain-Driven Design by Eric Evans](https://www.domainlanguage.com/ddd/)
- [Event Sourcing by Martin Fowler](https://martinfowler.com/eaaDev/EventSourcing.html)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [PostgreSQL JSONB Documentation](https://www.postgresql.org/docs/current/datatype-json.html)

---

**Last Updated**: 2026-01-02  
**Version**: 1.0  
**Maintainer**: Work-Log Development Team
