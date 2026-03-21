-- ============================================================================
-- V34: Rename tables to plural + thesaurus alignment
-- ============================================================================
-- All ALTER TABLE RENAME / ALTER INDEX RENAME operations are metadata-only
-- in PostgreSQL (instant, no table rewrite or data movement).
-- FK references auto-track table renames; index/constraint names do NOT.
-- Children (FK dependents) are renamed before parents to avoid transient
-- name collisions if any cross-table constraints existed with clashing names.
-- ============================================================================

-- ============================
-- 1. DROP deprecated audit_log (V2)
-- ============================
DROP TABLE IF EXISTS audit_log;

-- ============================
-- 2. RENAME TABLES (children first for FK ordering)
-- ============================

-- holiday_calendar_entry → holiday_calendar_rules (child of holiday_calendar)
ALTER TABLE holiday_calendar_entry RENAME TO holiday_calendar_rules;

-- holiday_calendar_entry_preset → holiday_calendar_rule_presets (child of holiday_calendar_preset)
ALTER TABLE holiday_calendar_entry_preset RENAME TO holiday_calendar_rule_presets;

-- holiday_calendar → holiday_calendars
ALTER TABLE holiday_calendar RENAME TO holiday_calendars;

-- holiday_calendar_preset → holiday_calendar_presets
ALTER TABLE holiday_calendar_preset RENAME TO holiday_calendar_presets;

-- fiscal_year_pattern → fiscal_year_rules
ALTER TABLE fiscal_year_pattern RENAME TO fiscal_year_rules;

-- fiscal_year_pattern_preset → fiscal_year_rule_presets
ALTER TABLE fiscal_year_pattern_preset RENAME TO fiscal_year_rule_presets;

-- monthly_period_pattern → monthly_period_rules
ALTER TABLE monthly_period_pattern RENAME TO monthly_period_rules;

-- monthly_period_pattern_preset → monthly_period_rule_presets
ALTER TABLE monthly_period_pattern_preset RENAME TO monthly_period_rule_presets;

-- daily_rejection_log → daily_rejection_logs
ALTER TABLE daily_rejection_log RENAME TO daily_rejection_logs;

-- organization → organizations (child of tenant)
ALTER TABLE organization RENAME TO organizations;

-- tenant → tenants
ALTER TABLE tenant RENAME TO tenants;

-- ============================
-- 3. RENAME INDEXES
-- ============================

-- From V2: tenant indexes
ALTER INDEX idx_tenant_code RENAME TO idx_tenants_code;
ALTER INDEX idx_tenant_status RENAME TO idx_tenants_status;

-- From V2: organization indexes
ALTER INDEX idx_organization_tenant_id RENAME TO idx_organizations_tenant_id;
ALTER INDEX idx_organization_parent_id RENAME TO idx_organizations_parent_id;
ALTER INDEX idx_organization_status RENAME TO idx_organizations_status;

-- From V2: fiscal_year_pattern indexes
ALTER INDEX idx_fiscal_year_pattern_tenant_id RENAME TO idx_fiscal_year_rules_tenant_id;
ALTER INDEX idx_fiscal_year_pattern_organization_id RENAME TO idx_fiscal_year_rules_organization_id;

-- From V2: monthly_period_pattern indexes
ALTER INDEX idx_monthly_period_pattern_tenant_id RENAME TO idx_monthly_period_rules_tenant_id;
ALTER INDEX idx_monthly_period_pattern_organization_id RENAME TO idx_monthly_period_rules_organization_id;

-- From V3: organization pattern ref indexes
ALTER INDEX idx_organization_fiscal_year_pattern RENAME TO idx_organizations_fiscal_year_rule;
ALTER INDEX idx_organization_monthly_period_pattern RENAME TO idx_organizations_monthly_period_rule;

-- From V23: holiday calendar indexes
ALTER INDEX idx_hc_tenant_id RENAME TO idx_holiday_calendars_tenant_id;
ALTER INDEX idx_hce_calendar_id RENAME TO idx_holiday_calendar_rules_calendar_id;
ALTER INDEX idx_hcep_calendar_id RENAME TO idx_holiday_calendar_rule_presets_calendar_id;

-- ============================
-- 4. RENAME CONSTRAINTS
-- ============================

