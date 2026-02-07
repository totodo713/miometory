package com.worklog.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Scheduling configuration for background jobs.
 * 
 * Enables Spring's @Scheduled annotation for periodic tasks such as:
 * - Session cleanup (expired user_sessions)
 * - Token cleanup (expired password_reset_tokens, stale persistent_logins)
 * - Audit log cleanup (logs older than retention period)
 * 
 * Scheduled jobs are defined in:
 * - SessionCleanupScheduler (daily at 3 AM)
 * - AuditLogCleanupScheduler (daily at 2 AM)
 */
@Configuration
@EnableScheduling
class SchedulerConfig
