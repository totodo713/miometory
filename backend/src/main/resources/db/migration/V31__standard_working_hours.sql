-- ==========================================
-- Standard Daily Working Hours
-- Migration: V31__standard_working_hours.sql
-- Feature: overtime-feature-phase-1 (GitHub Issue #89)
-- Date: 2026-03-03
-- ==========================================
-- Adds standard_daily_hours column to members, organization, and tenant tables.
-- NULL means "inherit from parent level" in the 4-tier resolution chain:
-- Member → Organization hierarchy → Tenant → System default
-- CHECK constraint: 0.25h to 24.00h (15-minute granularity)
-- System default seed: 8.0h (standard workday)

ALTER TABLE members ADD COLUMN standard_daily_hours DECIMAL(4,2)
  CHECK (standard_daily_hours IS NULL OR (standard_daily_hours >= 0.25 AND standard_daily_hours <= 24.00));

ALTER TABLE organization ADD COLUMN standard_daily_hours DECIMAL(4,2)
  CHECK (standard_daily_hours IS NULL OR (standard_daily_hours >= 0.25 AND standard_daily_hours <= 24.00));

ALTER TABLE tenant ADD COLUMN standard_daily_hours DECIMAL(4,2)
  CHECK (standard_daily_hours IS NULL OR (standard_daily_hours >= 0.25 AND standard_daily_hours <= 24.00));

INSERT INTO system_default_settings (setting_key, setting_value) VALUES
  ('standard_daily_hours', '{"hours": 8.0}')
ON CONFLICT (setting_key) DO NOTHING;
