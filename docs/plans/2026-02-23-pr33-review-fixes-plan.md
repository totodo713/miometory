# PR#33 Code Review Fixes Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix all Critical, Important, and Minor issues identified in the PR#33 code review.

**Architecture:** Fixes are grouped into 3 independent groups (infra/CI, backend, frontend/E2E) that can be committed separately.

**Tech Stack:** GitHub Actions YAML, Kotlin (Spring Boot config), TypeScript (React/Playwright)

---

### Task 1: Resolve merge conflict markers (C-1)

**Files:**
- Modify: `docs/plans/2026-02-22-devcontainer-ci-design.md:599-622`

**Step 1: Fix the conflict**

Replace lines 599-622 with the feature branch content (remove conflict markers and `No commit needed.` from main):

```markdown
**Step 4: Commit (no new files — verification only)**

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
```

**Step 2: Commit**

```bash
git add docs/plans/2026-02-22-devcontainer-ci-design.md
git commit -m "fix(docs): resolve merge conflict markers in devcontainer design document"
```

---

### Task 2: Add coverage job PR-only condition and pin Dockerfiles (I-2, M-1, M-6)

**Files:**
- Modify: `.github/workflows/ci.yml:120-123`
- Modify: `.devcontainer/backend/Dockerfile:1`
- Modify: `.devcontainer/frontend/Dockerfile:1`

**Step 1: Add `if` condition to coverage job**

In `.github/workflows/ci.yml`, change the `coverage` job (line 120-123) from:

```yaml
  coverage:
    name: Coverage Report
    runs-on: ubuntu-latest
    needs: [build-test-backend]
```

to:

```yaml
  coverage:
    name: Coverage Report
    if: github.event_name == 'pull_request' && github.event.action != 'labeled'
    runs-on: ubuntu-latest
    needs: [build-test-backend]
```

**Step 2: Pin Dockerfile base images**

In `.devcontainer/backend/Dockerfile`, change line 1:
- From: `FROM eclipse-temurin:21-jdk`
- To: `FROM eclipse-temurin:21.0.6-jdk`

In `.devcontainer/frontend/Dockerfile`, change line 1:
- From: `FROM node:20`
- To: `FROM node:20-slim`

Note: Use `node:20-slim` rather than pinning to a patch version, since devcontainer frontend only needs Node runtime + apt packages. The `-slim` variant is smaller and more stable than an exact patch pin.

**Step 3: Commit**

```bash
git add .github/workflows/ci.yml .devcontainer/backend/Dockerfile .devcontainer/frontend/Dockerfile
git commit -m "fix(ci): add PR-only condition to coverage job and pin Dockerfile base images"
```

---

### Task 3: Add AsyncConfig with custom thread pool (I-1)

**Files:**
- Create: `backend/src/main/java/com/worklog/infrastructure/config/AsyncConfig.kt`

**Step 1: Create AsyncConfig**

Create `backend/src/main/java/com/worklog/infrastructure/config/AsyncConfig.kt`:

```kotlin
package com.worklog.infrastructure.config

import java.lang.reflect.Method
import org.slf4j.LoggerFactory
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class AsyncConfig : AsyncConfigurer {

    private val log = LoggerFactory.getLogger(AsyncConfig::class.java)

    @Bean("applicationTaskExecutor")
    override fun getAsyncExecutor(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 4
        executor.setQueueCapacity(100)
        executor.setThreadNamePrefix("async-audit-")
        executor.initialize()
        return executor
    }

    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler {
        return AsyncUncaughtExceptionHandler { ex: Throwable, method: Method, params: Array<out Any> ->
            log.error(
                "Uncaught async exception in {}.{}(): {}",
                method.declaringClass.simpleName,
                method.name,
                ex.message,
                ex,
            )
        }
    }
}
```

**Step 2: Run backend tests to verify no regressions**

Run: `cd backend && ./gradlew test --tests "com.worklog.application.audit.*" --no-daemon`
Expected: All audit tests pass (TestAsyncConfig overrides the bean in test context)

**Step 3: Run format check**

Run: `cd backend && ./gradlew checkFormat --no-daemon`
Expected: No formatting violations

**Step 4: Commit**

```bash
git add backend/src/main/java/com/worklog/infrastructure/config/AsyncConfig.kt
git commit -m "feat(config): add explicit async thread pool with uncaught exception handler"
```

