# Work-Log: Engineer Management System

A comprehensive engineer management system built with event sourcing architecture, supporting multi-tenant organizations, fiscal year patterns, and work log tracking.

## 🚀 Project Overview

Work-Log is a DDD (Domain-Driven Design) and event-sourced application designed to manage engineers, projects, and work logs across multiple tenants and organizations. The system supports flexible fiscal year patterns, monthly period calculations, and hierarchical organization structures.

### Key Features

- **Multi-Tenant Architecture**: Isolated data and configurations per tenant
- **Hierarchical Organizations**: Up to 6 levels of organization hierarchy
- **Flexible Fiscal Year Patterns**: Support for various fiscal year start dates (e.g., April 1, November 1)
- **Monthly Period Patterns**: Configurable monthly closing dates (1-28)
- **Event Sourcing**: Complete audit trail of all domain changes
- **Date Info API**: Calculate fiscal year and monthly period for any date

## 📁 Project Structure

```
work-log/
├── backend/           # Spring Boot backend (Kotlin + Java)
│   ├── src/main/
│   │   ├── java/      # Domain logic (Java)
│   │   ├── kotlin/    # Infrastructure (Kotlin)
│   │   └── resources/ # Configuration and migrations
│   └── src/test/      # Test suite (Kotlin + JUnit)
├── frontend/          # Next.js frontend (TypeScript + React)
├── infra/            # Infrastructure configurations
│   └── docker/       # Docker Compose for local development
└── specs/            # Feature specifications and tasks
```

## 🛠 Technology Stack

### Backend
- **Framework**: Spring Boot 3.5.9
- **Languages**: Kotlin 2.3.0, Java 21
- **Database**: PostgreSQL (with JSONB for events)
- **Migrations**: Flyway
- **Security**: Spring Security (Basic Auth)
- **Testing**: JUnit 5, Testcontainers, Instancio, DBUnit Rider

### Frontend
- **Framework**: Next.js 16.1.1
- **UI Library**: React 19.2.3
- **Language**: TypeScript 5.x
- **Styling**: Tailwind CSS
- **Linter/Formatter**: Biome

### Infrastructure
- **Database**: PostgreSQL 17
- **Containerization**: Docker & Docker Compose
- **Build Tools**: Gradle (backend), npm (frontend)

## 🏗 Architecture

### Event Sourcing Pattern

All domain aggregates use event sourcing:
- `Tenant` - Multi-tenant isolation
- `Organization` - Hierarchical org structure
- `FiscalYearPattern` - Fiscal year definitions
- `MonthlyPeriodPattern` - Monthly period definitions

### Domain Events
- `TenantCreated`, `TenantRenamed`, `TenantDeactivated`
- `OrganizationCreated`, `OrganizationDeactivated`
- `FiscalYearPatternCreated`
- `MonthlyPeriodPatternCreated`

### Event Store Components
- **EventStore**: Append-only event storage with optimistic locking
- **SnapshotStore**: Performance optimization for aggregate reconstruction
- **AuditLogger**: Immutable audit log for all events

## 📋 Prerequisites

- **Java**: 21 or higher
- **Node.js**: 18.x or higher
- **Docker**: Latest version (for integration tests and local development)
- **PostgreSQL**: 17 (via Docker or local installation)

## 🚀 Getting Started

### 1. Clone the Repository

```bash
git clone <repository-url>
cd work-log
```

### 2. Backend Setup

```bash
cd backend

# Build the project
./gradlew build -x test

# Run the application (requires PostgreSQL)
./gradlew bootRun
```

The backend will start on `http://localhost:8080`

### 3. Frontend Setup

```bash
cd frontend

# Install dependencies
npm install

# Run development server
npm run dev
```

The frontend will start on `http://localhost:3000`

### 4. Database Setup (Docker)

```bash
cd infra/docker

# Start PostgreSQL
docker-compose -f docker-compose.dev.yml up -d
```

**Database Connection:**
- Host: `localhost`
- Port: `5432`
- Database: `worklog_dev`
- Username: `worklog`
- Password: `worklog`

## 🧪 Running Tests

### Backend Tests

```bash
cd backend

# Run all tests (requires Docker)
./gradlew test

# Run specific test class
./gradlew test --tests "FiscalYearPatternTest"

# Run without integration tests
./gradlew test --tests "*Test" --exclude-task testIntegration
```

**Test Coverage:**
- Domain tests: 81 tests (no Docker required)
- Integration tests: 21 tests (requires Docker)
- Total: 102 tests

### Frontend Tests

```bash
cd frontend

# Lint
npm run lint

# Format
npm run format
```

## 📚 API Documentation

### Authentication

All API endpoints require Basic Authentication:
```
Username: user
Password: password
```

### Core Endpoints

#### Tenants
- `POST /api/v1/tenants` - Create tenant
- `GET /api/v1/tenants` - List tenants
- `GET /api/v1/tenants/{id}` - Get tenant
- `PATCH /api/v1/tenants/{id}/deactivate` - Deactivate tenant

#### Organizations
- `POST /api/v1/tenants/{tenantId}/organizations` - Create organization
- `GET /api/v1/tenants/{tenantId}/organizations` - List organizations
- `GET /api/v1/tenants/{tenantId}/organizations/{id}` - Get organization
- `PATCH /api/v1/tenants/{tenantId}/organizations/{id}/deactivate` - Deactivate organization

