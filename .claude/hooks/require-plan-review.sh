#!/usr/bin/env bash
# PostToolUse hook: require plan-reviewer agent after writing plan files.
# Detects when plan.md or tasks.md is created/edited and instructs Claude
# to invoke the plan-reviewer subagent before proceeding with implementation.
#
# Output:
#   stdout = instruction fed back to Claude (mandatory review directive)
#   stderr = informational message shown to user
#
# Fail-open: if JSON parsing fails, exit 0 so normal operations are not disrupted.

set -euo pipefail

# Read PostToolUse JSON from stdin and extract file_path
FILE_PATH=$(python3 -c "
import json, sys
data = json.load(sys.stdin)
tool_input = data.get('tool_input', {})
print(tool_input.get('file_path', ''))
" 2>/dev/null) || exit 0

# Exit if no file path found
[[ -z "$FILE_PATH" ]] && exit 0

# Normalize to basename for pattern matching
BASENAME=$(basename "$FILE_PATH")

# Detect plan/task files (common naming conventions)
case "$BASENAME" in
  plan.md | tasks.md | PLAN.md | TASKS.md)
    echo "MANDATORY: A plan file ($BASENAME) was just written. You MUST invoke the plan-reviewer subagent (Task tool with subagent_type='plan-reviewer') to review this plan BEFORE writing any implementation code. Do not proceed with Write/Edit on source files until plan-reviewer returns APPROVED." >&1
    echo "Plan file detected ($BASENAME). Plan review required before implementation." >&2
    ;;
esac

exit 0
