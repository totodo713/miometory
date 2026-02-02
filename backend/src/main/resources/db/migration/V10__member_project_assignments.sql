-- V10__member_project_assignments.sql
-- Feature: 003-project-selector-worklog
-- Description: Tracks which members are assigned to which projects for worklog entry

CREATE TABLE IF NOT EXISTS member_project_assignments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    member_id UUID NOT NULL REFERENCES members(id),
    project_id UUID NOT NULL REFERENCES projects(id),
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by UUID REFERENCES members(id),
    is_active BOOLEAN NOT NULL DEFAULT true,
    
    CONSTRAINT uk_member_project_assignment 
        UNIQUE(tenant_id, member_id, project_id)
);

-- Index for finding all projects for a member
CREATE INDEX idx_member_project_assignments_member 
    ON member_project_assignments(member_id);

-- Index for finding all members for a project
CREATE INDEX idx_member_project_assignments_project 
    ON member_project_assignments(project_id);

-- Index for tenant-based queries
CREATE INDEX idx_member_project_assignments_tenant 
    ON member_project_assignments(tenant_id);

-- Partial index for efficiently finding active assignments for a member
CREATE INDEX idx_member_project_assignments_active 
    ON member_project_assignments(member_id, is_active) 
    WHERE is_active = true;

COMMENT ON TABLE member_project_assignments IS 
    'Tracks which members are assigned to which projects for worklog entry';
COMMENT ON COLUMN member_project_assignments.id IS 'Unique identifier for the assignment';
COMMENT ON COLUMN member_project_assignments.tenant_id IS 'Tenant this assignment belongs to';
COMMENT ON COLUMN member_project_assignments.member_id IS 'The member who is assigned';
COMMENT ON COLUMN member_project_assignments.project_id IS 'The project the member is assigned to';
COMMENT ON COLUMN member_project_assignments.assigned_at IS 'When the assignment was created';
COMMENT ON COLUMN member_project_assignments.assigned_by IS 'The admin who created this assignment (nullable)';
COMMENT ON COLUMN member_project_assignments.is_active IS 'Whether this assignment is currently active';
