package com.worklog.infrastructure.persistence

import com.worklog.domain.role.RoleId
import com.worklog.domain.user.User
import com.worklog.domain.user.UserId
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Integration tests for JdbcUserRepository (T036).
 *
 * Tests repository operations against a real PostgreSQL database using Testcontainers:
 * - CRUD operations (save, findById, findByEmail, delete)
 * - Custom queries (findByAccountStatus, findExpiredLockedUsers, findUnverifiedUsersBefore)
 * - Transaction behavior
 * - Data persistence
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class JdbcUserRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val postgresContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16-alpine")
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
        // Clean users table before each test
        jdbcTemplate.update("DELETE FROM users")

        // Ensure test role exists (needed for foreign key constraint)
        testRoleId = RoleId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        jdbcTemplate.update(
            """
            INSERT INTO roles (id, name, description, created_at, updated_at)
            VALUES (?, 'TEST_ROLE', 'Test role for integration tests', NOW(), NOW())
            ON CONFLICT (id) DO NOTHING
        """,
            testRoleId.value(),
        )
    }

    // ============================================================
    // Save and FindById Tests
    // ============================================================

    @Test
    fun `save and findById - should persist and retrieve user`() {
        // Given
        val user =
            try {
                User.create(
                    "test@example.com",
                    "Test User",
                    "hashed_password_123",
                    testRoleId,
                )
            } catch (e: IllegalArgumentException) {
                throw AssertionError("Failed to create user: ${e.message}", e)
            }

        // When
        repository.save(user)
        val retrieved = repository.findById(user.id)

        // Then
        Assertions.assertTrue(retrieved.isPresent)
        retrieved.get().let { found ->
            Assertions.assertEquals(user.id, found.id)
            Assertions.assertEquals(user.email, found.email)
            Assertions.assertEquals(user.name, found.name)
            Assertions.assertEquals(user.hashedPassword, found.hashedPassword)
            Assertions.assertEquals(user.roleId, found.roleId)
            Assertions.assertEquals(User.AccountStatus.UNVERIFIED, found.accountStatus)
            Assertions.assertEquals(0, found.failedLoginAttempts)
        }
    }

    @Test
    fun `save with update - should update existing user`() {
        // Given - create and save initial user
        val user =
            User.create(
                "test@example.com",
                "Original Name",
                "hashed_password_123",
                testRoleId,
            )
        repository.save(user)

        // When - update user and save again
        user.update(user.email, "Updated Name")
        repository.save(user)

        // Then
        val retrieved = repository.findById(user.id)
        Assertions.assertTrue(retrieved.isPresent)
        Assertions.assertEquals("Updated Name", retrieved.get().name)
    }

    @Test
    fun `findById - should return empty for non-existent user`() {
        // When
        val result = repository.findById(UserId.generate())

        // Then
        Assertions.assertTrue(result.isEmpty)
    }

    // ============================================================
    // FindByEmail Tests
    // ============================================================

    @Test
    fun `findByEmail - should find user case-insensitively`() {
        // Given
        val user =
            User.create(
                "test@example.com",
                "Test User",
                "hashed_password_123",
                testRoleId,
            )
        repository.save(user)

        // When - search with different case
        val result = repository.findByEmail("TEST@EXAMPLE.COM")

        // Then
        Assertions.assertTrue(result.isPresent)
        Assertions.assertEquals("test@example.com", result.get().email)
    }

    @Test
    fun `findByEmail - should return empty for non-existent email`() {
        // When
        val result = repository.findByEmail("test@example.com")

        // Then
        Assertions.assertTrue(result.isEmpty)
    }

    // ============================================================
    // ExistsByEmail Tests
    // ============================================================

    @Test
    fun `existsByEmail - should return true when email exists`() {
        // Given
        val user =
            User.create(
                "test@example.com",
                "Test User",
                "hashed_password_123",
                testRoleId,
            )
        repository.save(user)

        // When
        val exists = repository.existsByEmail("test@example.com")

        // Then
        Assertions.assertTrue(exists)
    }

    @Test
    fun `existsByEmail - should be case-insensitive`() {
        // Given
        val user =
            User.create(
                "test@example.com",
                "Test User",
                "hashed_password_123",
                testRoleId,
            )
        repository.save(user)

        // When
        val exists = repository.existsByEmail("TEST@EXAMPLE.COM")

        // Then
        Assertions.assertTrue(exists)
    }

    @Test
    fun `existsByEmail - should return false when email does not exist`() {
        // When
        val exists = repository.existsByEmail("test@example.com")

        // Then
        Assertions.assertFalse(exists)
    }

    // ============================================================
    // FindByAccountStatus Tests
    // ============================================================

    @Test
    fun `findByAccountStatus - should find users by status`() {
        // Given
        val user1 = User.create("user1@example.com", "User 1", "pass1", testRoleId)
        val user2 = User.create("user2@example.com", "User 2", "pass2", testRoleId)
        user2.verifyEmail()

        repository.save(user1)
        repository.save(user2)

        // When
        val unverified = repository.findByAccountStatus(User.AccountStatus.UNVERIFIED)
        val active = repository.findByAccountStatus(User.AccountStatus.ACTIVE)

        // Then
        Assertions.assertEquals(1, unverified.size)
        Assertions.assertEquals("user1@example.com", unverified[0].email)

        Assertions.assertEquals(1, active.size)
        Assertions.assertEquals("user2@example.com", active[0].email)
    }

    // ============================================================
    // FindExpiredLockedUsers Tests
    // ============================================================

    @Test
    fun `findExpiredLockedUsers - should find users with expired locks`() {
        // Given - create locked user
        val user = User.create("locked@example.com", "Locked User", "pass", testRoleId)
        user.verifyEmail()

        // Lock the user
        val lockedUser =
            User(
                user.id,
                user.email,
                user.name,
                user.hashedPassword,
                user.roleId,
                User.AccountStatus.LOCKED,
                5,
                Instant.now().plusSeconds(3600), // Set valid future lock time first
                user.createdAt,
                user.updatedAt,
                null,
                user.emailVerifiedAt,
                user.preferredLocale,
            )
        repository.save(lockedUser)

        // Temporarily disable the future lock constraint to allow setting past timestamp for testing
        // Note: This will be auto-rolled back by @Transactional
        jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT chk_locked_until_future")

        // Update locked_until to the past
        val expiredLockTime = Instant.now().minusSeconds(3600) // 1 hour ago
        jdbcTemplate.update(
            "UPDATE users SET locked_until = ? WHERE id = ?",
            Timestamp.from(expiredLockTime),
            user.id.value(),
        )

        // When
        val expiredUsers = repository.findExpiredLockedUsers(Instant.now())

        // Then
        Assertions.assertEquals(1, expiredUsers.size)
        Assertions.assertEquals("locked@example.com", expiredUsers[0].email)
        Assertions.assertEquals(User.AccountStatus.LOCKED, expiredUsers[0].accountStatus)
    }

    // ============================================================
    // FindUnverifiedUsersBefore Tests
    // ============================================================

    @Test
    fun `findUnverifiedUsersBefore - should find old unverified users`() {
        // Given - create user with old creation timestamp
        val oldCreationTime = Instant.now().minusSeconds(86400 * 8) // 8 days ago
        val oldUser =
            User(
                UserId.generate(),
                "old@example.com",
                "Old User",
                "pass",
                testRoleId,
                User.AccountStatus.UNVERIFIED,
                0,
                null, // lockedUntil
                oldCreationTime, // createdAt
                oldCreationTime, // updatedAt
                null, // lastLoginAt
                null, // emailVerifiedAt
                "ja", // preferredLocale
            )

        val recentUser = User.create("recent@example.com", "Recent User", "pass", testRoleId)

        repository.save(oldUser)
        repository.save(recentUser)

        // When - find users older than 7 days
        val cutoffDate = Instant.now().minusSeconds(86400 * 7)
        val oldUnverified = repository.findUnverifiedUsersBefore(cutoffDate)

        // Then
        Assertions.assertEquals(1, oldUnverified.size)
        Assertions.assertEquals("old@example.com", oldUnverified[0].email)
    }

    // ============================================================
    // Delete Tests
    // ============================================================

    @Test
    fun `deleteById - should remove user`() {
        // Given
        val user = User.create("test@example.com", "Test User", "pass", testRoleId)
        repository.save(user)

        Assertions.assertTrue(repository.findById(user.id).isPresent)

        // When
        repository.deleteById(user.id)

        // Then
        Assertions.assertTrue(repository.findById(user.id).isEmpty)
    }

    @Test
    fun `deleteAll - should remove all users`() {
        // Given
        repository.save(User.create("user1@example.com", "User 1", "pass1", testRoleId))
        repository.save(User.create("user2@example.com", "User 2", "pass2", testRoleId))

        Assertions.assertEquals(2, repository.count())

        // When
        repository.deleteAll()

        // Then
        Assertions.assertEquals(0, repository.count())
    }

    // ============================================================
    // Locale Tests
    // ============================================================

    @Test
    fun `updateLocale should update preferred locale`() {
        // Given
        val user = User.create("locale@example.com", "Locale User", "hashed_pass", testRoleId)
        repository.save(user)

        // When
        repository.updateLocale(user.id, "en")

        // Then
        val retrieved = repository.findById(user.id)
        Assertions.assertTrue(retrieved.isPresent)
        Assertions.assertEquals("en", retrieved.get().preferredLocale)
    }

    @Test
    fun `save should persist preferredLocale`() {
        // Given - create user with full constructor specifying locale="en"
        val user =
            User(
                UserId.generate(),
                "locale-save@example.com",
                "Locale Save User",
                "hashed_pass",
                testRoleId,
                User.AccountStatus.ACTIVE,
                0,
                null,
                Instant.now(),
                Instant.now(),
                null,
                Instant.now().minusSeconds(3600),
                "en",
            )

        // When
        repository.save(user)

        // Then
        val retrieved = repository.findById(user.id)
        Assertions.assertTrue(retrieved.isPresent)
        Assertions.assertEquals("en", retrieved.get().preferredLocale)
    }

    // ============================================================
    // Count Tests
    // ============================================================

    @Test
    fun `count - should return correct user count`() {
        // Given
        Assertions.assertEquals(0, repository.count())

        repository.save(User.create("user1@example.com", "User 1", "pass1", testRoleId))
        repository.save(User.create("user2@example.com", "User 2", "pass2", testRoleId))

        // When
        val count = repository.count()

        // Then
        Assertions.assertEquals(2, count)
    }

    // ============================================================
    // FindExistingEmails Tests
    // ============================================================

    @Test
    fun `findExistingEmails - should return matching emails`() {
        repository.save(User.create("alice@example.com", "Alice", "pass1", testRoleId))
        repository.save(User.create("bob@example.com", "Bob", "pass2", testRoleId))

        val result = repository.findExistingEmails(listOf("alice@example.com", "charlie@example.com"))

        Assertions.assertEquals(setOf("alice@example.com"), result)
    }

    @Test
    fun `findExistingEmails - should be case-insensitive`() {
        repository.save(User.create("alice@example.com", "Alice", "pass", testRoleId))

        val result = repository.findExistingEmails(listOf("ALICE@EXAMPLE.COM"))

        Assertions.assertEquals(1, result.size)
        Assertions.assertTrue(result.contains("alice@example.com"))
    }

    @Test
    fun `findExistingEmails - should return empty set for empty input`() {
        val result = repository.findExistingEmails(emptyList())

        Assertions.assertTrue(result.isEmpty())
    }

    @Test
    fun `findExistingEmails - should return empty set when no matches`() {
        val result = repository.findExistingEmails(listOf("nobody@example.com"))

        Assertions.assertTrue(result.isEmpty())
    }
}
