# Research: Foundation Infrastructure

**Feature Branch**: `001-foundation`  
**Date**: 2026-01-01  
**Status**: Complete

## Overview

This document consolidates research findings for the foundation infrastructure implementation, resolving all NEEDS CLARIFICATION items from the Technical Context.

---

## 1. Test Infrastructure Dependencies

### Decision: Testcontainers 2.0.2 with Spring Boot Integration

**Rationale**: Testcontainers 2.0.2 is the latest stable version with full Spring Boot 3.5.x compatibility. The `@ServiceConnection` annotation eliminates manual property wiring.

**Alternatives Considered**:
- H2 in-memory database: Rejected - PostgreSQL-specific features (JSONB) required
- Embedded PostgreSQL: Rejected - Testcontainers provides better isolation and closer parity to production

### Recommended Dependencies

```kotlin
// build.gradle.kts
dependencies {
    // Testcontainers BOM
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.2"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    
    // Spring Boot Testcontainers integration
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    
    // Database Rider (Jakarta EE classifier for Spring Boot 3.x)
    testImplementation("com.github.database-rider:rider-junit5:1.44.0:jakarta") {
        exclude(group = "com.github.database-rider", module = "rider-core")
    }
    testImplementation("com.github.database-rider:rider-core:1.44.0:jakarta")
    
    // Instancio for programmatic test data
    testImplementation("org.instancio:instancio-junit:5.5.1")
}
```

### Key Configuration Patterns

#### Base Integration Test Class

```kotlin
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class BaseIntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("worklog_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)  // Enable for local dev (disable in CI)
    }
}
```

#### Database Rider Configuration

- Use `@DBRider` annotation at class level
- Use `caseInsensitiveStrategy = Orthography.LOWERCASE` for PostgreSQL
- Datasets in YAML format at `src/test/resources/datasets/`
- Use `cleanBefore = true` for test isolation

#### Instancio Usage

```kotlin
@ExtendWith(InstancioExtension::class)
class DomainTest {
    
    @Test
    fun `should create tenant`() {
        val tenant = Instancio.of(Tenant::class.java)
            .generate(Select.field("id"), gen -> gen.text().uuid())
            .generate(Select.field("createdAt"), gen -> gen.temporal().instant().past())
            .create()
    }
}
```

---

## 2. Event Sourcing Implementation

### Decision: Custom Implementation with PostgreSQL JSONB

**Rationale**: 
- Full control over event schema and serialization
- JSONB enables queryable events with GIN indexes
- Simpler than integrating Axon/Eventuate frameworks for this scope
- PostgreSQL transaction IDs provide reliable ordering for async processing

**Alternatives Considered**:
- Axon Framework: Rejected - too heavyweight for initial implementation
- Eventuate: Rejected - adds complexity with Kafka dependency
- Plain JSON column: Rejected - JSONB offers better query/indexing capabilities

### Recommended Table Schema

```sql
-- Aggregate registry for optimistic locking
CREATE TABLE es_aggregate (
    id              UUID        PRIMARY KEY,
    version         INTEGER     NOT NULL DEFAULT 0,
    aggregate_type  TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_es_aggregate_type ON es_aggregate(aggregate_type);

-- Event store (append-only)
CREATE TABLE es_event (
    id              BIGSERIAL   PRIMARY KEY,
    aggregate_id    UUID        NOT NULL REFERENCES es_aggregate(id),
    version         INTEGER     NOT NULL,
    event_type      TEXT        NOT NULL,
    payload         JSONB       NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (aggregate_id, version)
);
CREATE INDEX idx_es_event_aggregate ON es_event(aggregate_id);
CREATE INDEX idx_es_event_type ON es_event(event_type);

-- Snapshots for performance optimization
CREATE TABLE es_snapshot (
    aggregate_id    UUID        NOT NULL REFERENCES es_aggregate(id),
    version         INTEGER     NOT NULL,
    state           JSONB       NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (aggregate_id, version)
);

-- Audit log for compliance
CREATE TABLE audit_log (
    id              BIGSERIAL   PRIMARY KEY,
    tenant_id       UUID,
    user_id         TEXT,
    action          TEXT        NOT NULL,
    resource_type   TEXT        NOT NULL,
    resource_id     UUID,
    details         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_log_tenant ON audit_log(tenant_id);
CREATE INDEX idx_audit_log_resource ON audit_log(resource_type, resource_id);
```

### Optimistic Locking Pattern

```kotlin
@Transactional
fun appendEvents(aggregateId: UUID, expectedVersion: Int, events: List<DomainEvent>) {
    // Atomic version check and increment
    val updated = jdbcTemplate.update("""
        UPDATE es_aggregate 
        SET version = version + :eventCount 
        WHERE id = :aggregateId AND version = :expectedVersion
    """, mapOf(
        "aggregateId" to aggregateId,
        "expectedVersion" to expectedVersion,
        "eventCount" to events.size
    ))
    
    if (updated == 0) {
        throw OptimisticLockException("Aggregate modified by another transaction")
    }
    
    // Insert events
    events.forEachIndexed { index, event ->
        eventRepository.insert(aggregateId, expectedVersion + index + 1, event)
    }
}
```

