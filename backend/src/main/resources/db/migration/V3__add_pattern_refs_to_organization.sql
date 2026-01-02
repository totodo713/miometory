-- Add pattern reference columns to organization table
-- These allow organizations to reference their fiscal year and monthly period patterns
-- for date calculation purposes

ALTER TABLE organization 
    ADD COLUMN fiscal_year_pattern_id UUID REFERENCES fiscal_year_pattern(id),
    ADD COLUMN monthly_period_pattern_id UUID REFERENCES monthly_period_pattern(id);

CREATE INDEX idx_organization_fiscal_year_pattern ON organization(fiscal_year_pattern_id);
CREATE INDEX idx_organization_monthly_period_pattern ON organization(monthly_period_pattern_id);
