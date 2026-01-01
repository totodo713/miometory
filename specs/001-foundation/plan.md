# Implementation Plan: Foundation Infrastructure

**Branch**: `001-foundation` | **Date**: 2026-01-01 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-foundation/spec.md`

## Summary

Build the foundational infrastructure for Engineer Management System Phase 1, including:
- Multi-tenant architecture with Tenant and Organization entities (max 6-level hierarchy)
- Fiscal Year and Monthly Period pattern management for flexible accounting periods
- Event Sourcing infrastructure (EventStore, Snapshots, Audit Logs)
- Comprehensive test infrastructure using Testcontainers, Database Rider, and Instancio

Technical approach: DDD + Event Sourcing with Spring Boot 3.x/Kotlin, PostgreSQL for persistence, JSONB for flexible event schema.

## Technical Context

**Language/Version**: Kotlin 2.3.0, Java 21 (backend); TypeScript 5.x (frontend)  
**Primary Dependencies**: Spring Boot 3.5.9, Spring Data JDBC, Spring Security, Flyway (backend); Next.js 16.1.1, React 19.x (frontend)  
**Storage**: PostgreSQL with JSONB for events  
**Testing**: JUnit 5, Testcontainers 2.0.2, Database Rider 1.44.0:jakarta, Instancio 5.5.1 (backend)  
**Target Platform**: Linux server (Docker), Web browser  
**Project Type**: Web application (frontend + backend)  
**Performance Goals**: API response time < 100ms, Test suite execution < 60s (SC-001, SC-005)  
**Constraints**: < 100ms API latency (per spec), 80%+ code coverage on domain layer (SC-007)  
**Scale/Scope**: Multi-tenant SaaS, 6-level org hierarchy max, indefinite event retention

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. Code Quality
- [x] Static analysis: Biome configured for frontend; backend uses IDE/Gradle checks
- [x] Code review: PR-based workflow (GitHub)
- [x] Documentation: Inline docs required per AGENTS.md guidelines
- **Status**: PASS

### II. Testing Discipline & Standards
- [x] Test pyramid: Unit tests (domain), Integration tests (Testcontainers), minimal E2E
- [x] Coverage target: 80%+ domain layer (SC-007)
- [x] Test isolation: Testcontainers + Database Rider ensure clean state
- **Status**: PASS

### III. Consistent User Experience (UX)
- [x] Phase 1 is API-only; frontend integration deferred
- [x] Error responses will follow consistent JSON structure
- **Status**: PASS (API-focused phase)

### IV. Performance Requirements
- [x] Target: < 100ms API response (SC-001)
- [x] Profiling: JVM metrics via Spring Actuator
- **Status**: PASS

### Dependency Compliance
- [x] All dependencies are current LTS/stable versions
- [x] Flyway for schema versioning ensures reproducibility
- **Status**: PASS

**Overall Gate Status**: PASS - No violations, proceed to Phase 0

## Project Structure

### Documentation (this feature)

```text
specs/001-foundation/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (OpenAPI specs)
└── tasks.md             # Phase 2 output (NOT created by plan)
```

### Source Code (repository root)

```text
backend/
├── src/
│   ├── main/
│   │   ├── java/com/worklog/
│   │   │   ├── api/                    # REST controllers
│   │   │   ├── domain/                 # Domain entities, aggregates, events
│   │   │   │   ├── tenant/             # Tenant aggregate
│   │   │   │   ├── organization/       # Organization aggregate
│   │   │   │   ├── fiscalyear/         # FiscalYearPattern value object
│   │   │   │   └── monthlyperiod/      # MonthlyPeriodPattern value object
│   │   │   ├── eventsourcing/          # EventStore, Snapshot infrastructure
│   │   │   └── infrastructure/         # Config, security, repositories
│   │   └── resources/
│   │       └── db/migration/           # Flyway migrations
│   └── test/
│       ├── java/com/worklog/           # Test classes
│       └── resources/
│           └── datasets/               # Database Rider YAML datasets
└── build.gradle.kts

frontend/
├── app/                                # Next.js App Router
├── public/
└── package.json
```

**Structure Decision**: Web application structure with existing backend/ and frontend/ directories. Backend follows DDD layered architecture with domain/, api/, and infrastructure/ packages. Event sourcing infrastructure in dedicated eventsourcing/ package.

## Complexity Tracking

> **No violations identified. Constitution Check passed.**

N/A - All design decisions align with constitution principles.

## Post-Design Constitution Re-Check

*Re-evaluated after Phase 1 design completion.*

### I. Code Quality
- [x] API contracts defined in OpenAPI 3.1 format (`contracts/openapi.yaml`)
- [x] Data model documented with validation rules (`data-model.md`)
- [x] Clear package structure defined for DDD layers
- **Status**: PASS

### II. Testing Discipline & Standards
- [x] Test infrastructure researched: Testcontainers 2.0.2, Database Rider 1.44.0, Instancio 5.5.1
- [x] Test data patterns defined (YAML datasets, programmatic generation)
- [x] Acceptance scenarios from spec mapped to testable API endpoints
- **Status**: PASS

### III. Consistent User Experience (UX)
- [x] Error response schema defined (`ErrorResponse` in OpenAPI)
- [x] Consistent API patterns (REST, JSON, standard HTTP status codes)
- [x] 401 Unauthorized for auth failures per FR-024
- **Status**: PASS

### IV. Performance Requirements
- [x] Event sourcing with optimistic locking documented
- [x] Snapshot strategy defined for aggregate performance
- [x] Database indexes specified for query performance
- **Status**: PASS

### Dependency Compliance
- [x] All dependency versions resolved and documented in `research.md`
- [x] Jakarta EE classifier specified for Spring Boot 3.x compatibility
- **Status**: PASS

**Overall Post-Design Status**: PASS - Design artifacts complete, ready for Phase 2 (tasks)
