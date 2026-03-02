#!/usr/bin/env bash
# PostToolUse hook: auto-run tests after editing test files
# Routes to appropriate test runner based on file path:
#   - Frontend test files (*.test.ts/tsx) -> vitest run (sync, 30s timeout)
#   - Backend test files (*Test.kt) -> stderr notification only (Testcontainers is slow)

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

# Read PostToolUse JSON from stdin and extract file_path
FILE_PATH=$(python3 -c "
import json, sys
data = json.load(sys.stdin)
tool_input = data.get('tool_input', {})
print(tool_input.get('file_path', ''))
" 2>/dev/null) || exit 0

# Exit if no file path found
[[ -z "$FILE_PATH" ]] && exit 0

# Exit if file doesn't exist
[[ ! -f "$FILE_PATH" ]] && exit 0

EXEC="$PROJECT_ROOT/.claude/hooks/devcontainer-exec.sh"

case "$FILE_PATH" in
  */frontend/*.test.ts | */frontend/*.test.tsx)
    OUTPUT=$("$EXEC" --workdir "$PROJECT_ROOT/frontend" -- timeout 30 npx vitest run "$FILE_PATH" 2>&1) || echo "$OUTPUT" >&2
    ;;
  */backend/*Test.kt)
    TEST_CLASS=$(basename "$FILE_PATH" .kt)
    echo "NOTE: Backend test file edited. Run manually: cd backend && ./gradlew test --tests \"*${TEST_CLASS}\"" >&2
    ;;
esac

exit 0
