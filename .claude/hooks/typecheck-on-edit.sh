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

case "$FILE_PATH" in
  */frontend/*.ts | */frontend/*.tsx)
    OUTPUT=$(cd "$PROJECT_ROOT/frontend" && timeout 30 npx tsc --noEmit --pretty 2>&1) || echo "$OUTPUT" >&2
    ;;
esac

exit 0
