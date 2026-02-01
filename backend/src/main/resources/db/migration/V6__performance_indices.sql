-- V6: Performance Indices for Common Query Patterns
-- Description: Additional indices to optimize read performance for calendar, approval, and manager views
-- Feature: 002-work-log-entry (Performance Requirements SC-006, SC-007)

-- ============================================================================
-- Work Log Entries Projection: Manager View Queries
-- ============================================================================

-- Composite index for organization + date range queries (manager dashboard)
-- Covers: "Show all entries for my org in a date range"
CREATE INDEX idx_work_log_entries_org_date 
    ON work_log_entries_projection (organization_id, work_date);

-- Composite index for status + date (pending approvals view)
-- Covers: "Show all SUBMITTED entries in a date range"
CREATE INDEX idx_work_log_entries_status_date 
    ON work_log_entries_projection (status, work_date) 
    WHERE status IN ('SUBMITTED', 'DRAFT');

-- Covering index for calendar view queries (avoid table lookups)
-- Covers: "Get all entries for member in date range with hours and status"
-- Note: This index extends the basic (member_id, work_date) index from V4 by
-- including additional columns to enable index-only scans for calendar queries.
-- Both indices are retained: V4's simpler index is still optimal for non-calendar
-- queries that only need member+date lookup without the extra columns.
CREATE INDEX idx_work_log_entries_calendar_covering 
    ON work_log_entries_projection (member_id, work_date) 
    INCLUDE (project_id, hours, status, notes);

-- ============================================================================
-- Absences Projection: Date Range Queries
-- ============================================================================

-- Composite index for organization + date range (manager absence view)
CREATE INDEX idx_absences_org_dates 
    ON absences_projection (organization_id, start_date, end_date);

-- Index for overlapping absence detection queries
-- Covers: "Find absences that overlap with a given date range"
CREATE INDEX idx_absences_overlap 
    ON absences_projection (member_id, end_date, start_date);

-- ============================================================================
-- Monthly Approvals: Pending Review Queries
-- ============================================================================

-- Composite index for manager approval dashboard
-- Covers: "Show all SUBMITTED approvals for my org in a fiscal period"
CREATE INDEX idx_monthly_approvals_org_status_period 
    ON monthly_approvals_projection (organization_id, status, fiscal_year, fiscal_month) 
    WHERE status = 'SUBMITTED';

-- ============================================================================
-- Event Store: Time-Series Optimization with BRIN Index
-- ============================================================================
-- These indices optimize event replay and projection rebuilding operations.
-- Note: event_store uses 'created_at' for timestamps, not 'occurred_at'.

-- BRIN index for time-series queries on event store
-- BRIN is much smaller than B-tree for time-series data with natural ordering
-- Covers: "Replay events in a time range" for projection rebuilding
CREATE INDEX IF NOT EXISTS idx_event_store_created_brin 
    ON event_store USING BRIN (created_at) 
    WITH (pages_per_range = 32);

-- Composite index for aggregate type + created_at (event sourcing queries)
-- Covers: "Get all events of type X after timestamp Y"
CREATE INDEX IF NOT EXISTS idx_event_store_type_created 
    ON event_store (aggregate_type, created_at);

-- ============================================================================
-- Members Table: Hierarchy Queries
-- ============================================================================

-- Covering index for subordinate lookup with display info
-- Covers: "Get all active subordinates for a manager with their names"
CREATE INDEX idx_members_manager_covering 
    ON members (manager_id) 
    INCLUDE (display_name, email, is_active) 
    WHERE manager_id IS NOT NULL AND is_active = true;

-- ============================================================================
-- Comments on Performance Indices
-- ============================================================================

COMMENT ON INDEX idx_work_log_entries_org_date IS 'Optimizes manager dashboard: org-wide entries in date range';
COMMENT ON INDEX idx_work_log_entries_status_date IS 'Optimizes pending approvals: entries by status in date range';
COMMENT ON INDEX idx_work_log_entries_calendar_covering IS 'Covering index for calendar view: avoids table lookups for common columns';
COMMENT ON INDEX idx_absences_org_dates IS 'Optimizes manager view: org-wide absences in date range';
COMMENT ON INDEX idx_absences_overlap IS 'Optimizes overlap detection: find absences overlapping a date range';
COMMENT ON INDEX idx_monthly_approvals_org_status_period IS 'Optimizes approval dashboard: pending approvals by org and period';
COMMENT ON INDEX idx_event_store_created_brin IS 'BRIN index for time-series event queries on event_store table (smaller than B-tree)';
COMMENT ON INDEX idx_event_store_type_created IS 'Optimizes event replay by aggregate type and time on event_store table';
COMMENT ON INDEX idx_members_manager_covering IS 'Covering index for subordinate list with display info';
