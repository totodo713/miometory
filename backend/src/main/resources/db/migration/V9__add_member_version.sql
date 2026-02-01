-- V9: Add version column to members table for optimistic locking
-- Description: Adds version column required by JdbcMemberRepository for concurrent update handling
-- Fix: Missing column that was expected by repository but not defined in V5__member_table.sql

-- ============================================================================
-- Add version column for optimistic locking
-- ============================================================================
ALTER TABLE members ADD COLUMN version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN members.version IS 'Optimistic locking version counter - incremented on each update';
