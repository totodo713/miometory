# CLAUDE.md

**Important: Read `AGENTS.md` first** — it contains project overview, commands, architecture, code style, and all shared development guidelines. This file only covers Claude Code-specific configuration.

## Hooks (`.claude/hooks/`)

- `format-on-edit.sh` (PostToolUse): Auto-formats files after Write/Edit (Biome for frontend, Spotless for backend)
- `auto-test-on-edit.sh` (PostToolUse): Runs relevant tests when source/test files are edited
- `git-safety-check.sh` (PreToolUse): Blocks dangerous git patterns (force push, --no-verify, branch -D, checkout ., reset --hard, clean, config)
- `sensitive-file-guard.sh` (PreToolUse): Blocks Write/Edit to sensitive files (.env, credentials, secrets)
- Hook pattern: read JSON from stdin → parse with `python3` → exit 0 (allow) or exit 2 (block)
- New hooks should follow fail-open principle (exit 0 on parse errors)

## Git Safety

Permissions in `settings.local.json` use granular git command patterns (not `Bash(git *)`).
Intentionally excluded (require user confirmation): `git reset`, `git clean`, `git restore`, `git config`
PreToolUse hook adds second layer: blocks `--force`, `--no-verify`, `-D`, `checkout .`, `stash drop/clear`, `rebase -i` within allowed commands
