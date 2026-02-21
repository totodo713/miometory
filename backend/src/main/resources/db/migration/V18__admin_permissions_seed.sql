-- ==========================================
-- Admin Roles & Permissions Seed Data
-- Migration: V18__admin_permissions_seed.sql
-- Feature: 015-admin-management
-- Date: 2026-02-21
-- ==========================================
-- Seeds predefined admin roles, permissions, and role-permission mappings.
-- Uses ON CONFLICT DO NOTHING for idempotency.

-- ============================================================================
-- 1. Roles
-- ============================================================================
INSERT INTO roles (id, name, description, created_at, updated_at) VALUES
    ('aa000000-0000-0000-0000-000000000001', 'SYSTEM_ADMIN', 'Global system administrator with cross-tenant access', NOW(), NOW()),
    ('aa000000-0000-0000-0000-000000000002', 'TENANT_ADMIN', 'Tenant-scoped administrator for member, project, and assignment management', NOW(), NOW()),
    ('aa000000-0000-0000-0000-000000000003', 'SUPERVISOR', 'Organization supervisor for daily/monthly approval and direct report management', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

-- ============================================================================
-- 2. Permissions (resource.action pattern)
-- ============================================================================
INSERT INTO permissions (id, name, description, created_at) VALUES
    -- Tenant permissions (System Admin)
    ('bb000000-0000-0000-0000-000000000001', 'tenant.view', 'View tenant details', NOW()),
    ('bb000000-0000-0000-0000-000000000002', 'tenant.create', 'Create new tenants', NOW()),
    ('bb000000-0000-0000-0000-000000000003', 'tenant.update', 'Edit tenant details', NOW()),
    ('bb000000-0000-0000-0000-000000000004', 'tenant.deactivate', 'Deactivate/activate tenants', NOW()),
    -- User permissions (System Admin)
    ('bb000000-0000-0000-0000-000000000005', 'user.view', 'View all user accounts globally', NOW()),
    ('bb000000-0000-0000-0000-000000000006', 'user.update_role', 'Change user roles', NOW()),
    ('bb000000-0000-0000-0000-000000000007', 'user.lock', 'Lock/unlock user accounts', NOW()),
    ('bb000000-0000-0000-0000-000000000008', 'user.reset_password', 'Initiate password resets', NOW()),
    -- Member permissions (Tenant Admin)
    ('bb000000-0000-0000-0000-000000000009', 'member.view', 'View members within tenant', NOW()),
    ('bb000000-0000-0000-0000-000000000010', 'member.create', 'Invite new members', NOW()),
    ('bb000000-0000-0000-0000-000000000011', 'member.update', 'Edit member details', NOW()),
    ('bb000000-0000-0000-0000-000000000012', 'member.deactivate', 'Deactivate/activate members', NOW()),
    -- Project permissions (Tenant Admin)
    ('bb000000-0000-0000-0000-000000000013', 'project.view', 'View projects within tenant', NOW()),
    ('bb000000-0000-0000-0000-000000000014', 'project.create', 'Create new projects', NOW()),
    ('bb000000-0000-0000-0000-000000000015', 'project.update', 'Edit project details', NOW()),
    ('bb000000-0000-0000-0000-000000000016', 'project.deactivate', 'Deactivate/activate projects', NOW()),
    -- Assignment permissions (Tenant Admin + Supervisor)
    ('bb000000-0000-0000-0000-000000000017', 'assignment.view', 'View member-project assignments', NOW()),
    ('bb000000-0000-0000-0000-000000000018', 'assignment.create', 'Create assignments', NOW()),
    ('bb000000-0000-0000-0000-000000000019', 'assignment.deactivate', 'Deactivate/activate assignments', NOW()),
    -- Daily approval permissions (Supervisor)
    ('bb000000-0000-0000-0000-000000000020', 'daily_approval.view', 'View daily entries for approval', NOW()),
    ('bb000000-0000-0000-0000-000000000021', 'daily_approval.approve', 'Approve daily entries', NOW()),
    ('bb000000-0000-0000-0000-000000000022', 'daily_approval.reject', 'Reject daily entries', NOW()),
    ('bb000000-0000-0000-0000-000000000023', 'daily_approval.recall', 'Recall daily approvals', NOW()),
    -- Monthly approval permissions (Supervisor)
    ('bb000000-0000-0000-0000-000000000024', 'monthly_approval.view', 'View monthly records for approval', NOW()),
    ('bb000000-0000-0000-0000-000000000025', 'monthly_approval.approve', 'Approve monthly records', NOW()),
    ('bb000000-0000-0000-0000-000000000026', 'monthly_approval.reject', 'Reject monthly records', NOW()),
    -- Tenant Admin assignment permission
    ('bb000000-0000-0000-0000-000000000027', 'tenant_admin.assign', 'Assign Tenant Admin role to other members', NOW())
ON CONFLICT (name) DO NOTHING;

-- ============================================================================
-- 3. Role-Permission Mappings
-- ============================================================================

-- SYSTEM_ADMIN: tenant.*, user.*, member.view, project.view
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.name IN (
    'tenant.view', 'tenant.create', 'tenant.update', 'tenant.deactivate',
    'user.view', 'user.update_role', 'user.lock', 'user.reset_password',
    'member.view', 'project.view'
  )
ON CONFLICT DO NOTHING;

-- TENANT_ADMIN: member.*, project.*, assignment.*, tenant_admin.assign
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'TENANT_ADMIN'
  AND p.name IN (
    'member.view', 'member.create', 'member.update', 'member.deactivate',
    'project.view', 'project.create', 'project.update', 'project.deactivate',
    'assignment.view', 'assignment.create', 'assignment.deactivate',
    'tenant_admin.assign'
  )
ON CONFLICT DO NOTHING;

-- SUPERVISOR: assignment.view, assignment.create, assignment.deactivate, daily_approval.*, monthly_approval.view
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SUPERVISOR'
  AND p.name IN (
    'assignment.view', 'assignment.create', 'assignment.deactivate',
    'daily_approval.view', 'daily_approval.approve', 'daily_approval.reject', 'daily_approval.recall',
    'monthly_approval.view', 'monthly_approval.approve', 'monthly_approval.reject'
  )
ON CONFLICT DO NOTHING;
