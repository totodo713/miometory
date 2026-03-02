#!/usr/bin/env bash
# PostToolUse hook: run TypeScript type check after editing frontend TS/TSX files
# Info-only: outputs type errors to stderr but never blocks (always exits 0)

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
  */frontend/*.ts | */frontend/*.tsx)
    OUTPUT=$("$EXEC" --workdir "$PROJECT_ROOT/frontend" -- timeout 30 npx tsc --noEmit --pretty 2>&1)
    EXIT_CODE=$?
    if [[ $EXIT_CODE -eq 124 ]]; then
      echo "TypeCheck: timed out after 30s" >&2
    elif [[ $EXIT_CODE -ne 0 ]]; then
      echo "$OUTPUT" >&2
    fi
    ;;
esac

exit 0
