-- Test seed data: Roles, permissions, and role-permission assignments
-- Required by: RoleRepositoryTest, AuthControllerSignupTest, Admin*ControllerTest
-- Runs after: R__test_infrastructure.sql (alphabetical order)

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
