-- ============================================================================
-- Repeatable Migration: Development Seed Data
-- ============================================================================
-- Purpose: Load member-project assignments for development/testing
-- This runs after all versioned migrations, and re-runs when file checksum changes.
--
-- IMPORTANT: This migration is conditional - it only inserts data if the
-- required tenant exists. This allows it to run safely in test environments.
--
-- Member-Project Assignments:
--   - Bob:     WORKLOG-DEV, INFRA-OPS (2 projects)
--   - Alice:   WORKLOG-DEV, INFRA-OPS (manager has access to all)
--   - Charlie: WORKLOG-DEV only (junior engineer)
--   - David:   INFRA-OPS only (infrastructure specialist)
--
-- UUIDs Reference:
--   Tenant:      550e8400-e29b-41d4-a716-446655440001
--   WORKLOG-DEV: 990e8400-e29b-41d4-a716-446655440001
--   INFRA-OPS:   990e8400-e29b-41d4-a716-446655440002
--   Bob:         00000000-0000-0000-0000-000000000001
--   Alice:       00000000-0000-0000-0000-000000000002
--   Charlie:     00000000-0000-0000-0000-000000000003
--   David:       00000000-0000-0000-0000-000000000004
-- ============================================================================

-- Member-Project Assignments (only insert if tenant and required entities exist)
DO $$
BEGIN
    -- Check if required dev data exists (tenant, member, project)
    IF EXISTS (SELECT 1 FROM tenant WHERE id = '550e8400-e29b-41d4-a716-446655440001')
       AND EXISTS (SELECT 1 FROM members WHERE id = '00000000-0000-0000-0000-000000000001')
       AND EXISTS (SELECT 1 FROM projects WHERE id = '990e8400-e29b-41d4-a716-446655440001')
    THEN
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

        RAISE NOTICE 'Dev seed data: Member-project assignments inserted/updated';
    ELSE
        RAISE NOTICE 'Dev seed data: Skipped (required entities not found - likely test environment)';
    END IF;
END $$;

-- ============================================================================
-- Authentication & Authorization Seed Data
-- Feature: 001-user-login-auth
-- ============================================================================

-- Seed Roles (ADMIN, USER, MODERATOR)
INSERT INTO roles (id, name, description) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN', 'System administrator with full access'),
    ('00000000-0000-0000-0000-000000000002', 'USER', 'Standard user with limited access'),
    ('00000000-0000-0000-0000-000000000003', 'MODERATOR', 'Content moderator with approval permissions')
ON CONFLICT (name) DO NOTHING;

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
ON CONFLICT (name) DO NOTHING;

-- ADMIN role: all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000001', id FROM permissions
ON CONFLICT DO NOTHING;

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

-- ============================================================================
-- User Account Seed Data
-- Feature: 012-login-auth-ui
-- ============================================================================
-- All users share password: Password1
-- BCrypt hash: $2b$12$gu2gIbw9xeZOjbqdlXLjo.uAofiuh/z.dFMOP1ARC0BE/88piI06u
-- UUIDs match existing members table IDs
-- ============================================================================

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
    role_id = EXCLUDED.role_id,
    account_status = EXCLUDED.account_status,
    email_verified_at = EXCLUDED.email_verified_at;

-- ============================================================================
-- Admin Management Seed Data
-- Feature: 015-admin-management
-- ============================================================================
-- Assigns admin roles to test users and creates sample approval/notification data.
-- Depends on V18__admin_permissions_seed.sql for SYSTEM_ADMIN, TENANT_ADMIN, SUPERVISOR roles.
-- ============================================================================

