# devcontainer & GitHub Actions CI Design

**Date**: 2026-02-22
**Status**: Approved

## Overview

CI連携を見据えたdevcontainer環境の構築と、GitHub Actions CIパイプラインの設定。
devcontainerとCIで同じコンテナ環境を共有し、環境差異による問題を排除する。

## Approach

- **devcontainer**: Docker Compose統合型 Multi-container
- **CI**: devcontainers/ci action でdevcontainerをCI環境として再利用
- **IDE**: VS Code + Dev Containers拡張

## devcontainer Design

### Directory Structure

```
.devcontainer/
├── devcontainer.json              # Main config (references Docker Compose)
├── docker-compose.yml             # 4-service definition for dev
├── backend/
│   └── Dockerfile                 # Java 21 + Gradle + dev tools
└── frontend/
    └── Dockerfile                 # Node 20 + npm + Playwright deps
```

### Services

| Service | Image/Base | Port | Role |
|---------|-----------|------|------|
| `backend` | Custom (Eclipse Temurin 21) | 8080 | Primary container (VS Code attaches here) |
| `frontend` | Custom (Node 20) | 3000 | Next.js dev server |
| `db` | postgres:17-alpine | 5432 | PostgreSQL |
| `redis` | redis:7-alpine | 6379 | Redis |

### devcontainer.json Key Settings

- **service**: `backend` (primary)
- **workspaceFolder**: `/workspaces/miometory`
- **extensions**: Java Extension Pack, Spring Boot Extension Pack, Kotlin, etc.
- **forwardPorts**: `[8080, 3000, 5432, 6379]`
- **postCreateCommand**: `cd backend && ./gradlew dependencies`
- **features**: `ghcr.io/devcontainers/features/node:1` (Node in backend container)
- **env**: Spring Boot dev profile, DB connection info

### backend/Dockerfile

- Base: `eclipse-temurin:21-jdk`
- Dev tools: git, curl, wget, unzip
- Non-root user: `vscode` (UID 1000)
- Gradle cache directory pre-created

### frontend/Dockerfile

- Base: `node:20`
- Dev tools: git, curl
- Playwright browser deps pre-installed
- Non-root user: `vscode` (UID 1000)

### docker-compose.yml

- Shared workspace volume across services
- `depends_on` with health checks for db/redis
- Environment variables for Spring Boot DB/Redis connection
- PostgreSQL/Redis health checks

## GitHub Actions CI Design

### Workflow File

`.github/workflows/ci.yml`

### Trigger

```yaml
on:
  pull_request:
    branches: [main]
```

### Concurrency

```yaml
concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true
```

### Jobs

```
PR created/updated
├── lint-format (independent, fastest feedback)
├── build-test-backend (independent, with Testcontainers)
├── build-test-frontend (independent)
├── coverage (needs: build-test-backend)
└── e2e-test (label: "e2e-test" only)
```

| Job | Trigger | devcontainers/ci | Commands |
|-----|---------|-----------------|----------|
| `lint-format` | Always | Yes | backend: `./gradlew checkFormat detekt`, frontend: `npm run check:ci` |
| `build-test-backend` | Always | Yes | `./gradlew build test` (with Testcontainers) |
| `build-test-frontend` | Always | Yes | `npm run build && npm test -- --run` |
| `coverage` | After build-test | Yes | `./gradlew jacocoTestReport` → PR comment |
| `e2e-test` | Label `e2e-test` | Yes | Start backend + frontend → `npx playwright test` |

### Cache Strategy

- **Gradle**: `~/.gradle/caches` + `~/.gradle/wrapper`
- **npm**: `~/.npm`
- **devcontainer image**: GHCR cache via devcontainers/ci built-in

### Testcontainers in CI

Backend tests use Testcontainers, requiring Docker-in-Docker (DinD) or Docker socket mount within devcontainer CI.

### E2E Job Special Configuration

1. Start devcontainer (includes PostgreSQL + Redis)
2. Start backend (`./gradlew bootRun` background)
3. Start frontend (`npm run dev` background)
4. Health check, then `npx playwright test`

### Artifacts

- **Test results**: JUnit XML format upload
- **Coverage**: JaCoCo report as artifact
- **E2E**: Playwright report + screenshots as artifacts

### Permissions

```yaml
permissions:
  contents: read
  pull-requests: write  # for coverage comment
```

## Decisions

1. **Multi-container devcontainer**: backend/frontend分離でコンテナの肥大化を防止
2. **Backend as primary**: VS Codeのアタッチ先。Node.jsはfeatureで追加
3. **devcontainers/ci for CI**: ローカルとCI環境の完全一致を実現
4. **Label-triggered E2E**: コスト効率とフレキシビリティのバランス
5. **Concurrency cancellation**: 同一PRの古いジョブを自動キャンセル

## Post-Implementation Notes

実装後のコードレビューで以下の改善を適用:

1. **`set -e` in runCmd** (I-1): 全CIジョブの`runCmd`先頭に`set -e`を追加し、途中のコマンド失敗を確実に検出
2. **User creation delegation comment** (I-2): Dockerfileに、non-rootユーザー作成がcommon-utils featureに委譲されている理由（GIDコンフリクト回避）をコメントで記録
3. **GHCR devcontainer image caching** (I-3): mainマージ時にdevcontainerイメージをGHCRにプッシュし、PRジョブでは`cacheFrom`で再利用。Gradle/npm依存キャッシュはdevcontainer内部のため未実装（イメージレイヤーキャッシュが主な効果）
4. **Healthcheck timeout/start_period** (M-1): `docker-compose.dev.yml`のPostgreSQL/Redisヘルスチェックに`timeout`と`start_period`を追加
5. **postCreateCommand fail-fast comment** (M-2): `devcontainer.json`に`&&`チェーンによるfail-fast動作が意図的である旨のコメントを追加
6. **Skip redundant jobs on `labeled` event** (M-3): `labeled`イベント時に非E2Eジョブをスキップし、E2Eジョブのみ実行
7. **Frontend test artifact upload** (M-4): フロントエンドテスト結果をJUnit XML形式で出力し、artifactとしてアップロード
8. **Design document update** (M-5): 本セクションを追加し、実装と設計ドキュメントの乖離を解消
