-- V7: Add Member Email Unique Constraint with Multi-Tenant Support
-- Description: Adds per-tenant email uniqueness constraint
-- Note: V5 was updated to not include any email unique constraint,
--       so this migration adds the proper tenant-scoped constraint

-- Add tenant-scoped unique constraint allowing same email in different tenants
-- Use IF NOT EXISTS pattern with DO block for idempotency
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'uk_member_tenant_email' 
        AND conrelid = 'members'::regclass
    ) THEN
        ALTER TABLE members ADD CONSTRAINT uk_member_tenant_email UNIQUE(tenant_id, email);
    END IF;
END $$;

COMMENT ON CONSTRAINT uk_member_tenant_email ON members IS 'Email must be unique within each tenant, but same email can exist in different tenants';
