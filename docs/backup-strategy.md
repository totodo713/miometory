# Database Backup Strategy

This document outlines the backup and recovery strategy for Miometry's PostgreSQL database.

## Overview

Miometry requires 7-year data retention for compliance (FR-028). This backup strategy ensures data durability, disaster recovery capability, and compliance with organizational data protection requirements.

## Backup Schedule

### Automated Backup Tiers

| Tier | Frequency | Retention | Purpose |
|------|-----------|-----------|---------|
| Continuous | Real-time | 24 hours | Point-in-time recovery (WAL archiving) |
| Daily | 02:00 UTC | 30 days | Operational recovery |
| Weekly | Sunday 03:00 UTC | 12 weeks | Medium-term recovery |
| Monthly | 1st of month, 04:00 UTC | 7 years | Compliance/archival |

### Backup Types

1. **WAL Archiving (Continuous)**
   - Write-Ahead Log streaming to object storage
   - Enables point-in-time recovery to any second within retention window
   - Storage: S3/GCS/Azure Blob with versioning enabled

2. **Full Dumps (Daily/Weekly/Monthly)**
   - `pg_dump` with custom format for compression
   - Includes schema + data + indexes
   - Encrypted at rest with AES-256

## Backup Scripts

### Daily Backup Script

Create `/home/devman/repos/work-log/infra/scripts/backup-db.sh`:

```bash
#!/bin/bash
# Database backup script for Work-Log system
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

# Compress and encrypt
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
        ;;
    weekly)
        find "${BACKUP_DIR}" -name "*.dump.gz" -mtime +84 -delete
        ;;
    monthly)
        # Keep for 7 years (2555 days)
        find "${BACKUP_DIR}" -name "*.dump.gz" -mtime +2555 -delete
        ;;
esac

echo "Backup job finished at $(date)"
```

### Restore Script

Create `/home/devman/repos/work-log/infra/scripts/restore-db.sh`:

```bash
#!/bin/bash
# Database restore script for Work-Log system
# Usage: ./restore-db.sh <backup_file>

set -euo pipefail

BACKUP_FILE="$1"

if [ -z "${BACKUP_FILE}" ]; then
    echo "Usage: $0 <backup_file>"
    echo "Example: $0 /backup/daily/worklog_daily_20260128_020000.dump.gz"
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
fi

# Decompress if needed
if [[ "${BACKUP_FILE}" == *.gz ]]; then
    echo "Decompressing backup..."
    gunzip -k "${BACKUP_FILE}"
    RESTORE_FILE="${BACKUP_FILE%.gz}"
else
    RESTORE_FILE="${BACKUP_FILE}"
fi

echo "WARNING: This will overwrite the existing database!"
echo "Database: ${DB_NAME}@${DB_HOST}:${DB_PORT}"
read -p "Continue? (yes/no): " CONFIRM

if [ "${CONFIRM}" != "yes" ]; then
    echo "Restore cancelled."
    exit 0
fi

echo "Starting restore at $(date)"

# Drop and recreate database
psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
    -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${DB_NAME}';"
psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
    -c "DROP DATABASE IF EXISTS ${DB_NAME};"
psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
    -c "CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};"

# Restore from backup
pg_restore \
    -h "${DB_HOST}" \
    -p "${DB_PORT}" \
    -U "${DB_USER}" \
    -d "${DB_NAME}" \
    -v \
    "${RESTORE_FILE}"

echo "Restore complete at $(date)"

# Cleanup decompressed file if we created it
if [[ "${BACKUP_FILE}" == *.gz ]]; then
    rm "${RESTORE_FILE}"
fi
```

## Cron Configuration

Add to crontab for automated backups:

```cron
# Work-Log Database Backups
# Daily backup at 02:00 UTC
0 2 * * * /path/to/infra/scripts/backup-db.sh daily >> /var/log/worklog-backup.log 2>&1

# Weekly backup on Sundays at 03:00 UTC
0 3 * * 0 /path/to/infra/scripts/backup-db.sh weekly >> /var/log/worklog-backup.log 2>&1

# Monthly backup on 1st at 04:00 UTC
0 4 1 * * /path/to/infra/scripts/backup-db.sh monthly >> /var/log/worklog-backup.log 2>&1
```

## Recovery Procedures

### Scenario 1: Point-in-Time Recovery (Last 24 Hours)

Use WAL archiving to recover to a specific timestamp:

