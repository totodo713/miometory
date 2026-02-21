-- Organization management permissions

INSERT INTO permissions (id, name, description, created_at) VALUES
    (gen_random_uuid(), 'organization.view', 'View organizations', NOW()),
    (gen_random_uuid(), 'organization.create', 'Create organizations', NOW()),
    (gen_random_uuid(), 'organization.update', 'Update organizations', NOW()),
    (gen_random_uuid(), 'organization.deactivate', 'Deactivate/reactivate organizations', NOW())
ON CONFLICT (name) DO NOTHING;

-- Grant all organization permissions to TENANT_ADMIN role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'TENANT_ADMIN'
  AND p.name IN ('organization.view', 'organization.create',
                  'organization.update', 'organization.deactivate')
ON CONFLICT DO NOTHING;

-- Grant view permission to SUPERVISOR role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SUPERVISOR'
  AND p.name = 'organization.view'
ON CONFLICT DO NOTHING;
