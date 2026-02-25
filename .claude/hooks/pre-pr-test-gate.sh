#!/usr/bin/env bash
# PreToolUse hook: block PR creation until lint, format, tests, and coverage are verified.
# Gates both `gh pr create` (Bash) and MCP `create_pull_request` tool.
# Uses a time-limited flag file (.claude/.pr-tests-verified) as the gate.
#
# Exit codes:
#   0 = allow (not a PR creation, or tests verified within TTL)
#   2 = block (tests not verified, message shown to user)
#
# Fail-open: if JSON parsing fails, exit 0 so normal operations are not disrupted.

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FLAG_FILE="$PROJECT_ROOT/.claude/.pr-tests-verified"
TTL_SECONDS=1800  # 30 minutes

# Parse PreToolUse JSON from stdin
read -r INPUT 2>/dev/null || exit 0

PARSED=$(python3 -c "
import json, sys
data = json.loads(sys.argv[1])
tool = data.get('tool_name', '')
cmd = data.get('tool_input', {}).get('command', '')
print(tool)
print(cmd)
" "$INPUT" 2>/dev/null) || exit 0

TOOL_NAME=$(echo "$PARSED" | head -1)
COMMAND=$(echo "$PARSED" | tail -n +2)

# --- Determine if this is a PR creation action ---

IS_PR_CREATE=false

# Bash: gh pr create
if [[ "$TOOL_NAME" == "Bash" ]] && [[ "$COMMAND" =~ gh[[:space:]]+pr[[:space:]]+create ]]; then
  IS_PR_CREATE=true
fi

# MCP: GitHub create_pull_request
if [[ "$TOOL_NAME" == "mcp__plugin_github_github__create_pull_request" ]]; then
  IS_PR_CREATE=true
fi

# Not a PR creation — allow
[[ "$IS_PR_CREATE" == "false" ]] && exit 0

# --- Check flag file ---

if [[ -f "$FLAG_FILE" ]]; then
  FLAG_AGE=$(( $(date +%s) - $(stat -c %Y "$FLAG_FILE" 2>/dev/null || echo 0) ))
  if [[ "$FLAG_AGE" -lt "$TTL_SECONDS" ]]; then
    # Flag exists and is fresh — allow PR creation
    rm -f "$FLAG_FILE"  # One-time use: remove after allowing
    exit 0
  fi
  # Flag is stale — remove and block
  rm -f "$FLAG_FILE"
fi

# --- Block PR creation ---

cat >&2 <<'BLOCK_MSG'
BLOCKED: PR creation requires lint, format, test, and coverage verification first.

Before creating a PR, you MUST complete ALL of the following steps:

1. IDENTIFY changed files:
   git diff main...HEAD --name-only

2. RUN lint and format checks (if relevant files changed):

   Backend (if *.java, *.kt, or *.gradle.kts changed):
     cd backend && ./gradlew checkFormat
     cd backend && ./gradlew detekt
   - checkFormat verifies Spotless formatting (palantir-java-format + ktlint)
   - detekt runs Kotlin static analysis
   - Both must pass with zero violations

   Frontend (if *.ts, *.tsx, *.js, *.jsx, or *.css changed):
     cd frontend && npx biome ci
   - biome ci checks both lint rules and formatting in one command
   - Must pass with zero errors (warnings are acceptable)

3. CHECK test file existence:
   - Backend source (*.java, *.kt) → corresponding *Test.java/*Test.kt must exist
   - Frontend source (*.ts, *.tsx) → corresponding *.test.ts/*.test.tsx must exist
   - List any source files WITHOUT corresponding tests as warnings

4. RUN backend tests (if backend files changed):
   cd backend && ./gradlew test jacocoTestReport
   - Check JaCoCo report for coverage of changed packages
   - Target: 80%+ LINE coverage per changed package

5. RUN frontend tests (if frontend files changed):
   cd frontend && npm test -- --run
   - Verify all tests pass

6. REPORT results to user:
   - Lint/format check results (pass/fail per tool)
   - Number of changed source files and their test coverage status
   - Test pass/fail summary
   - Coverage metrics for changed packages
   - Any warnings (missing tests, low coverage areas, lint warnings)

7. If ALL checks pass, create the verification flag:
   touch .claude/.pr-tests-verified

8. Then RETRY the PR creation command.
BLOCK_MSG
exit 2
