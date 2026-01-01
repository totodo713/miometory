package com.worklog

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Base class for integration tests using Testcontainers PostgreSQL.
 *
 * Features:
 * - PostgreSQL 16 container with reuse enabled for faster test execution
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
abstract class IntegrationTestBase {

    companion object {
        @JvmStatic
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("worklog_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)
            .apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("spring.flyway.locations") { "classpath:db/migration" }
        }
    }
}
