-- ==========================================
-- System Default Settings Table
-- Migration: V24__system_default_patterns.sql
-- Feature: System > Tenant > Organization settings inheritance
-- Date: 2026-02-26
-- ==========================================
-- Stores system-wide default values for fiscal year and monthly period patterns.
-- These are raw values (not pattern IDs) since system defaults are tenant-independent.
-- Uses JSONB for flexible value structure.

CREATE TABLE system_default_settings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    setting_key     VARCHAR(64) NOT NULL UNIQUE,
    setting_value   JSONB NOT NULL,
    updated_by      UUID REFERENCES users(id),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Seed default fiscal year pattern: April 1st start (Japanese standard)
INSERT INTO system_default_settings (setting_key, setting_value) VALUES
    ('default_fiscal_year_pattern', '{"startMonth": 4, "startDay": 1}'),
    ('default_monthly_period_pattern', '{"startDay": 1}')
ON CONFLICT (setting_key) DO NOTHING;
