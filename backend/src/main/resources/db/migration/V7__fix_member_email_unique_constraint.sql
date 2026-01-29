-- V7: Fix Member Email Unique Constraint for Multi-Tenant Support
-- Description: Changes email uniqueness from global to per-tenant scope
-- Issue: Original UNIQUE(email) constraint prevents same email across different tenants

-- Drop the existing global email unique constraint
ALTER TABLE members DROP CONSTRAINT IF EXISTS uk_member_email;

-- Add tenant-scoped unique constraint allowing same email in different tenants
ALTER TABLE members ADD CONSTRAINT uk_member_tenant_email UNIQUE(tenant_id, email);

COMMENT ON CONSTRAINT uk_member_tenant_email ON members IS 'Email must be unique within each tenant, but same email can exist in different tenants';
