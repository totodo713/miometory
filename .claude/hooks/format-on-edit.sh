#!/usr/bin/env bash
# PostToolUse hook: auto-format files after Write/Edit tool usage
# Routes to appropriate formatter based on file path:
#   - Frontend (ts/tsx/js/jsx/css/json) → Biome (sync)
#   - Backend (java/kt/gradle.kts) → Spotless (async, backgrounded)

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

# Exit if file doesn't exist (e.g. was deleted)
[[ ! -f "$FILE_PATH" ]] && exit 0

EXEC="$PROJECT_ROOT/.claude/hooks/devcontainer-exec.sh"

case "$FILE_PATH" in
  */frontend/*.ts | */frontend/*.tsx | */frontend/*.js | */frontend/*.jsx | */frontend/*.css | */frontend/*.json)
    "$EXEC" --workdir "$PROJECT_ROOT/frontend" -- npx biome format --write "$FILE_PATH" >/dev/null 2>&1 || true
    ;;
  */backend/*.java)
    "$EXEC" --workdir "$PROJECT_ROOT/backend" --background -- ./gradlew spotlessJavaApply >/dev/null 2>&1
    ;;
  */backend/*.kt)
    "$EXEC" --workdir "$PROJECT_ROOT/backend" --background -- ./gradlew spotlessKotlinApply >/dev/null 2>&1
    ;;
  */backend/*.gradle.kts)
    "$EXEC" --workdir "$PROJECT_ROOT/backend" --background -- ./gradlew spotlessKotlinGradleApply >/dev/null 2>&1
    ;;
esac

exit 0
