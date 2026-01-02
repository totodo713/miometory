-- Add indexes for event_store
CREATE INDEX idx_event_store_aggregate_id ON event_store(aggregate_id);
CREATE INDEX idx_event_store_aggregate_type ON event_store(aggregate_type);
CREATE UNIQUE INDEX idx_event_store_aggregate_version ON event_store(aggregate_id, version);

-- Snapshot store for aggregate state caching
CREATE TABLE snapshot_store (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id   UUID         NOT NULL UNIQUE,
    aggregate_type VARCHAR(64)  NOT NULL,
    version        BIGINT       NOT NULL,
    state          JSONB        NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_snapshot_store_aggregate_id ON snapshot_store(aggregate_id);

-- Audit log for compliance tracking
CREATE TABLE audit_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID,
    user_id       UUID,
    action        VARCHAR(32)  NOT NULL,
    resource_type VARCHAR(64)  NOT NULL,
    resource_id   UUID         NOT NULL,
    details       JSONB,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_tenant_id ON audit_log(tenant_id);
CREATE INDEX idx_audit_log_resource ON audit_log(resource_type, resource_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);

-- Tenant table
CREATE TABLE tenant (
    id          UUID PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL UNIQUE,
    name        VARCHAR(256) NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenant_code ON tenant(code);
CREATE INDEX idx_tenant_status ON tenant(status);

-- Organization table with hierarchical structure
CREATE TABLE organization (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenant(id),
    parent_id       UUID         REFERENCES organization(id),
    code            VARCHAR(32)  NOT NULL,
    name            VARCHAR(256) NOT NULL,
    level           INT          NOT NULL DEFAULT 1,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_organization_tenant_code UNIQUE(tenant_id, code),
    CONSTRAINT chk_organization_level CHECK (level >= 1 AND level <= 6)
);

CREATE INDEX idx_organization_tenant_id ON organization(tenant_id);
CREATE INDEX idx_organization_parent_id ON organization(parent_id);
CREATE INDEX idx_organization_status ON organization(status);

-- Fiscal year pattern
CREATE TABLE fiscal_year_pattern (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenant(id),
    organization_id UUID         REFERENCES organization(id),
    name            VARCHAR(128) NOT NULL,
    start_month     INT          NOT NULL,
    start_day       INT          NOT NULL DEFAULT 1,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_fiscal_year_start_month CHECK (start_month >= 1 AND start_month <= 12),
    CONSTRAINT chk_fiscal_year_start_day CHECK (start_day >= 1 AND start_day <= 31)
);

CREATE INDEX idx_fiscal_year_pattern_tenant_id ON fiscal_year_pattern(tenant_id);
CREATE INDEX idx_fiscal_year_pattern_organization_id ON fiscal_year_pattern(organization_id);

-- Monthly period pattern
CREATE TABLE monthly_period_pattern (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenant(id),
    organization_id UUID         REFERENCES organization(id),
    name            VARCHAR(128) NOT NULL,
    start_day       INT          NOT NULL DEFAULT 1,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_monthly_period_start_day CHECK (start_day >= 1 AND start_day <= 28)
);

CREATE INDEX idx_monthly_period_pattern_tenant_id ON monthly_period_pattern(tenant_id);
CREATE INDEX idx_monthly_period_pattern_organization_id ON monthly_period_pattern(organization_id);