### Code Structure

```
com.worklog/
├── eventsourcing/
│   ├── Aggregate.kt           # Base aggregate interface
│   ├── DomainEvent.kt         # Event marker interface
│   ├── EventStore.kt          # EventStore interface & impl
│   ├── SnapshotStore.kt       # Snapshot persistence
│   └── AuditLogger.kt         # Audit logging service
├── domain/
│   ├── tenant/
│   │   ├── Tenant.kt          # Tenant aggregate
│   │   ├── TenantEvents.kt    # TenantCreated, TenantDeactivated, etc.
│   │   └── TenantRepository.kt
│   ├── organization/
│   │   ├── Organization.kt    # Organization aggregate
│   │   ├── OrganizationEvents.kt
│   │   └── OrganizationRepository.kt
│   ├── fiscalyear/
│   │   ├── FiscalYearPattern.kt
│   │   └── FiscalYearPatternRepository.kt
│   └── monthlyperiod/
│       ├── MonthlyPeriodPattern.kt
│       └── MonthlyPeriodPatternRepository.kt
└── api/
    ├── TenantController.kt
    ├── OrganizationController.kt
    └── DateInfoController.kt
```

---

## 3. Fiscal Year & Monthly Period Calculations

### Decision: Value Objects with Calculation Logic

**Rationale**: 
- Encapsulate date calculation complexity in dedicated value objects
- Pure functions enable easy unit testing
- No external library dependency for this domain-specific logic

### Fiscal Year Calculation

```kotlin
data class FiscalYearPattern(
    val id: UUID,
    val tenantId: UUID,
    val name: String,
    val startMonth: Int,  // 1-12
    val startDay: Int     // 1-31
) {
    fun getFiscalYear(date: LocalDate): Int {
        val fiscalYearStart = LocalDate.of(date.year, startMonth, startDay)
        return if (date.isBefore(fiscalYearStart)) {
            date.year - 1
        } else {
            date.year
        }
    }
    
    fun getFiscalYearRange(fiscalYear: Int): Pair<LocalDate, LocalDate> {
        val start = LocalDate.of(fiscalYear, startMonth, startDay)
        val end = start.plusYears(1).minusDays(1)
        return Pair(start, end)
    }
}
```

### Monthly Period Calculation

```kotlin
data class MonthlyPeriodPattern(
    val id: UUID,
    val tenantId: UUID,
    val name: String,
    val startDay: Int  // 1-28 (締め日の翌日)
) {
    fun getMonthlyPeriod(date: LocalDate): Pair<LocalDate, LocalDate> {
        val periodStart = if (date.dayOfMonth >= startDay) {
            date.withDayOfMonth(startDay)
        } else {
            date.minusMonths(1).withDayOfMonth(startDay)
        }
        val periodEnd = periodStart.plusMonths(1).minusDays(1)
        return Pair(periodStart, periodEnd)
    }
}
```

---

## 4. Organization Hierarchy

### Decision: Adjacency List with Level Tracking

**Rationale**:
- Simple to implement with parent_id reference
- Level field (1-6) enables efficient depth validation
- Suitable for relatively shallow hierarchies (max 6 levels)

**Alternatives Considered**:
- Nested Sets: Rejected - complex updates for hierarchical changes
- Materialized Path: Considered for future if query patterns require it
- Closure Table: Rejected - overkill for max 6 levels

### Validation Rules

1. **Hierarchy Depth**: Check `parent.level < 6` before creating child
2. **Circular Reference**: Validate new parent is not a descendant of the organization
3. **Unique Code**: Validate code uniqueness within tenant

---

## 5. Schema Evolution Strategy

### Decision: Tolerant Reader + Upcasting

**Rationale**:
- Jackson's `@JsonIgnoreProperties(ignoreUnknown = true)` handles simple additions
- Upcasting transforms old events to new format at read time
- No database migrations required for event schema changes

### Pattern

```kotlin
// New fields with defaults - backward compatible
@JsonIgnoreProperties(ignoreUnknown = true)
data class TenantCreated(
    val tenantId: UUID,
    val code: String,
    val name: String,
    val description: String? = null  // New optional field
)
```

---

## Summary of Decisions

| Area | Decision | Version/Pattern |
|------|----------|-----------------|
| Testcontainers | Use with @ServiceConnection | 2.0.2 |
| Database Rider | Jakarta classifier | 1.44.0:jakarta |
| Instancio | JUnit 5 extension | 5.5.1 |
| Event Store | Custom JSONB implementation | - |
| Optimistic Locking | Version field in es_aggregate | - |
| Hierarchy Model | Adjacency List + Level | Max 6 levels |
| Date Calculations | Value Objects with pure functions | - |
| Schema Evolution | Tolerant Reader + Upcasting | - |

---

## References

- [Testcontainers Documentation](https://testcontainers.com/)
- [Database Rider Documentation](https://github.com/database-rider/database-rider)
- [Instancio User Guide](https://www.instancio.org/user-guide/)
- [PostgreSQL Event Sourcing (eugene-khyst)](https://github.com/eugene-khyst/postgresql-event-sourcing)
- [Martin Fowler - Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html)
