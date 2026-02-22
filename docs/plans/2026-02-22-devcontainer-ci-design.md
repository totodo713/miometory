# devcontainer & GitHub Actions CI Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Create a multi-container devcontainer environment and GitHub Actions CI pipeline that share the same container definitions, eliminating environment drift between local development and CI.

**Architecture:** Multi-container devcontainer using Docker Compose with 4 services (backend, frontend, db, redis). GitHub Actions CI uses `devcontainers/ci` action to run the same containers. CI jobs are parallelized: lint-format, build-test-backend, build-test-frontend run independently; coverage depends on backend tests; E2E is label-triggered.

**Tech Stack:** Docker, Docker Compose, VS Code Dev Containers, GitHub Actions, devcontainers/ci action, Eclipse Temurin 21, Node 20, PostgreSQL 17, Redis 7

---

### Task 1: Create devcontainer backend Dockerfile

**Files:**
- Create: `.devcontainer/backend/Dockerfile`

**Step 1: Create the Dockerfile**

```dockerfile
FROM eclipse-temurin:21-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
    git curl wget unzip \
    && rm -rf /var/lib/apt/lists/*

ARG USERNAME=vscode
ARG USER_UID=1000
ARG USER_GID=$USER_UID
RUN groupadd --gid $USER_GID $USERNAME \
    && useradd --uid $USER_UID --gid $USER_GID -m $USERNAME \
    && mkdir -p /home/$USERNAME/.gradle \
    && chown -R $USERNAME:$USERNAME /home/$USERNAME

USER $USERNAME
```

**Step 2: Verify Dockerfile syntax**

Run: `docker build -f .devcontainer/backend/Dockerfile .devcontainer/backend/ --no-cache --progress=plain 2>&1 | tail -5`
Expected: Build succeeds, final line shows image ID

**Step 3: Commit**

```bash
git add .devcontainer/backend/Dockerfile
git commit -m "feat(devcontainer): add backend Dockerfile with Java 21 JDK"
```

---

### Task 2: Create devcontainer frontend Dockerfile

**Files:**
- Create: `.devcontainer/frontend/Dockerfile`

**Step 1: Create the Dockerfile**

```dockerfile
FROM node:20

RUN apt-get update && apt-get install -y --no-install-recommends \
    git curl \
    && rm -rf /var/lib/apt/lists/*

# Playwright browser dependencies (chromium only for E2E)
RUN npx playwright install-deps chromium

ARG USERNAME=vscode
ARG USER_UID=1000
ARG USER_GID=$USER_UID
RUN groupadd --gid $USER_GID $USERNAME \
    && useradd --uid $USER_UID --gid $USER_GID -m $USERNAME

USER $USERNAME
```

**Step 2: Verify Dockerfile syntax**

Run: `docker build -f .devcontainer/frontend/Dockerfile .devcontainer/frontend/ --no-cache --progress=plain 2>&1 | tail -5`
Expected: Build succeeds

**Step 3: Commit**

```bash
git add .devcontainer/frontend/Dockerfile
git commit -m "feat(devcontainer): add frontend Dockerfile with Node 20 and Playwright deps"
```

---

### Task 3: Create devcontainer docker-compose.yml

**Files:**
- Create: `.devcontainer/docker-compose.yml`

**Context:** The existing `infra/docker/docker-compose.dev.yml` defines PostgreSQL 16 and Redis 7 for local dev. The devcontainer compose file defines all 4 services with the workspace volume mount. PostgreSQL is upgraded to 17-alpine to match the design doc. Credentials match the existing `application.yaml` defaults: user=worklog, pass=worklog, db=worklog.

**Step 1: Create docker-compose.yml**

