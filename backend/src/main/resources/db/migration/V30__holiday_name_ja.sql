-- ==========================================
-- Holiday Entry Japanese Names
-- Migration: V30__holiday_name_ja.sql
-- Feature: holiday-i18n
-- Date: 2026-03-02
-- ==========================================
-- Adds name_ja column to holiday_calendar_entry_preset and holiday_calendar_entry,
-- then backfills Japanese names for the 16 Japan Public Holidays.

-- 1. Add name_ja column to preset entries
ALTER TABLE holiday_calendar_entry_preset ADD COLUMN name_ja VARCHAR(128);

-- 2. Add name_ja column to tenant-level entries
ALTER TABLE holiday_calendar_entry ADD COLUMN name_ja VARCHAR(128);

-- 3. Backfill preset entries with Japanese names
UPDATE holiday_calendar_entry_preset SET name_ja = '元日'       WHERE name = 'New Year''s Day';
UPDATE holiday_calendar_entry_preset SET name_ja = '建国記念の日' WHERE name = 'National Foundation Day';
UPDATE holiday_calendar_entry_preset SET name_ja = '天皇誕生日'   WHERE name = 'Emperor''s Birthday';
UPDATE holiday_calendar_entry_preset SET name_ja = '春分の日'     WHERE name = 'Vernal Equinox Day';
UPDATE holiday_calendar_entry_preset SET name_ja = '昭和の日'     WHERE name = 'Showa Day';
UPDATE holiday_calendar_entry_preset SET name_ja = '憲法記念日'   WHERE name = 'Constitution Memorial Day';
UPDATE holiday_calendar_entry_preset SET name_ja = 'みどりの日'   WHERE name = 'Greenery Day';
UPDATE holiday_calendar_entry_preset SET name_ja = 'こどもの日'   WHERE name = 'Children''s Day';
UPDATE holiday_calendar_entry_preset SET name_ja = '山の日'       WHERE name = 'Mountain Day';
UPDATE holiday_calendar_entry_preset SET name_ja = '秋分の日'     WHERE name = 'Autumnal Equinox Day';
UPDATE holiday_calendar_entry_preset SET name_ja = '文化の日'     WHERE name = 'Culture Day';
UPDATE holiday_calendar_entry_preset SET name_ja = '勤労感謝の日' WHERE name = 'Labor Thanksgiving Day';
UPDATE holiday_calendar_entry_preset SET name_ja = '成人の日'     WHERE name = 'Coming of Age Day';
UPDATE holiday_calendar_entry_preset SET name_ja = '海の日'       WHERE name = 'Marine Day';
UPDATE holiday_calendar_entry_preset SET name_ja = '敬老の日'     WHERE name = 'Respect for the Aged Day';
UPDATE holiday_calendar_entry_preset SET name_ja = 'スポーツの日' WHERE name = 'Sports Day';

-- 4. Backfill tenant-level entries with Japanese names (for already-bootstrapped tenants)
UPDATE holiday_calendar_entry SET name_ja = '元日'       WHERE name = 'New Year''s Day';
UPDATE holiday_calendar_entry SET name_ja = '建国記念の日' WHERE name = 'National Foundation Day';
UPDATE holiday_calendar_entry SET name_ja = '天皇誕生日'   WHERE name = 'Emperor''s Birthday';
UPDATE holiday_calendar_entry SET name_ja = '春分の日'     WHERE name = 'Vernal Equinox Day';
UPDATE holiday_calendar_entry SET name_ja = '昭和の日'     WHERE name = 'Showa Day';
UPDATE holiday_calendar_entry SET name_ja = '憲法記念日'   WHERE name = 'Constitution Memorial Day';
UPDATE holiday_calendar_entry SET name_ja = 'みどりの日'   WHERE name = 'Greenery Day';
UPDATE holiday_calendar_entry SET name_ja = 'こどもの日'   WHERE name = 'Children''s Day';
UPDATE holiday_calendar_entry SET name_ja = '山の日'       WHERE name = 'Mountain Day';
UPDATE holiday_calendar_entry SET name_ja = '秋分の日'     WHERE name = 'Autumnal Equinox Day';
UPDATE holiday_calendar_entry SET name_ja = '文化の日'     WHERE name = 'Culture Day';
UPDATE holiday_calendar_entry SET name_ja = '勤労感謝の日' WHERE name = 'Labor Thanksgiving Day';
UPDATE holiday_calendar_entry SET name_ja = '成人の日'     WHERE name = 'Coming of Age Day';
UPDATE holiday_calendar_entry SET name_ja = '海の日'       WHERE name = 'Marine Day';
UPDATE holiday_calendar_entry SET name_ja = '敬老の日'     WHERE name = 'Respect for the Aged Day';
UPDATE holiday_calendar_entry SET name_ja = 'スポーツの日' WHERE name = 'Sports Day';

-- Rollback:
-- ALTER TABLE holiday_calendar_entry_preset DROP COLUMN name_ja;
-- ALTER TABLE holiday_calendar_entry DROP COLUMN name_ja;
