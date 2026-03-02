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

## Pre-PR Verification (MANDATORY)

`pre-pr-test-gate.sh` hook が PR 作成をブロック。lint/format + test + coverage (80%+) をパスした後、`touch .claude/.pr-tests-verified` で解除（30分有効・single-use）。
Review agents: `build-integrity-verifier`, `qa-ux-guardian`, `security-reviewer` を並列実行 → 全員 APPROVE 後に `e2e-test-engineer` を実行。

## Backend Format Before Commit

- **Always run `./gradlew spotlessApply` before committing backend changes** — Java (palantir-java-format) and Kotlin (ktlint) are checked separately in CI
- Common pitfall: multi-argument `this()` calls and constructor invocations get reformatted to one-arg-per-line; the auto-format hook may miss files not directly edited (e.g. test files created via Write tool)

## Git Safety

Permissions in `settings.local.json` use granular git command patterns (not `Bash(git *)`).
Intentionally excluded (require user confirmation): `git reset`, `git clean`, `git restore`, `git config`
PreToolUse hook adds second layer: blocks `--force`, `--no-verify`, `-D`, `checkout .`, `stash drop/clear`, `rebase -i` within allowed commands

## Frontend Lint/Format

- Run `npm ci` in `frontend/` before using `npx biome` — without it, `npx` may resolve a wrong global version
- CI uses `biome ci` (= `biome check` + format check); locally use `npx biome check` or `npx biome ci` to match
- Biome version is pinned in `frontend/package.json` (`@biomejs/biome`); always use the project-local binary

## Devcontainer Workflow

Claude Code hooks automatically delegate build/test/lint commands to the devcontainer when it is running. File editing remains on the WSL2 host.

- **Start**: `docker compose -f .devcontainer/docker-compose.yml up -d`
- **Stop**: `docker compose -f .devcontainer/docker-compose.yml down`
- **Path conversion**: Host `$PROJECT_ROOT/...` → Container `/workspaces/miometory/...` (automatic)
- **Fallback**: If the container is not running, hooks execute commands locally (same as before)
- **Manual exec**: `.claude/hooks/devcontainer-exec.sh --workdir DIR -- COMMAND`

## E2E Tests (Playwright)

- Devcontainer: `npx playwright test --project=chromium` — only the `chromium` Playwright project is configured and run in CI
- Playwright strict mode: locators matching multiple elements fail; use `.first()` or `{ exact: true }`
- Button selectors: `has-text("Assign")` also matches `"Assign Manager"` — use `getByRole("button", { name: "Assign", exact: true })` for modals
- ProjectSelector is a combobox (`role="combobox"` + `role="option"`), not a plain input; use `selectProject()` helper in `frontend/tests/e2e/fixtures/auth.ts`
- UI text source of truth: `frontend/messages/en.json` (next-intl) — always verify against this file, not guesses
- `AbsenceType` enum has `OTHER` (not `UNPAID_LEAVE`) — see `frontend/app/types/absence.ts`
- When fixing test selectors, read actual UI components first to avoid guesswork and rework