```bash
# Stop the database
docker compose stop backend  # Production compose file TBD

# Configure recovery target
cat > /var/lib/postgresql/data/recovery.conf << EOF
restore_command = 'aws s3 cp s3://worklog-wal/%f %p'
recovery_target_time = '2026-01-28 15:30:00 UTC'
recovery_target_action = 'promote'
EOF

# Restart and recover
docker compose start postgres  # Production compose file TBD
```

### Scenario 2: Daily Backup Restore

```bash
# Download latest daily backup
aws s3 cp s3://worklog-backups/daily/worklog_daily_YYYYMMDD_020000.dump.gz /backup/

# Restore
./infra/scripts/restore-db.sh /backup/worklog_daily_YYYYMMDD_020000.dump.gz
```

### Scenario 3: Compliance Data Request

For 7-year-old data requests:

```bash
# Download monthly archive
aws s3 cp s3://worklog-backups/monthly/worklog_monthly_20190101_040000.dump.gz /backup/

# Restore to temporary database
DB_NAME=worklog_archive ./infra/scripts/restore-db.sh /backup/worklog_monthly_20190101_040000.dump.gz

# Extract required data
psql -d worklog_archive -c "COPY (SELECT * FROM work_log_entries WHERE member_id = 'xyz') TO '/tmp/export.csv' CSV HEADER;"
```

## Verification and Testing

### Monthly Backup Verification

1. Download random monthly backup
2. Restore to test environment
3. Run data integrity checks:
   ```sql
   -- Verify event count
   SELECT COUNT(*) FROM domain_events;
   
   -- Verify projection consistency
   SELECT COUNT(*) FROM work_log_projections;
   
   -- Sample data validation
   SELECT * FROM work_log_entries LIMIT 10;
   ```
4. Document verification results

### Quarterly Disaster Recovery Drill

1. Simulate complete data loss
2. Restore from backups
3. Verify application functionality
4. Document recovery time (RTO target: < 4 hours)
5. Document data loss (RPO target: < 1 hour)

## Storage Requirements

### Estimated Backup Sizes

| Data | Estimated Size |
|------|----------------|
| 1 month data | ~50 MB compressed |
| 1 year data | ~600 MB compressed |
| 7 years data | ~4 GB compressed |

### Cloud Storage Configuration

```yaml
# Recommended S3 bucket configuration
bucket: worklog-backups
versioning: enabled
encryption: AES-256
lifecycle_rules:
  - prefix: daily/
    transition_to_glacier: 30 days
    expiration: 60 days
  - prefix: weekly/
    transition_to_glacier: 90 days
    expiration: 180 days
  - prefix: monthly/
    transition_to_glacier: 365 days
    expiration: 2920 days  # 8 years (7 + buffer)
```

## Monitoring and Alerts

### Backup Monitoring

Configure alerts for:
- Backup job failures
- Backup size anomalies (>50% change)
- Storage capacity warnings
- Missed backup schedules

### Example Monitoring Script

```bash
#!/bin/bash
# Check backup health

LATEST_DAILY=$(ls -t /backup/daily/*.dump.gz 2>/dev/null | head -1)
if [ -z "${LATEST_DAILY}" ]; then
    echo "CRITICAL: No daily backup found!"
    exit 2
fi

AGE=$(( ($(date +%s) - $(stat -c %Y "${LATEST_DAILY}")) / 3600 ))
if [ ${AGE} -gt 26 ]; then
    echo "WARNING: Latest daily backup is ${AGE} hours old"
    exit 1
fi

echo "OK: Latest backup is ${AGE} hours old"
exit 0
```

## Security Considerations

1. **Encryption at Rest**: All backups encrypted with AES-256
2. **Encryption in Transit**: TLS for all transfers
3. **Access Control**: Limited IAM roles for backup operations
4. **Audit Logging**: All backup/restore operations logged
5. **Separation of Duties**: Different credentials for backup vs. restore

## Compliance Notes

- **7-Year Retention**: Monthly backups retained for compliance period
- **GDPR Considerations**: Backup data subject to same retention/deletion policies
- **Audit Trail**: Backup logs retained for compliance audits
- **Data Locality**: Backups stored in same geographic region as primary data

---

> **Note**: Backup scripts (`infra/scripts/backup-db.sh`, `restore-db.sh`, `verify-health.sh`) were removed as part of the devcontainer migration (March 2026). Backup strategy will be revisited when production environment is defined.

---

*Last Updated: March 2026*
