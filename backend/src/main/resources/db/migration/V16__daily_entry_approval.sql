-- ==========================================
-- Daily Entry Approval Table
-- Migration: V16__daily_entry_approval.sql
-- Feature: 015-admin-management
-- Date: 2026-02-21
-- ==========================================
-- Tracks a supervisor's approval decision on an individual work log entry.
-- Separate from the monthly approval workflow.

CREATE TABLE daily_entry_approvals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_log_entry_id UUID NOT NULL REFERENCES work_log_entries_projection(id),
    member_id UUID NOT NULL REFERENCES members(id),
    supervisor_id UUID NOT NULL REFERENCES members(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('APPROVED', 'REJECTED', 'RECALLED')),
    comment VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_rejected_comment CHECK (status != 'REJECTED' OR comment IS NOT NULL)
);

-- Index for looking up approval by entry
CREATE INDEX idx_daily_approval_entry ON daily_entry_approvals(work_log_entry_id);

-- Index for supervisor's approval queue
CREATE INDEX idx_daily_approval_member ON daily_entry_approvals(member_id);

-- Index for supervisor's own decisions
CREATE INDEX idx_daily_approval_supervisor ON daily_entry_approvals(supervisor_id);

-- Index for filtering by status
CREATE INDEX idx_daily_approval_status ON daily_entry_approvals(status);

-- Enforce at most one active (non-RECALLED) approval per entry
CREATE UNIQUE INDEX idx_daily_approval_active
    ON daily_entry_approvals(work_log_entry_id)
    WHERE status != 'RECALLED';
