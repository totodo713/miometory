-- V28__user_session_selected_tenant.sql
-- Adds selected_tenant_id to user_sessions for multi-tenant selection support (issue #48)
ALTER TABLE user_sessions ADD COLUMN selected_tenant_id UUID REFERENCES tenant(id);
