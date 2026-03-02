---
name: db-debug
description: Debug Event Sourcing state by querying event_store and projection tables via PostgreSQL MCP
disable-model-invocation: true
---

# Event Sourcing Debugger

Debug aggregate state, event history, and projection consistency using the PostgreSQL MCP server.

## Usage

`/db-debug <aggregate-id>` — Show events and state for a specific aggregate
`/db-debug recent [N]` — Show the N most recent events (default: 20)
`/db-debug types` — List all aggregate types and event counts

## Prerequisites

The PostgreSQL MCP server (`postgres-mcp`) must be configured and the dev database must be running (via devcontainer).

## Workflow

### 1. Parse Arguments

- If argument is a UUID: treat as aggregate ID
- If argument is `recent` with optional count: show recent events
- If argument is `types`: show aggregate type summary
- If no argument: show usage help

### 2. Query Event Store

Use the PostgreSQL MCP `query` tool for all database operations.

**For a specific aggregate ID:**

```sql
-- Event history (time-ordered)
SELECT event_type, version, payload, created_at
FROM event_store
WHERE aggregate_id = '<uuid>'
ORDER BY version ASC;

-- Aggregate type
SELECT DISTINCT aggregate_type
FROM event_store
WHERE aggregate_id = '<uuid>'
LIMIT 1;

-- Latest snapshot (if exists)
SELECT version, state, created_at
FROM snapshot_store
WHERE aggregate_id = '<uuid>';
```

**For recent events:**

```sql
SELECT aggregate_type, aggregate_id, event_type, version, created_at
FROM event_store
ORDER BY created_at DESC
LIMIT <N>;
```

**For type summary:**

```sql
SELECT aggregate_type, COUNT(*) as event_count,
       COUNT(DISTINCT aggregate_id) as aggregate_count,
       MAX(created_at) as latest_event
FROM event_store
GROUP BY aggregate_type
ORDER BY event_count DESC;
```

### 3. Check Projection Consistency

Based on the aggregate_type, query the corresponding projection table.

> **Note**: This mapping must be updated when new Aggregate types or projection tables are added. Cross-check with migration files in `backend/src/main/resources/db/migration/`.

| Aggregate Type | Projection Table | Key Fields |
|---------------|-----------------|------------|
| WorkLogEntry | work_log_entry_* | member_id, entry_date, hours |
| Absence | absence_* | member_id, start_date, end_date |
| Tenant | tenant | code, name, status, version |
| Organization | organization | code, name, status, version |
| FiscalYearPattern | fiscal_year_pattern | start_month, start_day, version |
| MonthlyPeriodPattern | monthly_period_pattern | start_day, version |
| MonthlyApproval | daily_entry_approval | status |

Compare the `version` in the projection table with the max `version` in event_store for the aggregate. Report any discrepancy.

### 4. Output Format

```
## Event History: <aggregate_id>
Aggregate Type: <type>
Total Events: <count>
Current Version: <max_version>
Snapshot Version: <snapshot_version or "none">

| # | Event Type | Version | Timestamp |
|---|-----------|---------|-----------|
| 1 | Created   | 1       | 2024-...  |
| 2 | Updated   | 2       | 2024-...  |

### Projection Check
- Projection version: <version>
- Event store version: <version>
- Status: ✅ Consistent / ⚠️ Version mismatch (projection behind by N events)

### Latest Event Payload
<formatted JSON of the most recent event payload>
```