---

### Task 4: Add toString assertions in CommandContractTest (M-3)

**Files:**
- Modify: `backend/src/test/kotlin/com/worklog/application/command/CommandContractTest.kt`

**Step 1: Replace bare toString() calls with assertions**

In `CommandContractTest.kt`, replace all 4 bare `cmd.toString()` / `cmd1.toString()` calls with `assertNotNull(cmd.toString())` / `assertNotNull(cmd1.toString())`.

Lines to change:
- Line 25: `cmd1.toString()` → `assertNotNull(cmd1.toString())`
- Line 37: `cmd.toString()` → `assertNotNull(cmd.toString())`
- Line 49: `cmd.toString()` → `assertNotNull(cmd.toString())`
- Line 58: `cmd.toString()` → `assertNotNull(cmd.toString())`

Add import: `import kotlin.test.assertNotNull`

**Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.worklog.application.command.CommandContractTest" --no-daemon`
Expected: All tests pass

**Step 3: Commit**

```bash
git add backend/src/test/kotlin/com/worklog/application/command/CommandContractTest.kt
git commit -m "fix(test): add assertions to toString() calls in CommandContractTest"
```

---

### Task 5: Restore i18n in PasswordStrengthIndicator (I-3)

**Files:**
- Modify: `frontend/app/components/auth/PasswordStrengthIndicator.tsx`

**Step 1: Restore useTranslations**

In `PasswordStrengthIndicator.tsx`:

1. Add import after `"use client";` block: `import { useTranslations } from "next-intl";`
2. Add inside the component function (after destructuring props): `const t = useTranslations("passwordReset");`
3. Replace hardcoded strings:
   - `計算中...` → `{t("common.loading")}`
   - `label: "弱い"` → `label: t("strength.weak")`
   - `label: "普通"` → `label: t("strength.medium")`
   - `label: "強い"` → `label: t("strength.strong")`
   - `パスワード強度: ` (2 occurrences in output element) → `{t("strength.label")}: ` and `aria-label={\`${t("strength.label")}: ${config.label}\`}`

Note: Keep the `aria-live="polite"` attribute that was added to the calculating `<p>` — that's a genuine a11y improvement.

**Step 2: Run unit tests**

Run: `cd frontend && npm test -- --run --reporter=default PasswordStrengthIndicator`
Expected: All tests pass (test file already mocks `next-intl`)

**Step 3: Run format check**

Run: `cd frontend && npx biome check app/components/auth/PasswordStrengthIndicator.tsx`
Expected: No errors

**Step 4: Commit**

```bash
git add frontend/app/components/auth/PasswordStrengthIndicator.tsx
git commit -m "fix(i18n): restore useTranslations in PasswordStrengthIndicator"
```

---

### Task 6: Fix Playwright config and E2E auth fixture (I-4, I-5)

**Files:**
- Modify: `frontend/playwright.config.ts:32`
- Modify: `frontend/tests/e2e/fixtures/auth.ts:9-12`

**Step 1: Restore conditional reuseExistingServer**

In `frontend/playwright.config.ts`, change line 32:
- From: `reuseExistingServer: true,`
- To: `reuseExistingServer: !process.env.CI,`

**Step 2: Externalize E2E credentials**

In `frontend/tests/e2e/fixtures/auth.ts`, change the data block (lines 9-12):

From:
```typescript
      data: {
        email: "bob.engineer@miometry.example.com",
        password: "Password1",
        rememberMe: false,
      },
```

To:
```typescript
      data: {
        email: process.env.E2E_TEST_EMAIL || "bob.engineer@miometry.example.com",
        password: process.env.E2E_TEST_PASSWORD || "Password1",
        rememberMe: false,
      },
```

**Step 3: Run format check**

Run: `cd frontend && npx biome check playwright.config.ts tests/e2e/fixtures/auth.ts`
Expected: No errors

**Step 4: Commit**

```bash
git add frontend/playwright.config.ts frontend/tests/e2e/fixtures/auth.ts
git commit -m "fix(e2e): restore conditional reuseExistingServer and externalize test credentials"
```

---

### Task 7: Improve focus assertion in accessibility test (M-2)

**Files:**
- Modify: `frontend/tests/e2e/accessibility.spec.ts:177-178`

