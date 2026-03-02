package com.worklog

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * Base class for integration tests using Testcontainers PostgreSQL.
 *
 * Features:
 * - PostgreSQL 17 container with reuse enabled for faster test execution
 * - Spring Boot test context with web environment
 * - Active "test" profile
 * - Dynamic property source for database connection
 *
 * Usage:
 * ```
 * class MyIntegrationTest : IntegrationTestBase() {
 *     @Test
 *     fun myTest() {
 *         // test code
 *     }
 * }
 * ```
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Suppress("UtilityClassWithPublicConstructor") // Abstract base class for integration tests
abstract class IntegrationTestBase {
    @Autowired
    protected lateinit var baseJdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    companion object {
        private const val TEST_TENANT_ID = "550e8400-e29b-41d4-a716-446655440001"
        private const val TEST_ORG_ID = "880e8400-e29b-41d4-a716-446655440001"

        @JvmStatic
        private val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName("worklog_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true)
                .apply { start() }

        // Redis Testcontainer for integration tests
        @JvmStatic
        private val redis: GenericContainer<*> =
            GenericContainer("redis:8-alpine")
                .withExposedPorts(6379)
                .withReuse(true)
                .apply { start() }

        // MailHog Testcontainer to provide an SMTP server for mail health checks
        @JvmStatic
        private val mailhog: GenericContainer<*> =
            GenericContainer("mailhog/mailhog:latest")
                .withExposedPorts(1025, 8025)
                .withReuse(true)
                .apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("spring.flyway.locations") { "classpath:db/migration,classpath:db/testdata" }
            // Configure Redis Testcontainer host/port for tests
            registry.add("spring.redis.host") { redis.host }
            registry.add("spring.redis.port") { redis.getMappedPort(6379).toString() }
            // Configure MailHog SMTP host/port for tests so mail health indicator can succeed
            registry.add("spring.mail.host") { mailhog.host }
            registry.add("spring.mail.port") { mailhog.getMappedPort(1025).toString() }
            // If Testcontainers Redis mapping isn't picked up by Lettuce in time, disable Redis health check
            registry.add("management.health.redis.enabled") { "false" }
        }
    }

    /**
     * Executes the given block in a new, independent transaction that commits immediately.
     * This ensures data is visible to HTTP requests running in separate transactions.
     */
    protected fun executeInNewTransaction(block: () -> Unit) {
        val txTemplate = TransactionTemplate(transactionManager)
        txTemplate.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        txTemplate.executeWithoutResult { block() }
    }

    /**
     * Creates a member record for testing. Call this before creating work log entries or absences.
     * Uses a separate committed transaction so the data is visible to HTTP requests.
     */
    protected fun createTestMember(memberId: UUID, email: String = "test-$memberId@example.com") {
        executeInNewTransaction {
            baseJdbcTemplate.update(
                """INSERT INTO members (id, tenant_id, organization_id, email, display_name, manager_id, is_active, version, created_at, updated_at)
                   VALUES (?, ?::UUID, ?::UUID, ?, ?, NULL, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                   ON CONFLICT (id) DO NOTHING""",
                memberId,
                TEST_TENANT_ID,
                TEST_ORG_ID,
                email,
                "Test User $memberId",
            )
        }
    }

    /**
     * Sets the manager for a member. Call after creating both members.
     * This establishes the subordinate relationship used by isSubordinateOf().
     * Uses a separate committed transaction so the data is visible to HTTP requests.
     */
    protected fun setManagerForMember(memberId: UUID, managerId: UUID) {
        executeInNewTransaction {
            baseJdbcTemplate.update(
                "UPDATE members SET manager_id = ? WHERE id = ?",
                managerId,
                memberId,
            )
        }
    }

    /**
     * Creates a project record for testing. Call this before creating work log entries that reference the project.
     * Uses a separate committed transaction so the data is visible to HTTP requests.
     */
    protected fun createTestProject(projectId: UUID, code: String = "TEST-$projectId") {
        executeInNewTransaction {
            baseJdbcTemplate.update(
                """INSERT INTO projects (id, tenant_id, code, name, is_active, created_at, updated_at)
                   VALUES (?, ?::UUID, ?, ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                   ON CONFLICT (id) DO NOTHING""",
                projectId,
                TEST_TENANT_ID,
                code.take(50),
                "Test Project $projectId",
            )
        }
    }
}
