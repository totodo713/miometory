---
name: api-health-check
description: Run health checks against local backend endpoints and verify service connectivity (PostgreSQL, Redis, Mailpit)
disable-model-invocation: true
---

# API Health Check

Verify that local development services are running and backend API endpoints respond correctly.

## Usage

`/api-health-check` — Run all health checks
`/api-health-check api` — Check API endpoints only (skip infrastructure)
`/api-health-check infra` — Check infrastructure services only (DB, Redis, Mailpit)

## Workflow

### 1. Parse Arguments

- No argument or `all`: run all checks (infra + API)
- `api`: skip infrastructure checks, only test API endpoints
- `infra`: only check PostgreSQL, Redis, Mailpit connectivity

### 2. Infrastructure Health Checks

Check each service's connectivity. The dev environment runs via docker-compose (`.devcontainer/docker-compose.yml`).

**PostgreSQL (port 5432)**
```bash
PGPASSWORD=worklog psql -h localhost -U worklog -d worklog -c "SELECT 1;" 2>&1
```
- **Pass**: Returns `1`
- **Fail**: Connection refused or auth error

**Redis (port 6379)**
```bash
docker exec $(docker ps -qf "ancestor=redis:7-alpine") redis-cli ping 2>&1
```
- **Pass**: Returns `PONG`
- **Fail**: Connection error

**Mailpit (port 8025)**
```bash
curl -sf http://localhost:8025/api/v1/info 2>&1
```
- **Pass**: Returns JSON with Mailpit version info
- **Fail**: Connection refused

### 3. Backend API Health Checks

The backend runs on `http://localhost:8080` (default Spring Boot port).

**Spring Boot Actuator**
```bash
curl -sf http://localhost:8080/actuator/health 2>&1
```
- **Pass**: Returns `{"status":"UP"}`
- **Fail**: Connection refused or status DOWN

**Auth endpoint (smoke test)**
```bash
curl -sf -o /dev/null -w "%{http_code}" http://localhost:8080/api/auth/me 2>&1
```
- **Pass**: Returns `401` (expected for unauthenticated request)
- **Fail**: Connection refused or 5xx error

**API base path**
```bash
curl -sf -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/ 2>&1
```
- Note: 404 is acceptable (no root handler), 5xx indicates a problem

### 4. Frontend Dev Server Check

```bash
curl -sf -o /dev/null -w "%{http_code}" http://localhost:3000 2>&1
```
- **Pass**: Returns `200`
- **Fail**: Connection refused (frontend not started)

### 5. Output Report

```
## Health Check Report

### Infrastructure
| Service    | Port | Status |
|------------|------|--------|
| PostgreSQL | 5432 | ✅ UP / ❌ DOWN |
| Redis      | 6379 | ✅ UP / ❌ DOWN |
| Mailpit    | 8025 | ✅ UP / ❌ DOWN |

### Application
| Endpoint          | Expected | Actual | Status |
|-------------------|----------|--------|--------|
| /actuator/health  | 200      | ...    | ✅ / ❌ |
| /api/auth/me      | 401      | ...    | ✅ / ❌ |
| Frontend (3000)   | 200      | ...    | ✅ / ❌ |

### Overall: ✅ All services healthy / ⚠️ Partial / ❌ Critical services down

### Troubleshooting (if any service is down)
- PostgreSQL: `docker-compose up -d db`
- Redis: `docker-compose up -d redis`
- Backend: `cd backend && ./gradlew bootRun`
- Frontend: `cd frontend && npm run dev`
```
