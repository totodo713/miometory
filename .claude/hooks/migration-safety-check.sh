#!/usr/bin/env bash
# PreToolUse hook: warn or block destructive Flyway migration operations.
# Only applies to files matching db/migration/V*.sql pattern.
#
# Checks for:
#   - DROP TABLE / DROP COLUMN without IF EXISTS (blocked)
#   - TRUNCATE TABLE (blocked)
#   - DROP TABLE/COLUMN IF EXISTS (warned)
#   - RENAME column/table (warned)
#   - Column type changes (warned)
#   - ALTER on known large tables (warned)
#
# Exit codes:
#   0 = allow (not a migration file, or safe operations only)
#   2 = block (destructive operation detected without safeguards)
#
# Fail-open: if JSON parsing fails, exit 0 so normal operations are not disrupted.

set -euo pipefail

# Parse PreToolUse JSON from stdin
read -r INPUT 2>/dev/null || exit 0

# Delegate all logic to Python for reliable regex handling
python3 -c "
import json, sys, re

data = json.loads(sys.argv[1])
tool_input = data.get('tool_input', {})
file_path = tool_input.get('file_path', '')

# Only check Flyway migration files
if not re.search(r'/db/migration/V\d+__.*\.sql$', file_path):
    sys.exit(0)

# For Write tool: content field; for Edit tool: new_string field
content = tool_input.get('content', '') or tool_input.get('new_string', '')
if not content:
    sys.exit(0)

upper = content.upper()
warnings = []
blocks = []

# --- BLOCK: DROP TABLE without IF EXISTS ---
# Match DROP TABLE that is NOT followed by IF EXISTS
for m in re.finditer(r'DROP\s+TABLE\s+', upper):
    after = upper[m.end():]
    if not after.startswith('IF EXISTS') and not after.startswith('IF\n'):
        blocks.append(
            'DROP TABLE without IF EXISTS detected. This is irreversible and risks data loss.\n'
            '  Fix: Use \"DROP TABLE IF EXISTS <table>\" or reconsider if this is truly needed.'
        )
        break

# --- BLOCK: TRUNCATE TABLE ---
if re.search(r'TRUNCATE\s+TABLE', upper):
    blocks.append(
        'TRUNCATE TABLE detected. This permanently deletes all data from the table.\n'
        '  Fix: Use DELETE with WHERE clause if you need to remove specific rows.'
    )

# --- BLOCK: ALTER TABLE ... DROP COLUMN without IF EXISTS ---
for m in re.finditer(r'DROP\s+COLUMN\s+', upper):
    after = upper[m.end():]
    if not after.startswith('IF EXISTS') and not after.startswith('IF\n'):
        blocks.append(
            'DROP COLUMN without IF EXISTS detected. This is irreversible.\n'
            '  Fix: Use \"ALTER TABLE ... DROP COLUMN IF EXISTS <column>\" or migrate data first.'
        )
        break

# If any blocks found, report and exit 2
if blocks:
    for b in blocks:
        print(f'BLOCKED: {b}', file=sys.stderr)
    sys.exit(2)

# --- WARN: DROP TABLE IF EXISTS ---
if re.search(r'DROP\s+TABLE\s+IF\s+EXISTS', upper):
    warnings.append('Migration drops a table. Ensure this is intentional and data has been migrated.')

# --- WARN: DROP COLUMN IF EXISTS ---
if re.search(r'DROP\s+COLUMN\s+IF\s+EXISTS', upper):
    warnings.append('Migration drops a column. Ensure application code no longer references this column.')

# --- WARN: RENAME operations ---
if re.search(r'ALTER\s+TABLE.*RENAME\s+(COLUMN|TO)', upper):
    warnings.append('Migration renames a table or column. Ensure all application code is updated.')

# --- WARN: Column type changes ---
if re.search(r'ALTER\s+TABLE.*ALTER\s+COLUMN.*TYPE', upper):
    warnings.append('Migration changes a column type. This may cause data loss if types are incompatible.')

# --- WARN: Large table operations ---
large_tables = ['EVENT_STORE', 'WORK_LOG_ENTRIES', 'AUDIT_LOG', 'DOMAIN_EVENTS', 'SNAPSHOT_STORE']
for table in large_tables:
    if re.search(rf'ALTER\s+TABLE\s+.*{table}', upper):
        warnings.append(
            f'Migration modifies \"{table.lower()}\" which may be a large table. Consider:\n'
            '  - Adding indexes CONCURRENTLY (CREATE INDEX CONCURRENTLY)\n'
            '  - Running during low-traffic periods\n'
            '  - Testing with production-scale data volumes'
        )
        break

for w in warnings:
    print(f'WARNING: {w}', file=sys.stderr)

sys.exit(0)
" "$INPUT"

# Fail-open: if Python fails, allow the operation
exit 0
