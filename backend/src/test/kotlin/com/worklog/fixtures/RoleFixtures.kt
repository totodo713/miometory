package com.worklog.fixtures

import com.worklog.domain.role.Role
import com.worklog.domain.role.RoleId
import java.time.Instant
import java.util.UUID

/**
 * Test fixtures for Role-related entities.
 * Provides helper methods for creating test roles.
 */
object RoleFixtures {
    /**
     * Creates a valid role name.
     */
    fun validName(prefix: String = "ROLE"): String = "${prefix}_${UUID.randomUUID().toString().take(8).uppercase()}"

    /**
     * Creates a new random role ID.
     */
    fun randomRoleId(): RoleId = RoleId.generate()

    /**
     * Creates an ADMIN role for testing.
     */
    fun createAdminRole(
        id: RoleId = randomRoleId(),
        name: String = "ADMIN",
        description: String = "System administrator with full access",
    ): Role =
        Role(
            id,
            name,
            description,
            Instant.now().minusSeconds(86400),
            Instant.now().minusSeconds(3600),
        )

    /**
     * Creates a USER role for testing.
     */
    fun createUserRole(
        id: RoleId = randomRoleId(),
        name: String = "USER",
        description: String = "Standard user with limited access",
    ): Role =
        Role(
            id,
            name,
            description,
            Instant.now().minusSeconds(86400),
            Instant.now().minusSeconds(3600),
        )

    /**
     * Creates a MODERATOR role for testing.
     */
    fun createModeratorRole(
        id: RoleId = randomRoleId(),
        name: String = "MODERATOR",
        description: String = "Content moderator with approval permissions",
    ): Role =
        Role(
            id,
            name,
            description,
            Instant.now().minusSeconds(86400),
            Instant.now().minusSeconds(3600),
        )

    /**
     * Creates a custom role for testing.
     */
    fun createCustomRole(
        id: RoleId = randomRoleId(),
        name: String = validName(),
        description: String = "Custom test role",
    ): Role =
        Role(
            id,
            name,
            description,
            Instant.now(),
            Instant.now(),
        )

    /**
     * Creates role creation request data.
     */
    fun createRoleRequest(
        name: String = validName(),
        description: String = "Test role description",
    ): Map<String, Any> =
        mapOf(
            "name" to name,
            "description" to description,
        )

    /**
     * Invalid role names for validation testing.
     */
    val invalidNames =
        listOf(
            "", // Empty
            "   ", // Whitespace only
            "a".repeat(51), // Too long (max 50)
            "role with spaces", // Contains spaces (will be uppercased)
            "role@special!", // Special characters
        )
}