```yaml
services:
  backend:
    build:
      context: .
      dockerfile: backend/Dockerfile
    volumes:
      - ..:/workspaces/miometory:cached
    command: sleep infinity
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/worklog
      SPRING_DATASOURCE_USERNAME: worklog
      SPRING_DATASOURCE_PASSWORD: worklog
      REDIS_HOST: redis
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_healthy

  frontend:
    build:
      context: .
      dockerfile: frontend/Dockerfile
    volumes:
      - ..:/workspaces/miometory:cached
    command: sleep infinity
    environment:
      NODE_ENV: development
    depends_on:
      - backend

  db:
    image: postgres:17-alpine
    environment:
      POSTGRES_USER: worklog
      POSTGRES_PASSWORD: worklog
      POSTGRES_DB: worklog
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U worklog"]
      interval: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      retries: 5

volumes:
  postgres-data:
  redis-data:
```

**Step 2: Validate compose file**

Run: `docker compose -f .devcontainer/docker-compose.yml config --quiet`
Expected: No output (valid config)

**Step 3: Commit**

```bash
git add .devcontainer/docker-compose.yml
git commit -m "feat(devcontainer): add docker-compose with backend, frontend, db, redis services"
```

---

### Task 4: Create devcontainer.json

**Files:**
- Create: `.devcontainer/devcontainer.json`

**Context:** This is the main config file VS Code reads. It references the docker-compose.yml, sets `backend` as the primary service, and configures extensions, ports, and lifecycle commands. The `postCreateCommand` pre-resolves Gradle and npm dependencies so the first build is fast.

**Step 1: Create devcontainer.json**

```jsonc
{
  "name": "Miometry Dev",
  "dockerComposeFile": "docker-compose.yml",
  "service": "backend",
  "workspaceFolder": "/workspaces/miometory",

  "features": {
    "ghcr.io/devcontainers/features/node:1": {
      "version": "20"
    },
    "ghcr.io/devcontainers/features/docker-in-docker:2": {}
  },

  "forwardPorts": [8080, 3000, 5432, 6379],

  "postCreateCommand": "cd backend && ./gradlew dependencies --no-daemon && cd ../frontend && npm install",

  "customizations": {
    "vscode": {
      "extensions": [
        "vscjava.vscode-java-pack",
        "vmware.vscode-spring-boot",
        "fwcd.kotlin",
        "biomejs.biome",
        "ms-azuretools.vscode-docker",
        "eamodio.gitlens"
      ],
      "settings": {
        "java.jdt.ls.java.home": "/opt/java/openjdk",
        "editor.formatOnSave": true,
        "editor.defaultFormatter": "biomejs.biome",
        "[java]": {
          "editor.defaultFormatter": "redhat.java"
        },
        "[kotlin]": {
          "editor.defaultFormatter": "fwcd.kotlin"
        }
      }
    }
  }
}
```

**Step 2: Validate JSON syntax**

Run: `python3 -c "import json; json.load(open('.devcontainer/devcontainer.json'))"`
Expected: No output (valid JSON). Note: jsonc comments will cause an error — if using comments, strip them first or validate with a jsonc parser. Since `devcontainer.json` supports jsonc natively in VS Code, this is acceptable.

Actually, since `devcontainer.json` supports JSON with comments (jsonc), and the python validator won't accept comments, instead verify:

Run: `grep -c '"service"' .devcontainer/devcontainer.json`
Expected: `1` (basic sanity check)

**Step 3: Commit**

```bash
git add .devcontainer/devcontainer.json
git commit -m "feat(devcontainer): add devcontainer.json with multi-container config"
```

---

### Task 5: Test devcontainer locally

**Step 1: Build and start all services**

Run: `docker compose -f .devcontainer/docker-compose.yml up -d --build`
Expected: All 4 services start (backend, frontend, db, redis)

**Step 2: Verify services are healthy**

Run: `docker compose -f .devcontainer/docker-compose.yml ps`
Expected: `db` and `redis` show "healthy", `backend` and `frontend` show "running"

**Step 3: Verify backend can reach db**

