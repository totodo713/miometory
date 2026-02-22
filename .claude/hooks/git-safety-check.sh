#!/usr/bin/env bash
# PreToolUse hook: block dangerous git sub-patterns that permissions alone cannot catch.
# Permissions allow broad "Bash(git push *)" etc., but this hook inspects the actual
# command string for destructive flags like --force, --no-verify, -D, etc.
#
# Exit codes:
#   0 = allow (not a git command, or safe pattern)
#   2 = block (dangerous pattern detected, message shown to user)
#
# Fail-open: if JSON parsing fails, exit 0 so normal operations are not disrupted.

set -euo pipefail

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

# Only inspect Bash tool calls containing git commands
[[ "$TOOL_NAME" != "Bash" ]] && exit 0
[[ ! "$COMMAND" =~ ^[[:space:]]*git[[:space:]] ]] && exit 0

# --- Dangerous pattern checks ---

# git push: --force, --force-with-lease, deleting protected branches
if [[ "$COMMAND" =~ git[[:space:]]+push ]]; then
  if [[ "$COMMAND" =~ --force(-with-lease)? ]] || [[ "$COMMAND" =~ -f[[:space:]] ]] || [[ "$COMMAND" =~ -f$ ]]; then
    echo "BLOCKED: force push detected. Use normal push or ask user for confirmation." >&2
    exit 2
  fi
  if [[ "$COMMAND" =~ --delete[[:space:]]+(main|master) ]] || [[ "$COMMAND" =~ :(main|master) ]]; then
    echo "BLOCKED: deleting protected branch (main/master) via push." >&2
    exit 2
  fi
fi

# git commit: --no-verify / -n (hook bypass)
if [[ "$COMMAND" =~ git[[:space:]]+commit ]]; then
  if [[ "$COMMAND" =~ --no-verify ]] || [[ "$COMMAND" =~ (^|[[:space:]])-n([[:space:]]|$) ]]; then
    echo "BLOCKED: --no-verify / -n skips pre-commit hooks." >&2
    exit 2
  fi
  if [[ "$COMMAND" =~ --no-gpg-sign ]]; then
    echo "BLOCKED: --no-gpg-sign skips GPG signing." >&2
    exit 2
  fi
fi

# git branch: -D (force delete unmerged branch)
if [[ "$COMMAND" =~ git[[:space:]]+branch ]]; then
  if [[ "$COMMAND" =~ (^|[[:space:]])-D([[:space:]]|$) ]]; then
    echo "BLOCKED: git branch -D force-deletes unmerged branches. Use -d for safe delete." >&2
    exit 2
  fi
fi

# git checkout: single dot (discard all working directory changes)
if [[ "$COMMAND" =~ git[[:space:]]+checkout ]]; then
  if [[ "$COMMAND" =~ git[[:space:]]+checkout[[:space:]]+\.([[:space:]]|$) ]]; then
    echo "BLOCKED: 'git checkout .' discards all working directory changes." >&2
    exit 2
  fi
fi

# git stash: drop / clear (permanent deletion of stashed changes)
if [[ "$COMMAND" =~ git[[:space:]]+stash ]]; then
  if [[ "$COMMAND" =~ git[[:space:]]+stash[[:space:]]+(drop|clear) ]]; then
    echo "BLOCKED: git stash drop/clear permanently deletes stashed changes." >&2
    exit 2
  fi
fi

# git rebase: -i / --interactive (not supported in non-interactive environment)
if [[ "$COMMAND" =~ git[[:space:]]+rebase ]]; then
  if [[ "$COMMAND" =~ --interactive ]] || [[ "$COMMAND" =~ (^|[[:space:]])-i([[:space:]]|$) ]]; then
    echo "BLOCKED: interactive rebase requires terminal input, not supported here." >&2
    exit 2
  fi
fi

# git reset: --hard (discards uncommitted changes)
if [[ "$COMMAND" =~ git[[:space:]]+reset ]]; then
  if [[ "$COMMAND" =~ --hard ]]; then
    echo "BLOCKED: git reset --hard discards uncommitted changes." >&2
    exit 2
  fi
fi

# git clean: -f (permanently deletes untracked files)
if [[ "$COMMAND" =~ git[[:space:]]+clean ]]; then
  echo "BLOCKED: git clean can permanently delete untracked files." >&2
  exit 2
fi

# git restore: . (discard all working directory changes)
if [[ "$COMMAND" =~ git[[:space:]]+restore ]]; then
  if [[ "$COMMAND" =~ git[[:space:]]+restore[[:space:]]+\.([[:space:]]|$) ]]; then
    echo "BLOCKED: 'git restore .' discards all working directory changes." >&2
    exit 2
  fi
fi

# git config: potential security risk
if [[ "$COMMAND" =~ git[[:space:]]+config ]]; then
  echo "BLOCKED: git config changes may affect hooks, aliases, or credentials." >&2
  exit 2
fi

exit 0
