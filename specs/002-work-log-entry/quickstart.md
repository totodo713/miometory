# Quickstart Guide: Work-Log Entry System

**Feature**: 002-work-log-entry  
**Last Updated**: 2026-01-03

## Overview

This guide helps developers set up a local development environment for the Work-Log Entry System and run the application for the first time.

## Prerequisites

### Required Software

- **Docker & Docker Compose**: v24.0+ (for PostgreSQL, services)
- **Java Development Kit**: JDK 21 (Eclipse Temurin recommended)
- **Node.js**: v20.x LTS (for frontend)
- **Git**: v2.40+

### Optional Tools

- **IntelliJ IDEA**: 2024.3+ (Ultimate recommended for Spring Boot support)
- **VS Code**: With Java Extension Pack + ESLint + Biome extensions
- **Postman** or **HTTPie**: For API testing

## Quick Start (5 minutes)

### 1. Clone and Navigate

```bash
cd /home/devman/repos/work-log
git checkout 002-work-log-entry
```

### 2. Start Infrastructure

```bash
# Start PostgreSQL + Redis
cd infra/docker
docker-compose -f docker-compose.dev.yml up -d postgres redis

# Verify services are running
docker-compose -f docker-compose.dev.yml ps
```

Expected output:
```
NAME                    IMAGE           STATUS    PORTS
worklog-postgres        postgres:16     Up        0.0.0.0:5432->5432
worklog-redis           redis:7-alpine  Up        0.0.0.0:6379->6379
```

### 3. Run Database Migrations

```bash
cd ../../backend

# Run Flyway migrations
./gradlew flywayMigrate

# Verify migrations
./gradlew flywayInfo
```

Expected output:
```
+---------+------------------------+---------------------+---------+
| Version | Description            | Installed On        | State   |
+---------+------------------------+---------------------+---------+
| 1       | init                   | 2026-01-03 10:00:00 | Success |
| 2       | foundation             | 2026-01-03 10:00:01 | Success |
| 3       | add_pattern_refs       | 2026-01-03 10:00:02 | Success |
| 4       | work_log_entry_tables  | 2026-01-03 10:00:03 | Success |
| 5       | absence_tables         | 2026-01-03 10:00:04 | Success |
| 6       | approval_workflow      | 2026-01-03 10:00:05 | Success |
+---------+------------------------+---------------------+---------+
```

### 4. Start Backend

```bash
cd backend

# Run Spring Boot application
./gradlew bootRun

# Or run from IDE: Right-click BackendApplication.kt > Run
```

Backend will start on: **http://localhost:8080**

Verify health:
```bash
curl http://localhost:8080/api/v1/health
```

Expected response:
```json
{
  "status": "UP",
  "timestamp": "2026-01-03T10:05:00Z",
  "components": {
    "database": "UP",
    "eventStore": "UP"
  }
}
```

### 5. Start Frontend

Open a new terminal:

```bash
cd frontend

# Install dependencies (first time only)
npm install

# Start Next.js dev server
npm run dev
```

Frontend will start on: **http://localhost:3000**

Open browser: http://localhost:3000

## Development Workflow

### Backend Development

#### Run Tests

```bash
cd backend

# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.worklog.domain.worklog.WorkLogEntryTest"

# Run tests with coverage
./gradlew test jacocoTestReport

# View coverage report
open build/reports/jacoco/test/html/index.html
```

#### Hot Reload

Spring Boot DevTools is configured for hot reload:
1. Make code changes
2. Build automatically triggers (Ctrl+F9 in IntelliJ)
3. Application restarts automatically

#### Database Console (Development)

Access H2 console (if using H2 for tests):
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:postgresql://localhost:5432/worklog`
- Username: `worklog_user`
- Password: `worklog_pass`

#### API Testing

Using HTTPie:

```bash
# Create work log entry
http POST localhost:8080/api/v1/worklog/entries \
  Authorization:"Bearer YOUR_TOKEN" \
  date=2026-01-15 \
  projectId="550e8400-e29b-41d4-a716-446655440000" \
  hours=8.00 \
  comment="Development work"

# Get calendar
http GET localhost:8080/api/v1/worklog/calendar/2026/1 \
  Authorization:"Bearer YOUR_TOKEN"
```

