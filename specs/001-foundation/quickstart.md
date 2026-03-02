**Note**: Development now uses devcontainer. See [QUICKSTART.md](/QUICKSTART.md) for current setup instructions.

# Quickstart: Foundation Infrastructure

**Feature Branch**: `001-foundation`  
**Date**: 2026-01-01

## Prerequisites

- Java 21+
- Docker & Docker Compose
- Gradle 8.x (or use `./gradlew` wrapper)

## Development Setup

### 1. Start PostgreSQL

```bash
cd infra/docker
docker-compose -f docker-compose.dev.yml up -d
```

### 2. Run Backend

```bash
cd backend
./gradlew bootRun
```

Backend will start at `http://localhost:8080`

### 3. Verify Health

```bash
curl http://localhost:8080/health
# Expected: {"status":"UP"}
```

## Running Tests

### All Tests

```bash
cd backend
./gradlew test
```

### Single Test Class

```bash
./gradlew test --tests "com.worklog.domain.TenantTest"
```

### With Test Containers (Integration)

Tests automatically spin up PostgreSQL via Testcontainers. Ensure Docker is running.

```bash
./gradlew test --tests "*IntegrationTest"
```

## API Quick Reference

All API endpoints require Basic Authentication (except `/health`).

### Create Tenant

```bash
curl -X POST http://localhost:8080/api/tenants \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{"code": "acme", "name": "ACME Corporation"}'
```

### Create Fiscal Year Pattern

```bash
curl -X POST http://localhost:8080/api/tenants/{tenantId}/fiscal-year-patterns \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{"name": "4月開始", "startMonth": 4, "startDay": 1}'
```

### Create Monthly Period Pattern

```bash
curl -X POST http://localhost:8080/api/tenants/{tenantId}/monthly-period-patterns \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{"name": "21日締め", "startDay": 21}'
```

### Create Root Organization

```bash
curl -X POST http://localhost:8080/api/tenants/{tenantId}/organizations \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "code": "headquarters",
    "name": "本社",
    "fiscalYearPatternId": "{patternId}",
    "monthlyPeriodPatternId": "{patternId}"
  }'
```

### Create Child Organization

```bash
curl -X POST http://localhost:8080/api/tenants/{tenantId}/organizations \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "parentId": "{parentOrgId}",
    "code": "engineering",
    "name": "技術部"
  }'
```

### Get Date Info

```bash
curl -X POST http://localhost:8080/api/tenants/{tenantId}/organizations/{orgId}/date-info \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{"date": "2026-05-15"}'
```

Response:
```json
{
  "date": "2026-05-15",
  "fiscalYear": 2026,
  "fiscalYearStart": "2026-04-01",
  "fiscalYearEnd": "2027-03-31",
  "monthlyPeriodStart": "2026-04-21",
  "monthlyPeriodEnd": "2026-05-20",
  "displayMonth": 5,
  "displayYear": 2026
}
```

## Project Structure

```
backend/
├── src/main/java/com/worklog/
│   ├── api/                    # REST Controllers
│   ├── domain/                 # Domain entities & events
│   │   ├── tenant/
│   │   ├── organization/
│   │   ├── fiscalyear/
│   │   └── monthlyperiod/
│   ├── eventsourcing/          # Event store infrastructure
│   └── infrastructure/         # Config, security
└── src/test/
    ├── java/com/worklog/       # Test classes
    └── resources/datasets/     # Database Rider YAML files
```

## Key Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 3.5.9 | Framework |
| Kotlin | 2.3.0 | Language |
| PostgreSQL | 16 | Database |
| Flyway | (managed) | Migrations |
| Testcontainers | 2.0.2 | Integration tests |
| Database Rider | 1.44.0 | Test data |
| Instancio | 5.5.1 | Test data generation |

## Next Steps

1. Run the test suite to verify setup
2. Review `data-model.md` for entity details
3. Review `contracts/openapi.yaml` for full API specification
4. Check `tasks.md` (when generated) for implementation tasks
