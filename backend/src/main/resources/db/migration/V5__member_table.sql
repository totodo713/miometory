-- V5: Members Table for User Management and Manager Hierarchy
-- Description: Adds members table to support proxy entry feature and manager-subordinate relationships
-- Feature: 002-work-log-entry (User Story 7 - Manager Proxy Entry)

-- ============================================================================
-- Members Table (User/Employee Management)
-- ============================================================================
CREATE TABLE IF NOT EXISTS members (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    organization_id UUID NOT NULL REFERENCES organization(id),
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    manager_id UUID REFERENCES members(id),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for common query patterns
CREATE INDEX idx_members_tenant ON members(tenant_id);
CREATE INDEX idx_members_organization ON members(organization_id);
CREATE INDEX idx_members_manager ON members(manager_id) WHERE manager_id IS NOT NULL;
CREATE INDEX idx_members_email ON members(email);

COMMENT ON TABLE members IS 'User/employee records with manager hierarchy for proxy entry and approval workflows';
COMMENT ON COLUMN members.manager_id IS 'Direct manager for approval workflow and proxy entry permission (US7)';
COMMENT ON COLUMN members.is_active IS 'Active members can log time; inactive members are retained for audit history';

-- ============================================================================
-- Add foreign key constraints to work_log_entries_projection
-- ============================================================================
-- Note: We don't add FK constraints to projection tables as they are denormalized read models
-- The member_id and entered_by columns in work_log_entries_projection reference member.id conceptually
