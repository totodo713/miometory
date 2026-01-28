-- V5: Member Table for User Management and Manager Hierarchy
-- Description: Adds member table to support proxy entry feature and manager-subordinate relationships
-- Feature: 002-work-log-entry (User Story 7 - Manager Proxy Entry)

-- ============================================================================
-- Member Table (User/Employee Management)
-- ============================================================================
CREATE TABLE IF NOT EXISTS member (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    organization_id UUID NOT NULL REFERENCES organization(id),
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    manager_id UUID REFERENCES member(id),
    is_active BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_member_email UNIQUE(email)
);

-- Indexes for common query patterns
CREATE INDEX idx_member_tenant_id ON member(tenant_id);
CREATE INDEX idx_member_organization_id ON member(organization_id);
CREATE INDEX idx_member_manager_id ON member(manager_id) WHERE manager_id IS NOT NULL;
CREATE INDEX idx_member_email ON member(email);
CREATE INDEX idx_member_is_active ON member(is_active);

-- Composite index for finding subordinates efficiently
CREATE INDEX idx_member_manager_active ON member(manager_id, is_active) WHERE manager_id IS NOT NULL;

COMMENT ON TABLE member IS 'User/employee records with manager hierarchy for proxy entry and approval workflows';
COMMENT ON COLUMN member.manager_id IS 'Direct manager for approval workflow and proxy entry permission (US7)';
COMMENT ON COLUMN member.is_active IS 'Active members can log time; inactive members are retained for audit history';
COMMENT ON COLUMN member.version IS 'Optimistic locking version for concurrent updates';

-- ============================================================================
-- Add foreign key constraints to work_log_entries_projection
-- ============================================================================
-- Note: We don't add FK constraints to projection tables as they are denormalized read models
-- The member_id and entered_by columns in work_log_entries_projection reference member.id conceptually
