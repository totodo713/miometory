-- ==========================================
-- Email Verification Tokens Table
-- Migration: V12__email_verification_tokens.sql
-- Feature: 001-user-login-auth (Security fix)
-- Date: 2026-02-07
-- ==========================================

-- Email verification tokens (persistent storage)
-- Replaces in-memory token store for reliability and cluster support
CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    CONSTRAINT chk_email_verification_expires_at_after_created CHECK (expires_at > created_at)
);

CREATE UNIQUE INDEX idx_email_verification_tokens_token ON email_verification_tokens(token);
CREATE INDEX idx_email_verification_tokens_user_id ON email_verification_tokens(user_id);
CREATE INDEX idx_email_verification_tokens_expires_at ON email_verification_tokens(expires_at);
