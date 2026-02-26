-- ============================================================================
-- Master Data Preset Tables (System-Wide, No Tenant)
-- ============================================================================

-- Fiscal Year Pattern Presets
CREATE TABLE fiscal_year_pattern_preset (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    start_month INT          NOT NULL,
    start_day   INT          NOT NULL DEFAULT 1,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_fyp_preset_start_month CHECK (start_month >= 1 AND start_month <= 12),
    CONSTRAINT chk_fyp_preset_start_day CHECK (start_day >= 1 AND start_day <= 31),
    CONSTRAINT uq_fyp_preset_name UNIQUE (name)
);

-- Monthly Period Pattern Presets
CREATE TABLE monthly_period_pattern_preset (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    start_day   INT          NOT NULL DEFAULT 1,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mpp_preset_start_day CHECK (start_day >= 1 AND start_day <= 28),
    CONSTRAINT uq_mpp_preset_name UNIQUE (name)
);

-- Holiday Calendar Presets (header)
CREATE TABLE holiday_calendar_preset (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    country     VARCHAR(2),
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_hc_preset_name UNIQUE (name)
);

-- Holiday Calendar Entry Presets (detail)
CREATE TABLE holiday_calendar_entry_preset (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holiday_calendar_id     UUID         NOT NULL REFERENCES holiday_calendar_preset(id) ON DELETE CASCADE,
    name                    VARCHAR(128) NOT NULL,
    entry_type              VARCHAR(16)  NOT NULL,
    month                   INT          NOT NULL,
    day                     INT,
    nth_occurrence          INT,
    day_of_week             INT,
    specific_year           INT,
    CONSTRAINT chk_hcep_entry_type CHECK (entry_type IN ('FIXED', 'NTH_WEEKDAY')),
    CONSTRAINT chk_hcep_month CHECK (month >= 1 AND month <= 12),
    CONSTRAINT chk_hcep_day CHECK (day IS NULL OR (day >= 1 AND day <= 31)),
    CONSTRAINT chk_hcep_nth CHECK (nth_occurrence IS NULL OR (nth_occurrence >= 1 AND nth_occurrence <= 5)),
    CONSTRAINT chk_hcep_dow CHECK (day_of_week IS NULL OR (day_of_week >= 1 AND day_of_week <= 7)),
    CONSTRAINT chk_hcep_fixed CHECK (
        entry_type != 'FIXED' OR (day IS NOT NULL AND nth_occurrence IS NULL AND day_of_week IS NULL)
    ),
    CONSTRAINT chk_hcep_nth_weekday CHECK (
        entry_type != 'NTH_WEEKDAY' OR (nth_occurrence IS NOT NULL AND day_of_week IS NOT NULL AND day IS NULL)
    )
);

CREATE INDEX idx_hcep_calendar_id ON holiday_calendar_entry_preset(holiday_calendar_id);

-- ============================================================================
-- Tenant-Level Holiday Calendar (copy target for presets)
-- ============================================================================

