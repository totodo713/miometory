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
            is_active = EXCLUDED.is_active;

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
            is_active = EXCLUDED.is_active;

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
            is_active = EXCLUDED.is_active;

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
            is_active = EXCLUDED.is_active;

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
            is_active = EXCLUDED.is_active;

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
            is_active = EXCLUDED.is_active;

        RAISE NOTICE 'Dev seed data: Member-project assignments inserted/updated';
    ELSE
        RAISE NOTICE 'Dev seed data: Skipped (required entities not found - likely test environment)';
    END IF;
END $$;
