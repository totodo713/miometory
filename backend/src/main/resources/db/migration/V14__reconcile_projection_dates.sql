-- V14: Reconcile work_log_entries_projection with event store
-- Description: Fixes projection rows where work_date doesn't match the event store,
-- backfills missing projection entries, and applies status updates.
-- Root cause: Seed data ON CONFLICT didn't update work_date, and some UI-created
-- entries lost their projection rows due to previously-fixed bugs.

-- ============================================================================
-- 1. Delete misaligned and missing projection rows, then re-insert from event store
-- ============================================================================
-- Using DELETE + INSERT instead of UPDATE to avoid unique constraint collisions
-- when multiple rows shift dates in conflicting directions.

-- Delete projection rows whose work_date differs from the event store
DELETE FROM work_log_entries_projection p
USING event_store es
WHERE es.aggregate_id = p.id
  AND es.event_type = 'WorkLogEntryCreated'
  AND es.aggregate_type = 'WorkLogEntry'
  AND p.work_date != (es.payload->>'date')::date;

-- ============================================================================
-- 2. Insert/re-insert all entries from event store that are missing in projection
-- ============================================================================
INSERT INTO work_log_entries_projection
    (id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by)
SELECT
    es.aggregate_id,
    (es.payload->>'memberId')::uuid,
    COALESCE(m.organization_id, '880e8400-e29b-41d4-a716-446655440001'::uuid),
    (es.payload->>'projectId')::uuid,
    (es.payload->>'date')::date,
    (es.payload->>'hours')::numeric,
    es.payload->>'comment',
    COALESCE(
        (SELECT s.payload->>'toStatus'
         FROM event_store s
         WHERE s.aggregate_id = es.aggregate_id
           AND s.event_type = 'WorkLogEntryStatusChanged'
         ORDER BY s.version DESC LIMIT 1),
        'DRAFT'
    ),
    (es.payload->>'enteredBy')::uuid
FROM event_store es
LEFT JOIN members m ON m.id = (es.payload->>'memberId')::uuid
WHERE es.event_type = 'WorkLogEntryCreated'
  AND es.aggregate_type = 'WorkLogEntry'
  AND NOT EXISTS (SELECT 1 FROM work_log_entries_projection p WHERE p.id = es.aggregate_id)
  AND NOT EXISTS (
      SELECT 1 FROM event_store es2
      WHERE es2.aggregate_id = es.aggregate_id
        AND es2.event_type = 'WorkLogEntryDeleted'
  )
ON CONFLICT (member_id, project_id, work_date) DO UPDATE SET
    id = EXCLUDED.id,
    hours = EXCLUDED.hours,
    notes = EXCLUDED.notes,
    status = EXCLUDED.status,
    entered_by = EXCLUDED.entered_by,
    organization_id = EXCLUDED.organization_id,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- 3. Apply status updates from subsequent events
-- ============================================================================
UPDATE work_log_entries_projection p
SET status = latest.to_status,
    updated_at = CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT ON (aggregate_id)
        aggregate_id,
        payload->>'toStatus' as to_status
    FROM event_store
    WHERE event_type = 'WorkLogEntryStatusChanged'
      AND aggregate_type = 'WorkLogEntry'
    ORDER BY aggregate_id, version DESC
) latest
WHERE latest.aggregate_id = p.id
  AND p.status != latest.to_status;

-- ============================================================================
-- 4. Invalidate stale calendar and summary cache
-- ============================================================================
DELETE FROM monthly_calendar_projection;
DELETE FROM monthly_summary_projection;
