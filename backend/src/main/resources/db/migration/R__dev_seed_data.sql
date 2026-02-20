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

-- ============================================================================
-- Sample Daily Entry Approvals (Alice approves/rejects Bob's entries)
-- ============================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM daily_entry_approvals LIMIT 1) THEN
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
    IF EXISTS (SELECT 1 FROM in_app_notifications LIMIT 1) THEN
        RAISE NOTICE 'Dev seed data: Notifications already exist, skipping';
    ELSE
        INSERT INTO in_app_notifications (id, recipient_member_id, type, reference_id, title, message, is_read, created_at)
        VALUES
            -- Bob notified of daily approval
            ('na000000-0000-0000-0000-000000000001',
             '00000000-0000-0000-0000-000000000001',
             'DAILY_APPROVED',
             'da000000-0000-0000-0000-000000000001',
             'Entry approved',
             'Your entry for Sprint planning and story estimation was approved by Alice Manager',
             false,
             CURRENT_TIMESTAMP - INTERVAL '1 hour'),
            -- Bob notified of daily rejection
            ('na000000-0000-0000-0000-000000000002',
             '00000000-0000-0000-0000-000000000001',
             'DAILY_REJECTED',
             'da000000-0000-0000-0000-000000000002',
             'Entry rejected',
             'Your entry for Feature development (overtime) was rejected by Alice Manager: Please correct the hours',
             false,
             CURRENT_TIMESTAMP - INTERVAL '30 minutes'),
            -- Charlie notified of daily approval
            ('na000000-0000-0000-0000-000000000003',
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
