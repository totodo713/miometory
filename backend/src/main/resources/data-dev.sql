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
@@

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
@@

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
@@

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
@@

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
@@

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
@@

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
@@

-- ============================================================================
-- 6. Members (using UUIDs that match frontend hardcoded values)
-- Note: Table is named 'members' (plural) in the schema
-- ============================================================================

-- Alice (Manager) - Insert first since others reference her as manager
INSERT INTO members (
    id, tenant_id, organization_id, email, display_name, manager_id, is_active, version, created_at, updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    '550e8400-e29b-41d4-a716-446655440001',
    '880e8400-e29b-41d4-a716-446655440001',
    'alice.manager@miometry.example.com',
    'Alice Manager',
    NULL,  -- Alice has no manager (top-level)
    true,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;
@@

-- Bob (Regular User / Engineer) - Reports to Alice
INSERT INTO members (
    id, tenant_id, organization_id, email, display_name, manager_id, is_active, version, created_at, updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    '550e8400-e29b-41d4-a716-446655440001',
    '880e8400-e29b-41d4-a716-446655440001',
    'bob.engineer@miometry.example.com',
    'Bob Engineer',
    '00000000-0000-0000-0000-000000000002',  -- Reports to Alice
    true,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    manager_id = EXCLUDED.manager_id,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;
@@

-- Charlie (Subordinate Engineer) - Reports to Alice
INSERT INTO members (
    id, tenant_id, organization_id, email, display_name, manager_id, is_active, version, created_at, updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000003',
    '550e8400-e29b-41d4-a716-446655440001',
    '880e8400-e29b-41d4-a716-446655440001',
    'charlie.engineer@miometry.example.com',
    'Charlie Engineer',
    '00000000-0000-0000-0000-000000000002',  -- Reports to Alice
    true,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    manager_id = EXCLUDED.manager_id,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;
@@

-- David (Independent) - No manager
INSERT INTO members (
    id, tenant_id, organization_id, email, display_name, manager_id, is_active, version, created_at, updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000004',
    '550e8400-e29b-41d4-a716-446655440001',
    '880e8400-e29b-41d4-a716-446655440001',
    'david.independent@miometry.example.com',
    'David Independent',
    NULL,  -- Independent contributor, no manager
    true,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;
@@

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
    work_date = EXCLUDED.work_date,
    hours = EXCLUDED.hours,
    notes = EXCLUDED.notes,
    status = EXCLUDED.status,
    project_id = EXCLUDED.project_id,
    updated_at = CURRENT_TIMESTAMP;
@@

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
    work_date = EXCLUDED.work_date,
    hours = EXCLUDED.hours,
    notes = EXCLUDED.notes,
    status = EXCLUDED.status,
    entered_by = EXCLUDED.entered_by,
    updated_at = CURRENT_TIMESTAMP;
@@

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
    work_date = EXCLUDED.work_date,
    hours = EXCLUDED.hours,
    notes = EXCLUDED.notes,
    status = EXCLUDED.status,
    project_id = EXCLUDED.project_id,
    updated_at = CURRENT_TIMESTAMP;
@@

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
    work_date = EXCLUDED.work_date,
    hours = EXCLUDED.hours,
    notes = EXCLUDED.notes,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;
@@

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
@@

-- ============================================================================
-- 9-13. Event Store Records and Monthly Projections
-- ============================================================================
-- Provides event_store consistency with projection tables and adds
-- monthly approval, calendar, and summary projection data.
-- Uses a DO block for dynamic date/fiscal period calculations.
-- ============================================================================

DO $$
DECLARE
    v_now TEXT := to_char(CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"');
    v_d0 TEXT := to_char(CURRENT_DATE, 'YYYY-MM-DD');
    v_d1 TEXT := to_char(CURRENT_DATE - 1, 'YYYY-MM-DD');
    v_d2 TEXT := to_char(CURRENT_DATE - 2, 'YYYY-MM-DD');
    v_d3 TEXT := to_char(CURRENT_DATE - 3, 'YYYY-MM-DD');
    v_d4 TEXT := to_char(CURRENT_DATE - 4, 'YYYY-MM-DD');
    v_d5 TEXT := to_char(CURRENT_DATE - 5, 'YYYY-MM-DD');
    v_d6 TEXT := to_char(CURRENT_DATE - 6, 'YYYY-MM-DD');
    v_dp2 TEXT := to_char(CURRENT_DATE + 2, 'YYYY-MM-DD');
    v_dp3 TEXT := to_char(CURRENT_DATE + 3, 'YYYY-MM-DD');
    v_dp7 TEXT := to_char(CURRENT_DATE + 7, 'YYYY-MM-DD');
    v_dp14 TEXT := to_char(CURRENT_DATE + 14, 'YYYY-MM-DD');
    -- Member UUIDs
    v_bob TEXT := '00000000-0000-0000-0000-000000000001';
    v_alice TEXT := '00000000-0000-0000-0000-000000000002';
    v_charlie TEXT := '00000000-0000-0000-0000-000000000003';
    v_david TEXT := '00000000-0000-0000-0000-000000000004';
    -- Project UUIDs
    v_pw TEXT := '990e8400-e29b-41d4-a716-446655440001';
    v_pi TEXT := '990e8400-e29b-41d4-a716-446655440002';
    -- Fiscal period variables
    v_fs TEXT; v_fe TEXT; v_pfs TEXT; v_pfe TEXT;
    v_fy INT; v_fm INT; v_pfy INT; v_pfm INT;
    -- For calendar/summary computation
    v_cal JSONB; v_sum JSONB; v_hrs DECIMAL;
    v_member UUID;
BEGIN
    -- Calculate current fiscal period (21st-based monthly periods)
    IF EXTRACT(DAY FROM CURRENT_DATE) >= 21 THEN
        v_fs := to_char(date_trunc('month', CURRENT_DATE)::date + 20, 'YYYY-MM-DD');
        v_fe := to_char((date_trunc('month', CURRENT_DATE) + INTERVAL '1 month')::date + 19, 'YYYY-MM-DD');
    ELSE
        v_fs := to_char((date_trunc('month', CURRENT_DATE) - INTERVAL '1 month')::date + 20, 'YYYY-MM-DD');
        v_fe := to_char(date_trunc('month', CURRENT_DATE)::date + 19, 'YYYY-MM-DD');
    END IF;
    v_pfs := to_char(v_fs::date - INTERVAL '1 month', 'YYYY-MM-DD');
    v_pfe := to_char(v_fe::date - INTERVAL '1 month', 'YYYY-MM-DD');
    -- Fiscal month: Apr=1, May=2, ..., Mar=12; Fiscal year: labeled by year containing March
    IF EXTRACT(MONTH FROM v_fe::date) >= 4 THEN
        v_fm := EXTRACT(MONTH FROM v_fe::date)::INT - 3;
        v_fy := EXTRACT(YEAR FROM v_fe::date)::INT + 1;
    ELSE
        v_fm := EXTRACT(MONTH FROM v_fe::date)::INT + 9;
        v_fy := EXTRACT(YEAR FROM v_fe::date)::INT;
    END IF;
    IF v_fm = 1 THEN v_pfm := 12; v_pfy := v_fy - 1;
    ELSE v_pfm := v_fm - 1; v_pfy := v_fy;
    END IF;

    -- =========================================================================
    -- 9. Event Store: WorkLogEntryCreated events (19 records, version=1)
    -- =========================================================================
    INSERT INTO event_store (id, aggregate_type, aggregate_id, event_type, payload, version, created_at) VALUES
    -- Bob (8 entries)
    ('f0100000-0000-0000-0000-000000000001','WorkLogEntry','a0000000-0000-0000-0000-000000000001','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000001','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000001','memberId',v_bob,
       'projectId',v_pw,'date',v_d0,'hours',8.0,'comment','API endpoint implementation for work log entries','enteredBy',v_bob),
     1, CURRENT_TIMESTAMP),
    ('f0100000-0000-0000-0000-000000000002','WorkLogEntry','a0000000-0000-0000-0000-000000000002','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000002','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000002','memberId',v_bob,
       'projectId',v_pw,'date',v_d1,'hours',7.5,'comment','Code review and unit test fixes','enteredBy',v_bob),
     1, CURRENT_TIMESTAMP),
    ('f0100000-0000-0000-0000-000000000003','WorkLogEntry','a0000000-0000-0000-0000-000000000003','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000003','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000003','memberId',v_bob,
       'projectId',v_pw,'date',v_d2,'hours',8.0,'comment','Sprint planning and story estimation','enteredBy',v_bob),
     1, CURRENT_TIMESTAMP),
    ('f0100000-0000-0000-0000-000000000004','WorkLogEntry','a0000000-0000-0000-0000-000000000004','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000004','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000004','memberId',v_bob,
       'projectId',v_pi,'date',v_d3,'hours',4.0,'comment','CI/CD pipeline maintenance','enteredBy',v_bob),
     1, CURRENT_TIMESTAMP),
    ('f0100000-0000-0000-0000-000000000005','WorkLogEntry','a0000000-0000-0000-0000-000000000005','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000005','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000005','memberId',v_bob,
       'projectId',v_pw,'date',v_d3,'hours',4.0,'comment','Backend service refactoring','enteredBy',v_bob),
     1, CURRENT_TIMESTAMP),
    ('f0100000-0000-0000-0000-000000000006','WorkLogEntry','a0000000-0000-0000-0000-000000000006','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000006','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000006','memberId',v_bob,
       'projectId',v_pw,'date',v_d4,'hours',12.0,'comment','Feature development (overtime)','enteredBy',v_bob),
     1, CURRENT_TIMESTAMP),
    ('f0100000-0000-0000-0000-000000000007','WorkLogEntry','a0000000-0000-0000-0000-000000000007','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000007','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000007','memberId',v_bob,
       'projectId',v_pw,'date',v_d5,'hours',8.0,'comment','Database migration implementation','enteredBy',v_bob),
     1, CURRENT_TIMESTAMP),
    ('f0100000-0000-0000-0000-000000000008','WorkLogEntry','a0000000-0000-0000-0000-000000000008','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000008','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000008','memberId',v_bob,
       'projectId',v_pw,'date',v_d6,'hours',4.0,'comment','Morning: Technical documentation','enteredBy',v_bob),
     1, CURRENT_TIMESTAMP),
    -- Charlie (4 entries)
    ('f0100000-0000-0000-0000-000000000009','WorkLogEntry','b0000000-0000-0000-0000-000000000001','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000009','occurredAt',v_now,
       'aggregateId','b0000000-0000-0000-0000-000000000001','memberId',v_charlie,
       'projectId',v_pw,'date',v_d0,'hours',6.0,'comment','Frontend component development','enteredBy',v_charlie),
     1, CURRENT_TIMESTAMP),
    ('f0100000-0000-0000-0000-000000000010','WorkLogEntry','b0000000-0000-0000-0000-000000000002','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000010','occurredAt',v_now,
       'aggregateId','b0000000-0000-0000-0000-000000000002','memberId',v_charlie,
       'projectId',v_pw,'date',v_d1,'hours',8.0,'comment','React component integration (entered by manager)','enteredBy',v_alice),
     1, CURRENT_TIMESTAMP),
    ('f0100000-0000-0000-0000-000000000011','WorkLogEntry','b0000000-0000-0000-0000-000000000003','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000011','occurredAt',v_now,
       'aggregateId','b0000000-0000-0000-0000-000000000003','memberId',v_charlie,
       'projectId',v_pw,'date',v_d2,'hours',8.0,'comment','UI/UX implementation and styling','enteredBy',v_charlie),
     1, CURRENT_TIMESTAMP),
    ('f0100000-0000-0000-0000-000000000012','WorkLogEntry','b0000000-0000-0000-0000-000000000004','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000012','occurredAt',v_now,
       'aggregateId','b0000000-0000-0000-0000-000000000004','memberId',v_charlie,
       'projectId',v_pw,'date',v_d3,'hours',7.0,'comment','Testing and bug fixes','enteredBy',v_charlie),
     1, CURRENT_TIMESTAMP),
    -- Alice (4 entries)
    ('f0100000-0000-0000-0000-000000000013','WorkLogEntry','c0000000-0000-0000-0000-000000000010','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000013','occurredAt',v_now,
       'aggregateId','c0000000-0000-0000-0000-000000000010','memberId',v_alice,
       'projectId',v_pw,'date',v_d0,'hours',3.0,'comment','Team management and 1:1 meetings','enteredBy',v_alice),
     1, CURRENT_TIMESTAMP),
    ('f0100000-0000-0000-0000-000000000014','WorkLogEntry','c0000000-0000-0000-0000-000000000011','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000014','occurredAt',v_now,
       'aggregateId','c0000000-0000-0000-0000-000000000011','memberId',v_alice,
       'projectId',v_pi,'date',v_d0,'hours',2.0,'comment','Infrastructure planning review','enteredBy',v_alice),
     1, CURRENT_TIMESTAMP),
    ('f0100000-0000-0000-0000-000000000015','WorkLogEntry','c0000000-0000-0000-0000-000000000012','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000015','occurredAt',v_now,
       'aggregateId','c0000000-0000-0000-0000-000000000012','memberId',v_alice,
       'projectId',v_pw,'date',v_d1,'hours',6.0,'comment','Sprint review and retrospective facilitation','enteredBy',v_alice),
     1, CURRENT_TIMESTAMP),
    ('f0100000-0000-0000-0000-000000000016','WorkLogEntry','c0000000-0000-0000-0000-000000000013','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000016','occurredAt',v_now,
       'aggregateId','c0000000-0000-0000-0000-000000000013','memberId',v_alice,
       'projectId',v_pw,'date',v_d2,'hours',8.0,'comment','Quarterly planning and roadmap update','enteredBy',v_alice),
     1, CURRENT_TIMESTAMP),
    -- David (3 entries)
    ('f0100000-0000-0000-0000-000000000017','WorkLogEntry','d0000000-0000-0000-0000-000000000001','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000017','occurredAt',v_now,
       'aggregateId','d0000000-0000-0000-0000-000000000001','memberId',v_david,
       'projectId',v_pi,'date',v_d0,'hours',8.0,'comment','Kubernetes cluster optimization','enteredBy',v_david),
     1, CURRENT_TIMESTAMP),
    ('f0100000-0000-0000-0000-000000000018','WorkLogEntry','d0000000-0000-0000-0000-000000000002','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000018','occurredAt',v_now,
       'aggregateId','d0000000-0000-0000-0000-000000000002','memberId',v_david,
       'projectId',v_pi,'date',v_d1,'hours',8.0,'comment','Monitoring and alerting setup','enteredBy',v_david),
     1, CURRENT_TIMESTAMP),
    ('f0100000-0000-0000-0000-000000000019','WorkLogEntry','d0000000-0000-0000-0000-000000000003','WorkLogEntryCreated',
     jsonb_build_object('eventId','f0100000-0000-0000-0000-000000000019','occurredAt',v_now,
       'aggregateId','d0000000-0000-0000-0000-000000000003','memberId',v_david,
       'projectId',v_pi,'date',v_d2,'hours',8.0,'comment','Database performance tuning','enteredBy',v_david),
     1, CURRENT_TIMESTAMP)
    ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload, created_at = EXCLUDED.created_at;

    -- =========================================================================
    -- 9b. WorkLogEntryStatusChanged -> SUBMITTED events (14 records, version=2)
    -- =========================================================================
    INSERT INTO event_store (id, aggregate_type, aggregate_id, event_type, payload, version, created_at) VALUES
    -- Bob: a002-a008 (7 entries with SUBMITTED+)
    ('f0110000-0000-0000-0000-000000000001','WorkLogEntry','a0000000-0000-0000-0000-000000000002','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0110000-0000-0000-0000-000000000001','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000002','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_bob),
     2, CURRENT_TIMESTAMP),
    ('f0110000-0000-0000-0000-000000000002','WorkLogEntry','a0000000-0000-0000-0000-000000000003','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0110000-0000-0000-0000-000000000002','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000003','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_bob),
     2, CURRENT_TIMESTAMP),
    ('f0110000-0000-0000-0000-000000000003','WorkLogEntry','a0000000-0000-0000-0000-000000000004','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0110000-0000-0000-0000-000000000003','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000004','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_bob),
     2, CURRENT_TIMESTAMP),
    ('f0110000-0000-0000-0000-000000000004','WorkLogEntry','a0000000-0000-0000-0000-000000000005','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0110000-0000-0000-0000-000000000004','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000005','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_bob),
     2, CURRENT_TIMESTAMP),
    ('f0110000-0000-0000-0000-000000000005','WorkLogEntry','a0000000-0000-0000-0000-000000000006','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0110000-0000-0000-0000-000000000005','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000006','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_bob),
     2, CURRENT_TIMESTAMP),
    ('f0110000-0000-0000-0000-000000000006','WorkLogEntry','a0000000-0000-0000-0000-000000000007','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0110000-0000-0000-0000-000000000006','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000007','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_bob),
     2, CURRENT_TIMESTAMP),
    ('f0110000-0000-0000-0000-000000000007','WorkLogEntry','a0000000-0000-0000-0000-000000000008','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0110000-0000-0000-0000-000000000007','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000008','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_bob),
     2, CURRENT_TIMESTAMP),
    -- Charlie: b002-b004 (3 entries with SUBMITTED+)
    ('f0110000-0000-0000-0000-000000000008','WorkLogEntry','b0000000-0000-0000-0000-000000000002','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0110000-0000-0000-0000-000000000008','occurredAt',v_now,
       'aggregateId','b0000000-0000-0000-0000-000000000002','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_charlie),
     2, CURRENT_TIMESTAMP),
    ('f0110000-0000-0000-0000-000000000009','WorkLogEntry','b0000000-0000-0000-0000-000000000003','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0110000-0000-0000-0000-000000000009','occurredAt',v_now,
       'aggregateId','b0000000-0000-0000-0000-000000000003','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_charlie),
     2, CURRENT_TIMESTAMP),
    ('f0110000-0000-0000-0000-000000000010','WorkLogEntry','b0000000-0000-0000-0000-000000000004','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0110000-0000-0000-0000-000000000010','occurredAt',v_now,
       'aggregateId','b0000000-0000-0000-0000-000000000004','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_charlie),
     2, CURRENT_TIMESTAMP),
    -- Alice: c012-c013 (2 entries with SUBMITTED+)
    ('f0110000-0000-0000-0000-000000000011','WorkLogEntry','c0000000-0000-0000-0000-000000000012','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0110000-0000-0000-0000-000000000011','occurredAt',v_now,
       'aggregateId','c0000000-0000-0000-0000-000000000012','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_alice),
     2, CURRENT_TIMESTAMP),
    ('f0110000-0000-0000-0000-000000000012','WorkLogEntry','c0000000-0000-0000-0000-000000000013','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0110000-0000-0000-0000-000000000012','occurredAt',v_now,
       'aggregateId','c0000000-0000-0000-0000-000000000013','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_alice),
     2, CURRENT_TIMESTAMP),
    -- David: d002-d003 (2 entries with SUBMITTED+)
    ('f0110000-0000-0000-0000-000000000013','WorkLogEntry','d0000000-0000-0000-0000-000000000002','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0110000-0000-0000-0000-000000000013','occurredAt',v_now,
       'aggregateId','d0000000-0000-0000-0000-000000000002','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_david),
     2, CURRENT_TIMESTAMP),
    ('f0110000-0000-0000-0000-000000000014','WorkLogEntry','d0000000-0000-0000-0000-000000000003','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0110000-0000-0000-0000-000000000014','occurredAt',v_now,
       'aggregateId','d0000000-0000-0000-0000-000000000003','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_david),
     2, CURRENT_TIMESTAMP)
    ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload, created_at = EXCLUDED.created_at;

    -- =========================================================================
    -- 9c. WorkLogEntryStatusChanged -> APPROVED/REJECTED events (10 records, version=3)
    -- =========================================================================
    INSERT INTO event_store (id, aggregate_type, aggregate_id, event_type, payload, version, created_at) VALUES
    -- Bob APPROVED: a003, a004, a005, a007, a008
    ('f0120000-0000-0000-0000-000000000001','WorkLogEntry','a0000000-0000-0000-0000-000000000003','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0120000-0000-0000-0000-000000000001','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000003','fromStatus','SUBMITTED','toStatus','APPROVED','changedBy',v_alice),
     3, CURRENT_TIMESTAMP),
    ('f0120000-0000-0000-0000-000000000002','WorkLogEntry','a0000000-0000-0000-0000-000000000004','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0120000-0000-0000-0000-000000000002','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000004','fromStatus','SUBMITTED','toStatus','APPROVED','changedBy',v_alice),
     3, CURRENT_TIMESTAMP),
    ('f0120000-0000-0000-0000-000000000003','WorkLogEntry','a0000000-0000-0000-0000-000000000005','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0120000-0000-0000-0000-000000000003','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000005','fromStatus','SUBMITTED','toStatus','APPROVED','changedBy',v_alice),
     3, CURRENT_TIMESTAMP),
    -- Bob REJECTED: a006
    ('f0120000-0000-0000-0000-000000000004','WorkLogEntry','a0000000-0000-0000-0000-000000000006','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0120000-0000-0000-0000-000000000004','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000006','fromStatus','SUBMITTED','toStatus','REJECTED','changedBy',v_alice),
     3, CURRENT_TIMESTAMP),
    -- Bob APPROVED: a007, a008
    ('f0120000-0000-0000-0000-000000000005','WorkLogEntry','a0000000-0000-0000-0000-000000000007','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0120000-0000-0000-0000-000000000005','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000007','fromStatus','SUBMITTED','toStatus','APPROVED','changedBy',v_alice),
     3, CURRENT_TIMESTAMP),
    ('f0120000-0000-0000-0000-000000000006','WorkLogEntry','a0000000-0000-0000-0000-000000000008','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0120000-0000-0000-0000-000000000006','occurredAt',v_now,
       'aggregateId','a0000000-0000-0000-0000-000000000008','fromStatus','SUBMITTED','toStatus','APPROVED','changedBy',v_alice),
     3, CURRENT_TIMESTAMP),
    -- Charlie APPROVED: b003, b004
    ('f0120000-0000-0000-0000-000000000007','WorkLogEntry','b0000000-0000-0000-0000-000000000003','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0120000-0000-0000-0000-000000000007','occurredAt',v_now,
       'aggregateId','b0000000-0000-0000-0000-000000000003','fromStatus','SUBMITTED','toStatus','APPROVED','changedBy',v_alice),
     3, CURRENT_TIMESTAMP),
    ('f0120000-0000-0000-0000-000000000008','WorkLogEntry','b0000000-0000-0000-0000-000000000004','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0120000-0000-0000-0000-000000000008','occurredAt',v_now,
       'aggregateId','b0000000-0000-0000-0000-000000000004','fromStatus','SUBMITTED','toStatus','APPROVED','changedBy',v_alice),
     3, CURRENT_TIMESTAMP),
    -- Alice APPROVED: c013
    ('f0120000-0000-0000-0000-000000000009','WorkLogEntry','c0000000-0000-0000-0000-000000000013','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0120000-0000-0000-0000-000000000009','occurredAt',v_now,
       'aggregateId','c0000000-0000-0000-0000-000000000013','fromStatus','SUBMITTED','toStatus','APPROVED','changedBy',v_alice),
     3, CURRENT_TIMESTAMP),
    -- David APPROVED: d003
    ('f0120000-0000-0000-0000-000000000010','WorkLogEntry','d0000000-0000-0000-0000-000000000003','WorkLogEntryStatusChanged',
     jsonb_build_object('eventId','f0120000-0000-0000-0000-000000000010','occurredAt',v_now,
       'aggregateId','d0000000-0000-0000-0000-000000000003','fromStatus','SUBMITTED','toStatus','APPROVED','changedBy',v_alice),
     3, CURRENT_TIMESTAMP)
    ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload, created_at = EXCLUDED.created_at;

    -- =========================================================================
    -- 10. Event Store: Absence Events
    -- =========================================================================

    -- 10a. AbsenceRecorded events (5 records, version=1)
    INSERT INTO event_store (id, aggregate_type, aggregate_id, event_type, payload, version, created_at) VALUES
    ('f0200000-0000-0000-0000-000000000001','Absence','e0000000-0000-0000-0000-000000000001','AbsenceRecorded',
     jsonb_build_object('eventId','f0200000-0000-0000-0000-000000000001','occurredAt',v_now,
       'aggregateId','e0000000-0000-0000-0000-000000000001','memberId',v_bob,
       'date',v_dp7,'hours',8.0,'absenceType','PAID_LEAVE','reason','Summer vacation - visiting family','recordedBy',v_bob),
     1, CURRENT_TIMESTAMP),
    ('f0200000-0000-0000-0000-000000000002','Absence','e0000000-0000-0000-0000-000000000002','AbsenceRecorded',
     jsonb_build_object('eventId','f0200000-0000-0000-0000-000000000002','occurredAt',v_now,
       'aggregateId','e0000000-0000-0000-0000-000000000002','memberId',v_charlie,
       'date',v_dp14,'hours',8.0,'absenceType','PAID_LEAVE','reason','Personal day','recordedBy',v_charlie),
     1, CURRENT_TIMESTAMP),
    ('f0200000-0000-0000-0000-000000000003','Absence','e0000000-0000-0000-0000-000000000003','AbsenceRecorded',
     jsonb_build_object('eventId','f0200000-0000-0000-0000-000000000003','occurredAt',v_now,
       'aggregateId','e0000000-0000-0000-0000-000000000003','memberId',v_david,
       'date',v_d5,'hours',8.0,'absenceType','SICK_LEAVE','reason','Medical appointment and recovery','recordedBy',v_david),
     1, CURRENT_TIMESTAMP),
    ('f0200000-0000-0000-0000-000000000004','Absence','e0000000-0000-0000-0000-000000000004','AbsenceRecorded',
     jsonb_build_object('eventId','f0200000-0000-0000-0000-000000000004','occurredAt',v_now,
       'aggregateId','e0000000-0000-0000-0000-000000000004','memberId',v_alice,
       'date',v_dp3,'hours',4.0,'absenceType','PAID_LEAVE','reason','Afternoon off - dentist appointment','recordedBy',v_alice),
     1, CURRENT_TIMESTAMP),
    ('f0200000-0000-0000-0000-000000000005','Absence','e0000000-0000-0000-0000-000000000005','AbsenceRecorded',
     jsonb_build_object('eventId','f0200000-0000-0000-0000-000000000005','occurredAt',v_now,
       'aggregateId','e0000000-0000-0000-0000-000000000005','memberId',v_bob,
       'date',v_dp2,'hours',8.0,'absenceType','PAID_LEAVE','reason','Extended leave - rejected due to project deadline','recordedBy',v_bob),
     1, CURRENT_TIMESTAMP)
    ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload, created_at = EXCLUDED.created_at;

    -- 10b. AbsenceStatusChanged -> SUBMITTED events (5 records, version=2)
    INSERT INTO event_store (id, aggregate_type, aggregate_id, event_type, payload, version, created_at) VALUES
    ('f0210000-0000-0000-0000-000000000001','Absence','e0000000-0000-0000-0000-000000000001','AbsenceStatusChanged',
     jsonb_build_object('eventId','f0210000-0000-0000-0000-000000000001','occurredAt',v_now,
       'aggregateId','e0000000-0000-0000-0000-000000000001','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_bob),
     2, CURRENT_TIMESTAMP),
    ('f0210000-0000-0000-0000-000000000002','Absence','e0000000-0000-0000-0000-000000000002','AbsenceStatusChanged',
     jsonb_build_object('eventId','f0210000-0000-0000-0000-000000000002','occurredAt',v_now,
       'aggregateId','e0000000-0000-0000-0000-000000000002','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_charlie),
     2, CURRENT_TIMESTAMP),
    ('f0210000-0000-0000-0000-000000000003','Absence','e0000000-0000-0000-0000-000000000003','AbsenceStatusChanged',
     jsonb_build_object('eventId','f0210000-0000-0000-0000-000000000003','occurredAt',v_now,
       'aggregateId','e0000000-0000-0000-0000-000000000003','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_david),
     2, CURRENT_TIMESTAMP),
    ('f0210000-0000-0000-0000-000000000004','Absence','e0000000-0000-0000-0000-000000000004','AbsenceStatusChanged',
     jsonb_build_object('eventId','f0210000-0000-0000-0000-000000000004','occurredAt',v_now,
       'aggregateId','e0000000-0000-0000-0000-000000000004','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_alice),
     2, CURRENT_TIMESTAMP),
    ('f0210000-0000-0000-0000-000000000005','Absence','e0000000-0000-0000-0000-000000000005','AbsenceStatusChanged',
     jsonb_build_object('eventId','f0210000-0000-0000-0000-000000000005','occurredAt',v_now,
       'aggregateId','e0000000-0000-0000-0000-000000000005','fromStatus','DRAFT','toStatus','SUBMITTED','changedBy',v_bob),
     2, CURRENT_TIMESTAMP)
    ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload, created_at = EXCLUDED.created_at;

    -- 10c. AbsenceStatusChanged -> APPROVED/REJECTED events (4 records, version=3)
    INSERT INTO event_store (id, aggregate_type, aggregate_id, event_type, payload, version, created_at) VALUES
    ('f0220000-0000-0000-0000-000000000001','Absence','e0000000-0000-0000-0000-000000000001','AbsenceStatusChanged',
     jsonb_build_object('eventId','f0220000-0000-0000-0000-000000000001','occurredAt',v_now,
       'aggregateId','e0000000-0000-0000-0000-000000000001','fromStatus','SUBMITTED','toStatus','APPROVED','changedBy',v_alice),
     3, CURRENT_TIMESTAMP),
    ('f0220000-0000-0000-0000-000000000002','Absence','e0000000-0000-0000-0000-000000000003','AbsenceStatusChanged',
     jsonb_build_object('eventId','f0220000-0000-0000-0000-000000000002','occurredAt',v_now,
       'aggregateId','e0000000-0000-0000-0000-000000000003','fromStatus','SUBMITTED','toStatus','APPROVED','changedBy',v_alice),
     3, CURRENT_TIMESTAMP),
    ('f0220000-0000-0000-0000-000000000003','Absence','e0000000-0000-0000-0000-000000000004','AbsenceStatusChanged',
     jsonb_build_object('eventId','f0220000-0000-0000-0000-000000000003','occurredAt',v_now,
       'aggregateId','e0000000-0000-0000-0000-000000000004','fromStatus','SUBMITTED','toStatus','APPROVED','changedBy',v_alice),
     3, CURRENT_TIMESTAMP),
    ('f0220000-0000-0000-0000-000000000004','Absence','e0000000-0000-0000-0000-000000000005','AbsenceStatusChanged',
     jsonb_build_object('eventId','f0220000-0000-0000-0000-000000000004','occurredAt',v_now,
       'aggregateId','e0000000-0000-0000-0000-000000000005','fromStatus','SUBMITTED','toStatus','REJECTED','changedBy',v_alice),
     3, CURRENT_TIMESTAMP)
    ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload, created_at = EXCLUDED.created_at;

    -- =========================================================================
    -- 11. Event Store: MonthlyApproval Events + Projection
    -- =========================================================================

    -- 11a. MonthlyApprovalCreated events (5 records, version=1)
    INSERT INTO event_store (id, aggregate_type, aggregate_id, event_type, payload, version, created_at) VALUES
    -- Bob previous fiscal month (will be APPROVED)
    ('f0300000-0000-0000-0000-000000000001','MonthlyApproval','f0c00000-0000-0000-0000-000000000001','MonthlyApprovalCreated',
     jsonb_build_object('eventId','f0300000-0000-0000-0000-000000000001','occurredAt',v_now,
       'aggregateId','f0c00000-0000-0000-0000-000000000001','memberId',v_bob,
       'fiscalMonthStart',v_pfs,'fiscalMonthEnd',v_pfe),
     1, CURRENT_TIMESTAMP),
    -- Bob current fiscal month (PENDING)
    ('f0300000-0000-0000-0000-000000000004','MonthlyApproval','f0c00000-0000-0000-0000-000000000002','MonthlyApprovalCreated',
     jsonb_build_object('eventId','f0300000-0000-0000-0000-000000000004','occurredAt',v_now,
       'aggregateId','f0c00000-0000-0000-0000-000000000002','memberId',v_bob,
       'fiscalMonthStart',v_fs,'fiscalMonthEnd',v_fe),
     1, CURRENT_TIMESTAMP),
    -- Alice current fiscal month (PENDING)
    ('f0300000-0000-0000-0000-000000000005','MonthlyApproval','f0c00000-0000-0000-0000-000000000003','MonthlyApprovalCreated',
     jsonb_build_object('eventId','f0300000-0000-0000-0000-000000000005','occurredAt',v_now,
       'aggregateId','f0c00000-0000-0000-0000-000000000003','memberId',v_alice,
       'fiscalMonthStart',v_fs,'fiscalMonthEnd',v_fe),
     1, CURRENT_TIMESTAMP),
    -- Charlie current fiscal month (PENDING)
    ('f0300000-0000-0000-0000-000000000006','MonthlyApproval','f0c00000-0000-0000-0000-000000000004','MonthlyApprovalCreated',
     jsonb_build_object('eventId','f0300000-0000-0000-0000-000000000006','occurredAt',v_now,
       'aggregateId','f0c00000-0000-0000-0000-000000000004','memberId',v_charlie,
       'fiscalMonthStart',v_fs,'fiscalMonthEnd',v_fe),
     1, CURRENT_TIMESTAMP),
    -- David current fiscal month (PENDING)
    ('f0300000-0000-0000-0000-000000000007','MonthlyApproval','f0c00000-0000-0000-0000-000000000005','MonthlyApprovalCreated',
     jsonb_build_object('eventId','f0300000-0000-0000-0000-000000000007','occurredAt',v_now,
       'aggregateId','f0c00000-0000-0000-0000-000000000005','memberId',v_david,
       'fiscalMonthStart',v_fs,'fiscalMonthEnd',v_fe),
     1, CURRENT_TIMESTAMP)
    ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload, created_at = EXCLUDED.created_at;

    -- 11b. Bob's previous month: MonthSubmittedForApproval (version=2)
    INSERT INTO event_store (id, aggregate_type, aggregate_id, event_type, payload, version, created_at) VALUES
    ('f0300000-0000-0000-0000-000000000002','MonthlyApproval','f0c00000-0000-0000-0000-000000000001','MonthSubmittedForApproval',
     jsonb_build_object('eventId','f0300000-0000-0000-0000-000000000002','occurredAt',v_now,
       'aggregateId','f0c00000-0000-0000-0000-000000000001','submittedAt',v_now,
       'submittedBy',v_bob,'workLogEntryIds','[]'::jsonb,'absenceIds','[]'::jsonb),
     2, CURRENT_TIMESTAMP)
    ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload, created_at = EXCLUDED.created_at;

    -- 11c. Bob's previous month: MonthApproved (version=3)
    INSERT INTO event_store (id, aggregate_type, aggregate_id, event_type, payload, version, created_at) VALUES
    ('f0300000-0000-0000-0000-000000000003','MonthlyApproval','f0c00000-0000-0000-0000-000000000001','MonthApproved',
     jsonb_build_object('eventId','f0300000-0000-0000-0000-000000000003','occurredAt',v_now,
       'aggregateId','f0c00000-0000-0000-0000-000000000001','reviewedAt',v_now,'reviewedBy',v_alice),
     3, CURRENT_TIMESTAMP)
    ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload, created_at = EXCLUDED.created_at;

    -- 11d. monthly_approvals_projection records (5 records)
    INSERT INTO monthly_approvals_projection
        (id, member_id, organization_id, fiscal_year, fiscal_month, status,
         submitted_at, reviewed_at, reviewed_by, rejection_reason, version, created_at, updated_at)
    VALUES
    -- Bob previous month: APPROVED
    ('f0c00000-0000-0000-0000-000000000001',
     '00000000-0000-0000-0000-000000000001', '880e8400-e29b-41d4-a716-446655440001',
     v_pfy, v_pfm, 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     '00000000-0000-0000-0000-000000000002', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Bob current month: PENDING
    ('f0c00000-0000-0000-0000-000000000002',
     '00000000-0000-0000-0000-000000000001', '880e8400-e29b-41d4-a716-446655440001',
     v_fy, v_fm, 'PENDING', NULL, NULL, NULL, NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Alice current month: PENDING
    ('f0c00000-0000-0000-0000-000000000003',
     '00000000-0000-0000-0000-000000000002', '880e8400-e29b-41d4-a716-446655440001',
     v_fy, v_fm, 'PENDING', NULL, NULL, NULL, NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Charlie current month: PENDING
    ('f0c00000-0000-0000-0000-000000000004',
     '00000000-0000-0000-0000-000000000003', '880e8400-e29b-41d4-a716-446655440001',
     v_fy, v_fm, 'PENDING', NULL, NULL, NULL, NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- David current month: PENDING
    ('f0c00000-0000-0000-0000-000000000005',
     '00000000-0000-0000-0000-000000000004', '880e8400-e29b-41d4-a716-446655440001',
     v_fy, v_fm, 'PENDING', NULL, NULL, NULL, NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (member_id, fiscal_year, fiscal_month) DO UPDATE SET
        status = EXCLUDED.status,
        submitted_at = EXCLUDED.submitted_at,
        reviewed_at = EXCLUDED.reviewed_at,
        reviewed_by = EXCLUDED.reviewed_by,
        updated_at = CURRENT_TIMESTAMP;

    -- =========================================================================
    -- Reconcile work_log_entries_projection with event store
    -- =========================================================================
    -- On subsequent seed runs, the multi-row projection INSERT may fail to
    -- update dates due to PostgreSQL snapshot isolation within a single
    -- INSERT ... ON CONFLICT statement. This reconciliation step ensures
    -- projection dates, hours, and statuses always match the event store.
    --
    -- Three-step idempotent reconciliation:
    --   Step 1: UPDATE rows whose dates/hours diverged from the event store.
    --   Step 2: INSERT projection rows for events that have no projection yet.
    --   Step 3: DELETE projection rows whose aggregate was deleted in the event store.
    -- Each step is independently idempotent, so re-running seed data is safe.

    -- Step 1: Update existing projection rows whose dates/hours diverged
    UPDATE work_log_entries_projection p
    SET work_date = CAST(e.payload->>'date' AS DATE),
        hours = CAST(e.payload->>'hours' AS DECIMAL),
        notes = e.payload->>'comment',
        updated_at = CURRENT_TIMESTAMP
    FROM event_store e
    WHERE e.aggregate_id = p.id
      AND e.event_type = 'WorkLogEntryCreated'
      AND e.aggregate_type = 'WorkLogEntry'
      AND (p.work_date != CAST(e.payload->>'date' AS DATE)
           OR p.hours != CAST(e.payload->>'hours' AS DECIMAL));

    -- Step 2: Update projection statuses from latest status change events
    UPDATE work_log_entries_projection p
    SET status = latest.to_status,
        updated_at = CURRENT_TIMESTAMP
    FROM (
        SELECT DISTINCT ON (aggregate_id)
            aggregate_id,
            payload->>'toStatus' as to_status
        FROM event_store
        WHERE event_type = 'WorkLogEntryStatusChanged'
          AND aggregate_type = 'WorkLogEntry'
        ORDER BY aggregate_id, version DESC
    ) latest
    WHERE latest.aggregate_id = p.id
      AND p.status != latest.to_status;

    -- Step 3: Insert missing projection entries from event store
    INSERT INTO work_log_entries_projection
        (id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by, version, created_at, updated_at)
    SELECT
        e.aggregate_id,
        CAST(e.payload->>'memberId' AS UUID),
        m.organization_id,
        CAST(e.payload->>'projectId' AS UUID),
        CAST(e.payload->>'date' AS DATE),
        CAST(e.payload->>'hours' AS DECIMAL),
        e.payload->>'comment',
        COALESCE(
            (SELECT s.payload->>'toStatus'
             FROM event_store s
             WHERE s.aggregate_id = e.aggregate_id
               AND s.event_type = 'WorkLogEntryStatusChanged'
             ORDER BY s.version DESC LIMIT 1),
            'DRAFT'
        ),
        CAST(e.payload->>'enteredBy' AS UUID),
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    FROM event_store e
    JOIN members m ON CAST(e.payload->>'memberId' AS UUID) = m.id
    WHERE e.event_type = 'WorkLogEntryCreated'
      AND e.aggregate_type = 'WorkLogEntry'
      AND e.aggregate_id NOT IN (
          SELECT aggregate_id FROM event_store
          WHERE event_type = 'WorkLogEntryDeleted' AND aggregate_type = 'WorkLogEntry'
      )
      AND NOT EXISTS (
          SELECT 1 FROM work_log_entries_projection p WHERE p.id = e.aggregate_id
      )
    ON CONFLICT (member_id, project_id, work_date) DO UPDATE SET
        id = EXCLUDED.id,
        hours = EXCLUDED.hours,
        notes = EXCLUDED.notes,
        status = EXCLUDED.status,
        entered_by = EXCLUDED.entered_by,
        organization_id = EXCLUDED.organization_id,
        updated_at = CURRENT_TIMESTAMP;

    -- =========================================================================
    -- Clear stale calendar/summary cache before rebuilding
    -- =========================================================================
    DELETE FROM monthly_calendar_projection;
    DELETE FROM monthly_summary_projection;

    -- =========================================================================
    -- 12. Monthly Calendar Projection (dynamically computed per member)
    -- =========================================================================
    FOR v_member IN
        SELECT DISTINCT member_id FROM work_log_entries_projection
    LOOP
        -- Build calendar_data JSONB from work log entries in current fiscal period
        SELECT COALESCE(jsonb_agg(
            jsonb_build_object(
                'date', day_data.work_date::text,
                'totalHours', day_data.day_total,
                'entries', day_data.entries
            ) ORDER BY day_data.work_date
        ), '[]'::jsonb)
        INTO v_cal
        FROM (
            SELECT
                work_date,
                SUM(hours)::decimal as day_total,
                jsonb_agg(jsonb_build_object(
                    'projectId', project_id::text,
                    'hours', hours,
                    'status', status
                )) as entries
            FROM work_log_entries_projection
            WHERE member_id = v_member
              AND work_date BETWEEN v_fs::date AND v_fe::date
            GROUP BY work_date
        ) day_data;

        -- Total hours
        SELECT COALESCE(SUM(hours), 0) INTO v_hrs
        FROM work_log_entries_projection
        WHERE member_id = v_member
          AND work_date BETWEEN v_fs::date AND v_fe::date;

        INSERT INTO monthly_calendar_projection
            (id, member_id, fiscal_year, fiscal_month, calendar_data, total_hours, last_updated)
        VALUES (gen_random_uuid(), v_member, v_fy, v_fm, v_cal, v_hrs, CURRENT_TIMESTAMP)
        ON CONFLICT (member_id, fiscal_year, fiscal_month) DO UPDATE SET
            calendar_data = EXCLUDED.calendar_data,
            total_hours = EXCLUDED.total_hours,
            last_updated = CURRENT_TIMESTAMP;

        -- =====================================================================
        -- 13. Monthly Summary Projection (project breakdown per member)
        -- =====================================================================
        SELECT COALESCE(jsonb_agg(
            jsonb_build_object(
                'projectId', p_data.project_id::text,
                'projectName', p_data.project_name,
                'hours', p_data.proj_hours,
                'percentage', CASE WHEN v_hrs > 0
                    THEN ROUND(p_data.proj_hours / v_hrs * 100, 1)
                    ELSE 0 END
            )
        ), '[]'::jsonb)
        INTO v_sum
        FROM (
            SELECT
                w.project_id,
                p.name as project_name,
                SUM(w.hours)::decimal as proj_hours
            FROM work_log_entries_projection w
            JOIN projects p ON w.project_id = p.id
            WHERE w.member_id = v_member
              AND w.work_date BETWEEN v_fs::date AND v_fe::date
            GROUP BY w.project_id, p.name
        ) p_data;

        INSERT INTO monthly_summary_projection
            (id, member_id, fiscal_year, fiscal_month, project_summaries, total_hours, last_updated)
        VALUES (gen_random_uuid(), v_member, v_fy, v_fm, v_sum, v_hrs, CURRENT_TIMESTAMP)
        ON CONFLICT (member_id, fiscal_year, fiscal_month) DO UPDATE SET
            project_summaries = EXCLUDED.project_summaries,
            total_hours = EXCLUDED.total_hours,
            last_updated = CURRENT_TIMESTAMP;
    END LOOP;
END $$;
@@

-- ============================================================================
-- 14. Roles & Permissions (Authentication & Authorization)
-- ============================================================================

-- Seed Roles (ADMIN, USER, MODERATOR)
INSERT INTO roles (id, name, description) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN', 'System administrator with full access'),
    ('00000000-0000-0000-0000-000000000002', 'USER', 'Standard user with limited access'),
    ('00000000-0000-0000-0000-000000000003', 'MODERATOR', 'Content moderator with approval permissions')
ON CONFLICT (name) DO UPDATE SET
    description = EXCLUDED.description;
@@

-- Seed Permissions
INSERT INTO permissions (name, description) VALUES
    ('user.create', 'Create new users'),
    ('user.edit', 'Edit existing user details'),
    ('user.delete', 'Delete users'),
    ('user.view', 'View user details'),
    ('user.assign_role', 'Assign roles to users'),
    ('role.create', 'Create new roles'),
    ('role.edit', 'Edit existing roles'),
    ('role.delete', 'Delete roles'),
    ('role.view', 'View role details'),
    ('report.view', 'View reports'),
    ('report.export', 'Export reports'),
    ('audit_log.view', 'View audit logs'),
    ('admin.access', 'Access admin panel'),
    ('work_log.create', 'Create work log entries'),
    ('work_log.edit', 'Edit own work log entries'),
    ('work_log.edit_all', 'Edit any user''s work log entries'),
    ('work_log.view', 'View work log entries'),
    ('work_log.approve', 'Approve work log entries')
ON CONFLICT (name) DO UPDATE SET
    description = EXCLUDED.description;
@@

-- ADMIN role: all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000001', id FROM permissions
ON CONFLICT DO NOTHING;
@@

-- USER role: limited permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000002', id FROM permissions
WHERE name IN (
    'user.view',
    'report.view',
    'work_log.create',
    'work_log.edit',
    'work_log.view'
)
ON CONFLICT DO NOTHING;
@@

-- MODERATOR role: approval + view permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000003', id FROM permissions
WHERE name IN (
    'user.view',
    'report.view',
    'report.export',
    'work_log.view',
    'work_log.approve',
    'audit_log.view'
)
ON CONFLICT DO NOTHING;
@@

-- ============================================================================
-- 15. Users (core 4 members + sysadmin)
-- ============================================================================
-- All users share password: Password1
-- BCrypt hash: $2b$12$gu2gIbw9xeZOjbqdlXLjo.uAofiuh/z.dFMOP1ARC0BE/88piI06u

INSERT INTO users (id, email, hashed_password, name, role_id, account_status, failed_login_attempts, email_verified_at)
VALUES
    -- Bob Engineer (USER)
    ('00000000-0000-0000-0000-000000000001',
     'bob.engineer@miometry.example.com',
     '$2b$12$gu2gIbw9xeZOjbqdlXLjo.uAofiuh/z.dFMOP1ARC0BE/88piI06u',
     'Bob Engineer',
     '00000000-0000-0000-0000-000000000002',
     'active', 0, NOW()),
    -- Alice Manager (ADMIN)
    ('00000000-0000-0000-0000-000000000002',
     'alice.manager@miometry.example.com',
     '$2b$12$gu2gIbw9xeZOjbqdlXLjo.uAofiuh/z.dFMOP1ARC0BE/88piI06u',
     'Alice Manager',
     '00000000-0000-0000-0000-000000000001',
     'active', 0, NOW()),
    -- Charlie Engineer (USER)
    ('00000000-0000-0000-0000-000000000003',
     'charlie.engineer@miometry.example.com',
     '$2b$12$gu2gIbw9xeZOjbqdlXLjo.uAofiuh/z.dFMOP1ARC0BE/88piI06u',
     'Charlie Engineer',
     '00000000-0000-0000-0000-000000000002',
     'active', 0, NOW()),
    -- David Independent (USER)
    ('00000000-0000-0000-0000-000000000004',
     'david.independent@miometry.example.com',
     '$2b$12$gu2gIbw9xeZOjbqdlXLjo.uAofiuh/z.dFMOP1ARC0BE/88piI06u',
     'David Independent',
     '00000000-0000-0000-0000-000000000002',
     'active', 0, NOW())
ON CONFLICT (email) DO UPDATE SET
    hashed_password = EXCLUDED.hashed_password,
    name = EXCLUDED.name,
    account_status = EXCLUDED.account_status,
    email_verified_at = EXCLUDED.email_verified_at;
@@

-- System Admin member + user (global admin operations)
INSERT INTO members (
    id, tenant_id, organization_id, email, display_name, manager_id, is_active, version, created_at, updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000005',
    '550e8400-e29b-41d4-a716-446655440001',
    '880e8400-e29b-41d4-a716-446655440001',
    'sysadmin@miometry.example.com',
    'System Admin',
    NULL,
    true, 0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;
@@

INSERT INTO users (id, email, hashed_password, name, role_id, account_status, failed_login_attempts, email_verified_at)
VALUES (
    '00000000-0000-0000-0000-000000000005',
    'sysadmin@miometry.example.com',
    '$2b$12$gu2gIbw9xeZOjbqdlXLjo.uAofiuh/z.dFMOP1ARC0BE/88piI06u',
    'System Admin',
    (SELECT id FROM roles WHERE name = 'SYSTEM_ADMIN'),
    'active', 0, NOW()
)
ON CONFLICT (email) DO UPDATE SET
    hashed_password = EXCLUDED.hashed_password,
    name = EXCLUDED.name,
    role_id = EXCLUDED.role_id,
    account_status = EXCLUDED.account_status,
    email_verified_at = EXCLUDED.email_verified_at;
@@

-- ============================================================================
-- 16. Admin Role Assignments
-- ============================================================================

-- Assign Alice to SUPERVISOR role (she manages Bob and Charlie)
UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'SUPERVISOR')
WHERE email = 'alice.manager@miometry.example.com';
@@

-- Assign David to TENANT_ADMIN role
UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'TENANT_ADMIN')
WHERE email = 'david.independent@miometry.example.com';
@@

-- ============================================================================
-- 17. Member-Project Assignments (core 6 assignments)
-- ============================================================================

-- Bob -> WORKLOG-DEV (active)
INSERT INTO member_project_assignments (
    id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active
)
VALUES (
    'f0000000-0000-0000-0000-000000000001',
    '550e8400-e29b-41d4-a716-446655440001',
    '00000000-0000-0000-0000-000000000001',
    '990e8400-e29b-41d4-a716-446655440001',
    CURRENT_TIMESTAMP,
    '00000000-0000-0000-0000-000000000002',
    true
)
ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET
    is_active = EXCLUDED.is_active,
    assigned_at = EXCLUDED.assigned_at,
    assigned_by = EXCLUDED.assigned_by;
@@

-- Bob -> INFRA-OPS (active)
INSERT INTO member_project_assignments (
    id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active
)
VALUES (
    'f0000000-0000-0000-0000-000000000002',
    '550e8400-e29b-41d4-a716-446655440001',
    '00000000-0000-0000-0000-000000000001',
    '990e8400-e29b-41d4-a716-446655440002',
    CURRENT_TIMESTAMP,
    '00000000-0000-0000-0000-000000000002',
    true
)
ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET
    is_active = EXCLUDED.is_active,
    assigned_at = EXCLUDED.assigned_at,
    assigned_by = EXCLUDED.assigned_by;
@@

-- Alice -> WORKLOG-DEV (manager has access to all)
INSERT INTO member_project_assignments (
    id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active
)
VALUES (
    'f0000000-0000-0000-0000-000000000003',
    '550e8400-e29b-41d4-a716-446655440001',
    '00000000-0000-0000-0000-000000000002',
    '990e8400-e29b-41d4-a716-446655440001',
    CURRENT_TIMESTAMP,
    NULL,
    true
)
ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET
    is_active = EXCLUDED.is_active,
    assigned_at = EXCLUDED.assigned_at,
    assigned_by = EXCLUDED.assigned_by;
@@

-- Alice -> INFRA-OPS
INSERT INTO member_project_assignments (
    id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active
)
VALUES (
    'f0000000-0000-0000-0000-000000000004',
    '550e8400-e29b-41d4-a716-446655440001',
    '00000000-0000-0000-0000-000000000002',
    '990e8400-e29b-41d4-a716-446655440002',
    CURRENT_TIMESTAMP,
    NULL,
    true
)
ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET
    is_active = EXCLUDED.is_active,
    assigned_at = EXCLUDED.assigned_at,
    assigned_by = EXCLUDED.assigned_by;
@@

-- Charlie -> WORKLOG-DEV only (junior engineer)
INSERT INTO member_project_assignments (
    id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active
)
VALUES (
    'f0000000-0000-0000-0000-000000000005',
    '550e8400-e29b-41d4-a716-446655440001',
    '00000000-0000-0000-0000-000000000003',
    '990e8400-e29b-41d4-a716-446655440001',
    CURRENT_TIMESTAMP,
    '00000000-0000-0000-0000-000000000002',
    true
)
ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET
    is_active = EXCLUDED.is_active,
    assigned_at = EXCLUDED.assigned_at,
    assigned_by = EXCLUDED.assigned_by;
@@

-- David -> INFRA-OPS only (infrastructure specialist)
INSERT INTO member_project_assignments (
    id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active
)
VALUES (
    'f0000000-0000-0000-0000-000000000006',
    '550e8400-e29b-41d4-a716-446655440001',
    '00000000-0000-0000-0000-000000000004',
    '990e8400-e29b-41d4-a716-446655440002',
    CURRENT_TIMESTAMP,
    '00000000-0000-0000-0000-000000000002',
    true
)
ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET
    is_active = EXCLUDED.is_active,
    assigned_at = EXCLUDED.assigned_at,
    assigned_by = EXCLUDED.assigned_by;
@@

-- ============================================================================
-- 18. ACME Tenant (cross-tenant testing)
-- ============================================================================

INSERT INTO tenant (id, code, name, status, version, created_at, updated_at)
VALUES (
    '550e8400-e29b-41d4-a716-446655440002',
    'ACME',
    'ACME Corporation',
    'ACTIVE',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;
@@

-- ACME Fiscal Year Pattern (Jan-Dec calendar year)
INSERT INTO fiscal_year_pattern (id, tenant_id, organization_id, name, start_month, start_day, version, created_at)
VALUES (
    '770e8400-e29b-41d4-a716-446655440002',
    '550e8400-e29b-41d4-a716-446655440002',
    NULL,
    'Standard Calendar Year (January)',
    1, 1, 0, CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    start_month = EXCLUDED.start_month,
    start_day = EXCLUDED.start_day;
@@

-- ACME Monthly Period Pattern (1st-end standard)
INSERT INTO monthly_period_pattern (id, tenant_id, organization_id, name, start_day, version, created_at)
VALUES (
    '660e8400-e29b-41d4-a716-446655440002',
    '550e8400-e29b-41d4-a716-446655440002',
    NULL,
    'Standard Monthly Period (1st)',
    1, 0, CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    start_day = EXCLUDED.start_day;
@@

-- ACME Organization: Sales Department
INSERT INTO organization (
    id, tenant_id, parent_id, code, name, level, status, version,
    fiscal_year_pattern_id, monthly_period_pattern_id, created_at, updated_at
)
VALUES (
    '880e8400-e29b-41d4-a716-446655440002',
    '550e8400-e29b-41d4-a716-446655440002',
    NULL,
    'SALES',
    'Sales Department',
    1,
    'ACTIVE',
    0,
    '770e8400-e29b-41d4-a716-446655440002',
    '660e8400-e29b-41d4-a716-446655440002',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;
@@

-- ============================================================================
-- 19. Extended Members (8 additional members)
-- ============================================================================

-- Eve Frontend: USER, reports to Alice, active
INSERT INTO members (id, tenant_id, organization_id, email, display_name, manager_id, is_active, version, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000006',
    '550e8400-e29b-41d4-a716-446655440001',
    '880e8400-e29b-41d4-a716-446655440001',
    'eve.frontend@miometry.example.com',
    'Eve Frontend',
    '00000000-0000-0000-0000-000000000002',
    true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    manager_id = EXCLUDED.manager_id,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;
@@

-- Frank Backend: USER, reports to Alice, active
INSERT INTO members (id, tenant_id, organization_id, email, display_name, manager_id, is_active, version, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000007',
    '550e8400-e29b-41d4-a716-446655440001',
    '880e8400-e29b-41d4-a716-446655440001',
    'frank.backend@miometry.example.com',
    'Frank Backend',
    '00000000-0000-0000-0000-000000000002',
    true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    manager_id = EXCLUDED.manager_id,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;
@@

-- Grace Designer: USER, was managed by Alice, INACTIVE (left the company)
INSERT INTO members (id, tenant_id, organization_id, email, display_name, manager_id, is_active, version, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000008',
    '550e8400-e29b-41d4-a716-446655440001',
    '880e8400-e29b-41d4-a716-446655440001',
    'grace.designer@miometry.example.com',
    'Grace Designer',
    '00000000-0000-0000-0000-000000000002',
    false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    manager_id = EXCLUDED.manager_id,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;
@@

-- Hank QA Lead: SUPERVISOR, no manager, active
INSERT INTO members (id, tenant_id, organization_id, email, display_name, manager_id, is_active, version, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000009',
    '550e8400-e29b-41d4-a716-446655440001',
    '880e8400-e29b-41d4-a716-446655440001',
    'hank.qalead@miometry.example.com',
    'Hank QA Lead',
    NULL,
    true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;
@@

-- Ivy Support: USER, reports to Hank, active
INSERT INTO members (id, tenant_id, organization_id, email, display_name, manager_id, is_active, version, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-00000000000a',
    '550e8400-e29b-41d4-a716-446655440001',
    '880e8400-e29b-41d4-a716-446655440001',
    'ivy.support@miometry.example.com',
    'Ivy Support',
    '00000000-0000-0000-0000-000000000009',
    true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    manager_id = EXCLUDED.manager_id,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;
@@

-- Kevin Intern: USER, reports to Hank, active
INSERT INTO members (id, tenant_id, organization_id, email, display_name, manager_id, is_active, version, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-00000000000b',
    '550e8400-e29b-41d4-a716-446655440001',
    '880e8400-e29b-41d4-a716-446655440001',
    'kevin.intern@miometry.example.com',
    'Kevin Intern',
    '00000000-0000-0000-0000-000000000009',
    true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    manager_id = EXCLUDED.manager_id,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;
@@

-- Jack: ACME TENANT_ADMIN, no manager
INSERT INTO members (id, tenant_id, organization_id, email, display_name, manager_id, is_active, version, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-00000000000c',
    '550e8400-e29b-41d4-a716-446655440002',
    '880e8400-e29b-41d4-a716-446655440002',
    'jack.admin@acme.example.com',
    'Jack ACME Admin',
    NULL,
    true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;
@@

-- Kim: ACME USER, reports to Jack
INSERT INTO members (id, tenant_id, organization_id, email, display_name, manager_id, is_active, version, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-00000000000d',
    '550e8400-e29b-41d4-a716-446655440002',
    '880e8400-e29b-41d4-a716-446655440002',
    'kim.staff@acme.example.com',
    'Kim ACME Staff',
    '00000000-0000-0000-0000-00000000000c',
    true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    manager_id = EXCLUDED.manager_id,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;
@@

-- ============================================================================
-- 20. Extended Users (user accounts for extended members)
-- ============================================================================
-- All share password: Password1
-- BCrypt: $2b$12$gu2gIbw9xeZOjbqdlXLjo.uAofiuh/z.dFMOP1ARC0BE/88piI06u

INSERT INTO users (id, email, hashed_password, name, role_id, account_status, failed_login_attempts, email_verified_at)
VALUES
    -- Eve (USER)
    ('00000000-0000-0000-0000-000000000006',
     'eve.frontend@miometry.example.com',
     '$2b$12$gu2gIbw9xeZOjbqdlXLjo.uAofiuh/z.dFMOP1ARC0BE/88piI06u',
     'Eve Frontend',
     (SELECT id FROM roles WHERE name = 'USER'),
     'active', 0, NOW()),
    -- Frank (USER)
    ('00000000-0000-0000-0000-000000000007',
     'frank.backend@miometry.example.com',
     '$2b$12$gu2gIbw9xeZOjbqdlXLjo.uAofiuh/z.dFMOP1ARC0BE/88piI06u',
     'Frank Backend',
     (SELECT id FROM roles WHERE name = 'USER'),
     'active', 0, NOW()),
    -- Grace (USER, inactive member but user account still active for testing)
    ('00000000-0000-0000-0000-000000000008',
     'grace.designer@miometry.example.com',
     '$2b$12$gu2gIbw9xeZOjbqdlXLjo.uAofiuh/z.dFMOP1ARC0BE/88piI06u',
     'Grace Designer',
     (SELECT id FROM roles WHERE name = 'USER'),
     'active', 0, NOW()),
    -- Hank (SUPERVISOR)
    ('00000000-0000-0000-0000-000000000009',
     'hank.qalead@miometry.example.com',
     '$2b$12$gu2gIbw9xeZOjbqdlXLjo.uAofiuh/z.dFMOP1ARC0BE/88piI06u',
     'Hank QA Lead',
     (SELECT id FROM roles WHERE name = 'SUPERVISOR'),
     'active', 0, NOW()),
    -- Ivy (USER)
    ('00000000-0000-0000-0000-00000000000a',
     'ivy.support@miometry.example.com',
     '$2b$12$gu2gIbw9xeZOjbqdlXLjo.uAofiuh/z.dFMOP1ARC0BE/88piI06u',
     'Ivy Support',
     (SELECT id FROM roles WHERE name = 'USER'),
     'active', 0, NOW()),
    -- Kevin (USER)
    ('00000000-0000-0000-0000-00000000000b',
     'kevin.intern@miometry.example.com',
     '$2b$12$gu2gIbw9xeZOjbqdlXLjo.uAofiuh/z.dFMOP1ARC0BE/88piI06u',
     'Kevin Intern',
     (SELECT id FROM roles WHERE name = 'USER'),
     'active', 0, NOW()),
    -- Jack (ACME TENANT_ADMIN)
    ('00000000-0000-0000-0000-00000000000c',
     'jack.admin@acme.example.com',
     '$2b$12$gu2gIbw9xeZOjbqdlXLjo.uAofiuh/z.dFMOP1ARC0BE/88piI06u',
     'Jack ACME Admin',
     (SELECT id FROM roles WHERE name = 'TENANT_ADMIN'),
     'active', 0, NOW()),
    -- Kim (ACME USER)
    ('00000000-0000-0000-0000-00000000000d',
     'kim.staff@acme.example.com',
     '$2b$12$gu2gIbw9xeZOjbqdlXLjo.uAofiuh/z.dFMOP1ARC0BE/88piI06u',
     'Kim ACME Staff',
     (SELECT id FROM roles WHERE name = 'USER'),
     'active', 0, NOW())
ON CONFLICT (email) DO UPDATE SET
    hashed_password = EXCLUDED.hashed_password,
    name = EXCLUDED.name,
    role_id = EXCLUDED.role_id,
    account_status = EXCLUDED.account_status,
    email_verified_at = EXCLUDED.email_verified_at;
@@

-- ============================================================================
-- 21. Additional Projects
-- ============================================================================

-- Miometry: MOBILE-APP (active, with end date)
INSERT INTO projects (id, tenant_id, code, name, is_active, valid_from, valid_until, created_at, updated_at)
VALUES (
    '990e8400-e29b-41d4-a716-446655440004',
    '550e8400-e29b-41d4-a716-446655440001',
    'MOBILE-APP',
    'Mobile Application',
    true, '2026-01-01', '2026-06-30',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, code = EXCLUDED.code,
    is_active = EXCLUDED.is_active,
    valid_from = EXCLUDED.valid_from, valid_until = EXCLUDED.valid_until,
    updated_at = CURRENT_TIMESTAMP;
@@

-- Miometry: DATA-ANLZ (active, no end date)
INSERT INTO projects (id, tenant_id, code, name, is_active, valid_from, valid_until, created_at, updated_at)
VALUES (
    '990e8400-e29b-41d4-a716-446655440005',
    '550e8400-e29b-41d4-a716-446655440001',
    'DATA-ANLZ',
    'Data Analytics Platform',
    true, '2025-07-01', NULL,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, code = EXCLUDED.code,
    is_active = EXCLUDED.is_active,
    valid_from = EXCLUDED.valid_from, valid_until = EXCLUDED.valid_until,
    updated_at = CURRENT_TIMESTAMP;
@@

-- Miometry: RESEARCH-AI (INACTIVE, ended)
INSERT INTO projects (id, tenant_id, code, name, is_active, valid_from, valid_until, created_at, updated_at)
VALUES (
    '990e8400-e29b-41d4-a716-446655440006',
    '550e8400-e29b-41d4-a716-446655440001',
    'RESEARCH-AI',
    'AI Research Project',
    false, '2024-04-01', '2025-03-31',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, code = EXCLUDED.code,
    is_active = EXCLUDED.is_active,
    valid_from = EXCLUDED.valid_from, valid_until = EXCLUDED.valid_until,
    updated_at = CURRENT_TIMESTAMP;
@@

-- ACME: SALES-CRM (active)
INSERT INTO projects (id, tenant_id, code, name, is_active, valid_from, valid_until, created_at, updated_at)
VALUES (
    '990e8400-e29b-41d4-a716-446655440007',
    '550e8400-e29b-41d4-a716-446655440002',
    'SALES-CRM',
    'Sales CRM System',
    true, '2025-06-01', NULL,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, code = EXCLUDED.code,
    is_active = EXCLUDED.is_active,
    valid_from = EXCLUDED.valid_from, valid_until = EXCLUDED.valid_until,
    updated_at = CURRENT_TIMESTAMP;
@@

-- ============================================================================
-- 22. Extended Member-Project Assignments (10 assignments)
-- ============================================================================

-- Eve -> WORKLOG-DEV
INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
VALUES ('f0000000-0000-0000-0000-000000000010', '550e8400-e29b-41d4-a716-446655440001',
        '00000000-0000-0000-0000-000000000006', '990e8400-e29b-41d4-a716-446655440001',
        CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000002', true)
ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;
@@

-- Eve -> MOBILE-APP
INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
VALUES ('f0000000-0000-0000-0000-000000000011', '550e8400-e29b-41d4-a716-446655440001',
        '00000000-0000-0000-0000-000000000006', '990e8400-e29b-41d4-a716-446655440004',
        CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000002', true)
ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;
@@

-- Frank -> INFRA-OPS
INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
VALUES ('f0000000-0000-0000-0000-000000000012', '550e8400-e29b-41d4-a716-446655440001',
        '00000000-0000-0000-0000-000000000007', '990e8400-e29b-41d4-a716-446655440002',
        CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000002', true)
ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;
@@

-- Frank -> DATA-ANLZ
INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
VALUES ('f0000000-0000-0000-0000-000000000013', '550e8400-e29b-41d4-a716-446655440001',
        '00000000-0000-0000-0000-000000000007', '990e8400-e29b-41d4-a716-446655440005',
        CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000002', true)
ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;
@@

-- Hank -> WORKLOG-DEV
INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
VALUES ('f0000000-0000-0000-0000-000000000014', '550e8400-e29b-41d4-a716-446655440001',
        '00000000-0000-0000-0000-000000000009', '990e8400-e29b-41d4-a716-446655440001',
        CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000004', true)
ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;
@@

-- Hank -> DATA-ANLZ
INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
VALUES ('f0000000-0000-0000-0000-000000000015', '550e8400-e29b-41d4-a716-446655440001',
        '00000000-0000-0000-0000-000000000009', '990e8400-e29b-41d4-a716-446655440005',
        CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000004', true)
ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;
@@

-- Ivy -> INFRA-OPS
INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
VALUES ('f0000000-0000-0000-0000-000000000016', '550e8400-e29b-41d4-a716-446655440001',
        '00000000-0000-0000-0000-00000000000a', '990e8400-e29b-41d4-a716-446655440002',
        CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000009', true)
ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;
@@

-- Kevin -> MOBILE-APP
INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
VALUES ('f0000000-0000-0000-0000-000000000017', '550e8400-e29b-41d4-a716-446655440001',
        '00000000-0000-0000-0000-00000000000b', '990e8400-e29b-41d4-a716-446655440004',
        CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000009', true)
ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;
@@

-- Jack -> SALES-CRM (ACME)
INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
VALUES ('f0000000-0000-0000-0000-000000000018', '550e8400-e29b-41d4-a716-446655440002',
        '00000000-0000-0000-0000-00000000000c', '990e8400-e29b-41d4-a716-446655440007',
        CURRENT_TIMESTAMP, NULL, true)
ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;
@@

-- Kim -> SALES-CRM (ACME)
INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
VALUES ('f0000000-0000-0000-0000-000000000019', '550e8400-e29b-41d4-a716-446655440002',
        '00000000-0000-0000-0000-00000000000d', '990e8400-e29b-41d4-a716-446655440007',
        CURRENT_TIMESTAMP, '00000000-0000-0000-0000-00000000000c', true)
ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;
@@

-- ============================================================================
-- 23. Extended Work Log Entries (SUBMITTED, for approval dashboard testing)
-- ============================================================================

-- Eve's entries
INSERT INTO work_log_entries_projection (
    id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by, version, created_at, updated_at
) VALUES
    ('e1000000-0000-0000-0000-000000000001',
     '00000000-0000-0000-0000-000000000006',
     '880e8400-e29b-41d4-a716-446655440001',
     '990e8400-e29b-41d4-a716-446655440001',
     CURRENT_DATE - INTERVAL '1 day', 8.00,
     'React component refactoring and code review',
     'SUBMITTED', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('e1000000-0000-0000-0000-000000000002',
     '00000000-0000-0000-0000-000000000006',
     '880e8400-e29b-41d4-a716-446655440001',
     '990e8400-e29b-41d4-a716-446655440004',
     CURRENT_DATE - INTERVAL '2 days', 7.00,
     'Mobile app wireframe and prototype implementation',
     'SUBMITTED', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('e1000000-0000-0000-0000-000000000003',
     '00000000-0000-0000-0000-000000000006',
     '880e8400-e29b-41d4-a716-446655440001',
     '990e8400-e29b-41d4-a716-446655440001',
     CURRENT_DATE, 4.50,
     'Morning standup and feature development',
     'DRAFT', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
    work_date = EXCLUDED.work_date, hours = EXCLUDED.hours,
    notes = EXCLUDED.notes, status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;
@@

-- Frank's entries
INSERT INTO work_log_entries_projection (
    id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by, version, created_at, updated_at
) VALUES
    ('e1000000-0000-0000-0000-000000000004',
     '00000000-0000-0000-0000-000000000007',
     '880e8400-e29b-41d4-a716-446655440001',
     '990e8400-e29b-41d4-a716-446655440002',
     CURRENT_DATE - INTERVAL '1 day', 8.00,
     'CI/CD pipeline optimization and Docker image cleanup',
     'SUBMITTED', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('e1000000-0000-0000-0000-000000000005',
     '00000000-0000-0000-0000-000000000007',
     '880e8400-e29b-41d4-a716-446655440001',
     '990e8400-e29b-41d4-a716-446655440005',
     CURRENT_DATE - INTERVAL '2 days', 6.50,
     'ETL pipeline development and data quality checks',
     'SUBMITTED', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('e1000000-0000-0000-0000-000000000006',
     '00000000-0000-0000-0000-000000000007',
     '880e8400-e29b-41d4-a716-446655440001',
     '990e8400-e29b-41d4-a716-446655440002',
     CURRENT_DATE, 3.00,
     'Kubernetes deployment configuration',
     'DRAFT', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
    work_date = EXCLUDED.work_date, hours = EXCLUDED.hours,
    notes = EXCLUDED.notes, status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;
@@

-- Ivy's entries
INSERT INTO work_log_entries_projection (
    id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by, version, created_at, updated_at
) VALUES
    ('e1000000-0000-0000-0000-000000000007',
     '00000000-0000-0000-0000-00000000000a',
     '880e8400-e29b-41d4-a716-446655440001',
     '990e8400-e29b-41d4-a716-446655440002',
     CURRENT_DATE - INTERVAL '1 day', 6.00,
     'Customer support ticket triage and resolution',
     'SUBMITTED', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('e1000000-0000-0000-0000-000000000008',
     '00000000-0000-0000-0000-00000000000a',
     '880e8400-e29b-41d4-a716-446655440001',
     '990e8400-e29b-41d4-a716-446655440002',
     CURRENT_DATE, 5.00,
     'Internal documentation and FAQ update',
     'DRAFT', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
    work_date = EXCLUDED.work_date, hours = EXCLUDED.hours,
    notes = EXCLUDED.notes, status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;
@@

-- Kevin's entry
INSERT INTO work_log_entries_projection (
    id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by, version, created_at, updated_at
) VALUES
    ('e1000000-0000-0000-0000-000000000009',
     '00000000-0000-0000-0000-00000000000b',
     '880e8400-e29b-41d4-a716-446655440001',
     '990e8400-e29b-41d4-a716-446655440004',
     CURRENT_DATE - INTERVAL '1 day', 7.00,
     'QA test case writing and manual testing',
     'SUBMITTED', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
    work_date = EXCLUDED.work_date, hours = EXCLUDED.hours,
    notes = EXCLUDED.notes, status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;
@@

-- ACME: Kim's entry
INSERT INTO work_log_entries_projection (
    id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by, version, created_at, updated_at
) VALUES
    ('e1000000-0000-0000-0000-00000000000a',
     '00000000-0000-0000-0000-00000000000d',
     '880e8400-e29b-41d4-a716-446655440002',
     '990e8400-e29b-41d4-a716-446655440007',
     CURRENT_DATE - INTERVAL '1 day', 8.00,
     'CRM customer data migration preparation',
     'SUBMITTED', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
    work_date = EXCLUDED.work_date, hours = EXCLUDED.hours,
    notes = EXCLUDED.notes, status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;
@@

-- ============================================================================
-- 24. Daily Entry Approvals (Alice approves/rejects entries)
-- ============================================================================

INSERT INTO daily_entry_approvals (id, work_log_entry_id, member_id, supervisor_id, status, comment, created_at, updated_at)
VALUES
    -- Alice APPROVED Bob's entry from 2 days ago
    ('da000000-0000-0000-0000-000000000001',
     'a0000000-0000-0000-0000-000000000003',
     '00000000-0000-0000-0000-000000000001',
     '00000000-0000-0000-0000-000000000002',
     'APPROVED', 'Looks good',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Alice REJECTED Bob's entry from 4 days ago (excessive hours)
    ('da000000-0000-0000-0000-000000000002',
     'a0000000-0000-0000-0000-000000000006',
     '00000000-0000-0000-0000-000000000001',
     '00000000-0000-0000-0000-000000000002',
     'REJECTED', 'Please correct the hours - 12 hours seems excessive for a single day',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Alice APPROVED Charlie's entry from 2 days ago
    ('da000000-0000-0000-0000-000000000003',
     'b0000000-0000-0000-0000-000000000003',
     '00000000-0000-0000-0000-000000000003',
     '00000000-0000-0000-0000-000000000002',
     'APPROVED', NULL,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    comment = EXCLUDED.comment,
    updated_at = CURRENT_TIMESTAMP;
@@

-- ============================================================================
-- 25. In-App Notifications
-- ============================================================================

INSERT INTO in_app_notifications (id, recipient_member_id, type, reference_id, title, message, is_read, created_at)
VALUES
    -- Bob notified of daily approval
    ('a0a00000-0000-0000-0000-000000000001',
     '00000000-0000-0000-0000-000000000001',
     'DAILY_APPROVED',
     'da000000-0000-0000-0000-000000000001',
     'Entry approved',
     'Your entry for Sprint planning and story estimation was approved by Alice Manager',
     false,
     CURRENT_TIMESTAMP - INTERVAL '1 hour'),
    -- Bob notified of daily rejection
    ('a0a00000-0000-0000-0000-000000000002',
     '00000000-0000-0000-0000-000000000001',
     'DAILY_REJECTED',
     'da000000-0000-0000-0000-000000000002',
     'Entry rejected',
     'Your entry for Feature development (overtime) was rejected by Alice Manager: Please correct the hours',
     false,
     CURRENT_TIMESTAMP - INTERVAL '30 minutes'),
    -- Charlie notified of daily approval
    ('a0a00000-0000-0000-0000-000000000003',
     '00000000-0000-0000-0000-000000000003',
     'DAILY_APPROVED',
     'da000000-0000-0000-0000-000000000003',
     'Entry approved',
     'Your entry for UI/UX implementation and styling was approved by Alice Manager',
     true,
     CURRENT_TIMESTAMP - INTERVAL '2 hours'),
    -- Alice: pending approval notifications
    ('a0a00000-0000-0000-0000-000000000010',
     '00000000-0000-0000-0000-000000000002',
     'DAILY_SUBMITTED',
     'e1000000-0000-0000-0000-000000000001',
     'New entry submitted',
     'Eve Frontend submitted a work log entry for review',
     false,
     CURRENT_TIMESTAMP - INTERVAL '3 hours'),
    ('a0a00000-0000-0000-0000-000000000011',
     '00000000-0000-0000-0000-000000000002',
     'DAILY_SUBMITTED',
     'e1000000-0000-0000-0000-000000000004',
     'New entry submitted',
     'Frank Backend submitted a work log entry for review',
     false,
     CURRENT_TIMESTAMP - INTERVAL '2 hours'),
    -- Hank: pending approval notifications
    ('a0a00000-0000-0000-0000-000000000012',
     '00000000-0000-0000-0000-000000000009',
     'DAILY_SUBMITTED',
     'e1000000-0000-0000-0000-000000000007',
     'New entry submitted',
     'Ivy Support submitted a work log entry for review',
     false,
     CURRENT_TIMESTAMP - INTERVAL '1 hour'),
    ('a0a00000-0000-0000-0000-000000000013',
     '00000000-0000-0000-0000-000000000009',
     'DAILY_SUBMITTED',
     'e1000000-0000-0000-0000-000000000009',
     'New entry submitted',
     'Kevin Intern submitted a work log entry for review',
     true,
     CURRENT_TIMESTAMP - INTERVAL '4 hours'),
    -- David: member management notification
    ('a0a00000-0000-0000-0000-000000000014',
     '00000000-0000-0000-0000-000000000004',
     'MEMBER_ASSIGNED',
     '00000000-0000-0000-0000-000000000006',
     'New member added',
     'Eve Frontend has been added to the Engineering department',
     false,
     CURRENT_TIMESTAMP - INTERVAL '1 day'),
    -- System Admin: system notification
    ('a0a00000-0000-0000-0000-000000000015',
     '00000000-0000-0000-0000-000000000005',
     'SYSTEM_ALERT',
     '550e8400-e29b-41d4-a716-446655440002',
     'New tenant created',
     'ACME Corporation tenant has been registered in the system',
     false,
     CURRENT_TIMESTAMP - INTERVAL '12 hours')
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    message = EXCLUDED.message,
    is_read = EXCLUDED.is_read;
@@

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
-- UNION ALL SELECT 'users', count(*) FROM users
-- UNION ALL SELECT 'roles', count(*) FROM roles
-- UNION ALL SELECT 'work_log_entries', count(*) FROM work_log_entries_projection
-- UNION ALL SELECT 'absences', count(*) FROM absences_projection
-- UNION ALL SELECT 'event_store', count(*) FROM event_store
-- UNION ALL SELECT 'monthly_approvals', count(*) FROM monthly_approvals_projection
-- UNION ALL SELECT 'monthly_calendar', count(*) FROM monthly_calendar_projection
-- UNION ALL SELECT 'monthly_summary', count(*) FROM monthly_summary_projection
-- UNION ALL SELECT 'daily_entry_approvals', count(*) FROM daily_entry_approvals
-- UNION ALL SELECT 'in_app_notifications', count(*) FROM in_app_notifications
-- UNION ALL SELECT 'member_project_assignments', count(*) FROM member_project_assignments;
