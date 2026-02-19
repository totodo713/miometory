-- V14: Reconcile work_log_entries_projection with event store
-- Description: Fixes projection rows where work_date doesn't match the event store,
-- backfills missing projection entries, and applies status updates.
-- Root cause: Seed data ON CONFLICT didn't update work_date, and some UI-created
-- entries lost their projection rows due to previously-fixed bugs.

-- ============================================================================
-- 1. Fix misaligned projection dates
-- Update projection rows where work_date differs from the event store payload date
-- ============================================================================
UPDATE work_log_entries_projection p
SET work_date = (es.payload->>'date')::date,
    updated_at = CURRENT_TIMESTAMP
FROM event_store es
WHERE es.aggregate_id = p.id
  AND es.event_type = 'WorkLogEntryCreated'
  AND p.work_date != (es.payload->>'date')::date;

-- ============================================================================
-- 2. Backfill missing projection entries
-- Re-insert entries that exist in event store but not in projection
-- ============================================================================
INSERT INTO work_log_entries_projection (id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by)
SELECT
    es.aggregate_id,
    (es.payload->>'memberId')::uuid,
    COALESCE(m.organization_id, '880e8400-e29b-41d4-a716-446655440001'::uuid),
    (es.payload->>'projectId')::uuid,
    (es.payload->>'date')::date,
    (es.payload->>'hours')::numeric,
    es.payload->>'comment',
    'DRAFT',
    (es.payload->>'enteredBy')::uuid
FROM event_store es
LEFT JOIN members m ON m.id = (es.payload->>'memberId')::uuid
WHERE es.event_type = 'WorkLogEntryCreated'
  AND es.aggregate_type = 'WorkLogEntry'
  AND NOT EXISTS (SELECT 1 FROM work_log_entries_projection p WHERE p.id = es.aggregate_id)
  -- Exclude deleted entries
  AND NOT EXISTS (
      SELECT 1 FROM event_store es2
      WHERE es2.aggregate_id = es.aggregate_id
        AND es2.event_type = 'WorkLogEntryDeleted'
  )
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 3. Apply status updates from subsequent events
-- Set projection status to the latest WorkLogEntryStatusChanged event
-- ============================================================================
UPDATE work_log_entries_projection p
SET status = es.payload->>'toStatus',
    updated_at = CURRENT_TIMESTAMP
FROM event_store es
WHERE es.aggregate_id = p.id
  AND es.event_type = 'WorkLogEntryStatusChanged'
  AND es.version = (SELECT MAX(version) FROM event_store WHERE aggregate_id = p.id AND event_type = 'WorkLogEntryStatusChanged');

-- ============================================================================
-- 4. Invalidate stale calendar and summary cache
-- Forces recalculation on next API request
-- ============================================================================
DELETE FROM monthly_calendar_projection;
DELETE FROM monthly_summary_projection;
