# CLAUDE.md

**Important: Read `AGENTS.md` first** — it contains project overview, commands, architecture, code style, and all shared development guidelines. This file only covers Claude Code-specific configuration.

## Hooks (`.claude/hooks/`)

- `format-on-edit.sh` (PostToolUse): Auto-formats files after Write/Edit (Biome for frontend, Spotless for backend)
- `auto-test-on-edit.sh` (PostToolUse): Runs relevant tests when source/test files are edited
- `git-safety-check.sh` (PreToolUse): Blocks dangerous git patterns (force push, --no-verify, branch -D, checkout ., reset --hard, clean, config)
- `sensitive-file-guard.sh` (PreToolUse): Blocks Write/Edit to sensitive files (.env, credentials, secrets)
- `pre-pr-test-gate.sh` (PreToolUse): Blocks PR creation until lint, format, tests, and coverage pass
- Hook pattern: read JSON from stdin → parse with `python3` → exit 0 (allow) or exit 2 (block)
- New hooks should follow fail-open principle (exit 0 on parse errors)

## Plan Review Workflow

`docs/plan/` 配下のplanファイルのレビューには `/review-plan` コマンド（またはreview-planスキル）を使用する:

1. `/review-plan docs/plan/feature-x.md` でレビュー対象を指定して実行（引数省略で最新ファイルを対象）
2. 3つのレビューエージェントが **並列** で起動される:
   - `chief-product-officer` — product feasibility, spec alignment, feature completeness
   - `security-reviewer` — security risks, auth/authz, tenant isolation, injection
   - `ux-design-advisor` — UX quality, accessibility, user flow, responsive design
3. 全員 APPROVED → 実装に進んで良い。1つでも REJECTED → plan修正後、REJECTEDのレビューアーのみ再実行
4. planファイル編集時にreview-planスキルが自動提案されることもある

## Pre-PR Verification (MANDATORY)

Before creating a PR (`gh pr create` or MCP `create_pull_request`):
1. The `pre-pr-test-gate.sh` PreToolUse hook will **block** PR creation until all checks pass
2. You MUST complete these steps before retrying:
   - Identify changed files with `git diff main...HEAD --name-only`
   - Run lint/format: backend `./gradlew checkFormat && ./gradlew detekt`, frontend `npx biome ci`
   - Verify corresponding test files exist for each changed source file
   - Run backend tests: `cd backend && ./gradlew test jacocoTestReport` (if backend files changed)
   - Run frontend tests: `cd frontend && npm test -- --run` (if frontend files changed)
   - Check coverage: 80%+ LINE coverage per changed package (JaCoCo for backend)
   - Report results to user with lint/format + test pass/fail summary and coverage metrics
3. After all checks pass, invoke three review agents **in parallel**:
   - `build-integrity-verifier` — build configuration, dependency changes, structural impacts
   - `qa-ux-guardian` — UI quality, UX consistency, information architecture, accessibility
   - `security-reviewer` — authentication, authorization, tenant isolation, injection, data exposure
   - ALL three must APPROVE. If ANY rejects, fix issues and re-run only the rejected reviewers
4. After step 3 passes, invoke `e2e-test-engineer` to review E2E test coverage
   - If APPROVED → proceed. If REJECTED → implement recommended E2E tests, then proceed
5. After all checks and reviews pass: `touch .claude/.pr-tests-verified` then retry PR creation
4. The verification flag expires after 30 minutes and is single-use (removed after PR creation)

## Git Safety

Permissions in `settings.local.json` use granular git command patterns (not `Bash(git *)`).
Intentionally excluded (require user confirmation): `git reset`, `git clean`, `git restore`, `git config`
PreToolUse hook adds second layer: blocks `--force`, `--no-verify`, `-D`, `checkout .`, `stash drop/clear`, `rebase -i` within allowed commands

## Frontend Lint/Format

- Run `npm ci` in `frontend/` before using `npx biome` — without it, `npx` may resolve a wrong global version
- CI uses `biome ci` (= `biome check` + format check); locally use `npx biome check` or `npx biome ci` to match
- Biome version is pinned in `frontend/package.json` (`@biomejs/biome`); always use the project-local binary

## E2E Tests (Playwright)

- WSL2: `npx playwright test --project=chromium` — only the `chromium` Playwright project is configured and run in CI
- Playwright strict mode: locators matching multiple elements fail; use `.first()` or `{ exact: true }`
- Button selectors: `has-text("Assign")` also matches `"Assign Manager"` — use `getByRole("button", { name: "Assign", exact: true })` for modals
- ProjectSelector is a combobox (`role="combobox"` + `role="option"`), not a plain input; use `selectProject()` helper in `frontend/tests/e2e/fixtures/auth.ts`
- UI text source of truth: `frontend/messages/en.json` (next-intl) — always verify against this file, not guesses
- `AbsenceType` enum has `OTHER` (not `UNPAID_LEAVE`) — see `frontend/app/types/absence.ts`
- When fixing test selectors, read actual UI components first to avoid guesswork and rework
