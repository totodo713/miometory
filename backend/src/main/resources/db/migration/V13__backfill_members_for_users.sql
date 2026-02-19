-- V13: Backfill members for users without member records
-- Description: Creates member records for users that signed up but had no corresponding
-- member row, then rebuilds projections for any orphaned events.
-- Fix: Signup only created users row, not members row, causing projections to be skipped.

-- ============================================================================
-- Backfill members for users without member records
-- Only runs when the default tenant and organization exist (skipped in test environments)
-- ============================================================================
INSERT INTO members (id, tenant_id, organization_id, email, display_name, manager_id, is_active, version, created_at, updated_at)
SELECT u.id,
       '550e8400-e29b-41d4-a716-446655440001'::UUID,
       '880e8400-e29b-41d4-a716-446655440001'::UUID,
       u.email,
       u.name,
       NULL,
       true,
       0,
       u.created_at,
       u.created_at
FROM users u
WHERE NOT EXISTS (SELECT 1 FROM members m WHERE m.id = u.id)
  AND u.account_status != 'deleted'
  AND EXISTS (SELECT 1 FROM tenant WHERE id = '550e8400-e29b-41d4-a716-446655440001'::UUID)
  AND EXISTS (SELECT 1 FROM organization WHERE id = '880e8400-e29b-41d4-a716-446655440001'::UUID);

-- ============================================================================
-- Rebuild work_log_entries_projection for orphaned events
-- ============================================================================
INSERT INTO work_log_entries_projection (id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by)
SELECT es.aggregate_id,
       (es.payload->>'memberId')::UUID,
       m.organization_id,
       (es.payload->>'projectId')::UUID,
       (es.payload->>'date')::DATE,
       (es.payload->>'hours')::DECIMAL,
       es.payload->>'comment',
       'DRAFT',
       (es.payload->>'enteredBy')::UUID
FROM event_store es
JOIN members m ON m.id = (es.payload->>'memberId')::UUID
WHERE es.event_type = 'WorkLogEntryCreated'
  AND es.aggregate_type = 'WorkLogEntry'
  AND NOT EXISTS (SELECT 1 FROM work_log_entries_projection p WHERE p.id = es.aggregate_id)
  AND NOT EXISTS (
      SELECT 1 FROM event_store es2
      WHERE es2.aggregate_id = es.aggregate_id
        AND es2.event_type = 'WorkLogEntryDeleted'
  )
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- Rebuild absences_projection for orphaned events
-- ============================================================================
INSERT INTO absences_projection (id, member_id, organization_id, absence_type, start_date, end_date, hours_per_day, notes, status)
SELECT es.aggregate_id,
       (es.payload->>'memberId')::UUID,
       m.organization_id,
       (es.payload->>'absenceType')::VARCHAR,
       (es.payload->>'date')::DATE,
       (es.payload->>'date')::DATE,
       (es.payload->>'hours')::DECIMAL,
       es.payload->>'reason',
       'DRAFT'
FROM event_store es
JOIN members m ON m.id = (es.payload->>'memberId')::UUID
WHERE es.event_type = 'AbsenceRecorded'
  AND es.aggregate_type = 'Absence'
  AND NOT EXISTS (SELECT 1 FROM absences_projection p WHERE p.id = es.aggregate_id)
  AND NOT EXISTS (
      SELECT 1 FROM event_store es2
      WHERE es2.aggregate_id = es.aggregate_id
        AND es2.event_type = 'AbsenceDeleted'
  )
ON CONFLICT (id) DO NOTHING;
