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
--
-- Project IDs:
-- PROJECT_WORKLOG:         990e8400-e29b-41d4-a716-446655440001  (Active, ongoing)
-- PROJECT_INFRA:           990e8400-e29b-41d4-a716-446655440002  (Active, ongoing)
-- PROJECT_LEGACY:          990e8400-e29b-41d4-a716-446655440003  (Inactive/closed)
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
-- 5. Projects (multiple projects for realistic testing)
-- ============================================================================

-- Active project: Work-Log Development (main project)
INSERT INTO projects (
    id, tenant_id, code, name, is_active, valid_from, valid_until, created_at, updated_at
)
VALUES (
    '990e8400-e29b-41d4-a716-446655440001',
    '550e8400-e29b-41d4-a716-446655440001',
    'WORKLOG-DEV',
    'Work-Log Development Project',
    true,
    '2025-01-01',
    NULL,  -- No end date (ongoing project)
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    code = EXCLUDED.code,
    is_active = EXCLUDED.is_active,
    valid_from = EXCLUDED.valid_from,
    valid_until = EXCLUDED.valid_until,
    updated_at = CURRENT_TIMESTAMP;

-- Active project: Infrastructure & DevOps
INSERT INTO projects (
    id, tenant_id, code, name, is_active, valid_from, valid_until, created_at, updated_at
)
VALUES (
    '990e8400-e29b-41d4-a716-446655440002',
    '550e8400-e29b-41d4-a716-446655440001',
    'INFRA-OPS',
    'Infrastructure & DevOps',
    true,
    '2025-01-01',
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    code = EXCLUDED.code,
    is_active = EXCLUDED.is_active,
    valid_from = EXCLUDED.valid_from,
    valid_until = EXCLUDED.valid_until,
    updated_at = CURRENT_TIMESTAMP;

-- Inactive project: Legacy System (closed, no new entries allowed)
INSERT INTO projects (
    id, tenant_id, code, name, is_active, valid_from, valid_until, created_at, updated_at
)
VALUES (
    '990e8400-e29b-41d4-a716-446655440003',
    '550e8400-e29b-41d4-a716-446655440001',
    'LEGACY-SYS',
    'Legacy System Maintenance',
    false,  -- Project closed
    '2024-01-01',
    '2024-12-31',  -- Project ended
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    code = EXCLUDED.code,
    is_active = EXCLUDED.is_active,
    valid_from = EXCLUDED.valid_from,
    valid_until = EXCLUDED.valid_until,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- 6. Members (using UUIDs that match frontend hardcoded values)
-- Note: Table is named 'members' (plural) in the schema
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
-- 7. Work Log Entries - Comprehensive test data
-- ============================================================================
-- Covers various scenarios:
--   - All statuses: DRAFT, SUBMITTED, APPROVED, REJECTED
--   - Multiple projects
--   - Self-entry and proxy entry
--   - Past week for calendar testing
--   - Different hour amounts (full day, half day, overtime)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 7.1 Bob's Work Log Entries (Regular Engineer - Reports to Alice)
-- ---------------------------------------------------------------------------
INSERT INTO work_log_entries_projection (
    id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by, version, created_at, updated_at
)
VALUES 
    -- Today: DRAFT (not yet submitted)
    (
        'a0000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000001',  -- Bob
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',  -- WORKLOG-DEV
        CURRENT_DATE,
        8.00,
        'API endpoint implementation for work log entries',
        'DRAFT',
        NULL,  -- Self-entered
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- Yesterday: SUBMITTED (awaiting approval)
    (
        'a0000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000001',
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',
        CURRENT_DATE - INTERVAL '1 day',
        7.50,
        'Code review and unit test fixes',
        'SUBMITTED',
        NULL,
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- 2 days ago: APPROVED
    (
        'a0000000-0000-0000-0000-000000000003',
        '00000000-0000-0000-0000-000000000001',
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',
        CURRENT_DATE - INTERVAL '2 days',
        8.00,
        'Sprint planning and story estimation',
        'APPROVED',
        NULL,
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- 3 days ago: APPROVED (different project - INFRA)
    (
        'a0000000-0000-0000-0000-000000000004',
        '00000000-0000-0000-0000-000000000001',
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440002',  -- INFRA-OPS
        CURRENT_DATE - INTERVAL '3 days',
        4.00,
        'CI/CD pipeline maintenance',
        'APPROVED',
        NULL,
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- 3 days ago: APPROVED (split day - two projects)
    (
        'a0000000-0000-0000-0000-000000000005',
        '00000000-0000-0000-0000-000000000001',
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',  -- WORKLOG-DEV
        CURRENT_DATE - INTERVAL '3 days',
        4.00,
        'Backend service refactoring',
        'APPROVED',
        NULL,
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- 4 days ago: REJECTED (needs correction)
    (
        'a0000000-0000-0000-0000-000000000006',
        '00000000-0000-0000-0000-000000000001',
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',
        CURRENT_DATE - INTERVAL '4 days',
        12.00,  -- Excessive hours - rejected
        'Feature development (overtime)',
        'REJECTED',
        NULL,
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- 5 days ago: APPROVED
    (
        'a0000000-0000-0000-0000-000000000007',
        '00000000-0000-0000-0000-000000000001',
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',
        CURRENT_DATE - INTERVAL '5 days',
        8.00,
        'Database migration implementation',
        'APPROVED',
        NULL,
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- 6 days ago: APPROVED (half day)
    (
        'a0000000-0000-0000-0000-000000000008',
        '00000000-0000-0000-0000-000000000001',
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',
        CURRENT_DATE - INTERVAL '6 days',
        4.00,
        'Morning: Technical documentation',
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
    project_id = EXCLUDED.project_id,
    updated_at = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------------------------
-- 7.2 Charlie's Work Log Entries (Junior Engineer - Reports to Alice)
-- ---------------------------------------------------------------------------
INSERT INTO work_log_entries_projection (
    id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by, version, created_at, updated_at
)
VALUES 
    -- Today: DRAFT
    (
        'b0000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000003',  -- Charlie
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',
        CURRENT_DATE,
        6.00,
        'Frontend component development',
        'DRAFT',
        NULL,
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- Yesterday: SUBMITTED (proxy entry by manager Alice)
    (
        'b0000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000003',  -- Charlie (member)
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',
        CURRENT_DATE - INTERVAL '1 day',
        8.00,
        'React component integration (entered by manager)',
        'SUBMITTED',
        '00000000-0000-0000-0000-000000000002',  -- Entered by Alice (proxy)
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- 2 days ago: APPROVED
    (
        'b0000000-0000-0000-0000-000000000003',
        '00000000-0000-0000-0000-000000000003',
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',
        CURRENT_DATE - INTERVAL '2 days',
        8.00,
        'UI/UX implementation and styling',
        'APPROVED',
        NULL,
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- 3 days ago: APPROVED
    (
        'b0000000-0000-0000-0000-000000000004',
        '00000000-0000-0000-0000-000000000003',
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',
        CURRENT_DATE - INTERVAL '3 days',
        7.00,
        'Testing and bug fixes',
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
    entered_by = EXCLUDED.entered_by,
    updated_at = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------------------------
-- 7.3 Alice's Work Log Entries (Manager - also logs her own work)
-- ---------------------------------------------------------------------------
INSERT INTO work_log_entries_projection (
    id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by, version, created_at, updated_at
)
VALUES 
    -- Today: DRAFT
    (
        'c0000000-0000-0000-0000-000000000010',
        '00000000-0000-0000-0000-000000000002',  -- Alice
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',
        CURRENT_DATE,
        3.00,
        'Team management and 1:1 meetings',
        'DRAFT',
        NULL,
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- Today: DRAFT (split between projects)
    (
        'c0000000-0000-0000-0000-000000000011',
        '00000000-0000-0000-0000-000000000002',
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440002',  -- INFRA-OPS
        CURRENT_DATE,
        2.00,
        'Infrastructure planning review',
        'DRAFT',
        NULL,
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- Yesterday: SUBMITTED
    (
        'c0000000-0000-0000-0000-000000000012',
        '00000000-0000-0000-0000-000000000002',
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',
        CURRENT_DATE - INTERVAL '1 day',
        6.00,
        'Sprint review and retrospective facilitation',
        'SUBMITTED',
        NULL,
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- 2 days ago: APPROVED
    (
        'c0000000-0000-0000-0000-000000000013',
        '00000000-0000-0000-0000-000000000002',
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440001',
        CURRENT_DATE - INTERVAL '2 days',
        8.00,
        'Quarterly planning and roadmap update',
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
    project_id = EXCLUDED.project_id,
    updated_at = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------------------------
-- 7.4 David's Work Log Entries (Independent - no manager)
-- ---------------------------------------------------------------------------
INSERT INTO work_log_entries_projection (
    id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by, version, created_at, updated_at
)
VALUES 
    -- Today: DRAFT
    (
        'd0000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000004',  -- David
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440002',  -- INFRA-OPS
        CURRENT_DATE,
        8.00,
        'Kubernetes cluster optimization',
        'DRAFT',
        NULL,
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- Yesterday: SUBMITTED
    (
        'd0000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000004',
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440002',
        CURRENT_DATE - INTERVAL '1 day',
        8.00,
        'Monitoring and alerting setup',
        'SUBMITTED',
        NULL,
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- 2 days ago: APPROVED
    (
        'd0000000-0000-0000-0000-000000000003',
        '00000000-0000-0000-0000-000000000004',
        '880e8400-e29b-41d4-a716-446655440001',
        '990e8400-e29b-41d4-a716-446655440002',
        CURRENT_DATE - INTERVAL '2 days',
        8.00,
        'Database performance tuning',
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

-- ============================================================================
-- 8. Sample Absences (various types and statuses)
-- ============================================================================
INSERT INTO absences_projection (
    id, member_id, organization_id, absence_type, start_date, end_date, hours_per_day, notes, status, version, created_at, updated_at
)
VALUES 
    -- Bob's planned vacation (next week) - APPROVED
    (
        'e0000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000001',  -- Bob
        '880e8400-e29b-41d4-a716-446655440001',
        'PAID_LEAVE',
        CURRENT_DATE + INTERVAL '7 days',
        CURRENT_DATE + INTERVAL '9 days',
        8.00,
        'Summer vacation - visiting family',
        'APPROVED',
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- Charlie's pending leave request - SUBMITTED (awaiting approval)
    (
        'e0000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000003',  -- Charlie
        '880e8400-e29b-41d4-a716-446655440001',
        'PAID_LEAVE',
        CURRENT_DATE + INTERVAL '14 days',
        CURRENT_DATE + INTERVAL '14 days',
        8.00,
        'Personal day',
        'SUBMITTED',
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- David's past sick leave - APPROVED (for history)
    (
        'e0000000-0000-0000-0000-000000000003',
        '00000000-0000-0000-0000-000000000004',  -- David
        '880e8400-e29b-41d4-a716-446655440001',
        'SICK_LEAVE',
        CURRENT_DATE - INTERVAL '5 days',
        CURRENT_DATE - INTERVAL '4 days',
        8.00,
        'Medical appointment and recovery',
        'APPROVED',
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- Alice's half-day absence - APPROVED
    (
        'e0000000-0000-0000-0000-000000000004',
        '00000000-0000-0000-0000-000000000002',  -- Alice
        '880e8400-e29b-41d4-a716-446655440001',
        'PAID_LEAVE',
        CURRENT_DATE + INTERVAL '3 days',
        CURRENT_DATE + INTERVAL '3 days',
        4.00,  -- Half day
        'Afternoon off - dentist appointment',
        'APPROVED',
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- Bob's rejected absence request - REJECTED
    (
        'e0000000-0000-0000-0000-000000000005',
        '00000000-0000-0000-0000-000000000001',  -- Bob
        '880e8400-e29b-41d4-a716-446655440001',
        'PAID_LEAVE',
        CURRENT_DATE + INTERVAL '2 days',
        CURRENT_DATE + INTERVAL '5 days',
        8.00,
        'Extended leave - rejected due to project deadline',
        'REJECTED',
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
-- Run these queries to verify seed data:
--
-- Count all records:
-- SELECT 'tenant' as table_name, count(*) FROM tenant
-- UNION ALL SELECT 'organization', count(*) FROM organization
-- UNION ALL SELECT 'projects', count(*) FROM projects
-- UNION ALL SELECT 'members', count(*) FROM members
-- UNION ALL SELECT 'work_log_entries', count(*) FROM work_log_entries_projection
-- UNION ALL SELECT 'absences', count(*) FROM absences_projection;
--
-- View work log entries by member:
-- SELECT m.display_name, w.work_date, w.hours, w.status, p.code as project
-- FROM work_log_entries_projection w
-- JOIN members m ON w.member_id = m.id
-- JOIN projects p ON w.project_id = p.id
-- ORDER BY m.display_name, w.work_date DESC;
--
-- View absences by member:
-- SELECT m.display_name, a.absence_type, a.start_date, a.end_date, a.status
-- FROM absences_projection a
-- JOIN members m ON a.member_id = m.id
-- ORDER BY a.start_date;
