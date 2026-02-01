#!/bin/bash
# Database backup script for Miometry
# Usage: ./backup-db.sh [daily|weekly|monthly]

set -euo pipefail

BACKUP_TYPE="${1:-daily}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backup/${BACKUP_TYPE}"
BACKUP_FILE="${BACKUP_DIR}/worklog_${BACKUP_TYPE}_${TIMESTAMP}.dump"

# Database connection (use environment variables in production)
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-worklog}"
DB_USER="${DB_USER:-worklog}"

# Create backup directory
mkdir -p "${BACKUP_DIR}"

# Perform backup
echo "Starting ${BACKUP_TYPE} backup at $(date)"
pg_dump \
    -h "${DB_HOST}" \
    -p "${DB_PORT}" \
    -U "${DB_USER}" \
    -d "${DB_NAME}" \
    -Fc \
    -f "${BACKUP_FILE}"

# Compress backup file
gzip "${BACKUP_FILE}"
FINAL_FILE="${BACKUP_FILE}.gz"

# Calculate checksum
sha256sum "${FINAL_FILE}" > "${FINAL_FILE}.sha256"

echo "Backup complete: ${FINAL_FILE}"
echo "Size: $(du -h "${FINAL_FILE}" | cut -f1)"

# Upload to cloud storage (uncomment for production)
# aws s3 cp "${FINAL_FILE}" "s3://worklog-backups/${BACKUP_TYPE}/"
# aws s3 cp "${FINAL_FILE}.sha256" "s3://worklog-backups/${BACKUP_TYPE}/"

# Cleanup old backups based on retention policy
case "${BACKUP_TYPE}" in
    daily)
        find "${BACKUP_DIR}" -name "*.dump.gz" -mtime +30 -delete
        find "${BACKUP_DIR}" -name "*.dump.gz.sha256" -mtime +30 -delete
        ;;
    weekly)
        find "${BACKUP_DIR}" -name "*.dump.gz" -mtime +84 -delete
        find "${BACKUP_DIR}" -name "*.dump.gz.sha256" -mtime +84 -delete
        ;;
    monthly)
        # Keep for 7 years (2555 days)
        find "${BACKUP_DIR}" -name "*.dump.gz" -mtime +2555 -delete
        find "${BACKUP_DIR}" -name "*.dump.gz.sha256" -mtime +2555 -delete
        ;;
esac

echo "Backup job finished at $(date)"