-- From V2: organizations constraints
ALTER TABLE organizations RENAME CONSTRAINT uq_organization_tenant_code TO uq_organizations_tenant_code;
ALTER TABLE organizations RENAME CONSTRAINT chk_organization_level TO chk_organizations_level;

-- From V2: fiscal_year_rules constraints
ALTER TABLE fiscal_year_rules RENAME CONSTRAINT chk_fiscal_year_start_month TO chk_fiscal_year_rules_start_month;
ALTER TABLE fiscal_year_rules RENAME CONSTRAINT chk_fiscal_year_start_day TO chk_fiscal_year_rules_start_day;

-- From V2: monthly_period_rules constraints
ALTER TABLE monthly_period_rules RENAME CONSTRAINT chk_monthly_period_start_day TO chk_monthly_period_rules_start_day;

-- From V23: fiscal_year_rule_presets constraints
ALTER TABLE fiscal_year_rule_presets RENAME CONSTRAINT chk_fyp_preset_start_month TO chk_fyr_preset_start_month;
ALTER TABLE fiscal_year_rule_presets RENAME CONSTRAINT chk_fyp_preset_start_day TO chk_fyr_preset_start_day;
ALTER TABLE fiscal_year_rule_presets RENAME CONSTRAINT uq_fyp_preset_name TO uq_fyr_preset_name;

-- From V23: monthly_period_rule_presets constraints
ALTER TABLE monthly_period_rule_presets RENAME CONSTRAINT chk_mpp_preset_start_day TO chk_mpr_preset_start_day;
ALTER TABLE monthly_period_rule_presets RENAME CONSTRAINT uq_mpp_preset_name TO uq_mpr_preset_name;

-- From V23: holiday_calendar_presets constraints
ALTER TABLE holiday_calendar_presets RENAME CONSTRAINT uq_hc_preset_name TO uq_holiday_calendar_presets_name;

-- From V23: holiday_calendars constraints
ALTER TABLE holiday_calendars RENAME CONSTRAINT uq_hc_tenant_name TO uq_holiday_calendars_tenant_name;

-- From V23: holiday_calendar_rules constraints (7 total)
ALTER TABLE holiday_calendar_rules RENAME CONSTRAINT chk_hce_entry_type TO chk_hcr_entry_type;
ALTER TABLE holiday_calendar_rules RENAME CONSTRAINT chk_hce_month TO chk_hcr_month;
ALTER TABLE holiday_calendar_rules RENAME CONSTRAINT chk_hce_day TO chk_hcr_day;
ALTER TABLE holiday_calendar_rules RENAME CONSTRAINT chk_hce_nth TO chk_hcr_nth;
ALTER TABLE holiday_calendar_rules RENAME CONSTRAINT chk_hce_dow TO chk_hcr_dow;
ALTER TABLE holiday_calendar_rules RENAME CONSTRAINT chk_hce_fixed TO chk_hcr_fixed;
ALTER TABLE holiday_calendar_rules RENAME CONSTRAINT chk_hce_nth_weekday TO chk_hcr_nth_weekday;

-- From V23: holiday_calendar_rule_presets constraints (7 total)
ALTER TABLE holiday_calendar_rule_presets RENAME CONSTRAINT chk_hcep_entry_type TO chk_hcrp_entry_type;
ALTER TABLE holiday_calendar_rule_presets RENAME CONSTRAINT chk_hcep_month TO chk_hcrp_month;
ALTER TABLE holiday_calendar_rule_presets RENAME CONSTRAINT chk_hcep_day TO chk_hcrp_day;
ALTER TABLE holiday_calendar_rule_presets RENAME CONSTRAINT chk_hcep_nth TO chk_hcrp_nth;
ALTER TABLE holiday_calendar_rule_presets RENAME CONSTRAINT chk_hcep_dow TO chk_hcrp_dow;
ALTER TABLE holiday_calendar_rule_presets RENAME CONSTRAINT chk_hcep_fixed TO chk_hcrp_fixed;
ALTER TABLE holiday_calendar_rule_presets RENAME CONSTRAINT chk_hcep_nth_weekday TO chk_hcrp_nth_weekday;

-- From V15: daily_rejection_logs constraints
ALTER TABLE daily_rejection_logs RENAME CONSTRAINT uq_daily_rejection_member_date TO uq_daily_rejection_logs_member_date;
