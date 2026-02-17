package com.worklog.fixtures

import com.worklog.domain.organization.Organization
import com.worklog.domain.organization.OrganizationId
import com.worklog.domain.shared.Code
import com.worklog.domain.tenant.TenantId
import java.util.UUID

/**
 * Test fixtures for Organization-related entities.
 */
object OrganizationFixtures {
    /**
     * Creates a valid organization code.
     */
    fun validCode(prefix: String = "ORG"): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"

    /**
     * Creates a random Code value object.
     */
    fun randomCode(prefix: String = "ORG"): Code = Code(validCode(prefix))

    /**
     * Creates a valid organization name.
     */
    fun validName(suffix: String = ""): String = "Test Organization${if (suffix.isNotEmpty()) " $suffix" else ""}"

    /**
     * Creates a new random organization ID.
     */
    fun randomId(): UUID = UUID.randomUUID()

    /**
     * Creates a random OrganizationId value object.
     */
    fun randomOrganizationId(): OrganizationId = OrganizationId(randomId())

    /**
     * Creates a sample Organization aggregate for testing.
     */
    fun createOrganization(
        id: OrganizationId = randomOrganizationId(),
        tenantId: TenantId = TenantId(UUID.randomUUID()),
        parentId: OrganizationId? = null,
        code: Code = randomCode(),
        name: String = validName(),
        level: Int = 1,
    ): Organization = Organization.create(id, tenantId, parentId, code, name, level)

    /**
     * Creates organization creation request data.
     */
    fun createOrganizationRequest(
        code: String = validCode(),
        name: String = validName(),
        parentId: UUID? = null,
        level: Int = 1,
    ): Map<String, Any?> = mutableMapOf<String, Any?>(
        "code" to code,
        "name" to name,
        "level" to level,
    ).apply {
        if (parentId != null) {
            this["parentId"] = parentId.toString()
        }
    }

    /**
     * Creates organization update request data.
     */
    fun updateOrganizationRequest(name: String = validName("Updated"), parentId: UUID? = null): Map<String, Any?> =
        mutableMapOf<String, Any?>(
            "name" to name,
        ).apply {
            if (parentId != null) {
                this["parentId"] = parentId.toString()
            }
        }

    /**
     * Invalid levels for validation testing (valid range: 1-6).
     */
    val invalidLevels = listOf(0, -1, 7, 10, 100)

    /**
     * Creates a hierarchy of organization requests for testing.
     */
    fun createHierarchy(depth: Int = 3): List<Map<String, Any?>> {
        val organizations = mutableListOf<Map<String, Any?>>()
        var parentId: UUID? = null

        for (level in 1..depth) {
            val orgId = randomId()
            organizations.add(
                createOrganizationRequest(
                    code = validCode("L$level"),
                    name = validName("Level $level"),
                    parentId = parentId,
                    level = level,
                ) + ("id" to orgId),
            )
            parentId = orgId
        }

        return organizations
    }
}
