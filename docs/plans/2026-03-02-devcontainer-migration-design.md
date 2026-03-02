# Plan: Devcontainer-Only Development + Lightweight CI

## Context

Currently the project has 3 separate environment configurations: local dev (docker-compose.dev.yml + host-machine tools), devcontainer (4-service docker-compose), and production (nginx + full stack). This creates maintenance burden and environment drift.

**Goal**: Simplify to devcontainer-only development, lighten CI by removing devcontainer usage, and delete all production files (production environment is TBD).

## Summary of Changes

1. **Devcontainer**: Merge backend + frontend into single container (Java 21 + Node 20), keep db + redis as sidecars
2. **CI**: Replace all `devcontainers/ci` action with `setup-java` + `setup-node` on ubuntu-latest
3. **Delete**: All production files, local dev compose, `infra/` directory entirely
4. **Version alignment**: Testcontainers PostgreSQL 16 -> 17

---

## Phase 1: New Devcontainer Structure

### 1.1 CREATE `.devcontainer/Dockerfile` (unified)

```dockerfile
FROM eclipse-temurin:21.0.6_7-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
    git curl wget unzip ca-certificates gnupg \
    && mkdir -p /etc/apt/keyrings \
    && curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key \
       | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_20.x nodistro main" \
       > /etc/apt/sources.list.d/nodesource.list \
    && apt-get update && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

RUN npx playwright install-deps chromium
```

### 1.2 MODIFY `.devcontainer/docker-compose.yml`

- Replace 4 services (backend, frontend, db, redis) with 3 (app, db, redis)
- `app` service: single unified container, all env vars merged
- db/redis: unchanged

### 1.3 MODIFY `.devcontainer/devcontainer.json`

- `"service"`: `"backend"` -> `"app"`
- Remove `node:1` feature (Node now in Dockerfile)
- Keep `common-utils` (vscode user) and `docker-in-docker` (Testcontainers)

### 1.4 DELETE old devcontainer Dockerfiles

- `.devcontainer/backend/Dockerfile`
- `.devcontainer/frontend/Dockerfile`

---

## Phase 2: CI Workflow Rewrite

### File: `.github/workflows/ci.yml`

**All jobs**: Remove `devcontainers/ci` action, `docker/login-action`, GHCR references.

| Job | Setup | Services |
|-----|-------|----------|
| `lint-format` | setup-java@v4 (temurin 21, cache gradle) + setup-node@v4 (20, cache npm) | None |
| `build-test-backend` | setup-java@v4 | None (Testcontainers handles DB) |
| `build-test-frontend` | setup-node@v4 | None |
| `coverage` | No change (already no devcontainer) | None |
| `e2e-test` | setup-java@v4 + setup-node@v4 + playwright install | Service containers: postgres:17-alpine, redis:7-alpine |
| `build-devcontainer-cache` | **DELETE this job entirely** | N/A |

**Permissions**: Remove `packages: write` (no longer pushing to GHCR).

---

## Phase 3: File Deletions

### Production files
| File | Notes |
|------|-------|
| `infra/docker/docker-compose.prod.yml` | Production compose |
| `infra/docker/nginx/nginx.conf` | Nginx reverse proxy |
| `backend/Dockerfile` | Production backend image |
| `frontend/Dockerfile` | Production frontend image |
| `backend/src/main/resources/application-prod.yaml` | Production Spring config |

### Local dev files
| File | Notes |
|------|-------|
| `infra/docker/docker-compose.dev.yml` | Replaced by devcontainer |

### Operations scripts
| File | Notes |
|------|-------|
| `infra/scripts/backup-db.sh` | Production ops |
| `infra/scripts/restore-db.sh` | Production ops |
| `infra/scripts/verify-health.sh` | Production ops |

### Directories to remove
- `infra/` (entire directory tree)
- `.devcontainer/backend/`
- `.devcontainer/frontend/`

---

## Phase 4: Documentation Updates

### High-priority (must update)

| File | Changes |
|------|---------|
| `AGENTS.md` | Remove `infra/docker/` from project overview. Replace dev env commands (docker-compose.dev.yml -> devcontainer). Remove prod references. |
| `QUICKSTART.md` | Major rewrite: devcontainer-based setup. Remove production deployment section. Update troubleshooting (remove docker-compose.dev.yml refs). |
| `README.md` | Update project structure, quick start, remove prod docker refs |

### Skills

| File | Changes |
|------|---------|
| `.claude/skills/deploy-check/SKILL.md` | Remove step 6 (docker-compose.prod.yml config validation) |
| `.claude/skills/db-debug/SKILL.md` | Update prerequisites: remove docker-compose.dev.yml reference |

### Low-priority (historical spec docs)

Add deprecation note at top of each: "Note: Development now uses devcontainer. See QUICKSTART.md."

Files: `specs/001-foundation/quickstart.md`, `specs/002-work-log-entry/quickstart.md`, and 11 other spec quickstart/tasks files.

### Other docs

| File | Changes |
|------|---------|
| `docs/backup-strategy.md` | Add note that backup scripts were removed; strategy TBD with production env |

---

## Phase 5: Version Alignment

Update Testcontainers PostgreSQL from 16 to 17 in 4 files:

| File | Change |
|------|--------|
| `backend/src/test/kotlin/com/worklog/IntegrationTestBase.kt` | `postgres:16-alpine` -> `postgres:17-alpine` |
| `backend/src/test/kotlin/com/worklog/infrastructure/persistence/JdbcUserRepositoryTest.kt` | Same |
| `backend/src/test/kotlin/com/worklog/infrastructure/persistence/PasswordResetTokenRepositoryTest.kt` | Same |
| `backend/src/test/kotlin/com/worklog/infrastructure/persistence/JdbcUserEmailUniquenessTest.kt` | Same |

---

## Verification

### 1. Devcontainer

```bash
# Validate compose file
docker compose -f .devcontainer/docker-compose.yml config --quiet

# Build image
docker build -f .devcontainer/Dockerfile .devcontainer/

# Verify Java + Node in single container
docker compose -f .devcontainer/docker-compose.yml run --rm app java -version
docker compose -f .devcontainer/docker-compose.yml run --rm app node --version
```

### 2. Tests (local)

```bash
cd backend && ./gradlew test          # Testcontainers with postgres:17
cd frontend && npm test -- --run       # Unit tests
```

### 3. CI

- Push branch, create PR -> verify all CI jobs pass without devcontainer
- Add `e2e-test` label -> verify E2E job with service containers

### 4. Broken references

```bash
grep -r "docker-compose.dev.yml" . --include="*.md" --include="*.sh" --include="*.yml"
grep -r "docker-compose.prod.yml" . --include="*.md" --include="*.sh" --include="*.yml"
grep -r "application-prod" . --include="*.kt" --include="*.java" --include="*.yaml"
```

Only historical spec docs (with deprecation notes) should match.