-- Admin seed data: only insert if the dev tenant and roles exist
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM tenant WHERE id = '550e8400-e29b-41d4-a716-446655440001')
       AND EXISTS (SELECT 1 FROM roles WHERE name = 'SYSTEM_ADMIN')
    THEN
        -- System Admin user + member (new test user for global admin operations)
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

        -- Assign Alice to SUPERVISOR role (she manages Bob and Charlie)
        UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'SUPERVISOR')
        WHERE email = 'alice.manager@miometry.example.com';

        -- Assign David to TENANT_ADMIN role
        UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'TENANT_ADMIN')
        WHERE email = 'david.independent@miometry.example.com';

        RAISE NOTICE 'Dev seed data: Admin management roles and users inserted';
    ELSE
        RAISE NOTICE 'Dev seed data: Skipping admin management seed (tenant or roles not found)';
    END IF;
END $$;

-- ============================================================================
-- Sample Daily Entry Approvals (Alice approves/rejects Bob's entries)
-- ============================================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM members WHERE id = '00000000-0000-0000-0000-000000000001') THEN
        RAISE NOTICE 'Dev seed data: Skipping daily entry approvals (dev members not found)';
    ELSIF EXISTS (SELECT 1 FROM daily_entry_approvals LIMIT 1) THEN
        RAISE NOTICE 'Dev seed data: Daily entry approvals already exist, skipping';
    ELSE
        -- Alice APPROVED Bob's entry from 2 days ago
        INSERT INTO daily_entry_approvals (id, work_log_entry_id, member_id, supervisor_id, status, comment, created_at, updated_at)
        VALUES (
            'da000000-0000-0000-0000-000000000001',
            'a0000000-0000-0000-0000-000000000003',
            '00000000-0000-0000-0000-000000000001',
            '00000000-0000-0000-0000-000000000002',
            'APPROVED',
            'Looks good',
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        );

        -- Alice REJECTED Bob's entry from 4 days ago (excessive hours)
        INSERT INTO daily_entry_approvals (id, work_log_entry_id, member_id, supervisor_id, status, comment, created_at, updated_at)
        VALUES (
            'da000000-0000-0000-0000-000000000002',
            'a0000000-0000-0000-0000-000000000006',
            '00000000-0000-0000-0000-000000000001',
            '00000000-0000-0000-0000-000000000002',
            'REJECTED',
            'Please correct the hours - 12 hours seems excessive for a single day',
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        );

        -- Alice APPROVED Charlie's entry from 2 days ago
        INSERT INTO daily_entry_approvals (id, work_log_entry_id, member_id, supervisor_id, status, comment, created_at, updated_at)
        VALUES (
            'da000000-0000-0000-0000-000000000003',
            'b0000000-0000-0000-0000-000000000003',
            '00000000-0000-0000-0000-000000000003',
            '00000000-0000-0000-0000-000000000002',
            'APPROVED',
            NULL,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        );

        RAISE NOTICE 'Dev seed data: Daily entry approvals inserted';
    END IF;
END $$;

-- ============================================================================
-- Sample In-App Notifications
-- ============================================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM members WHERE id = '00000000-0000-0000-0000-000000000001') THEN
        RAISE NOTICE 'Dev seed data: Skipping notifications (dev members not found)';
    ELSIF EXISTS (SELECT 1 FROM in_app_notifications LIMIT 1) THEN
        RAISE NOTICE 'Dev seed data: Notifications already exist, skipping';
    ELSE
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
             CURRENT_TIMESTAMP - INTERVAL '2 hours');

        RAISE NOTICE 'Dev seed data: In-app notifications inserted';
    END IF;
END $$;

-- ============================================================================
-- E2E Testing: Extended Admin Management Seed Data
-- Feature: 015-admin-management (E2E verification)
-- ============================================================================
-- Provides comprehensive test data for manual E2E verification of all admin
-- management scenarios across different roles:
--   SYSTEM_ADMIN: Cross-tenant management (2 tenants)
--   TENANT_ADMIN: Member/project/assignment management (11+ members, 6 projects)
--   SUPERVISOR:   Daily approval dashboard, subordinate management
--   USER:         Limited access verification
--
-- Additional UUIDs:
--   Tenant 2 (ACME):        550e8400-e29b-41d4-a716-446655440002
--   Organization 2 (Sales):  880e8400-e29b-41d4-a716-446655440002
--   Members 006-00d:         00000000-0000-0000-0000-000000000006 through 00d
--   Projects 004-007:        990e8400-e29b-41d4-a716-446655440004 through 007
-- ============================================================================

