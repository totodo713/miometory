# Miometry (ミオメトリー): Time Entry Management System

A comprehensive time entry management system built with event sourcing architecture, supporting multi-tenant organizations, fiscal year patterns, and work log tracking.

## 🚀 Project Overview

Miometry is a DDD (Domain-Driven Design) and event-sourced application designed to manage engineers, projects, and work logs across multiple tenants and organizations. The system supports flexible fiscal year patterns, monthly period calculations, and hierarchical organization structures.

### Key Features

- **Multi-Tenant Architecture**: Isolated data and configurations per tenant
- **Hierarchical Organizations**: Up to 6 levels of organization hierarchy
- **Flexible Fiscal Year Patterns**: Support for various fiscal year start dates (e.g., April 1, November 1)
- **Monthly Period Patterns**: Configurable monthly closing dates (1-28, with 21st-20th default)
- **Event Sourcing**: Complete audit trail of all domain changes
- **Date Info API**: Calculate fiscal year and monthly period for any date
- **Work Log Entry**: Daily time entry with multi-project allocation
- **Absence Tracking**: Vacation, sick leave, and other absence types
- **Approval Workflow**: Submit, approve, and reject work logs
- **CSV Import/Export**: Bulk data management with streaming processing
- **Proxy Entry**: Managers can enter time on behalf of team members

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
│   └── docker/       # Docker Compose for local/production
├── specs/            # Feature specifications and tasks
└── docs/             # User documentation
```

## 🛠 Technology Stack

### Backend
- **Framework**: Spring Boot 3.5.9
- **Languages**: Kotlin 2.3.0, Java 21
- **Database**: PostgreSQL (with JSONB for events)
- **Migrations**: Flyway
- **Security**: Spring Security (Form-based Auth, Password Reset)
- **Testing**: JUnit 5, Testcontainers, Instancio, DBUnit Rider

### Frontend
- **Framework**: Next.js 16.1.6
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
- `WorkLogEntry` - Daily time entries
- `Absence` - Leave tracking
- `ApprovalWorkflow` - Submission and approval status
- `Member` - User profiles and roles

### Domain Events
- `TenantCreated`, `TenantRenamed`, `TenantDeactivated`
- `OrganizationCreated`, `OrganizationDeactivated`
- `FiscalYearPatternCreated`, `MonthlyPeriodPatternCreated`
- `EntryCreated`, `EntryUpdated`, `EntrySubmitted`
- `EntryApproved`, `EntryRejected`

### Event Store Components
- **EventStore**: Append-only event storage with optimistic locking
- **SnapshotStore**: Performance optimization for aggregate reconstruction
- **AuditLogger**: Immutable audit log for all events
- **Projections**: Read models (MonthlyCalendar, ApprovalQueue, DailyEntry)

## 📋 Prerequisites

- **Java**: 21 or higher
- **Node.js**: 18.x or higher
- **Docker**: Latest version (for integration tests and local development)
- **PostgreSQL**: 17 (via Docker or local installation)

## 🚀 Getting Started

### Quick Start (Docker)

```bash
# Start all services
cd infra/docker
docker-compose -f docker-compose.dev.yml up -d

# Backend (with seed data)
cd backend
./gradlew bootRun --args='--spring.profiles.active=dev'

# Frontend
cd frontend
npm install && npm run dev
```

The application will be available at:
- Frontend: http://localhost:3000
- Backend API: http://localhost:8080

### Development Setup with Seed Data

For reviewers and new developers, we provide a complete seed data setup:

```bash
# 1. Start Docker containers (PostgreSQL + Redis)
cd infra/docker
docker-compose -f docker-compose.dev.yml up -d

# 2. Start backend with dev profile (loads seed data automatically)
cd backend
./gradlew bootRun --args='--spring.profiles.active=dev'

# 3. Start frontend
cd frontend
npm install && npm run dev
```

The `dev` profile automatically:
- Runs Flyway migrations (V1-V12)
- Loads `data-dev.sql` with sample data
- Disables Redis cache and rate limiting for easier debugging

#### Seed Data Contents

| Entity | Count | Description |
|--------|-------|-------------|
| Tenant | 1 | Miometry Corporation |
| Organization | 1 | Engineering Department |
| Members | 4 | Test users (see below) |
| Work Log Entries | 5 | Sample entries with various statuses |
| Absences | 2 | Vacation and sick leave examples |

#### Test Users

| User | Email | Role | UUID |
|------|-------|------|------|
| Bob Engineer | bob.engineer@miometry.example.com | Regular user | `00000000-0000-0000-0000-000000000001` |
| Alice Manager | alice.manager@miometry.example.com | Manager | `00000000-0000-0000-0000-000000000002` |
| Charlie Engineer | charlie.engineer@miometry.example.com | Subordinate | `00000000-0000-0000-0000-000000000003` |
| David Independent | david.independent@miometry.example.com | Independent | `00000000-0000-0000-0000-000000000004` |

> **Note**: The frontend currently uses hardcoded mock UUIDs. Bob (UUID ending in `...01`) is the default user, and Alice (UUID ending in `...02`) is used for manager view testing.

#### Resetting Seed Data

The seed data is idempotent. To reset to a clean state:

```bash
# Option 1: Re-run with dev profile (updates existing data)
./gradlew bootRun --args='--spring.profiles.active=dev'

