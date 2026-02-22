#!/usr/bin/env bash
# PreToolUse hook: block Write/Edit on sensitive files (secrets, credentials, keys).
# Allows *.env.sample and *.env.example as safe templates.
#
# Exit codes:
#   0 = allow (not a sensitive file, or allowlisted pattern)
#   2 = block (sensitive file detected, message shown to user)
#
# Fail-open: if JSON parsing fails, exit 0 so normal operations are not disrupted.

set -euo pipefail

# Parse PreToolUse JSON from stdin
read -r INPUT 2>/dev/null || exit 0

FILE_PATH=$(python3 -c "
import json, sys
data = json.loads(sys.argv[1])
print(data.get('tool_input', {}).get('file_path', ''))
" "$INPUT" 2>/dev/null) || exit 0

# Exit if no file path found
[[ -z "$FILE_PATH" ]] && exit 0

# Normalize to basename and full path for pattern matching
BASENAME=$(basename "$FILE_PATH")

# --- Allowlist: safe template files ---
case "$BASENAME" in
  *.env.sample | *.env.example | env.sample | env.example)
    exit 0
    ;;
esac

# --- Block sensitive basename patterns ---
case "$BASENAME" in
  # Credentials files — config/data extensions only (source files like credentials.ts are safe)
  credentials | credentials.json | credentials.yml | credentials.yaml | credentials.xml | credentials.conf | credentials.properties | \
  *-credentials | *-credentials.json | *-credentials.yml | *-credentials.yaml | \
  *_credentials | *_credentials.json | *_credentials.yml | *_credentials.yaml | \
  .credentials)
    echo "BLOCKED: Writing to credentials file '$BASENAME'. These files may contain secrets." >&2
    exit 2
    ;;
  # Key/certificate files (these extensions are almost exclusively used for sensitive material)
  *.pem | *.p12 | *.jks)
    echo "BLOCKED: Writing to key/certificate file '$BASENAME'. These files contain sensitive material." >&2
    exit 2
    ;;
esac

# --- Block sensitive path patterns ---
case "$FILE_PATH" in
  # .env files (with or without suffix)
  *.env | */.env | *.env.* | */.env.*)
    echo "BLOCKED: Writing to env file '$BASENAME'. These files may contain secrets." >&2
    exit 2
    ;;
  # Certificate and key directories
  */certs/* | */keys/*)
    echo "BLOCKED: Writing to certs/keys directory. These files contain sensitive material." >&2
    exit 2
    ;;
  # Production env patterns
  *prod.env*)
    echo "BLOCKED: Writing to production env file '$BASENAME'. These files contain secrets." >&2
    exit 2
    ;;
esac

exit 0