### Frontend Development

#### Run Tests

```bash
cd frontend

# Run unit tests (Vitest)
npm test

# Run tests in watch mode
npm test -- --watch

# Run E2E tests (Playwright)
npm run test:e2e

# Run E2E tests with UI
npm run test:e2e -- --ui
```

#### Lint & Format

```bash
# Lint all files
npm run lint

# Fix linting issues
npm run lint:fix

# Format all files
npm run format

# Format single file
npx biome format app/worklog/page.tsx --write
```

#### Component Development

Using React DevTools:
1. Install React DevTools browser extension
2. Open http://localhost:3000/worklog
3. Open browser DevTools > Components tab
4. Inspect component hierarchy and props

#### API Client Testing

Frontend uses TanStack Query for API calls:

```typescript
// Example: Test API client in browser console
import { worklogApi } from '@/services/worklogApi';

// Fetch calendar data
const data = await worklogApi.getCalendar(2026, 1);
console.log(data);
```

## Configuration

### Environment Variables

#### Backend (`backend/src/main/resources/application-dev.yaml`)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/worklog
    username: worklog_user
    password: worklog_pass
  security:
    oauth2:
      client:
        provider:
          mock-sso:
            issuer-uri: http://localhost:8090/mock-auth
  
server:
  port: 8080

logging:
  level:
    com.worklog: DEBUG
    org.springframework.security: DEBUG
```

#### Frontend (`.env.local`)

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
NEXT_PUBLIC_SSO_ENABLED=false
NEXT_PUBLIC_MOCK_AUTH=true
```

Create `.env.local` file:

```bash
cd frontend
cat > .env.local << 'EOF'
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
NEXT_PUBLIC_SSO_ENABLED=false
NEXT_PUBLIC_MOCK_AUTH=true
EOF
```

### SSO Mock Configuration (Local Development)

For local development, authentication is mocked:

1. **Backend**: Uses Spring Security test configuration
2. **Frontend**: Uses mock auth service

Mock users created on startup:

| Username | Role | Password | Member ID |
|----------|------|----------|-----------|
| engineer1@example.com | MEMBER | password | uuid-1 |
| engineer2@example.com | MEMBER | password | uuid-2 |
| manager1@example.com | MANAGER | password | uuid-manager-1 |
| admin@example.com | ADMIN | password | uuid-admin |

Login process (mock):
1. Navigate to http://localhost:3000
2. Enter username (e.g., engineer1@example.com)
3. Enter password: `password`
4. Click "Sign In"
5. Redirected to /worklog

## Seeding Test Data

### Option 1: SQL Script

```bash
cd backend/src/test/resources

# Load test data
psql -h localhost -U worklog_user -d worklog -f test-data.sql
```

### Option 2: Gradle Task

```bash
cd backend

# Run seed task
./gradlew seedTestData
```

Test data includes:
- 3 members (2 engineers, 1 manager)
- 5 active projects
- 10 holidays for 2026
- 30 days of work log entries for January 2026
- 5 absence entries

### Option 3: API Calls (Postman Collection)

Import collection: `specs/002-work-log-entry/postman/worklog-api.postman_collection.json`

## Troubleshooting

### Backend Issues

**Problem**: `Address already in use: 8080`

Solution:
```bash
# Find process using port 8080
lsof -i :8080

# Kill process
kill -9 <PID>

# Or use different port
./gradlew bootRun --args='--server.port=8081'
```

**Problem**: `Connection refused: localhost:5432`

Solution:
```bash
# Check PostgreSQL is running
docker-compose -f infra/docker/docker-compose.dev.yml ps

# Restart if needed
docker-compose -f infra/docker/docker-compose.dev.yml restart postgres

# Check logs
docker-compose -f infra/docker/docker-compose.dev.yml logs postgres
```

**Problem**: Flyway migration fails

Solution:
```bash
# Drop database and recreate
psql -h localhost -U worklog_user -c "DROP DATABASE worklog; CREATE DATABASE worklog;"

# Re-run migrations
./gradlew flywayMigrate
```