# Option 2: Full database reset
docker-compose -f docker-compose.dev.yml down -v
docker-compose -f docker-compose.dev.yml up -d
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Manual Setup

See [QUICKSTART.md](QUICKSTART.md) for detailed setup instructions.

## 🧪 Running Tests

### Backend Tests

```bash
cd backend

# Run all tests (requires Docker)
./gradlew test

# Run specific test class
./gradlew test --tests "FiscalYearPatternTest"
```

### Frontend Tests

```bash
cd frontend

# Unit tests
npm run test

# E2E tests
npm run test:e2e

# Lint
npm run lint
```

## 📚 API Documentation

### Authentication Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/signup` | Register new user account |
| POST | `/api/v1/auth/login` | Authenticate and create session |
| POST | `/api/v1/auth/logout` | Invalidate session |
| POST | `/api/v1/auth/verify-email` | Verify email with token |
| POST | `/api/v1/auth/password-reset/request` | Request password reset email |
| POST | `/api/v1/auth/password-reset/confirm` | Reset password with token |

See [Authentication API](docs/api/authentication.md) for full request/response schemas.

### Core Endpoints

| Resource | Endpoint | Description |
|----------|----------|-------------|
| Work Logs | `GET/POST /api/v1/worklog/entries` | Daily time entries |
| Calendar | `GET /api/v1/worklog/calendar/{year}/{month}` | Monthly calendar view |
| Absences | `GET/POST /api/v1/worklog/absences` | Leave management |
| Approvals | `GET/POST /api/v1/worklog/approvals` | Workflow management |
| CSV Import | `POST /api/v1/worklog/import` | Bulk data import |
| CSV Export | `GET /api/v1/worklog/export` | Data export |

Full API documentation available at `/api-docs.html` when running the backend.

## 📖 Documentation

- [Quick Start Guide](QUICKSTART.md) - Get up and running
- [User Manual](docs/user-manual.md) - End-user documentation
- [Manager Guide](docs/manager-guide.md) - Approval workflow guide
- [Backup Strategy](docs/backup-strategy.md) - Data backup procedures
- [Authentication API](docs/api/authentication.md) - Auth endpoint reference
- [Password Reset Architecture](docs/architecture/password-reset-flow.md) - Auth system design
- [Agent Guidelines](AGENTS.md) - Coding standards for AI agents

## 🎯 Current Status

### Phase 1: Foundation ✅ COMPLETE
- Multi-tenant architecture
- Fiscal year and monthly period patterns
- Event sourcing infrastructure

### Phase 2: Work Log Entry ✅ COMPLETE
- Daily time entry (US1)
- Multi-project allocation (US2)
- Absence tracking (US3)
- Approval workflow (US4)
- CSV import/export (US5)
- Copy previous month (US6)
- Proxy entry (US7)

### Phase 3: Polish ✅ COMPLETE
- Performance benchmarks
- Error handling
- Auto-save functionality
- Session timeout warnings

### Phase 4: User Authentication 🚧 IN PROGRESS
- User signup and email verification
- Login/logout with session management
- Password reset via email
- Rate limiting for auth endpoints

## 🐳 Docker Support

### Development Environment

```bash
cd infra/docker
docker-compose -f docker-compose.dev.yml up -d
```

### Production Environment

```bash
cd infra/docker
docker-compose -f docker-compose.prod.yml up -d
```

Services:
- `miometry-db` - PostgreSQL database
- `miometry-backend` - Spring Boot API
- `miometry-frontend` - Next.js application

## 📈 Performance Targets

| Metric | Target |
|--------|--------|
| Calendar load | < 1 second |
| CSV import | 100 rows/second |
| Concurrent users | 100+ |
| Mobile entry time | < 2 minutes |
| Auto-save reliability | 99.9% |

## 🤝 Contributing

1. Follow the coding standards in `AGENTS.md`
2. Write tests for all new features
3. Ensure all tests pass before committing
4. Use conventional commit messages
5. Update documentation as needed

## 📝 License

This project is licensed under the [Apache License 2.0](LICENSE).

## 👥 Team

[Add team information here]

---

**Last Updated**: 2026-02-25
**Version**: 0.3.0
**Product**: Miometry (ミオメトリー)
