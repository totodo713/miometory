-- Remove tenant-scoped read permissions from SYSTEM_ADMIN.
-- SYSTEM_ADMIN should only manage system-level resources (tenants, users).
-- Rollback:
--   INSERT INTO role_permissions (role_id, permission_id)
--     SELECT r.id, p.id FROM roles r, permissions p
--     WHERE r.name = 'SYSTEM_ADMIN' AND p.name IN ('member.view', 'project.view');

DELETE FROM role_permissions
WHERE role_id = (SELECT id FROM roles WHERE name = 'SYSTEM_ADMIN')
  AND permission_id IN (
    SELECT id FROM permissions WHERE name IN ('member.view', 'project.view')
  );
