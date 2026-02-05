package com.worklog.infrastructure.persistence

import com.worklog.domain.role.RoleId
import com.worklog.domain.user.User
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.*

/**
 * Integration tests for email uniqueness at DB and repository level (T038).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class JdbcUserEmailUniquenessTest {

    companion object {
        @Container
        @JvmStatic
        val postgresContainer: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("worklog_test")
            .withUsername("test")
            .withPassword("test")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgresContainer::getJdbcUrl)
            registry.add("spring.datasource.username", postgresContainer::getUsername)
            registry.add("spring.datasource.password", postgresContainer::getPassword)
            registry.add("spring.flyway.enabled") { "true" }
        }
    }

    @Autowired
    private lateinit var repository: JdbcUserRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var testRoleId: RoleId

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM users")

        testRoleId = RoleId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        jdbcTemplate.update(
            """
            INSERT INTO roles (id, name, description, created_at, updated_at)
            VALUES (?, 'TEST_ROLE', 'Test role for integration tests', NOW(), NOW())
            ON CONFLICT (id) DO NOTHING
            """, testRoleId.value()
        )
    }

    @Test
    fun `save - duplicate email should throw DataIntegrityViolationException`() {
        // Given
        val user1 = User.create("unique@example.com", "User One", "pass1", testRoleId)
        repository.save(user1)

        // When / Then - different user (different id) but same email should violate unique constraint
        val user2 = User.create("unique@example.com", "User Two", "pass2", testRoleId)

        Assertions.assertThrows(DataIntegrityViolationException::class.java) {
            repository.save(user2)
        }
    }

    @Test
    fun `existsByEmail - is case insensitive`() {
        // Given
        val user = User.create("CaseTest@example.com", "Case Test", "pass", testRoleId)
        repository.save(user)

        // When / Then
        Assertions.assertTrue(repository.existsByEmail("casetest@EXAMPLE.com"))
    }

    @Test
    fun `save - duplicate email with different case should throw`() {
        // Given
        val lower = User.create("case@example.com", "Lower", "p1", testRoleId)
        repository.save(lower)

        // When - create with different case
        val upper = User.create("CASE@EXAMPLE.COM", "Upper", "p2", testRoleId)

        // Then
        Assertions.assertThrows(DataIntegrityViolationException::class.java) {
            repository.save(upper)
        }
    }

    @Test
    fun `save - same id should update without uniqueness error`() {
        // Given
        val user = User.create("sameid@example.com", "Original", "p1", testRoleId)
        repository.save(user)

        // Update name and save same id
        user.update("sameid@example.com", "Updated Name")
        Assertions.assertDoesNotThrow { repository.save(user) }

        val found = repository.findByEmail("sameid@example.com").orElseThrow()
        Assertions.assertEquals("Updated Name", found.name)
    }
}