Run: `docker compose -f .devcontainer/docker-compose.yml exec backend bash -c "curl -s db:5432 || echo 'PostgreSQL port reachable'"`
Expected: Connection response (PostgreSQL doesn't speak HTTP, but port should be reachable)

Better test:
Run: `docker compose -f .devcontainer/docker-compose.yml exec backend bash -c "apt-get update && apt-get install -y postgresql-client && psql -h db -U worklog -d worklog -c 'SELECT 1'"`
Or simply:
Run: `docker compose -f .devcontainer/docker-compose.yml exec db psql -U worklog -d worklog -c 'SELECT 1'`
Expected: Output showing `1`

**Step 4: Clean up**

Run: `docker compose -f .devcontainer/docker-compose.yml down -v`
Expected: All containers and volumes removed

**Step 5: Commit (no changes needed — this was a verification step)**

No commit needed.

---

### Task 6: Create GitHub Actions CI workflow — lint-format job

**Files:**
- Create: `.github/workflows/ci.yml`

**Context:** This is the first CI job. It runs Spotless + Detekt for backend and Biome CI check for frontend. Uses `devcontainers/ci` action with the devcontainer config. The `node` feature in devcontainer.json gives the backend container access to Node 20 + npm, so both backend and frontend commands can run from the same container.

**Step 1: Create the workflow file with lint-format job**

```yaml
name: CI

on:
  pull_request:
    branches: [main]

permissions:
  contents: read
  pull-requests: write

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  lint-format:
    name: Lint & Format Check
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Run lint and format checks in devcontainer
        uses: devcontainers/ci@v0.3
        with:
          configFile: .devcontainer/devcontainer.json
          push: never
          runCmd: |
            cd backend && ./gradlew checkFormat --no-daemon
            cd ../frontend && npm ci && npm run check:ci
```

**Step 2: Validate YAML syntax**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"`
Expected: No output (valid YAML)

**Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add lint-format job using devcontainers/ci"
```

---

### Task 7: Add build-test-backend job to CI workflow

**Files:**
- Modify: `.github/workflows/ci.yml`

**Context:** Backend tests use Testcontainers which needs Docker. The `docker-in-docker` feature in devcontainer.json provides Docker inside the container. Testcontainers auto-detects Docker daemon. The `--no-daemon` flag prevents Gradle daemon from persisting in CI.

**Step 1: Add build-test-backend job**

Append after `lint-format` job:

```yaml
  build-test-backend:
    name: Build & Test Backend
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Build and test backend in devcontainer
        uses: devcontainers/ci@v0.3
        with:
          configFile: .devcontainer/devcontainer.json
          push: never
          runCmd: |
            cd backend && ./gradlew build test --no-daemon

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: backend-test-results
          path: backend/build/test-results/test/
          retention-days: 7
```

**Step 2: Validate YAML**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"`
Expected: No output

**Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add build-test-backend job with Testcontainers support"
```

---

### Task 8: Add build-test-frontend job to CI workflow

**Files:**
- Modify: `.github/workflows/ci.yml`

**Context:** Frontend tests use Vitest with jsdom. No Docker needed inside the container. `npm ci` ensures reproducible installs from lock file. `npm run build` verifies the production build succeeds (catches TypeScript errors missed by unit tests).

**Step 1: Add build-test-frontend job**

Append after `build-test-backend` job:

```yaml
  build-test-frontend:
    name: Build & Test Frontend
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Build and test frontend in devcontainer
        uses: devcontainers/ci@v0.3
        with:
          configFile: .devcontainer/devcontainer.json
          push: never
          runCmd: |
            cd frontend && npm ci && npm run build && npm test -- --run

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: frontend-test-results
          path: frontend/tests/unit/
          retention-days: 7
```

**Step 2: Validate YAML**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"`
Expected: No output

**Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add build-test-frontend job"
```

---

### Task 9: Add coverage job to CI workflow

**Files:**
- Modify: `.github/workflows/ci.yml`

**Context:** JaCoCo generates XML and HTML reports. The coverage job depends on `build-test-backend` completing (it reruns tests to generate coverage). The `madrapps/jacoco-report` action posts coverage summary as a PR comment. Requires `pull-requests: write` permission (already set at workflow level).

**Step 1: Add coverage job**

Append after `build-test-frontend` job:

```yaml
  coverage:
    name: Coverage Report
    runs-on: ubuntu-latest
    needs: [build-test-backend]
    steps:
      - uses: actions/checkout@v4

      - name: Generate coverage report in devcontainer
        uses: devcontainers/ci@v0.3
        with:
          configFile: .devcontainer/devcontainer.json
          push: never
          runCmd: |
            cd backend && ./gradlew jacocoTestReport --no-daemon

      - name: Upload JaCoCo report
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: backend/build/reports/jacoco/
          retention-days: 14

      - name: Post coverage comment on PR
        uses: madrapps/jacoco-report@v1.7.1
        with:
          paths: backend/build/reports/jacoco/test/jacocoTestReport.xml
          token: ${{ secrets.GITHUB_TOKEN }}
          min-coverage-overall: 80
          min-coverage-changed-files: 80
          title: "Backend Coverage Report"
```

**Step 2: Validate YAML**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"`
Expected: No output

**Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add coverage job with JaCoCo PR comment"
```

---

### Task 10: Add E2E test job to CI workflow

**Files:**
- Modify: `.github/workflows/ci.yml`

**Context:** E2E tests require full stack: backend (Spring Boot on port 8080), frontend (Next.js on port 3000), PostgreSQL, and Redis. The devcontainer docker-compose already starts db and redis. The E2E job starts backend and frontend as background processes, waits for health checks, then runs Playwright. Triggered only when PR has `e2e-test` label. Playwright config already handles CI mode (`forbidOnly`, `retries: 2`, `workers: 1`).

**Step 1: Add e2e-test job**

Append after `coverage` job:

```yaml
  e2e-test:
    name: E2E Tests
    runs-on: ubuntu-latest
    if: contains(github.event.pull_request.labels.*.name, 'e2e-test')
    steps:
      - uses: actions/checkout@v4

      - name: Run E2E tests in devcontainer
        uses: devcontainers/ci@v0.3
        with:
          configFile: .devcontainer/devcontainer.json
          push: never
          runCmd: |
            # Install dependencies
            cd /workspaces/miometory/frontend && npm ci
            cd /workspaces/miometory/backend && ./gradlew build --no-daemon -x test

            # Start backend in background
            cd /workspaces/miometory/backend
            ./gradlew bootRun --args='--spring.profiles.active=dev' --no-daemon &
            BACKEND_PID=$!

            # Wait for backend to be ready
            echo "Waiting for backend..."
            for i in $(seq 1 60); do
              if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
                echo "Backend is ready"
                break
              fi
              sleep 2
            done

            # Install Playwright browsers and run E2E
            cd /workspaces/miometory/frontend
            npx playwright install chromium
            CI=true npx playwright test

            # Cleanup
            kill $BACKEND_PID 2>/dev/null || true

      - name: Upload Playwright report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: playwright-report
          path: frontend/playwright-report/
          retention-days: 14

      - name: Upload Playwright screenshots
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: playwright-screenshots
          path: frontend/test-results/
          retention-days: 7
```

**Step 2: Validate YAML**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"`
Expected: No output

**Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add label-triggered E2E test job with Playwright"
```

---

### Task 11: Final verification and documentation

**Files:**
- Verify: `.devcontainer/devcontainer.json`, `.devcontainer/docker-compose.yml`, `.devcontainer/backend/Dockerfile`, `.devcontainer/frontend/Dockerfile`, `.github/workflows/ci.yml`

**Step 1: Verify all devcontainer files exist**

Run: `ls -la .devcontainer/ .devcontainer/backend/ .devcontainer/frontend/ .github/workflows/`
Expected: All files present

**Step 2: Verify complete CI workflow is valid YAML**

Run: `python3 -c "import yaml; data=yaml.safe_load(open('.github/workflows/ci.yml')); print('Jobs:', list(data['jobs'].keys()))"`
Expected: `Jobs: ['lint-format', 'build-test-backend', 'build-test-frontend', 'coverage', 'e2e-test']`

**Step 3: Verify docker-compose config**

Run: `docker compose -f .devcontainer/docker-compose.yml config --quiet`
Expected: No output (valid)

**Step 4: Commit (no new files — verification only)**

No commit needed.
