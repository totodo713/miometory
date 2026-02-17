# Quick Start Guide - Miometry Development

**Last Updated**: 2026-01-28

## 5-Minute Setup

### 1. Start Infrastructure

```bash
cd /home/devman/repos/work-log/infra/docker
docker compose -f docker-compose.dev.yml up -d

# Verify services are running
docker compose -f docker-compose.dev.yml ps
```

Expected output:
```
NAME                    IMAGE           STATUS    PORTS
miometry-postgres       postgres:16     Up        0.0.0.0:5432->5432
miometry-redis          redis:7-alpine  Up        0.0.0.0:6379->6379
```

> **MailHog (Optional)**: For testing email features (signup verification, password reset), start MailHog:
> ```bash
> docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog
> ```
> MailHog UI: http://localhost:8025 — All outgoing emails are captured here in development.

### 2. Start Backend (Spring Boot)

```bash
cd /home/devman/repos/work-log/backend
./gradlew bootRun

# Wait for: "Started BackendApplication in X.xxx seconds"
# Backend runs on: http://localhost:8080
```

### 3. Start Frontend (Next.js)

```bash
cd /home/devman/repos/work-log/frontend
npm install  # First time only
npm run dev

# Wait for: "Ready in xxx ms"
# Frontend runs on: http://localhost:3000
```

### 4. Open Application

- **Main URL**: http://localhost:3000/worklog
- **Calendar View**: Monthly fiscal period (21st to 20th)
- **Daily Entry**: Click any date to log hours

---

## Verify Everything Works

### Health Check

```bash
curl http://localhost:8080/api/v1/health
# Expected: {"status":"ok"}
```

### Test Calendar API

```bash
curl "http://localhost:8080/api/v1/worklog/calendar/2026/1?memberId=00000000-0000-0000-0000-000000000001"
# Expected: JSON with fiscal period dates and daily totals
```

### Create Test Entry

```bash
curl -X POST http://localhost:8080/api/v1/worklog/entries \
  -H "Content-Type: application/json" \
  -d '{
    "memberId": "00000000-0000-0000-0000-000000000001",
    "projectId": "00000000-0000-0000-0000-000000000002",
    "date": "2026-01-15",
    "hours": 8.0,
    "comment": "Development work"
  }'
# Expected: HTTP 201 with entry ID
```

---

## Test Password Reset Flow

After starting the backend with MailHog running:

### 1. Create a Test Account

```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "name": "Test User", "password": "Password1"}'
# Expected: HTTP 201
```

### 2. Request Password Reset

```bash
curl -X POST http://localhost:8080/api/v1/auth/password-reset/request \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com"}'
# Expected: HTTP 200 (always returns 200 for anti-enumeration)
```

### 3. Get Reset Token from MailHog

Open http://localhost:8025 and find the password reset email. Copy the token from the reset link.

### 4. Confirm Password Reset

```bash
curl -X POST http://localhost:8080/api/v1/auth/password-reset/confirm \
  -H "Content-Type: application/json" \
  -d '{"token": "<token-from-email>", "newPassword": "NewPassword1"}'
# Expected: HTTP 200
```

### 5. Login with New Password

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "password": "NewPassword1", "rememberMe": false}'
# Expected: HTTP 200 with session details
```

---

## SSO Mock Configuration (Local Development)

For local development, authentication is disabled. All endpoints are public.

### Test Users

When SSO is enabled, these mock users are available:

| Username | Role | Password | Member ID | Capabilities |
|----------|------|----------|-----------|--------------|
| engineer1@example.com | MEMBER | password | 00000000-0000-0000-0000-000000000001 | Enter own time |
| engineer2@example.com | MEMBER | password | 00000000-0000-0000-0000-000000000002 | Enter own time |
| manager1@example.com | MANAGER | password | 00000000-0000-0000-0000-000000000003 | Approve team, proxy entry |
| admin@example.com | ADMIN | password | 00000000-0000-0000-0000-000000000004 | Full access |

### Environment Variables

Backend (`application.yaml`):
```yaml
# Currently disabled for development - all requests allowed
spring:
  security:
    oauth2:
      client:
        # Enable when SSO provider is configured
```

Frontend (`.env.local`):
```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
NEXT_PUBLIC_SSO_ENABLED=false
NEXT_PUBLIC_MOCK_AUTH=true
```

### Email / SMTP Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `MAIL_HOST` | `localhost` | SMTP server host (use MailHog for dev) |
| `MAIL_PORT` | `1025` | SMTP server port |
| `MAIL_USERNAME` | *(empty)* | SMTP username (not needed for MailHog) |
| `MAIL_PASSWORD` | *(empty)* | SMTP password (not needed for MailHog) |
| `MAIL_SMTP_AUTH` | `false` | Enable SMTP authentication |
| `MAIL_SMTP_STARTTLS` | `false` | Enable STARTTLS |
| `FRONTEND_BASE_URL` | `http://localhost:3000` | Base URL for links in emails (password reset, email verification) |

---

## Current Features (All 7 User Stories Complete)

### US1: Daily Time Entry
- 15-minute granularity (0.25h increments)
- Max 24h per day validation
- 60-second auto-save
- localStorage backup for offline resilience

### US2: Multi-Project Time Allocation
- Multiple projects per day
- Running total display
- Real-time validation