CREATE TABLE holiday_calendar (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenant(id),
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    country     VARCHAR(2),
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_hc_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_hc_tenant_id ON holiday_calendar(tenant_id);

CREATE TABLE holiday_calendar_entry (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holiday_calendar_id     UUID         NOT NULL REFERENCES holiday_calendar(id) ON DELETE CASCADE,
    name                    VARCHAR(128) NOT NULL,
    entry_type              VARCHAR(16)  NOT NULL,
    month                   INT          NOT NULL,
    day                     INT,
    nth_occurrence          INT,
    day_of_week             INT,
    specific_year           INT,
    CONSTRAINT chk_hce_entry_type CHECK (entry_type IN ('FIXED', 'NTH_WEEKDAY')),
    CONSTRAINT chk_hce_month CHECK (month >= 1 AND month <= 12),
    CONSTRAINT chk_hce_day CHECK (day IS NULL OR (day >= 1 AND day <= 31)),
    CONSTRAINT chk_hce_nth CHECK (nth_occurrence IS NULL OR (nth_occurrence >= 1 AND nth_occurrence <= 5)),
    CONSTRAINT chk_hce_dow CHECK (day_of_week IS NULL OR (day_of_week >= 1 AND day_of_week <= 7)),
    CONSTRAINT chk_hce_fixed CHECK (
        entry_type != 'FIXED' OR (day IS NOT NULL AND nth_occurrence IS NULL AND day_of_week IS NULL)
    ),
    CONSTRAINT chk_hce_nth_weekday CHECK (
        entry_type != 'NTH_WEEKDAY' OR (nth_occurrence IS NOT NULL AND day_of_week IS NOT NULL AND day IS NULL)
    )
);

CREATE INDEX idx_hce_calendar_id ON holiday_calendar_entry(holiday_calendar_id);

-- ============================================================================
-- Permissions
-- ============================================================================
INSERT INTO permissions (id, name, description, created_at) VALUES
    ('bb000000-0000-0000-0000-000000000028', 'master_data.view', 'View system master data presets', NOW()),
    ('bb000000-0000-0000-0000-000000000029', 'master_data.create', 'Create master data presets', NOW()),
    ('bb000000-0000-0000-0000-000000000030', 'master_data.update', 'Update master data presets', NOW()),
    ('bb000000-0000-0000-0000-000000000031', 'master_data.deactivate', 'Deactivate/activate master data presets', NOW())
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.name IN ('master_data.view', 'master_data.create', 'master_data.update', 'master_data.deactivate')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- Seed: Fiscal Year Pattern Presets
-- ============================================================================
INSERT INTO fiscal_year_pattern_preset (id, name, description, start_month, start_day) VALUES
    ('00000000-0000-0000-0000-f10000000001', 'Japanese Fiscal Year (April Start)', 'Standard Japanese fiscal year starting April 1', 4, 1),
    ('00000000-0000-0000-0000-f10000000002', 'Calendar Year (January Start)', 'Standard calendar year starting January 1', 1, 1)
ON CONFLICT (name) DO NOTHING;

-- ============================================================================
-- Seed: Monthly Period Pattern Presets
-- ============================================================================
INSERT INTO monthly_period_pattern_preset (id, name, description, start_day) VALUES
    ('00000000-0000-0000-0000-a20000000001', 'Standard (1st of Month)', 'Monthly period starting on the 1st', 1),
    ('00000000-0000-0000-0000-a20000000002', '16th Cutoff', 'Monthly period starting on the 16th', 16),
    ('00000000-0000-0000-0000-a20000000003', '21st Cutoff', 'Monthly period starting on the 21st', 21),
    ('00000000-0000-0000-0000-a20000000004', '26th Cutoff', 'Monthly period starting on the 26th', 26)
ON CONFLICT (name) DO NOTHING;

-- ============================================================================
-- Seed: Holiday Calendar Preset (Japan)
-- ============================================================================
INSERT INTO holiday_calendar_preset (id, name, description, country) VALUES
    ('00000000-0000-0000-0000-bc0000000001', 'Japan Public Holidays', 'Standard Japanese public holidays', 'JP')
ON CONFLICT (name) DO NOTHING;

INSERT INTO holiday_calendar_entry_preset (holiday_calendar_id, name, entry_type, month, day, nth_occurrence, day_of_week) VALUES
    -- FIXED holidays
    ('00000000-0000-0000-0000-bc0000000001', 'New Year''s Day',             'FIXED', 1,  1,    NULL, NULL),
    ('00000000-0000-0000-0000-bc0000000001', 'National Foundation Day',     'FIXED', 2,  11,   NULL, NULL),
    ('00000000-0000-0000-0000-bc0000000001', 'Emperor''s Birthday',         'FIXED', 2,  23,   NULL, NULL),
    ('00000000-0000-0000-0000-bc0000000001', 'Vernal Equinox Day',          'FIXED', 3,  20,   NULL, NULL),
    ('00000000-0000-0000-0000-bc0000000001', 'Showa Day',                   'FIXED', 4,  29,   NULL, NULL),
    ('00000000-0000-0000-0000-bc0000000001', 'Constitution Memorial Day',   'FIXED', 5,  3,    NULL, NULL),
    ('00000000-0000-0000-0000-bc0000000001', 'Greenery Day',                'FIXED', 5,  4,    NULL, NULL),
    ('00000000-0000-0000-0000-bc0000000001', 'Children''s Day',             'FIXED', 5,  5,    NULL, NULL),
    ('00000000-0000-0000-0000-bc0000000001', 'Mountain Day',                'FIXED', 8,  11,   NULL, NULL),
    ('00000000-0000-0000-0000-bc0000000001', 'Autumnal Equinox Day',        'FIXED', 9,  23,   NULL, NULL),
    ('00000000-0000-0000-0000-bc0000000001', 'Culture Day',                 'FIXED', 11, 3,    NULL, NULL),
    ('00000000-0000-0000-0000-bc0000000001', 'Labor Thanksgiving Day',      'FIXED', 11, 23,   NULL, NULL),
    -- NTH_WEEKDAY holidays (Happy Monday)
    ('00000000-0000-0000-0000-bc0000000001', 'Coming of Age Day',           'NTH_WEEKDAY', 1,  NULL, 2, 1),
    ('00000000-0000-0000-0000-bc0000000001', 'Marine Day',                  'NTH_WEEKDAY', 7,  NULL, 3, 1),
    ('00000000-0000-0000-0000-bc0000000001', 'Respect for the Aged Day',    'NTH_WEEKDAY', 9,  NULL, 3, 1),
    ('00000000-0000-0000-0000-bc0000000001', 'Sports Day',                  'NTH_WEEKDAY', 10, NULL, 2, 1);