DO $$
BEGIN
    -- Guard: skip if base infrastructure or admin roles not yet loaded
    IF NOT EXISTS (SELECT 1 FROM tenant WHERE id = '550e8400-e29b-41d4-a716-446655440001')
       OR NOT EXISTS (SELECT 1 FROM roles WHERE name = 'SYSTEM_ADMIN')
    THEN
        RAISE NOTICE 'E2E seed: Skipping (base tenant or admin roles not found)';
        RETURN;
    END IF;

    -- =================================================================
    -- 1. Second Tenant: ACME Corporation (cross-tenant testing)
    -- =================================================================
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

    -- =================================================================
    -- 2. Additional Miometry Members (pagination/search/filter testing)
    -- =================================================================
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

    -- =================================================================
    -- 3. ACME Tenant Members
    -- =================================================================
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

    -- =================================================================
    -- 4. User Accounts for new members
    -- =================================================================
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

    -- =================================================================
    -- 5. Additional Projects
    -- =================================================================
    -- Miometry: MOBILE-APP (active, with end date)
    INSERT INTO projects (id, tenant_id, code, name, is_active, valid_from, valid_until, created_at, updated_at)
    VALUES (
        '990e8400-e29b-41d4-a716-446655440004',
        '550e8400-e29b-41d4-a716-446655440001',
        'MOBILE-APP',
        'Mobile Application',
        true,
        '2026-01-01',
        '2026-06-30',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (id) DO UPDATE SET
        name = EXCLUDED.name, code = EXCLUDED.code,
        is_active = EXCLUDED.is_active,
        valid_from = EXCLUDED.valid_from, valid_until = EXCLUDED.valid_until,
        updated_at = CURRENT_TIMESTAMP;

    -- Miometry: DATA-ANLZ (active, no end date)
    INSERT INTO projects (id, tenant_id, code, name, is_active, valid_from, valid_until, created_at, updated_at)
    VALUES (
        '990e8400-e29b-41d4-a716-446655440005',
        '550e8400-e29b-41d4-a716-446655440001',
        'DATA-ANLZ',
        'Data Analytics Platform',
        true,
        '2025-07-01',
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (id) DO UPDATE SET
        name = EXCLUDED.name, code = EXCLUDED.code,
        is_active = EXCLUDED.is_active,
        valid_from = EXCLUDED.valid_from, valid_until = EXCLUDED.valid_until,
        updated_at = CURRENT_TIMESTAMP;

    -- Miometry: RESEARCH-AI (INACTIVE, ended)
    INSERT INTO projects (id, tenant_id, code, name, is_active, valid_from, valid_until, created_at, updated_at)
    VALUES (
        '990e8400-e29b-41d4-a716-446655440006',
        '550e8400-e29b-41d4-a716-446655440001',
        'RESEARCH-AI',
        'AI Research Project',
        false,
        '2024-04-01',
        '2025-03-31',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (id) DO UPDATE SET
        name = EXCLUDED.name, code = EXCLUDED.code,
        is_active = EXCLUDED.is_active,
        valid_from = EXCLUDED.valid_from, valid_until = EXCLUDED.valid_until,
        updated_at = CURRENT_TIMESTAMP;

    -- ACME: SALES-CRM (active)
    INSERT INTO projects (id, tenant_id, code, name, is_active, valid_from, valid_until, created_at, updated_at)
    VALUES (
        '990e8400-e29b-41d4-a716-446655440007',
        '550e8400-e29b-41d4-a716-446655440002',
        'SALES-CRM',
        'Sales CRM System',
        true,
        '2025-06-01',
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (id) DO UPDATE SET
        name = EXCLUDED.name, code = EXCLUDED.code,
        is_active = EXCLUDED.is_active,
        valid_from = EXCLUDED.valid_from, valid_until = EXCLUDED.valid_until,
        updated_at = CURRENT_TIMESTAMP;

    -- =================================================================
    -- 6. Member-Project Assignments (new members)
    -- =================================================================
    -- Eve -> WORKLOG-DEV
    INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
    VALUES ('f0000000-0000-0000-0000-000000000010', '550e8400-e29b-41d4-a716-446655440001',
            '00000000-0000-0000-0000-000000000006', '990e8400-e29b-41d4-a716-446655440001',
            CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000002', true)
    ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;

    -- Eve -> MOBILE-APP
    INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
    VALUES ('f0000000-0000-0000-0000-000000000011', '550e8400-e29b-41d4-a716-446655440001',
            '00000000-0000-0000-0000-000000000006', '990e8400-e29b-41d4-a716-446655440004',
            CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000002', true)
    ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;

    -- Frank -> INFRA-OPS
    INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
    VALUES ('f0000000-0000-0000-0000-000000000012', '550e8400-e29b-41d4-a716-446655440001',
            '00000000-0000-0000-0000-000000000007', '990e8400-e29b-41d4-a716-446655440002',
            CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000002', true)
    ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;

    -- Frank -> DATA-ANLZ
    INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
    VALUES ('f0000000-0000-0000-0000-000000000013', '550e8400-e29b-41d4-a716-446655440001',
            '00000000-0000-0000-0000-000000000007', '990e8400-e29b-41d4-a716-446655440005',
            CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000002', true)
    ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;

    -- Hank -> WORKLOG-DEV
    INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
    VALUES ('f0000000-0000-0000-0000-000000000014', '550e8400-e29b-41d4-a716-446655440001',
            '00000000-0000-0000-0000-000000000009', '990e8400-e29b-41d4-a716-446655440001',
            CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000004', true)
    ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;

    -- Hank -> DATA-ANLZ
    INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
    VALUES ('f0000000-0000-0000-0000-000000000015', '550e8400-e29b-41d4-a716-446655440001',
            '00000000-0000-0000-0000-000000000009', '990e8400-e29b-41d4-a716-446655440005',
            CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000004', true)
    ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;

    -- Ivy -> INFRA-OPS
    INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
    VALUES ('f0000000-0000-0000-0000-000000000016', '550e8400-e29b-41d4-a716-446655440001',
            '00000000-0000-0000-0000-00000000000a', '990e8400-e29b-41d4-a716-446655440002',
            CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000009', true)
    ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;

    -- Kevin -> MOBILE-APP
    INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
    VALUES ('f0000000-0000-0000-0000-000000000017', '550e8400-e29b-41d4-a716-446655440001',
            '00000000-0000-0000-0000-00000000000b', '990e8400-e29b-41d4-a716-446655440004',
            CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000009', true)
    ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;

    -- Jack -> SALES-CRM (ACME)
    INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
    VALUES ('f0000000-0000-0000-0000-000000000018', '550e8400-e29b-41d4-a716-446655440002',
            '00000000-0000-0000-0000-00000000000c', '990e8400-e29b-41d4-a716-446655440007',
            CURRENT_TIMESTAMP, NULL, true)
    ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;

    -- Kim -> SALES-CRM (ACME)
    INSERT INTO member_project_assignments (id, tenant_id, member_id, project_id, assigned_at, assigned_by, is_active)
    VALUES ('f0000000-0000-0000-0000-000000000019', '550e8400-e29b-41d4-a716-446655440002',
            '00000000-0000-0000-0000-00000000000d', '990e8400-e29b-41d4-a716-446655440007',
            CURRENT_TIMESTAMP, '00000000-0000-0000-0000-00000000000c', true)
    ON CONFLICT (tenant_id, member_id, project_id) DO UPDATE SET is_active = EXCLUDED.is_active;

    -- =================================================================
    -- 7. Work Log Entries (SUBMITTED) for approval dashboard testing
    -- =================================================================
    -- Eve's entries: SUBMITTED (Alice should see these in approval dashboard)
    INSERT INTO work_log_entries_projection (
        id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by, version, created_at, updated_at
    ) VALUES
        -- Eve yesterday: SUBMITTED on WORKLOG-DEV
        ('e1000000-0000-0000-0000-000000000001',
         '00000000-0000-0000-0000-000000000006',
         '880e8400-e29b-41d4-a716-446655440001',
         '990e8400-e29b-41d4-a716-446655440001',
         CURRENT_DATE - INTERVAL '1 day', 8.00,
         'React component refactoring and code review',
         'SUBMITTED', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        -- Eve 2 days ago: SUBMITTED on MOBILE-APP
        ('e1000000-0000-0000-0000-000000000002',
         '00000000-0000-0000-0000-000000000006',
         '880e8400-e29b-41d4-a716-446655440001',
         '990e8400-e29b-41d4-a716-446655440004',
         CURRENT_DATE - INTERVAL '2 days', 7.00,
         'Mobile app wireframe and prototype implementation',
         'SUBMITTED', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        -- Eve today: DRAFT
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

    -- Frank's entries: SUBMITTED (Alice should see these in approval dashboard)
    INSERT INTO work_log_entries_projection (
        id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by, version, created_at, updated_at
    ) VALUES
        -- Frank yesterday: SUBMITTED on INFRA-OPS
        ('e1000000-0000-0000-0000-000000000004',
         '00000000-0000-0000-0000-000000000007',
         '880e8400-e29b-41d4-a716-446655440001',
         '990e8400-e29b-41d4-a716-446655440002',
         CURRENT_DATE - INTERVAL '1 day', 8.00,
         'CI/CD pipeline optimization and Docker image cleanup',
         'SUBMITTED', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        -- Frank 2 days ago: SUBMITTED on DATA-ANLZ
        ('e1000000-0000-0000-0000-000000000005',
         '00000000-0000-0000-0000-000000000007',
         '880e8400-e29b-41d4-a716-446655440001',
         '990e8400-e29b-41d4-a716-446655440005',
         CURRENT_DATE - INTERVAL '2 days', 6.50,
         'ETL pipeline development and data quality checks',
         'SUBMITTED', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        -- Frank today: DRAFT
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

    -- Ivy's entries: SUBMITTED (Hank should see these in approval dashboard)
    INSERT INTO work_log_entries_projection (
        id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by, version, created_at, updated_at
    ) VALUES
        -- Ivy yesterday: SUBMITTED on INFRA-OPS
        ('e1000000-0000-0000-0000-000000000007',
         '00000000-0000-0000-0000-00000000000a',
         '880e8400-e29b-41d4-a716-446655440001',
         '990e8400-e29b-41d4-a716-446655440002',
         CURRENT_DATE - INTERVAL '1 day', 6.00,
         'Customer support ticket triage and resolution',
         'SUBMITTED', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        -- Ivy today: DRAFT
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

    -- Kevin's entries: SUBMITTED (Hank should see these)
    INSERT INTO work_log_entries_projection (
        id, member_id, organization_id, project_id, work_date, hours, notes, status, entered_by, version, created_at, updated_at
    ) VALUES
        -- Kevin yesterday: SUBMITTED on MOBILE-APP
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

    -- ACME: Kim's entry (for ACME tenant approval testing)
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

    -- =================================================================
    -- 8. Additional Notifications (various types for testing)
    -- =================================================================
    INSERT INTO in_app_notifications (id, recipient_member_id, type, reference_id, title, message, is_read, created_at)
    VALUES
        -- Alice: pending approval notification (subordinates submitted entries)
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
        -- Hank: pending approval notification
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
        -- David: member management notification (new member assigned)
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

    RAISE NOTICE 'E2E seed data: Extended admin management test data inserted/updated';
END $$;
