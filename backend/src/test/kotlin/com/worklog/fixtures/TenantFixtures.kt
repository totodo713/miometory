package com.worklog.fixtures

import java.util.UUID

/**
 * Test fixtures for Tenant-related entities.
 * Uses Instancio for generating test data.
 */
object TenantFixtures {
    /**
     * Creates a valid tenant code (alphanumeric, max 32 chars).
     */
    fun validCode(prefix: String = "TENANT"): String = "${prefix}_${UUID.randomUUID().toString().take(8).uppercase()}"

    /**
     * Creates a valid tenant name.
     */
    fun validName(suffix: String = ""): String = "Test Tenant${if (suffix.isNotEmpty()) " $suffix" else ""}"

    /**
     * Creates a new random tenant ID.
     */
    fun randomId(): UUID = UUID.randomUUID()

    /**
     * Creates tenant creation request data.
     */
    fun createTenantRequest(
        code: String = validCode(),
        name: String = validName(),
    ): Map<String, Any> =
        mapOf(
            "code" to code,
            "name" to name,
        )

    /**
     * Creates tenant update request data.
     */
    fun updateTenantRequest(name: String = validName("Updated")): Map<String, Any> =
        mapOf(
            "name" to name,
        )

    /**
     * Invalid codes for validation testing.
     */
    val invalidCodes =
        listOf(
            "", // Empty
            "   ", // Whitespace only
            "a".repeat(33), // Too long (max 32)
            "tenant with spaces", // Contains spaces
            "tenant@special!", // Special characters
        )

    /**
     * Invalid names for validation testing.
     */
    val invalidNames =
        listOf(
            "", // Empty
            "   ", // Whitespace only
            "a".repeat(257), // Too long (max 256)
        )
}
