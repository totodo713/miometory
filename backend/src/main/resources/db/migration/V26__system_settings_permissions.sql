-- ==========================================
-- System Settings Permissions
-- Migration: V26__system_settings_permissions.sql
-- Feature: System > Tenant > Organization settings inheritance
-- Date: 2026-02-26
-- ==========================================
-- Adds system_settings.view and system_settings.update permissions for SYSTEM_ADMIN.

INSERT INTO permissions (id, name, description, created_at) VALUES
    (gen_random_uuid(), 'system_settings.view', 'View system-wide default settings', NOW()),
    (gen_random_uuid(), 'system_settings.update', 'Update system-wide default settings', NOW())
ON CONFLICT (name) DO NOTHING;

-- Grant to SYSTEM_ADMIN role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.name IN ('system_settings.view', 'system_settings.update')
ON CONFLICT DO NOTHING;

-- Rollback:
-- DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE name IN ('system_settings.view', 'system_settings.update'));
-- DELETE FROM permissions WHERE name IN ('system_settings.view', 'system_settings.update');
