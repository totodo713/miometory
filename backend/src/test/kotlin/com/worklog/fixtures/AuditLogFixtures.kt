package com.worklog.fixtures

import com.worklog.domain.audit.AuditLog
import com.worklog.domain.user.UserId
import java.time.Instant
import java.util.UUID

/**
 * Test fixtures for AuditLog-related entities.
 * Provides helper methods for creating test audit logs.
 */
object AuditLogFixtures {
    /**
     * Creates a login success audit log for testing.
     */
    fun createLoginSuccessLog(
        userId: UserId,
        ipAddress: String = "192.168.1.100",
        details: String? = null,
    ): AuditLog =
        AuditLog(
            UUID.randomUUID(),
            userId,
            AuditLog.LOGIN_SUCCESS,
            ipAddress,
            Instant.now(),
            details,
            90,
        )

    /**
     * Creates a login failure audit log for testing.
     */
    fun createLoginFailureLog(
        userId: UserId? = null,
        ipAddress: String = "192.168.1.100",
        details: String = "{\"reason\":\"invalid_credentials\"}",
    ): AuditLog =
        AuditLog(
            UUID.randomUUID(),
            userId,
            AuditLog.LOGIN_FAILURE,
            ipAddress,
            Instant.now(),
            details,
            90,
        )

    /**
     * Creates a logout audit log for testing.
     */
    fun createLogoutLog(
        userId: UserId,
        ipAddress: String = "192.168.1.100",
    ): AuditLog =
        AuditLog(
            UUID.randomUUID(),
            userId,
            AuditLog.LOGOUT,
            ipAddress,
            Instant.now(),
            null,
            90,
        )

    /**
     * Creates a password change audit log for testing.
     */
    fun createPasswordChangeLog(
        userId: UserId,
        ipAddress: String = "192.168.1.100",
    ): AuditLog =
        AuditLog(
            UUID.randomUUID(),
            userId,
            AuditLog.PASSWORD_CHANGE,
            ipAddress,
            Instant.now(),
            null,
            90,
        )

    /**
     * Creates an account locked audit log for testing.
     */
    fun createAccountLockedLog(
        userId: UserId,
        ipAddress: String = "192.168.1.100",
        details: String = "{\"reason\":\"max_failed_attempts\",\"attempts\":5}",
    ): AuditLog =
        AuditLog(
            UUID.randomUUID(),
            userId,
            AuditLog.ACCOUNT_LOCKED,
            ipAddress,
            Instant.now(),
            details,
            90,
        )

    /**
     * Creates a permission denied audit log for testing.
     */
    fun createPermissionDeniedLog(
        userId: UserId,
        ipAddress: String = "192.168.1.100",
        details: String = "{\"resource\":\"admin.access\",\"action\":\"view\"}",
    ): AuditLog =
        AuditLog(
            UUID.randomUUID(),
            userId,
            AuditLog.PERMISSION_DENIED,
            ipAddress,
            Instant.now(),
            details,
            90,
        )

    /**
     * Creates a system event audit log (no user) for testing.
     */
    fun createSystemEventLog(
        eventType: String = AuditLog.AUDIT_LOG_CLEANUP,
        details: String? = "{\"deleted_count\":150}",
    ): AuditLog =
        AuditLog(
            UUID.randomUUID(),
            null,
            eventType,
            null,
            Instant.now(),
            details,
            90,
        )

    /**
     * Creates an expired audit log for testing cleanup.
     */
    fun createExpiredLog(
        userId: UserId? = null,
        eventType: String = AuditLog.LOGIN_SUCCESS,
        daysOld: Long = 91,
    ): AuditLog =
        AuditLog(
            UUID.randomUUID(),
            userId,
            eventType,
            "192.168.1.100",
            Instant.now().minusSeconds(daysOld * 86400),
            null,
            90,
        )
}
