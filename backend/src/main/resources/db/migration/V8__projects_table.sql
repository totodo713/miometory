-- V8: Projects Table
-- Description: Adds projects table for tracking what members log time against
-- Feature: 002-work-log-entry
-- 
-- Domain model: com.worklog.domain.project.Project
-- Referenced by: work_log_entries_projection.project_id

CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    valid_from DATE,
    valid_until DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Business rule: project code must be unique within tenant
    CONSTRAINT uk_projects_tenant_code UNIQUE(tenant_id, code),
    
    -- Business rule: validity period must be logically consistent
    CONSTRAINT chk_projects_validity CHECK (
        valid_from IS NULL OR valid_until IS NULL OR valid_from <= valid_until
    )
);

-- Indices for common query patterns
CREATE INDEX IF NOT EXISTS idx_projects_tenant ON projects(tenant_id);
CREATE INDEX IF NOT EXISTS idx_projects_code ON projects(code);
CREATE INDEX IF NOT EXISTS idx_projects_active ON projects(is_active) WHERE is_active = true;

-- Add foreign key from work_log_entries_projection to projects
-- Note: Using DO block to make this idempotent
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_work_log_entries_project' 
        AND table_name = 'work_log_entries_projection'
    ) THEN
        ALTER TABLE work_log_entries_projection 
        ADD CONSTRAINT fk_work_log_entries_project 
        FOREIGN KEY (project_id) REFERENCES projects(id);
    END IF;
END $$;

-- Documentation
COMMENT ON TABLE projects IS 'Projects that members can log work hours against';
COMMENT ON COLUMN projects.id IS 'Unique project identifier (UUID)';
COMMENT ON COLUMN projects.tenant_id IS 'Tenant this project belongs to';
COMMENT ON COLUMN projects.code IS 'Business identifier for the project (e.g., PROJ-123)';
COMMENT ON COLUMN projects.name IS 'Human-readable project name';
COMMENT ON COLUMN projects.is_active IS 'Whether project accepts new time entries';
COMMENT ON COLUMN projects.valid_from IS 'Start date for accepting time entries (null = no start limit)';
COMMENT ON COLUMN projects.valid_until IS 'End date for accepting time entries (null = no end limit)';
COMMENT ON COLUMN projects.created_at IS 'Timestamp when project was created';
COMMENT ON COLUMN projects.updated_at IS 'Timestamp when project was last updated';