### Frontend Issues

**Problem**: `Module not found: Can't resolve '@/...'`

Solution:
```bash
# Clear node_modules and reinstall
rm -rf node_modules package-lock.json
npm install

# Restart dev server
npm run dev
```

**Problem**: `Error: ECONNREFUSED localhost:8080`

Solution:
1. Ensure backend is running on port 8080
2. Check `.env.local` has correct API URL
3. Verify CORS configuration in backend

**Problem**: React component not hot-reloading

Solution:
```bash
# Restart Next.js dev server
# Ctrl+C to stop, then:
npm run dev
```

## Performance Tips

### Backend

1. **Enable Spring Boot DevTools**: Already configured in `build.gradle.kts`
2. **Use `@Profile("dev")`** for dev-only beans
3. **H2 in-memory for tests**: Faster than PostgreSQL
4. **JUnit parallel execution**: Configured in `gradle.properties`

### Frontend

1. **React Fast Refresh**: Enabled by default in Next.js 16
2. **Turbopack**: Use `npm run dev:turbo` for faster builds
3. **SWC minification**: Configured in `next.config.ts`
4. **Selective test running**: `npm test -- Calendar.test.tsx`

## Next Steps

### Implement Your First Feature

1. **Choose a task** from `tasks.md`
2. **Create a branch**: `git checkout -b feature/your-task`
3. **Write tests first**: Domain test → Service test → Controller test → E2E test
4. **Implement feature**: Follow existing patterns
5. **Run all tests**: `./gradlew test && cd ../frontend && npm test`
6. **Commit**: `git add . && git commit -m "feat: your feature"`
7. **Open PR**: Push and create pull request

### Learn the Codebase

- **Domain Model**: Read `specs/002-work-log-entry/data-model.md`
- **API Contract**: Review `specs/002-work-log-entry/contracts/openapi.yaml`
- **Architecture**: See `ARCHITECTURE.md`
- **Coding Standards**: See `AGENTS.md`
- **Constitution**: Read `.specify/memory/constitution.md`

### Key Files to Study

**Backend**:
- `backend/src/main/java/com/worklog/domain/worklog/WorkLogEntry.java` - Aggregate root
- `backend/src/main/java/com/worklog/application/service/WorkLogEntryService.java` - Application service
- `backend/src/main/java/com/worklog/api/WorkLogController.java` - REST controller
- `backend/src/main/java/com/worklog/infrastructure/projection/WorkLogProjection.java` - Read model

**Frontend**:
- `frontend/app/worklog/page.tsx` - Calendar view
- `frontend/app/components/worklog/Calendar.tsx` - Calendar component
- `frontend/app/services/worklogApi.ts` - API client
- `frontend/app/hooks/useAutoSave.ts` - Auto-save hook

## Support

### Documentation

- **Feature Spec**: `specs/002-work-log-entry/spec.md`
- **Implementation Plan**: `specs/002-work-log-entry/plan.md`
- **Research Notes**: `specs/002-work-log-entry/research.md`
- **Data Model**: `specs/002-work-log-entry/data-model.md`

### Getting Help

1. **Check existing docs**: See links above
2. **Search issues**: `git log --grep="keyword"`
3. **Ask the team**: Post in #work-log-dev channel
4. **Constitution**: All changes must pass `.specify/memory/constitution.md` gates

## CI/CD Pipeline

### GitHub Actions Workflow

Located in `.github/workflows/ci.yml`:

1. **Build**: `./gradlew build` + `npm run build`
2. **Test**: `./gradlew test` + `npm test` + `npm run test:e2e`
3. **Lint**: `./gradlew ktlintCheck` + `npm run lint`
4. **Coverage**: JaCoCo report uploaded to Codecov
5. **Deploy**: Automatic deployment to staging on `main` branch push

### Local CI Simulation

```bash
# Run all CI checks locally
./scripts/ci-check.sh
```

This script runs:
- Backend build + tests
- Frontend build + tests + lint
- Constitution compliance check
- Coverage report generation

---

**You're ready to start developing!** 🚀

Open http://localhost:3000 in your browser and start exploring the Work-Log Entry System.
