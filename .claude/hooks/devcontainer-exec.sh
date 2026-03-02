#!/usr/bin/env bash
# Shared wrapper: delegates command execution to devcontainer when available.
# Falls back to local execution when the container is not running.
#
# Usage:
#   devcontainer-exec.sh [--workdir DIR] [--background] -- COMMAND [ARGS...]
#
# Options:
#   --workdir DIR    Set working directory (converted to container path)
#   --background     Run command in the background (stdout/stderr suppressed)
#
# Path conversion:
#   Host:      /home/devman/repos/miometory/...
#   Container: /workspaces/miometory/...

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CONTAINER_ROOT="/workspaces/miometory"
COMPOSE_FILE="$PROJECT_ROOT/.devcontainer/docker-compose.yml"

# --- Parse options ---
WORKDIR=""
BACKGROUND=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --workdir)
      WORKDIR="$2"
      shift 2
      ;;
    --background)
      BACKGROUND=true
      shift
      ;;
    --)
      shift
      break
      ;;
    *)
      break
      ;;
  esac
done

# Remaining args are the command to execute
CMD=("$@")
[[ ${#CMD[@]} -eq 0 ]] && exit 0

# --- Convert host path to container path ---
convert_path() {
  local path="$1"
  echo "${path/$PROJECT_ROOT/$CONTAINER_ROOT}"
}

# Convert paths in command arguments
convert_cmd_args() {
  local converted=()
  for arg in "${CMD[@]}"; do
    if [[ "$arg" == "$PROJECT_ROOT"* ]]; then
      converted+=("$(convert_path "$arg")")
    else
      converted+=("$arg")
    fi
  done
  CMD=("${converted[@]}")
}

# --- Detect running devcontainer ---
CONTAINER_ID=""
if command -v docker &>/dev/null; then
  CONTAINER_ID=$(docker compose -f "$COMPOSE_FILE" ps -q app 2>/dev/null) || true
  # Verify the container is actually running
  if [[ -n "$CONTAINER_ID" ]]; then
    RUNNING=$(docker inspect -f '{{.State.Running}}' "$CONTAINER_ID" 2>/dev/null) || true
    if [[ "$RUNNING" != "true" ]]; then
      CONTAINER_ID=""
    fi
  fi
fi

# --- Execute ---
if [[ -n "$CONTAINER_ID" ]]; then
  # Container is running: execute inside devcontainer
  convert_cmd_args

  EXEC_ARGS=(-u vscode)
  if [[ -n "$WORKDIR" ]]; then
    EXEC_ARGS+=(-w "$(convert_path "$WORKDIR")")
  fi

  if [[ "$BACKGROUND" == true ]]; then
    docker exec "${EXEC_ARGS[@]}" "$CONTAINER_ID" "${CMD[@]}" >/dev/null 2>&1 &
  else
    docker exec "${EXEC_ARGS[@]}" "$CONTAINER_ID" "${CMD[@]}"
  fi
else
  # Container not running: fall back to local execution
  if [[ -n "$WORKDIR" ]]; then
    cd "$WORKDIR"
  fi

  if [[ "$BACKGROUND" == true ]]; then
    "${CMD[@]}" >/dev/null 2>&1 &
  else
    "${CMD[@]}"
  fi
fi
