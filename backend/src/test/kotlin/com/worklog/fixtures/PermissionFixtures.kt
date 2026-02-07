package com.worklog.fixtures

import com.worklog.domain.permission.Permission
import com.worklog.domain.permission.PermissionId
import java.time.Instant

/**
 * Test fixtures for Permission-related entities.
 * Provides helper methods for creating test permissions.
 */
object PermissionFixtures {
    /**
     * Creates a new random permission ID.
     */
    fun randomPermissionId(): PermissionId = PermissionId.generate()

    /**
     * Creates a user.create permission for testing.
     */
    fun createUserCreatePermission(
        id: PermissionId = randomPermissionId(),
        name: String = "user.create",
        description: String = "Create new users",
    ): Permission =
        Permission(
            id,
            name,
            description,
            Instant.now().minusSeconds(86400),
        )

    /**
     * Creates a user.delete permission for testing.
     */
    fun createUserDeletePermission(
        id: PermissionId = randomPermissionId(),
        name: String = "user.delete",
        description: String = "Delete users",
    ): Permission =
        Permission(
            id,
            name,
            description,
            Instant.now().minusSeconds(86400),
        )

    /**
     * Creates a report.view permission for testing.
     */
    fun createReportViewPermission(
        id: PermissionId = randomPermissionId(),
        name: String = "report.view",
        description: String = "View reports",
    ): Permission =
        Permission(
            id,
            name,
            description,
            Instant.now().minusSeconds(86400),
        )

    /**
     * Creates an admin.access permission for testing.
     */
    fun createAdminAccessPermission(
        id: PermissionId = randomPermissionId(),
        name: String = "admin.access",
        description: String = "Access admin panel",
    ): Permission =
        Permission(
            id,
            name,
            description,
            Instant.now().minusSeconds(86400),
        )

    /**
     * Creates a custom permission for testing.
     */
    fun createCustomPermission(
        id: PermissionId = randomPermissionId(),
        name: String = "custom.action",
        description: String = "Custom test permission",
    ): Permission =
        Permission(
            id,
            name,
            description,
            Instant.now(),
        )

    /**
     * Creates permission creation request data.
     */
    fun createPermissionRequest(
        name: String = "custom.action",
        description: String = "Test permission description",
    ): Map<String, Any> =
        mapOf(
            "name" to name,
            "description" to description,
        )

    /**
     * Invalid permission names for validation testing.
     */
    val invalidNames =
        listOf(
            "", // Empty
            "   ", // Whitespace only
            "a".repeat(101), // Too long (max 100)
            "permission", // Missing dot separator
            "permission.", // Missing action part
            ".action", // Missing resource part
            "Permission.Action", // Uppercase (must be lowercase)
            "permission.action.extra", // Too many parts
            "permission action", // Contains space
            "permission@action", // Special characters
        )

    /**
     * Valid permission names for testing.
     */
    val validNames =
        listOf(
            "user.create",
            "user.read",
            "user.update",
            "user.delete",
            "report.view",
            "report.export",
            "admin.access",
            "audit_log.view",
            "work_log.submit",
        )
}
