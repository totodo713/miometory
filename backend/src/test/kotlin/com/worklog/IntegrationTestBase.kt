package com.worklog

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.GenericContainer
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
        private val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16-alpine")
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
            registry.add("spring.flyway.locations") { "classpath:db/migration" }
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
}
