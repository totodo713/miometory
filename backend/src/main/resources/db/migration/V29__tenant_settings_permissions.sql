-- ==========================================
-- Tenant Settings Permissions
-- Migration: V29__tenant_settings_permissions.sql
-- Feature: Tenant admin self-service settings management
-- Date: 2026-03-02
-- ==========================================
-- Adds tenant_settings.view and tenant_settings.manage permissions for TENANT_ADMIN.

INSERT INTO permissions (id, name, description, created_at) VALUES
    (gen_random_uuid(), 'tenant_settings.view', 'View own tenant settings and patterns', NOW()),
    (gen_random_uuid(), 'tenant_settings.manage', 'Manage own tenant settings and patterns', NOW())
ON CONFLICT (name) DO NOTHING;

-- Grant to TENANT_ADMIN role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'TENANT_ADMIN'
  AND p.name IN ('tenant_settings.view', 'tenant_settings.manage')
ON CONFLICT DO NOTHING;

-- Rollback:
-- DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE name IN ('tenant_settings.view', 'tenant_settings.manage'));
-- DELETE FROM permissions WHERE name IN ('tenant_settings.view', 'tenant_settings.manage');
