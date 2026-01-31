-- ============================================================================
-- Development Seed Data for Work-Log Application
-- ============================================================================
-- Purpose: Provide reproducible test data for development and code review
-- Usage: Activated via Spring profile 'dev' (application-dev.yaml)
-- 
-- This script is idempotent - safe to run multiple times using ON CONFLICT clauses
-- ============================================================================

-- ============================================================================
-- UUIDs used throughout (for reference)
-- ============================================================================
-- TENANT_ID:               550e8400-e29b-41d4-a716-446655440001
-- ORGANIZATION_ID:         880e8400-e29b-41d4-a716-446655440001
-- FISCAL_YEAR_PATTERN_ID:  770e8400-e29b-41d4-a716-446655440001
-- MONTHLY_PERIOD_PATTERN_ID: 660e8400-e29b-41d4-a716-446655440001
-- PROJECT_ID:              990e8400-e29b-41d4-a716-446655440001
--
-- Member IDs (match frontend hardcoded values):
-- BOB (regular user):      00000000-0000-0000-0000-000000000001
-- ALICE (manager):         00000000-0000-0000-0000-000000000002
-- CHARLIE (subordinate):   00000000-0000-0000-0000-000000000003
-- DAVID (independent):     00000000-0000-0000-0000-000000000004
-- ============================================================================

