-- Test infrastructure: create tenant and organization for integration tests
-- This repeatable migration runs after all versioned migrations

INSERT INTO tenant (id, code, name, status, version, created_at, updated_at)
VALUES ('550e8400-e29b-41d4-a716-446655440001', 'MIOMETRY', 'Miometry Inc.', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

INSERT INTO organization (id, tenant_id, parent_id, code, name, level, status, version, created_at, updated_at)
VALUES ('880e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440001', NULL, 'ENG', 'Engineering Department', 1, 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