### US3: Absence Recording
- Paid leave, sick leave, personal time
- Date range selection
- Calendar color coding

### US4: Monthly Approval Workflow
- Submit month for approval
- Manager approval queue
- Read-only after approval
- Rejection with reason

### US5: CSV Import/Export
- Template download
- Progress indicator (SSE)
- Row-level validation errors
- Export with date filters

### US6: Copy Previous Month
- Copy project list from prior month
- Confirmation dialog
- Projects copied with zero hours

### US7: Manager Proxy Entry
- Select subordinate from dropdown
- "Entering as [Name]" banner
- Audit trail preserved

---

## Running Tests

### Backend Tests

```bash
cd backend

# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests "com.worklog.domain.worklog.WorkLogEntryTest"

# Generate coverage report
./gradlew jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

### Frontend Tests

```bash
cd frontend

# Unit tests (Vitest)
npm test

# Watch mode
npm test -- --watch

# E2E tests (Playwright)
npm run test:e2e
```

---

## Common API Endpoints

### Work Log Entries

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/worklog/entries` | Create entry |
| GET | `/api/v1/worklog/entries` | List entries (with date filter) |
| GET | `/api/v1/worklog/entries/{id}` | Get single entry |
| PATCH | `/api/v1/worklog/entries/{id}` | Update entry |
| DELETE | `/api/v1/worklog/entries/{id}` | Delete entry |

### Calendar

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/worklog/calendar/{year}/{month}` | Monthly calendar view |
| GET | `/api/v1/worklog/calendar/{year}/{month}/summary` | Monthly summary by project |

### Absences

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/absences` | Create absence |
| GET | `/api/v1/absences` | List absences |
| PATCH | `/api/v1/absences/{id}` | Update absence |
| DELETE | `/api/v1/absences/{id}` | Delete absence |

### Approvals

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/worklog/submissions` | Submit month for approval |
| GET | `/api/v1/worklog/approvals/queue` | Manager's pending approvals |
| POST | `/api/v1/worklog/approvals/{id}/approve` | Approve month |
| POST | `/api/v1/worklog/approvals/{id}/reject` | Reject month |

### CSV Operations

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/worklog/csv/template` | Download CSV template |
| POST | `/api/v1/worklog/csv/import` | Import CSV (streaming) |
| GET | `/api/v1/worklog/csv/export/{year}/{month}` | Export month to CSV |

---

## Troubleshooting

### Backend Issues

#### Port 8080 already in use
```bash
lsof -ti:8080 | xargs kill -9
```

#### Database connection refused
```bash
cd infra/docker
docker compose -f docker-compose.dev.yml up -d postgres
```

#### Flyway migration errors
```bash
# Reset database (deletes all data)
docker compose -f infra/docker/docker-compose.dev.yml down -v
docker compose -f infra/docker/docker-compose.dev.yml up -d
```

### Frontend Issues

#### Module not found errors
```bash
cd frontend
rm -rf node_modules package-lock.json
npm install
npm run dev
```

#### Calendar shows "Loading..." forever
1. Check backend is running: `curl http://localhost:8080/api/v1/health`
2. Open DevTools (F12) > Network tab
3. Look for failed API calls

---

## Production Deployment

### Using Production Docker Compose

```bash
cd infra/docker

# Start with TLS/nginx (requires SSL certificates)
docker compose -f docker-compose.prod.yml up -d
```

Production includes:
- nginx reverse proxy with TLS termination
- HSTS and security headers
- Rate limiting
- CSRF protection enabled
- 30-minute session timeout

### Health Check Verification

```bash
# Check all services healthy
curl -k https://localhost/api/v1/health
curl -k https://localhost/ready
```

---

## Project Structure

```
work-log/
  backend/                    # Spring Boot 3.5.9, Kotlin 2.3, Java 21
    src/main/java/com/worklog/
      domain/                 # DDD aggregates, entities, value objects
      application/            # Commands, services
      api/                    # REST controllers
      infrastructure/         # Repositories, projections
    src/test/                 # JUnit 5 tests, Testcontainers
  frontend/                   # Next.js 16.1.1, React 19, TypeScript
    app/                      # App router pages and components
      worklog/                # Main work log pages
      components/             # React components
      services/               # API client, stores
    tests/                    # Vitest unit tests
    e2e/                      # Playwright E2E tests
  infra/docker/               # Docker Compose configurations
  specs/                      # Feature specifications
```

---

## Useful Commands

```bash
# Backend
cd backend
./gradlew bootRun          # Start server
./gradlew test             # Run tests
./gradlew build            # Build JAR
./gradlew jacocoTestReport # Coverage report

# Frontend
cd frontend
npm run dev               # Start dev server
npm test                  # Unit tests
npm run test:e2e          # E2E tests
npm run lint              # Lint check
npm run format            # Auto-format

# Docker
cd infra/docker
docker compose -f docker-compose.dev.yml up -d     # Start services
docker compose -f docker-compose.dev.yml down      # Stop services
docker compose -f docker-compose.dev.yml down -v   # Reset (delete data)
docker compose -f docker-compose.dev.yml logs -f   # View logs
```

---

**Ready to code!** Open http://localhost:3000/worklog in your browser.