#### Fiscal Year Patterns
- `POST /api/v1/tenants/{tenantId}/fiscal-year-patterns` - Create pattern
- `GET /api/v1/tenants/{tenantId}/fiscal-year-patterns` - List patterns
- `GET /api/v1/tenants/{tenantId}/fiscal-year-patterns/{id}` - Get pattern

#### Monthly Period Patterns
- `POST /api/v1/tenants/{tenantId}/monthly-period-patterns` - Create pattern
- `GET /api/v1/tenants/{tenantId}/monthly-period-patterns` - List patterns
- `GET /api/v1/tenants/{tenantId}/monthly-period-patterns/{id}` - Get pattern

#### Date Info (Calculation)
- `POST /api/v1/tenants/{tenantId}/organizations/{id}/date-info` - Calculate fiscal year and monthly period

**Example Request:**
```bash
curl -X POST http://localhost:8080/api/v1/tenants/{tenantId}/organizations/{id}/date-info \
  -H "Content-Type: application/json" \
  -u user:password \
  -d '{"date": "2025-01-15"}'
```

**Example Response:**
```json
{
  "date": "2025-01-15",
  "fiscalYear": 2024,
  "fiscalYearStart": "2024-04-01",
  "fiscalYearEnd": "2025-03-31",
  "monthlyPeriodStart": "2024-12-21",
  "monthlyPeriodEnd": "2025-01-20",
  "fiscalYearPatternId": "uuid",
  "monthlyPeriodPatternId": "uuid",
  "organizationId": "uuid"
}
```

## 🗄 Database Schema

### Main Tables
- `tenant` - Tenant entities
- `organization` - Organization hierarchy
- `fiscal_year_pattern` - Fiscal year definitions
- `monthly_period_pattern` - Monthly period definitions

### Event Sourcing Tables
- `event_store` - All domain events (append-only)
- `snapshot_store` - Aggregate snapshots for performance
- `audit_log` - Immutable audit trail

### Migrations
- `V1__init.sql` - Initial schema
- `V2__foundation.sql` - Event sourcing tables
- `V3__add_pattern_refs_to_organization.sql` - Pattern references

## 🐳 Docker Support

### Development Environment

```bash
cd infra/docker
docker-compose -f docker-compose.dev.yml up -d
```

Services:
- PostgreSQL (port 5432)

### Production Build

```bash
# Backend
cd backend
./gradlew bootJar
docker build -t work-log-backend .

# Frontend
cd frontend
npm run build
docker build -t work-log-frontend .
```

## 📈 Development Workflow

### Branch Strategy
- `main` - Production-ready code
- `001-foundation` - Feature branch for foundation phase
- Future features: `002-member`, `003-project`, etc.

### Commit Message Convention
- `feat:` - New feature
- `fix:` - Bug fix
- `docs:` - Documentation changes
- `test:` - Test additions/changes
- `refactor:` - Code refactoring

### Testing Strategy
1. **Domain Tests** - Unit tests for domain logic (no DB)
2. **Integration Tests** - Full stack tests with Testcontainers
3. **API Tests** - REST API endpoint tests

## 🎯 Current Status

### Phase 1-5: Foundation ✅ COMPLETE
- ✅ Tenant & Organization management
- ✅ Fiscal Year & Monthly Period patterns
- ✅ Event Sourcing infrastructure
- ✅ Date calculation API
- ✅ All domain tests passing (81/81)

### Phase 6: Polish 🚧 IN PROGRESS
- ⏳ Global exception handler
- ⏳ Error response standardization
- ⏳ Performance testing
- ⏳ Code coverage verification

### Phase 7: Final Testing ⏳ BLOCKED
- ⚠️ Requires Docker access for integration tests
- ⏳ Full test suite execution (102 tests)
- ⏳ Merge to main branch

## 🔧 Troubleshooting

### Docker Permission Denied

If you see: `permission denied while trying to connect to the docker API`

**Solution:**
```bash
# Add user to docker group
sudo usermod -aG docker $USER
newgrp docker

# Verify
docker ps
```

### Test Failures

If integration tests fail:
1. Ensure Docker is running: `docker ps`
2. Check Testcontainers logs in `backend/build/reports/tests/test/`
3. Verify database migrations: Check Flyway logs

### Build Failures

```bash
# Clean build
cd backend
./gradlew clean build -x test

# Check Java version
java -version  # Should be 21+
```

## 📖 Documentation

- [Feature Specification](specs/001-foundation/spec.md) - Detailed feature requirements
- [Tasks Breakdown](specs/001-foundation/tasks.md) - Implementation tasks
- [Phase 7 Instructions](PHASE7_INSTRUCTIONS.md) - Final testing guide
- [Docker Setup Guide](DOCKER_SETUP_REQUIRED.md) - Docker configuration
- [Phase 5 Gap Analysis](PHASE5_GAP_ANALYSIS.md) - Architectural decisions
- [Agent Guidelines](AGENTS.md) - Coding standards for AI agents

## 🤝 Contributing

1. Follow the coding standards in `AGENTS.md`
2. Write tests for all new features
3. Ensure all tests pass before committing
4. Use conventional commit messages
5. Update documentation as needed

## 📝 License

[Add your license here]

## 👥 Team

[Add team information here]

---

**Last Updated**: 2026-01-02  
**Version**: 0.0.1-SNAPSHOT  
**Branch**: 001-foundation
