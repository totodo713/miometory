-- ==========================================
-- Nullable Member Organization + assign_tenant Permission
-- Migration: V27__nullable_member_organization.sql
-- Feature: Public user registration foundation (Issue #47)
-- Date: 2026-02-28
-- ==========================================
-- Makes organization_id nullable on members table and adds member.assign_tenant permission.
-- FK constraint REFERENCES organization(id) is retained — NULL values skip FK validation per SQL standard.

-- 1. Drop NOT NULL constraint on organization_id
ALTER TABLE members ALTER COLUMN organization_id DROP NOT NULL;

-- 2. Add member.assign_tenant permission
INSERT INTO permissions (id, name, description, created_at) VALUES
    (gen_random_uuid(), 'member.assign_tenant', 'Assign a member to a tenant/organization', NOW())
ON CONFLICT (name) DO NOTHING;

-- 3. Grant to SYSTEM_ADMIN and TENANT_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('SYSTEM_ADMIN', 'TENANT_ADMIN')
  AND p.name = 'member.assign_tenant'
ON CONFLICT DO NOTHING;

-- Rollback:
-- ALTER TABLE members ALTER COLUMN organization_id SET NOT NULL;
-- DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE name = 'member.assign_tenant');
-- DELETE FROM permissions WHERE name = 'member.assign_tenant';
