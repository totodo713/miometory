-- ==========================================
-- Tenant Default Pattern Columns
-- Migration: V25__tenant_default_patterns.sql
-- Feature: System > Tenant > Organization settings inheritance
-- Date: 2026-02-26
-- ==========================================
-- Adds optional default pattern references to tenant table.
-- NULL means fall through to system defaults.

ALTER TABLE tenant ADD COLUMN default_fiscal_year_pattern_id UUID REFERENCES fiscal_year_pattern(id);
ALTER TABLE tenant ADD COLUMN default_monthly_period_pattern_id UUID REFERENCES monthly_period_pattern(id);

-- Rollback:
-- ALTER TABLE tenant DROP COLUMN default_fiscal_year_pattern_id;
-- ALTER TABLE tenant DROP COLUMN default_monthly_period_pattern_id;