-- ============================================================================
-- 1. Tenant (must be first - referenced by all other tables)
-- ============================================================================
INSERT INTO tenant (id, code, name, status, version, created_at, updated_at)
VALUES (
    '550e8400-e29b-41d4-a716-446655440001',
    'MIOMETRY',
    'Miometry Corporation',
    'ACTIVE',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- 2. Fiscal Year Pattern (April-start Japanese fiscal year)
-- ============================================================================
INSERT INTO fiscal_year_pattern (id, tenant_id, organization_id, name, start_month, start_day, version, created_at)
VALUES (
    '770e8400-e29b-41d4-a716-446655440001',
    '550e8400-e29b-41d4-a716-446655440001',
    NULL,  -- Tenant-wide pattern (not org-specific)
    'Japanese Fiscal Year (April Start)',
    4,     -- April
    1,     -- 1st day
    0,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    start_month = EXCLUDED.start_month,
    start_day = EXCLUDED.start_day;

-- ============================================================================
-- 3. Monthly Period Pattern (21st to 20th billing cycle)
-- ============================================================================
INSERT INTO monthly_period_pattern (id, tenant_id, organization_id, name, start_day, version, created_at)
VALUES (
    '660e8400-e29b-41d4-a716-446655440001',
    '550e8400-e29b-41d4-a716-446655440001',
    NULL,  -- Tenant-wide pattern (not org-specific)
    'Standard Monthly Period (21st Start)',
    21,    -- Period runs from 21st to 20th
    0,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    start_day = EXCLUDED.start_day;

-- ============================================================================
-- 4. Organization (references tenant and patterns)
-- ============================================================================
INSERT INTO organization (
    id, tenant_id, parent_id, code, name, level, status, version,
    fiscal_year_pattern_id, monthly_period_pattern_id, created_at, updated_at
)
VALUES (
    '880e8400-e29b-41d4-a716-446655440001',
    '550e8400-e29b-41d4-a716-446655440001',
    NULL,  -- Root organization (no parent)
    'ENG',
    'Engineering Department',
    1,
    'ACTIVE',
    0,
    '770e8400-e29b-41d4-a716-446655440001',  -- Fiscal year pattern
    '660e8400-e29b-41d4-a716-446655440001',  -- Monthly period pattern
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    status = EXCLUDED.status,
    fiscal_year_pattern_id = EXCLUDED.fiscal_year_pattern_id,
    monthly_period_pattern_id = EXCLUDED.monthly_period_pattern_id,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- 5. Members (using UUIDs that match frontend hardcoded values)
-- Note: Table is named 'members' (plural) in the actual schema
-- ============================================================================

-- Alice (Manager) - Insert first since others reference her as manager
INSERT INTO members (
    id, tenant_id, organization_id, email, display_name, manager_id, is_active, created_at, updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    '550e8400-e29b-41d4-a716-446655440001',
    '880e8400-e29b-41d4-a716-446655440001',
    'alice.manager@miometry.example.com',
    'Alice Manager',
    NULL,  -- Alice has no manager (top-level)
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;

-- Bob (Regular User / Engineer) - Reports to Alice
INSERT INTO members (
    id, tenant_id, organization_id, email, display_name, manager_id, is_active, created_at, updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    '550e8400-e29b-41d4-a716-446655440001',
    '880e8400-e29b-41d4-a716-446655440001',
    'bob.engineer@miometry.example.com',
    'Bob Engineer',
    '00000000-0000-0000-0000-000000000002',  -- Reports to Alice
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    manager_id = EXCLUDED.manager_id,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;

-- Charlie (Subordinate Engineer) - Reports to Alice
INSERT INTO members (
    id, tenant_id, organization_id, email, display_name, manager_id, is_active, created_at, updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000003',
    '550e8400-e29b-41d4-a716-446655440001',
    '880e8400-e29b-41d4-a716-446655440001',
    'charlie.engineer@miometry.example.com',
    'Charlie Engineer',
    '00000000-0000-0000-0000-000000000002',  -- Reports to Alice
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    manager_id = EXCLUDED.manager_id,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;

-- David (Independent) - No manager
INSERT INTO members (
    id, tenant_id, organization_id, email, display_name, manager_id, is_active, created_at, updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000004',
    '550e8400-e29b-41d4-a716-446655440001',
    '880e8400-e29b-41d4-a716-446655440001',
    'david.independent@miometry.example.com',
    'David Independent',
    NULL,  -- Independent contributor, no manager
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- 6. Sample Work Log Entries (for current month - testing purposes)
-- ============================================================================
-- Note: Uses dynamic date calculation for current month

-- Bob's work log entries for current week
INSERT INTO work_log_entries_projection (
    id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by, version, created_at, updated_at
)
VALUES 
    -- Today's entry
    (
        'a0000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000001',  -- Bob
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',  -- Project ID
        CURRENT_DATE,
        8.00,
        'Development work on user authentication module',
        'DRAFT',
        NULL,  -- Self-entered
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- Yesterday's entry
    (
        'a0000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000001',  -- Bob
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',
        CURRENT_DATE - INTERVAL '1 day',
        7.50,
        'Code review and bug fixes',
        'SUBMITTED',
        NULL,
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- 2 days ago
    (
        'a0000000-0000-0000-0000-000000000003',
        '00000000-0000-0000-0000-000000000001',  -- Bob
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',
        CURRENT_DATE - INTERVAL '2 days',
        8.00,
        'Sprint planning and estimation',
        'APPROVED',
        NULL,
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
ON CONFLICT (id) DO UPDATE SET
    hours = EXCLUDED.hours,
    notes = EXCLUDED.notes,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

-- Charlie's work log entries
INSERT INTO work_log_entries_projection (
    id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by, version, created_at, updated_at
)
VALUES 
    (
        'b0000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000003',  -- Charlie
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',
        CURRENT_DATE,
        6.00,
        'Frontend implementation',
        'DRAFT',
        NULL,
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- Proxy entry by Alice for Charlie (yesterday)
    (
        'b0000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000003',  -- Charlie (member)
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',
        CURRENT_DATE - INTERVAL '1 day',
        8.00,
        'Backend API development (entered by manager)',
        'SUBMITTED',
        '00000000-0000-0000-0000-000000000002',  -- Entered by Alice (proxy)
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
ON CONFLICT (id) DO UPDATE SET
    hours = EXCLUDED.hours,
    notes = EXCLUDED.notes,
    status = EXCLUDED.status,
    entered_by = EXCLUDED.entered_by,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- 7. Sample Absences (for testing absence features)
-- ============================================================================
INSERT INTO absences_projection (
    id, member_id, organization_id, absence_type, start_date, end_date, hours_per_day, notes, status, version, created_at, updated_at
)
VALUES 
    -- Bob's planned vacation (next week)
    (
        'c0000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000001',  -- Bob
        '880e8400-e29b-41d4-a716-446655440001',
        'PAID_LEAVE',
        CURRENT_DATE + INTERVAL '7 days',
        CURRENT_DATE + INTERVAL '9 days',
        8.00,
        'Summer vacation',
        'APPROVED',
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- David's sick leave (last week, for history)
    (
        'c0000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000004',  -- David
        '880e8400-e29b-41d4-a716-446655440001',
        'SICK_LEAVE',
        CURRENT_DATE - INTERVAL '5 days',
        CURRENT_DATE - INTERVAL '4 days',
        8.00,
        'Medical appointment',
        'APPROVED',
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
ON CONFLICT (id) DO UPDATE SET
    absence_type = EXCLUDED.absence_type,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date,
    hours_per_day = EXCLUDED.hours_per_day,
    notes = EXCLUDED.notes,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- Verification Queries (commented out - for manual testing)
-- ============================================================================
-- SELECT 'tenant' as table_name, count(*) FROM tenant
-- UNION ALL SELECT 'organization', count(*) FROM organization
-- UNION ALL SELECT 'fiscal_year_pattern', count(*) FROM fiscal_year_pattern
-- UNION ALL SELECT 'monthly_period_pattern', count(*) FROM monthly_period_pattern
-- UNION ALL SELECT 'member', count(*) FROM member
-- UNION ALL SELECT 'work_log_entries_projection', count(*) FROM work_log_entries_projection
-- UNION ALL SELECT 'absences_projection', count(*) FROM absences_projection;
