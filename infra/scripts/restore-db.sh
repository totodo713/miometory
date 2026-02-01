#!/bin/bash
# Database restore script for Miometry
# Usage: ./restore-db.sh <backup_file>

set -euo pipefail

BACKUP_FILE="${1:-}"

if [ -z "${BACKUP_FILE}" ]; then
    echo "Usage: $0 <backup_file>"
    echo "Example: $0 /backup/daily/worklog_daily_20260128_020000.dump.gz"
    exit 1
fi

if [ ! -f "${BACKUP_FILE}" ]; then
    echo "Error: Backup file not found: ${BACKUP_FILE}"
    exit 1
fi

# Database connection
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-worklog}"
DB_USER="${DB_USER:-worklog}"

# Verify checksum if available
if [ -f "${BACKUP_FILE}.sha256" ]; then
    echo "Verifying backup integrity..."
    sha256sum -c "${BACKUP_FILE}.sha256" || { echo "Checksum verification failed!"; exit 1; }
    echo "Checksum verified."
fi

# Decompress if needed
if [[ "${BACKUP_FILE}" == *.gz ]]; then
    echo "Decompressing backup..."
    RESTORE_FILE="${BACKUP_FILE%.gz}"
    gunzip -c "${BACKUP_FILE}" > "${RESTORE_FILE}"
else
    RESTORE_FILE="${BACKUP_FILE}"
fi

echo ""
echo "=========================================="
echo "  WARNING: Database Restore Operation"
echo "=========================================="
echo ""
echo "This will OVERWRITE the existing database!"
echo ""
echo "Target Database: ${DB_NAME}"
echo "Host: ${DB_HOST}:${DB_PORT}"
echo "User: ${DB_USER}"
echo "Backup File: ${BACKUP_FILE}"
echo ""
read -p "Type 'yes' to continue: " CONFIRM

if [ "${CONFIRM}" != "yes" ]; then
    echo "Restore cancelled."
    # Cleanup decompressed file if we created it
    if [[ "${BACKUP_FILE}" == *.gz ]]; then
        rm -f "${RESTORE_FILE}"
    fi
    exit 0
fi

echo ""
echo "Starting restore at $(date)"

# Terminate existing connections
# Use psql variables with identifier quoting to prevent SQL injection
echo "Terminating existing connections..."
psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
    -v dbname="${DB_NAME}" \
    -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = :'dbname' AND pid <> pg_backend_pid();" \
    2>/dev/null || true

# Drop and recreate database
# Use psql variables with identifier quoting to prevent SQL injection
echo "Recreating database..."
psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
    -v dbname="${DB_NAME}" -v dbowner="${DB_USER}" \
    -c 'DROP DATABASE IF EXISTS :"dbname";'
psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
    -v dbname="${DB_NAME}" -v dbowner="${DB_USER}" \
    -c 'CREATE DATABASE :"dbname" OWNER :"dbowner";'

# Restore from backup
echo "Restoring data..."
pg_restore \
    -h "${DB_HOST}" \
    -p "${DB_PORT}" \
    -U "${DB_USER}" \
    -d "${DB_NAME}" \
    --no-owner \
    --no-privileges \
    -v \
    "${RESTORE_FILE}"

RESTORE_STATUS=$?

echo ""
echo "Restore complete at $(date)"

# Cleanup decompressed file if we created it
if [[ "${BACKUP_FILE}" == *.gz ]]; then
    rm -f "${RESTORE_FILE}"
fi

if [ ${RESTORE_STATUS} -eq 0 ]; then
    echo "SUCCESS: Database restored successfully."
    
    # Verify restore
    echo ""
    echo "Verification:"
    psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" \
        -c "SELECT 'Tables: ' || count(*)::text FROM information_schema.tables WHERE table_schema = 'public';"
else
    echo "WARNING: Restore completed with warnings or errors (exit code: ${RESTORE_STATUS})"
fi

exit ${RESTORE_STATUS}
