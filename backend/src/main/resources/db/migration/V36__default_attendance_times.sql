-- ==========================================
-- Default Attendance Times (Hierarchical)
-- Migration: V36__default_attendance_times.sql
-- Feature: GitHub Issue #136
-- Date: 2026-03-21
-- ==========================================
-- Adds default_start_time / default_end_time columns to members, organizations, and tenants.
-- NULL means "inherit from parent level" in the 4-tier resolution chain:
-- Member → Organization hierarchy → Tenant → System default (09:00 / 18:00)

ALTER TABLE members ADD COLUMN default_start_time TIME;
ALTER TABLE members ADD COLUMN default_end_time TIME;

ALTER TABLE organizations ADD COLUMN default_start_time TIME;
ALTER TABLE organizations ADD COLUMN default_end_time TIME;

ALTER TABLE tenants ADD COLUMN default_start_time TIME;
ALTER TABLE tenants ADD COLUMN default_end_time TIME;

INSERT INTO system_default_settings (setting_key, setting_value) VALUES
  ('default_attendance_times', '{"startTime": "09:00", "endTime": "18:00"}')
ON CONFLICT (setting_key) DO NOTHING;
