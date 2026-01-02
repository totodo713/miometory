-- V4: Work Log Entry System Tables
-- Description: Event sourcing tables and projections for work log entries, absences, and monthly approvals
-- Feature: 002-work-log-entry

-- ============================================================================
-- Event Sourcing: Work Log Events Table
-- ============================================================================
CREATE TABLE IF NOT EXISTS work_log_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL,
    CONSTRAINT uk_aggregate_version UNIQUE (aggregate_id, version)
);

CREATE INDEX idx_work_log_events_aggregate ON work_log_events (aggregate_id, version);
CREATE INDEX idx_work_log_events_type ON work_log_events (event_type);
CREATE INDEX idx_work_log_events_occurred ON work_log_events (occurred_at);

COMMENT ON TABLE work_log_events IS 'Event sourcing: Immutable stream of work log domain events';
COMMENT ON COLUMN work_log_events.aggregate_id IS 'UUID of the aggregate (WorkLogEntry, Absence, or MonthlyApproval)';
COMMENT ON COLUMN work_log_events.aggregate_type IS 'Type of aggregate: WorkLogEntry, Absence, MonthlyApproval';
COMMENT ON COLUMN work_log_events.event_type IS 'Event type: Created, Updated, Deleted, StatusChanged, etc.';
COMMENT ON COLUMN work_log_events.event_data IS 'Event payload as JSONB';
COMMENT ON COLUMN work_log_events.version IS 'Optimistic locking version (starts at 0)';

