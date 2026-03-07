# CLAUDE.md

**Important: Read `AGENTS.md` first** — it contains project overview, commands, architecture, code style, and all shared development guidelines. This file only covers Claude Code-specific configuration.

## Hooks (`.claude/hooks/`)

- `devcontainer-exec.sh`: Shared wrapper that delegates commands to devcontainer when running, falls back to local execution otherwise. Used by format/test/typecheck hooks
- `format-on-edit.sh` (PostToolUse): Auto-formats files after Write/Edit (Biome for frontend, Spotless for backend) — via devcontainer
- `auto-test-on-edit.sh` (PostToolUse): Runs relevant tests when source/test files are edited — via devcontainer
- `typecheck-on-edit.sh` (PostToolUse): Runs TypeScript type check after editing frontend files — via devcontainer
- `git-safety-check.sh` (PreToolUse): Blocks dangerous git patterns (force push, --no-verify, branch -D, checkout ., reset --hard, clean, config)
- `sensitive-file-guard.sh` (PreToolUse): Blocks Write/Edit to sensitive files (.env, credentials, secrets)
- `pre-pr-test-gate.sh` (PreToolUse): Blocks PR creation until lint, format, tests, and coverage pass
- `migration-safety-check.sh` (PreToolUse): Warns/blocks destructive Flyway migration operations (DROP without IF EXISTS, TRUNCATE, column type changes)
- `auto-generated-file-guard.sh` (PreToolUse): Blocks Write/Edit on auto-generated files (package-lock.json, gradlew, .next/*, tsconfig.tsbuildinfo)
- Hook pattern: read JSON from stdin → parse with `python3` → exit 0 (allow) or exit 2 (block)
- New hooks should follow fail-open principle (exit 0 on parse errors)

## Plan Review Workflow

Plan review は `/review-plan` スキルに委譲。3エージェント並列レビュー（CPO, Security, UX）→ 全員 APPROVED で実装可。

## Plan Files

- Plan ドキュメントは `docs/plans/YYYY-MM-DD-<topic>-plan.md` に保存（`.claude/plans/` ではない）
- Design ドキュメントは `docs/plans/YYYY-MM-DD-<topic>-design.md`
- **重複防止**: 同一トピックの plan を更新する場合、新規ファイルを作らず既存を編集するか、旧ファイルを削除して一本化する

## Pre-PR Verification (MANDATORY)

`pre-pr-test-gate.sh` hook が PR 作成をブロック。lint/format + test + coverage (80%+) をパスした後、`touch .claude/.pr-tests-verified` で解除（30分有効・single-use）。
Review agents: `build-integrity-verifier`, `qa-ux-guardian`, `security-reviewer` を並列実行 → 全員 APPROVE 後に `e2e-test-engineer` を実行。

## Backend Format Before Commit

- **Always run `./gradlew spotlessApply` before committing backend changes** — Java (palantir-java-format) and Kotlin (ktlint) are checked separately in CI
- Common pitfall: multi-argument `this()` calls and constructor invocations get reformatted to one-arg-per-line; the auto-format hook may miss files not directly edited (e.g. test files created via Write tool)
- Common pitfall: Spotless may remove newly added imports if the referencing code hasn't been written yet — add imports AFTER the code that uses them, or re-add if removed

## Git Safety

Permissions in `settings.local.json` use granular git command patterns (not `Bash(git *)`).
Intentionally excluded (require user confirmation): `git reset`, `git clean`, `git restore`, `git config`
PreToolUse hook adds second layer: blocks `--force`, `--no-verify`, `-D`, `checkout .`, `stash drop/clear`, `rebase -i` within allowed commands

## Frontend UI Patterns

- **Modals/Dialogs**: Must follow `ConfirmDialog.tsx` pattern — `role="dialog"` or `role="alertdialog"`, `aria-modal="true"`, `aria-labelledby` with `useId()`, Escape key handler, focus trap (Tab cycling), initial focus on first interactive element
- **Form validation**: Use `aria-invalid`, `aria-describedby` pointing to error `<p>` with `role="alert"` — see login page for reference
- **Dropdown menus**: Use `role="menu"` on container, `role="menuitem"` on items, `aria-expanded`/`aria-haspopup` on trigger, Escape key to close — see `UserMenu.tsx`
- **Accessible names**: Prefer `sr-only` span over `aria-label` when the button has dynamic visible text (e.g. user name) — `aria-label` overrides visible text entirely
- **Loading states**: Use `LoadingSpinner` component (not custom text) — provides `aria-live="polite"` and sr-only label
- **Focus visible**: All interactive elements need `focus:outline-none focus:ring-2 focus:ring-blue-500` (WCAG 2.4.7)
- **Type safety for state updaters**: Prefer `Pick<T, 'field1' | 'field2'>` over `Partial<T>` when only specific fields should be mutable

## Frontend Lint/Format

- Run `npm ci` in `frontend/` before using `npx biome` — without it, `npx` may resolve a wrong global version
- CI uses `biome ci` (= `biome check` + format check); locally use `npx biome check` or `npx biome ci` to match
- Biome version is pinned in `frontend/package.json` (`@biomejs/biome`); always use the project-local binary
- After adding JSX attributes (role, aria-*), run `npx biome check --write <file>` — long attribute lines get reformatted to multi-line
- After adding keys to i18n JSON files (`messages/en.json`, `messages/ja.json`), run `npx biome check --write` — indentation mismatches fail CI
- **i18n key-parity test**: `key-parity.test.ts` enforces no empty translation values — if a key is semantically empty in one language (e.g. English suffix), use `" "` (space) instead of `""`

## Devcontainer Workflow

Claude Code hooks automatically delegate build/test/lint commands to the devcontainer when it is running. File editing remains on the WSL2 host.

- **Start**: `docker compose -f .devcontainer/docker-compose.yml up -d`
- **Stop**: `docker compose -f .devcontainer/docker-compose.yml down`
- **Port forwarding** (on-demand): `docker compose -f .devcontainer/docker-compose.yml -f .devcontainer/docker-compose.ports.yml up -d app` — exposes FE:3000, BE:8080 to host
- **Start servers**: `exec -d app bash -c "cd /workspaces/miometory/backend && ./gradlew bootRun > /tmp/backend.log 2>&1"` / `exec -d app bash -c "cd /workspaces/miometory/frontend && npm run dev > /tmp/frontend.log 2>&1"`
- **Path conversion**: Host `$PROJECT_ROOT/...` → Container `/workspaces/miometory/...` (automatic)
- **Fallback**: If the container is not running, hooks execute commands locally (same as before)
- **Worktree gotcha**: git worktree ではdevcontainerが起動していないことが多い。手動で `devcontainer-exec.sh` を呼ぶ場合、`--workdir` にコンテナパス (`/workspaces/...`) を使わず、ホストの絶対パスを使うか、直接 `cd` + コマンド実行する
- **Worktree git CWD**: `backend/` や `frontend/` 内で作業中に `git add` する場合、パスが git root からの相対パスと不一致になる → `git -C <worktree-root> add <relative-path>` を使うか、git root に戻ってから実行
- **Manual exec**: `.claude/hooks/devcontainer-exec.sh --workdir DIR -- COMMAND`

## E2E Tests (Playwright)

- **Shared E2E fixtures**: `frontend/tests/e2e/fixtures/auth.ts` contains mock helpers (`mockProjectsApi`, `mockCalendarApi`, etc.) — when renaming API response fields, update fixtures too (not just spec files)
- **Inline E2E mocks**: 個別specファイル内の `page.route()` インラインmockもDTOフィールド名と同期が必要 — 共有fixtureだけでなくspec内のmockも検索すること
- Devcontainer: `npx playwright test --project=chromium` — only the `chromium` Playwright project is configured and run in CI
- Playwright strict mode: locators matching multiple elements fail; use `.first()` or `{ exact: true }`
- Verifying modal open: use `getByRole("heading", { name })` not `getByText()` — button text and modal title often match, causing strict-mode violation
- Button selectors: `has-text("Assign")` also matches `"Assign Manager"` — use `getByRole("button", { name: "Assign", exact: true })` for modals
- ProjectSelector is a combobox (`role="combobox"` + `role="option"`), not a plain input; use `selectProject()` helper in `frontend/tests/e2e/fixtures/auth.ts`
- UI text source of truth: `frontend/messages/en.json` (next-intl) — always verify against this file, not guesses
- Before writing E2E text assertions, read the exact value from `en.json` with `node -e "..."` — do not guess translated strings
- Same i18n key name can differ across namespaces (`header.logout` = "Logout" vs `waiting.logout` = "Log Out") — always check the correct namespace
- `AbsenceType` enum has `OTHER` (not `UNPAID_LEAVE`) — see `frontend/app/types/absence.ts`
- When fixing test selectors, read actual UI components first to avoid guesswork and rework
- Explicit `role` attribute overrides implicit HTML role — `<Link role="menuitem">` needs `getByRole("menuitem")`, not `getByRole("link")`
- TimesheetRow の time input は `aria-label` パターン `"Start {date}"` / `"End {date}"` / `"Save {date}"` で特定可能

## Frontend Patterns

- **Loading skeleton ↔ content layout parity**: Skeleton grids (column count, card count) must match the actual content layout to prevent CLS (Cumulative Layout Shift)
- **useToast in useEffect deps**: `ToastProvider` の context value が `useMemo` 未適用（#131）— `useEffect` 依存配列に `toast` を入れるとエラー時に無限ループする。`toast` メソッドは deps から除外するか、Provider 側で value を memoize すること

## Permission System

- **ロール二重構造**: data-dev.sql のレガシーロール (ADMIN/USER/MODERATOR, `00000000-...` UUID) と V18 の管理ロール (SYSTEM_ADMIN/TENANT_ADMIN/SUPERVISOR, `aa000000-...` UUID) が共存
- **設定権限は V18 に未含**: `system_settings.*` は V26、`tenant_settings.*` は V29 で後追い追加 — 権限不足の調査時は V18 だけでなく後続マイグレーションも確認すること
- **AdminNav 表示条件**: 各ナビアイテムの `permission` フィールドで制御 — `AdminNav.tsx` の `NAV_ITEMS` 定義を参照

## GitHub Issue Management

- PR description に `Closes #xx` を含めて issue の自動クローズ漏れを防ぐ
- Copilot/レビュアーコメントへの返信: `gh api repos/{owner}/{repo}/pulls/{pr}/comments/{id}/replies -X POST -f body="..."`
- **Copilot "outdated" コメント**: 行番号がズレただけで指摘自体は未修正のことがある — outdated も含めて全件確認すること

## Backend Testing Patterns

- **DomainException status mapping**: `GlobalExceptionHandler` がエラーコードで HTTP ステータスを決定 — `*_NOT_FOUND` → 404, `DUPLICATE_*` → 409, `ALREADY_*`/`CANNOT_*` → 422, その他 → 400
- **`gradlew` location**: `backend/gradlew`（プロジェクトルート直下ではない）— `cd backend && ./gradlew` または `backend/gradlew` で実行

## Backend Auth in Tests

- **`Authentication` parameter is null in test profile**: `TestRestTemplate`-based integration tests run with security disabled (`permitAll`), so `Authentication` is null. Controllers with manual auth checks must guard `if (authentication == null) return;` for dev/test compatibility. `MockMvc`-based tests (e.g. `AdminIntegrationTestBase`) use `.with(user(email))` and provide non-null auth.
- **MockMvc `jsonPath().doesNotExist()`**: null 値のフィールドはキーが存在するため `doesNotExist()` は失敗する。また JsonPath フィルタ式 `$[?(@.field == 'val')].other` は配列を返すため挙動が異なる → `$[0].field` インデックスを使う

## Backend Architecture Patterns

- **CRUD entity (非Event Sourced)**: `DailyAttendance` は event sourcing を使わない単純CRUD — `JdbcDailyAttendanceRepository` が UPSERT + 楽観ロック（version カラム）を直接管理
- **SecurityConfig httpBasic**: dev/test profile では `httpBasic(Customizer.withDefaults())` + `permitAll()` で、Controller の `Authentication` パラメータが任意受信可能（認証なしリクエストも通る）
- **OpenAPI spec is manually maintained**: `backend/src/main/resources/static/api-docs/openapi.yaml` は自動生成ではない — DTO フィールド名・型・エンドポイント変更時は手動で同期が必要

## Troubleshooting

- **Post-merge build verification**: マージコンフリクト解消後は必ず `cd backend && ./gradlew build` を実行 — auto-merge が壊れた参照（削除済みメソッド呼び出し、コンストラクタ引数順の不一致）を作ることがある
- **Flyway validation failure** ("applied migration not resolved locally"): `flyway_schema_history` に孤児レコードあり → `DELETE FROM flyway_schema_history WHERE description = '...'` で該当行を削除
- **Flyway checksum mismatch in tests** ("Migration checksum mismatch for migration version N"): Testcontainers の `.withReuse(true)` がキャッシュした旧DBと migration file の内容が不一致 → `docker ps -a --filter "label=org.testcontainers"` で対象 PostgreSQL コンテナを特定し `docker stop/rm` で削除、再テスト
- **MockK varargs type inference** (`Cannot infer type for type parameter 'T'`): `jdbcTemplate.queryForList(any<String>(), any(), any())` は型推論に失敗する → `queryForList(any<String>(), *anyVararg())` + 戻り値を `emptyList<Map<String, Any>>()` のように明示型指定
- **Detekt `EqualsNullCall`**: テストで `.equals(null)` を直接呼ぶとdetektが失敗する → `assertNotEquals(null, x)` を使う。CIでは Lint & Format Check と Build & Test Backend の両方がdetektを実行するため、1つの違反で2ジョブ失敗する
