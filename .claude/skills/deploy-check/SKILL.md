---
name: deploy-check
description: Run pre-deployment checklist to verify migration continuity, builds, tests, and configuration
disable-model-invocation: true
---

# Pre-Deployment Checklist

Automatically verify that the project is ready for production deployment.

## Usage

`/deploy-check` — Run all checks
`/deploy-check --quick` — Skip build and test steps (migration + config only)

## Checks

Run each check in order. Report Pass/Fail for each and stop on critical failures.

### 1. Git Status Check

Verify no uncommitted changes exist:
```bash
git status --porcelain
```
- **Pass**: No output (clean working tree)
- **Fail**: List uncommitted files and warn

### 2. Flyway Migration Version Continuity

Scan migration files for version gaps or duplicates:
```bash
ls backend/src/main/resources/db/migration/V*.sql | sed 's/.*V\([0-9]*\)__.*/\1/' | sort -n
```
- **Pass**: Version numbers are sequential with no gaps or duplicates
- **Fail**: Report which versions are missing or duplicated

Also check naming convention:
- Files must match pattern `V{N}__{snake_case_description}.sql`
- No spaces, uppercase, or special characters in description

### 3. Seed Data Coverage

Check that `R__dev_seed_data.sql` includes INSERT statements for all domain tables:
```bash
# Extract table names from CREATE TABLE statements in migrations
grep -h "CREATE TABLE" backend/src/main/resources/db/migration/V*.sql | sed 's/.*CREATE TABLE.*IF NOT EXISTS //' | sed 's/.*CREATE TABLE //' | sed 's/ .*//' | sort -u

# Extract table names from seed data INSERT statements
grep -h "INSERT INTO" backend/src/main/resources/db/migration/R__dev_seed_data.sql | sed 's/.*INSERT INTO //' | sed 's/ .*//' | sort -u
```
- **Pass**: All domain tables have seed data (exclude system tables: `event_store`, `snapshot_store`, `audit_log`)
- **Warn**: Domain tables without seed data (list them)

### 4. Backend Build

```bash
cd backend && ./gradlew build --no-daemon 2>&1
```
- **Pass**: BUILD SUCCESSFUL
- **Fail**: Show error output

### 5. Frontend Build

```bash
cd frontend && npm run build 2>&1
```
- **Pass**: Exit code 0
- **Fail**: Show error output

### 6. Docker Compose Config Validation

```bash
cd infra/docker && docker compose -f docker-compose.prod.yml config --quiet 2>&1
```
- **Pass**: No errors
- **Fail**: Show config errors

### 7. Summary Report

Format the final report:

```
## Deploy Readiness Report

| Check | Status |
|-------|--------|
| Git Status | ✅ Clean / ⚠️ Uncommitted changes |
| Migration Continuity | ✅ Sequential / ❌ Gaps found |
| Seed Data Coverage | ✅ Complete / ⚠️ Missing tables |
| Backend Build | ✅ Pass / ❌ Fail |
| Frontend Build | ✅ Pass / ❌ Fail |
| Docker Config | ✅ Valid / ❌ Invalid |

### Overall: ✅ Ready / ⚠️ Warnings / ❌ Not Ready
```
