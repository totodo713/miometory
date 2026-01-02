# Release Notes - v0.1.0-foundation

**Release Date:** January 2, 2026  
**Branch:** 001-foundation → main  
**Tag:** v0.1.0-foundation

## Overview

This is the initial release of the Work-Log application, implementing the **Foundation Feature** (001-foundation). This release establishes the core domain model, event sourcing infrastructure, and REST API for multi-tenant work log management.

## What's New

### Core Domain Model
- **Multi-Tenant Architecture**: Full tenant isolation with event-sourced aggregates
- **Organization Hierarchy**: Support for nested organizational structures with pattern inheritance
- **Fiscal Year Patterns**: Configurable fiscal year start dates (any month/day combination)
- **Monthly Period Patterns**: Flexible monthly period definitions with arbitrary start dates
- **Date Calculation Service**: Hierarchical pattern resolution for fiscal year/period calculation

### Event Sourcing Infrastructure
- **EventStore**: JDBC-based event persistence with PostgreSQL/JSONB
- **SnapshotStore**: Aggregate snapshots for performance optimization
- **AuditLogger**: Comprehensive audit trail for all domain events
- **Projection Tables**: Read-optimized views for queries

### REST API
All endpoints follow RESTful conventions with proper error handling:

- **Tenant Management**
  - `POST /api/v1/tenants` - Create tenant
  - `GET /api/v1/tenants/{id}` - Get tenant details
  - `GET /api/v1/tenants` - List all tenants

- **Organization Management**
  - `POST /api/v1/tenants/{tenantId}/organizations` - Create organization
  - `GET /api/v1/tenants/{tenantId}/organizations/{id}` - Get organization
  - `GET /api/v1/tenants/{tenantId}/organizations` - List organizations
  - `POST /api/v1/tenants/{tenantId}/organizations/{id}/date-info` - Calculate date info

- **Pattern Management**
  - Fiscal Year Patterns: Create, retrieve, list
  - Monthly Period Patterns: Create, retrieve, list

### Technical Components

**Backend Stack:**
- Spring Boot 3.5.9
- Kotlin 2.3.0 + Java 21
- Spring Data JDBC
- PostgreSQL 16
- Flyway migrations
- Testcontainers (integration testing)

**Frontend Stack:**
- Next.js 16.1.1
- React 19.x
- TypeScript 5.x
- Biome (linting/formatting)

## Test Coverage

**169 tests passing** (100% success rate)

### Test Categories
- **Unit Tests** (78 tests)
  - Domain aggregate tests (Tenant, Organization, Patterns)
  - Value object tests
  - Event sourcing infrastructure tests

- **Integration Tests** (91 tests)
  - API endpoint tests
  - Repository tests with Testcontainers
  - Application service tests
  - Date calculation edge cases

### Edge Cases Covered
- Leap year handling (Feb 29 in non-leap years)
- Request validation (missing required fields)
- Organization hierarchy traversal
- Pattern inheritance resolution
- Concurrent updates (optimistic locking)

## Database Migrations

Three Flyway migrations included:

1. **V1__init.sql**: Event sourcing schema (event_store, snapshots, audit_log)
2. **V2__foundation.sql**: Domain tables (tenants, organizations, patterns)
3. **V3__add_pattern_refs_to_organization.sql**: Organization pattern references

## Bug Fixes (Phase 7)

### Docker Permissions
- Fixed Testcontainers permission error by using `sg docker -c` wrapper

### Architecture Alignment
- Removed invalid event assertions from pattern tests (patterns are simple entities)
- Fixed test data persistence to use event-sourced repositories correctly

### SQL/JDBC Fixes
- Added `organization_id` column to pattern INSERT statements
- Fixed TenantId type mapping: extract `value()` for JDBC parameters

### Edge Case Fixes
- **Leap Day Handling**: FiscalYearPattern now adjusts Feb 29 → Feb 28 in non-leap years
- **Request Validation**: Added `@NotNull` validation for DateInfoRequest.date field

## Documentation

### Architecture Documentation
- **ARCHITECTURE.md**: Comprehensive architecture decisions and patterns
- **AGENTS.md**: Coding standards and agent instructions
- **CODE_QUALITY_REVIEW.md**: Code quality guidelines and review checklist

### Specification Documents
- **specs/001-foundation/spec.md**: Feature specification with acceptance criteria
- **specs/001-foundation/data-model.md**: Domain model and entity relationships
- **specs/001-foundation/contracts/openapi.yaml**: Full OpenAPI 3.0 specification

### Phase Documentation
- **PHASE5_GAP_ANALYSIS.md**: Architecture trade-offs and decisions
- **PHASE7_INSTRUCTIONS.md**: Final testing and merge procedures
- **QUICKSTART_PHASE7.md**: Quick reference for Phase 7 tasks

## Known Limitations

### Architectural Decisions
- **Pattern Entities**: FiscalYearPattern and MonthlyPeriodPattern use simple entity pattern instead of full event sourcing (documented in PHASE5_GAP_ANALYSIS.md)
- **Manual Projections**: Repository `updateProjection()` manually inserts into projection tables (future: consider @TransactionalEventListener)

### Future Enhancements
- Performance testing (T076 blocked, pending production-like infrastructure)
- Horizontal scaling support
- Event replay mechanism
- CQRS separation for read models

## Upgrade Notes

This is the initial release, no upgrades required.

### Database Setup
1. Ensure PostgreSQL 16+ is running
2. Create database: `createdb worklog_dev`
3. Flyway will auto-apply migrations on first run

### Docker Setup (Development)
```bash
cd infra/docker
docker-compose -f docker-compose.dev.yml up -d
```

### Running Tests
```bash
cd backend
# Requires Docker access for Testcontainers
sg docker -c "./gradlew clean test"
```

### Building Backend
```bash
cd backend
./gradlew build
```

### Running Backend
```bash
cd backend
./gradlew bootRun
# API available at http://localhost:8080
```

### Running Frontend
```bash
cd frontend
npm install
npm run dev
# UI available at http://localhost:3000
```

## Breaking Changes

N/A (initial release)

## Contributors

Developed through 7 phases of iterative development following DDD, Event Sourcing, and Clean Architecture principles.

## What's Next

See `specs/` directory for planned features:
- Member management and work log entries
- Project assignment and tracking
- Reporting and analytics
- Bulk operations and data import/export

---

**For detailed technical documentation, see:**
- [ARCHITECTURE.md](./ARCHITECTURE.md) - Architecture decisions
- [README.md](./README.md) - Getting started guide
- [specs/001-foundation/spec.md](./specs/001-foundation/spec.md) - Feature specification
