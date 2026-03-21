-- V35: Rename entry_type column to rule_type for thesaurus consistency (entry → rule)
-- Part of issue #126: テーブル名の複数形統一 + シソーラス見直し

-- Rename columns (PostgreSQL auto-updates CHECK constraint bodies)
ALTER TABLE holiday_calendar_rules RENAME COLUMN entry_type TO rule_type;
ALTER TABLE holiday_calendar_rule_presets RENAME COLUMN entry_type TO rule_type;

-- Rename constraints to reflect new column name
ALTER TABLE holiday_calendar_rules RENAME CONSTRAINT chk_hcr_entry_type TO chk_hcr_rule_type;
ALTER TABLE holiday_calendar_rule_presets RENAME CONSTRAINT chk_hcrp_entry_type TO chk_hcrp_rule_type;