-- ============================================================================
-- Read Model: Work Log Entries Projection
-- ============================================================================
CREATE TABLE IF NOT EXISTS work_log_entries_projection (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES members(id),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    project_id UUID NOT NULL REFERENCES projects(id),
    work_date DATE NOT NULL,
    hours DECIMAL(4,2) NOT NULL CHECK (hours >= 0.25 AND hours <= 24.0 AND MOD(hours * 4, 1) = 0),
    notes TEXT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    entered_by UUID REFERENCES members(id),
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_work_log_entry_member_project_date ON work_log_entries_projection (member_id, project_id, work_date);
CREATE INDEX idx_work_log_entries_member_date ON work_log_entries_projection (member_id, work_date);
CREATE INDEX idx_work_log_entries_organization ON work_log_entries_projection (organization_id);
CREATE INDEX idx_work_log_entries_status ON work_log_entries_projection (status);
CREATE INDEX idx_work_log_entries_entered_by ON work_log_entries_projection (entered_by) WHERE entered_by IS NOT NULL;

COMMENT ON TABLE work_log_entries_projection IS 'Read model: Current state of work log entries';
COMMENT ON COLUMN work_log_entries_projection.hours IS 'Hours worked (0.25h increments, max 24h per entry)';
COMMENT ON COLUMN work_log_entries_projection.status IS 'DRAFT: Editable, SUBMITTED: Locked awaiting approval, APPROVED/REJECTED: Final state';
COMMENT ON COLUMN work_log_entries_projection.entered_by IS 'Manager who entered on behalf of member (proxy entry), NULL if self-entered';
COMMENT ON COLUMN work_log_entries_projection.version IS 'Optimistic locking version for concurrent updates';

-- ============================================================================
-- Read Model: Absences Projection
-- ============================================================================
CREATE TABLE IF NOT EXISTS absences_projection (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES members(id),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    absence_type VARCHAR(20) NOT NULL CHECK (absence_type IN ('PAID_LEAVE', 'SICK_LEAVE', 'SPECIAL_LEAVE', 'OTHER')),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL CHECK (end_date >= start_date),
    hours_per_day DECIMAL(4,2) NOT NULL CHECK (hours_per_day >= 0.25 AND hours_per_day <= 24.0 AND MOD(hours_per_day * 4, 1) = 0),
    notes TEXT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_absences_member_dates ON absences_projection (member_id, start_date, end_date);
CREATE INDEX idx_absences_organization ON absences_projection (organization_id);
CREATE INDEX idx_absences_status ON absences_projection (status);

COMMENT ON TABLE absences_projection IS 'Read model: Current state of absence records';
COMMENT ON COLUMN absences_projection.hours_per_day IS 'Hours per day for this absence (0.25h increments, max 24h)';
COMMENT ON COLUMN absences_projection.absence_type IS 'Type of absence: PAID_LEAVE, SICK_LEAVE, SPECIAL_LEAVE, OTHER';

-- ============================================================================
-- Read Model: Monthly Approvals Projection
-- ============================================================================
CREATE TABLE IF NOT EXISTS monthly_approvals_projection (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES members(id),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    fiscal_year INTEGER NOT NULL CHECK (fiscal_year >= 2000 AND fiscal_year <= 2100),
    fiscal_month INTEGER NOT NULL CHECK (fiscal_month >= 1 AND fiscal_month <= 12),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    submitted_at TIMESTAMP,
    reviewed_at TIMESTAMP,
    reviewed_by UUID REFERENCES members(id),
    rejection_reason TEXT,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_member_period UNIQUE (member_id, fiscal_year, fiscal_month),
    CONSTRAINT chk_submitted_at CHECK (status = 'PENDING' OR submitted_at IS NOT NULL),
    CONSTRAINT chk_reviewed CHECK ((status IN ('APPROVED', 'REJECTED') AND reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL) OR status NOT IN ('APPROVED', 'REJECTED'))
);

CREATE INDEX idx_monthly_approvals_member ON monthly_approvals_projection (member_id, fiscal_year, fiscal_month);
CREATE INDEX idx_monthly_approvals_organization ON monthly_approvals_projection (organization_id);
CREATE INDEX idx_monthly_approvals_status ON monthly_approvals_projection (status);
CREATE INDEX idx_monthly_approvals_reviewer ON monthly_approvals_projection (reviewed_by) WHERE reviewed_by IS NOT NULL;

COMMENT ON TABLE monthly_approvals_projection IS 'Read model: Monthly approval workflow state';
COMMENT ON COLUMN monthly_approvals_projection.fiscal_year IS 'Fiscal year (e.g., 2026)';
COMMENT ON COLUMN monthly_approvals_projection.fiscal_month IS 'Fiscal month (1-12, period 21st to 20th)';
COMMENT ON COLUMN monthly_approvals_projection.status IS 'PENDING: Not submitted, SUBMITTED: Awaiting review, APPROVED/REJECTED: Final';

-- ============================================================================
-- Read Model: Monthly Calendar Projection (Optimized for Calendar View)
-- ============================================================================
CREATE TABLE IF NOT EXISTS monthly_calendar_projection (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES members(id),
    fiscal_year INTEGER NOT NULL CHECK (fiscal_year >= 2000 AND fiscal_year <= 2100),
    fiscal_month INTEGER NOT NULL CHECK (fiscal_month >= 1 AND fiscal_month <= 12),
    calendar_data JSONB NOT NULL,
    total_hours DECIMAL(6,2) NOT NULL CHECK (total_hours >= 0),
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_member_month UNIQUE (member_id, fiscal_year, fiscal_month)
);

CREATE INDEX idx_calendar_member_period ON monthly_calendar_projection (member_id, fiscal_year, fiscal_month);

COMMENT ON TABLE monthly_calendar_projection IS 'Read model: Optimized for calendar view rendering (<1s load time for 30 entries)';
COMMENT ON COLUMN monthly_calendar_projection.calendar_data IS 'JSONB array of daily summaries: [{date, totalHours, entries: [{projectId, hours, status}]}]';
COMMENT ON COLUMN monthly_calendar_projection.total_hours IS 'Total hours for the month (all projects + absences)';

-- ============================================================================
-- Read Model: Monthly Summary Projection (Project Breakdown)
-- ============================================================================
CREATE TABLE IF NOT EXISTS monthly_summary_projection (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES members(id),
    fiscal_year INTEGER NOT NULL CHECK (fiscal_year >= 2000 AND fiscal_year <= 2100),
    fiscal_month INTEGER NOT NULL CHECK (fiscal_month >= 1 AND fiscal_month <= 12),
    project_summaries JSONB NOT NULL,
    total_hours DECIMAL(6,2) NOT NULL CHECK (total_hours >= 0),
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_member_month_summary UNIQUE (member_id, fiscal_year, fiscal_month)
);

CREATE INDEX idx_summary_member_period ON monthly_summary_projection (member_id, fiscal_year, fiscal_month);

COMMENT ON TABLE monthly_summary_projection IS 'Read model: Monthly project allocation breakdown';
COMMENT ON COLUMN monthly_summary_projection.project_summaries IS 'JSONB array: [{projectId, projectName, hours, percentage}]';
COMMENT ON COLUMN monthly_summary_projection.total_hours IS 'Total hours for the month (excluding absences)';
