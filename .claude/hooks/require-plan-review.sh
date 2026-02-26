#!/usr/bin/env bash
# PostToolUse hook: require multi-agent plan review after writing plan files.
# Detects when files under docs/plan/ are created/edited and instructs Claude
# to invoke three specialist review agents in parallel before implementation.
#
# Review agents:
#   1. chief-product-officer — product feasibility, spec alignment
#   2. security-reviewer     — security risks, auth/authz concerns
#   3. ux-design-advisor     — UX quality, accessibility, user flow
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

# Detect plan files under docs/plan/ directory
# Match any file written inside the docs/plan/ folder (at any depth)
if [[ "$FILE_PATH" != */docs/plan/* ]]; then
  exit 0
fi

cat <<'DIRECTIVE'
MANDATORY PLAN REVIEW: A plan file was just written. Before writing ANY implementation code, you MUST:

1. Invoke the following three review agents IN PARALLEL using the Task tool:
   - Task(subagent_type="chief-product-officer"): Review the plan for product feasibility, spec alignment, and feature completeness. Read the plan file and AGENTS.md, then output APPROVED or REJECTED with reasons.
   - Task(subagent_type="security-reviewer"): Review the plan for security risks — authentication, authorization, tenant isolation, injection, and data exposure concerns. Read the plan file and AGENTS.md, then output APPROVED or REJECTED with reasons.
   - Task(subagent_type="ux-design-advisor"): Review the plan for UX quality — user flow, accessibility, information architecture, and responsive design. Read the plan file and AGENTS.md, then output APPROVED or REJECTED with reasons.

2. After all three agents return, synthesize their results:
   - If ALL three return APPROVED → announce "PLAN APPROVED" and proceed with implementation
   - If ANY returns REJECTED → announce "PLAN REJECTED", list the issues, revise the plan, and re-run the rejected reviewers

3. Do NOT use Write/Edit on source files until all three reviewers approve.
DIRECTIVE
BASENAME=$(basename "$FILE_PATH")
echo "Plan file detected (docs/plan/$BASENAME). Multi-agent review required before implementation." >&2

exit 0
