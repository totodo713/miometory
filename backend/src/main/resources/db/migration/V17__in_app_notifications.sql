-- ==========================================
-- In-App Notifications Table
-- Migration: V17__in_app_notifications.sql
-- Feature: 015-admin-management
-- Date: 2026-02-21
-- ==========================================
-- Stores notifications for approval events and admin actions.
-- Delivered to recipients via client-side polling.

CREATE TABLE in_app_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_member_id UUID NOT NULL REFERENCES members(id),
    type VARCHAR(30) NOT NULL,
    reference_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(500) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Composite index for efficient notification listing (unread first, newest first)
CREATE INDEX idx_notification_recipient
    ON in_app_notifications(recipient_member_id, is_read, created_at DESC);

-- Index for cleanup/retention queries
CREATE INDEX idx_notification_created
    ON in_app_notifications(created_at);
