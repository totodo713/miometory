-- V15: Create daily_rejection_log table for tracking per-day rejections
-- Description: Lightweight projection table for storing daily rejection metadata.
-- Used for calendar indicators and rejection reason display in the daily workflow.

CREATE TABLE daily_rejection_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id UUID NOT NULL,
    work_date DATE NOT NULL,
    rejected_by UUID NOT NULL,
    rejection_reason TEXT NOT NULL,
    affected_entry_ids UUID[] NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_daily_rejection_member_date UNIQUE (member_id, work_date)
);

CREATE INDEX idx_daily_rejection_member_date ON daily_rejection_log (member_id, work_date);