**Step 1: Replace tag name assertion**

In `accessibility.spec.ts`, replace lines 177-178:

From:
```typescript
    const focusedElement = await page.evaluate(() => document.activeElement?.tagName);
    expect(["BUTTON", "A", "INPUT", "SELECT", "NEXTJS-PORTAL"]).toContain(focusedElement);
```

To:
```typescript
    const hasFocus = await page.evaluate(
      () => document.activeElement !== null && document.activeElement !== document.body,
    );
    expect(hasFocus).toBe(true);
```

**Step 2: Commit**

```bash
git add frontend/tests/e2e/accessibility.spec.ts
git commit -m "fix(test): use resilient focus assertion instead of tag name list"
```

---

### Task 8: Extract projects API mock helper (M-4)

**Files:**
- Modify: `frontend/tests/e2e/fixtures/auth.ts`
- Modify: `frontend/tests/e2e/absence-entry.spec.ts`
- Modify: `frontend/tests/e2e/accessibility.spec.ts`
- Modify: `frontend/tests/e2e/approval-workflow.spec.ts`
- Modify: `frontend/tests/e2e/auto-save.spec.ts`
- Modify: `frontend/tests/e2e/daily-entry.spec.ts`
- Modify: `frontend/tests/e2e/multi-project-entry.spec.ts`

**Step 1: Add helper to auth fixture**

In `frontend/tests/e2e/fixtures/auth.ts`, add after the existing exports:

```typescript
/** Mock the projects API to prevent timeouts in tests */
export async function mockProjectsApi(
  page: import("@playwright/test").Page,
  projects = [
    { id: "project-1", code: "PROJ-001", name: "Project Alpha" },
    { id: "project-2", code: "PROJ-002", name: "Project Beta" },
  ],
) {
  await page.route("**/api/v1/members/*/projects", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ projects, count: projects.length }),
    });
  });
}
```

**Step 2: Update spec files**

In each of the 6 spec files, replace the inline `page.route("**/api/v1/members/*/projects", ...)` block with a call to the helper.

For files using 2 default projects (absence-entry, accessibility, approval-workflow, auto-save, daily-entry):
```typescript
import { mockProjectsApi } from "./fixtures/auth";
// ...
await mockProjectsApi(page);
```

For `multi-project-entry.spec.ts` (uses 3 projects):
```typescript
import { mockProjectsApi } from "./fixtures/auth";
// ...
await mockProjectsApi(page, [
  { id: "project-1", code: "PROJ-001", name: "Project Alpha" },
  { id: "project-2", code: "PROJ-002", name: "Project Beta" },
  { id: "project-3", code: "PROJ-003", name: "Project Gamma" },
]);
```

Note: `accessibility.spec.ts` uses different project names ("Main Project", "Support Project") — update to use the default helper values for consistency. The test does not assert on project names.

**Step 3: Run format check**

Run: `cd frontend && npx biome check tests/e2e/fixtures/auth.ts tests/e2e/absence-entry.spec.ts tests/e2e/accessibility.spec.ts tests/e2e/approval-workflow.spec.ts tests/e2e/auto-save.spec.ts tests/e2e/daily-entry.spec.ts tests/e2e/multi-project-entry.spec.ts`
Expected: No errors

**Step 4: Commit**

```bash
git add frontend/tests/e2e/fixtures/auth.ts frontend/tests/e2e/absence-entry.spec.ts frontend/tests/e2e/accessibility.spec.ts frontend/tests/e2e/approval-workflow.spec.ts frontend/tests/e2e/auto-save.spec.ts frontend/tests/e2e/daily-entry.spec.ts frontend/tests/e2e/multi-project-entry.spec.ts
git commit -m "refactor(test): extract mockProjectsApi helper to reduce E2E test duplication"
```

---

### Task 9: Run full test suite and verify

**Step 1: Run backend tests**

Run: `cd backend && ./gradlew test --no-daemon`
Expected: All tests pass

**Step 2: Run frontend unit tests**

Run: `cd frontend && npm test -- --run`
Expected: All tests pass

**Step 3: Run lint checks**

Run: `cd backend && ./gradlew checkFormat detekt --no-daemon`
Run: `cd frontend && npm run check:ci`
Expected: No violations
