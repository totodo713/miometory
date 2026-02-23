package com.worklog.api

import com.worklog.IntegrationTestBase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import java.util.UUID

/**
 * Base class for admin controller integration tests.
 *
 * Extends IntegrationTestBase with MockMvc support and helpers
 * for creating test users with specific admin roles.
 *
 * Role IDs reference V18__admin_permissions_seed.sql and R__test_seed_data.sql.
 */
@AutoConfigureMockMvc
abstract class AdminIntegrationTestBase : IntegrationTestBase() {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    companion object {
        // V18 admin roles
        const val SYSTEM_ADMIN_ROLE_ID = "aa000000-0000-0000-0000-000000000001"
        const val TENANT_ADMIN_ROLE_ID = "aa000000-0000-0000-0000-000000000002"
        const val SUPERVISOR_ROLE_ID = "aa000000-0000-0000-0000-000000000003"

        // Dev seed role (no admin permissions)
        const val USER_ROLE_ID = "00000000-0000-0000-0000-000000000002"

        // Test data from R__test_infrastructure.sql
        const val ADM_TEST_TENANT_ID = "550e8400-e29b-41d4-a716-446655440001"
        const val ADM_TEST_ORG_ID = "880e8400-e29b-41d4-a716-446655440001"
    }

    /**
     * Creates a user in the users table with the given role.
     * Returns the generated user ID.
     */
    protected fun createUser(email: String, roleId: String, name: String = "Test User"): UUID {
        val userId = UUID.randomUUID()
        baseJdbcTemplate.update(
            """INSERT INTO users (id, email, hashed_password, name, role_id, account_status, created_at, updated_at)
               VALUES (?, ?, ?, ?, ?::UUID, 'active', NOW(), NOW())
               ON CONFLICT (email) DO NOTHING""",
            userId,
            email,
            "\$2b\$12\$placeholder_hash_for_test",
            name,
            roleId,
        )
        return userId
    }

    /**
     * Creates a member record for a user (needed for tenant-scoped controllers).
     * Returns the member ID.
     */
    protected fun createMemberForUser(
        email: String,
        memberId: UUID = UUID.randomUUID(),
        tenantId: String = ADM_TEST_TENANT_ID,
        orgId: String = ADM_TEST_ORG_ID,
        managerId: UUID? = null,
    ): UUID {
        baseJdbcTemplate.update(
            """INSERT INTO members (id, tenant_id, organization_id, email, display_name, manager_id, is_active, version, created_at, updated_at)
               VALUES (?, ?::UUID, ?::UUID, ?, ?, ?, true, 0, NOW(), NOW())
               ON CONFLICT (id) DO NOTHING""",
            memberId,
            tenantId,
            orgId,
            email,
            "Test $email",
            managerId,
        )
        return memberId
    }

    /**
     * Creates a project in the test tenant. Returns the project ID.
     */
    protected fun createProjectInTenant(
        projectId: UUID = UUID.randomUUID(),
        code: String = "PRJ-${projectId.toString().take(8)}",
        tenantId: String = ADM_TEST_TENANT_ID,
    ): UUID {
        baseJdbcTemplate.update(
            """INSERT INTO projects (id, tenant_id, code, name, is_active, created_at, updated_at)
               VALUES (?, ?::UUID, ?, ?, true, NOW(), NOW())
               ON CONFLICT (id) DO NOTHING""",
            projectId,
            tenantId,
            code,
            "Test Project $code",
        )
        return projectId
    }
}
