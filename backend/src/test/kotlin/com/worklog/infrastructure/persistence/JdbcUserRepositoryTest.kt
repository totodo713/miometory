package com.worklog.infrastructure.persistence

import com.worklog.domain.role.RoleId
import com.worklog.domain.user.User
import com.worklog.domain.user.UserId
import org.junit.jupiter.api.*
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
import java.time.Instant
import java.util.*

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
        // Clean users table before each test
        jdbcTemplate.update("DELETE FROM users")
        
        // Set up test role (assuming roles table is populated by migrations)
        testRoleId = RoleId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    }

    // ============================================================
    // Save and FindById Tests
    // ============================================================

    @Test
    fun `save and findById - should persist and retrieve user`() {
        // Given
        val user = User.create(
            "[email protected]",
            "Test User",
            "hashed_password_123",
            testRoleId
        )

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
        val user = User.create(
            "[email protected]",
            "Original Name",
            "hashed_password_123",
            testRoleId
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
        val user = User.create(
            "[email protected]",
            "Test User",
            "hashed_password_123",
            testRoleId
        )
        repository.save(user)

        // When - search with different case
        val result = repository.findByEmail("TEST@EXAMPLE.COM")

        // Then
        Assertions.assertTrue(result.isPresent)
        Assertions.assertEquals("[email protected]", result.get().email)
    }

    @Test
    fun `findByEmail - should return empty for non-existent email`() {
        // When
        val result = repository.findByEmail("[email protected]")

        // Then
        Assertions.assertTrue(result.isEmpty)
    }

    // ============================================================
    // ExistsByEmail Tests
    // ============================================================

    @Test
    fun `existsByEmail - should return true when email exists`() {
        // Given
        val user = User.create(
            "[email protected]",
            "Test User",
            "hashed_password_123",
            testRoleId
        )
        repository.save(user)

        // When
        val exists = repository.existsByEmail("[email protected]")

        // Then
        Assertions.assertTrue(exists)
    }

    @Test
    fun `existsByEmail - should be case-insensitive`() {
        // Given
        val user = User.create(
            "[email protected]",
            "Test User",
            "hashed_password_123",
            testRoleId
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
        val exists = repository.existsByEmail("[email protected]")

        // Then
        Assertions.assertFalse(exists)
    }

    // ============================================================
    // FindByAccountStatus Tests
    // ============================================================

    @Test
    fun `findByAccountStatus - should find users by status`() {
        // Given
        val user1 = User.create("[email protected]", "User 1", "pass1", testRoleId)
        val user2 = User.create("[email protected]", "User 2", "pass2", testRoleId)
        user2.verifyEmail()
        
        repository.save(user1)
        repository.save(user2)

        // When
        val unverified = repository.findByAccountStatus(User.AccountStatus.UNVERIFIED)
        val active = repository.findByAccountStatus(User.AccountStatus.ACTIVE)

        // Then
        Assertions.assertEquals(1, unverified.size)
        Assertions.assertEquals("[email protected]", unverified[0].email)
        
        Assertions.assertEquals(1, active.size)
        Assertions.assertEquals("[email protected]", active[0].email)
    }

    // ============================================================
    // FindExpiredLockedUsers Tests
    // ============================================================

    @Test
    fun `findExpiredLockedUsers - should find users with expired locks`() {
        // Given - create locked user with expired lock time
        val user = User.create("[email protected]", "Locked User", "pass", testRoleId)
        user.verifyEmail()
        
        // Lock the user with a timestamp in the past
        user.recordFailedLogin(5, 15) // This will lock the account
        
        // Manually set an expired lock time by creating a new user with past locked_until
        val expiredLockTime = Instant.now().minusSeconds(3600) // 1 hour ago
        val lockedUser = User(
            user.id,
            user.email,
            user.name,
            user.hashedPassword,
            user.roleId,
            User.AccountStatus.LOCKED,
            5,
            expiredLockTime, // Lock expired 1 hour ago
            user.createdAt,
            user.updatedAt,
            null,
            user.emailVerifiedAt
        )
        repository.save(lockedUser)

        // When
        val expiredUsers = repository.findExpiredLockedUsers(Instant.now())

        // Then
        Assertions.assertEquals(1, expiredUsers.size)
        Assertions.assertEquals("[email protected]", expiredUsers[0].email)
        Assertions.assertEquals(User.AccountStatus.LOCKED, expiredUsers[0].accountStatus)
    }

    // ============================================================
    // FindUnverifiedUsersBefore Tests
    // ============================================================

    @Test
    fun `findUnverifiedUsersBefore - should find old unverified users`() {
        // Given - create user with old creation timestamp
        val oldCreationTime = Instant.now().minusSeconds(86400 * 8) // 8 days ago
        val oldUser = User(
            UserId.generate(),
            "[email protected]",
            "Old User",
            "pass",
            testRoleId,
            User.AccountStatus.UNVERIFIED,
            0,
            null, // lockedUntil
            oldCreationTime, // createdAt
            oldCreationTime, // updatedAt
            null, // lastLoginAt
            null  // emailVerifiedAt
        )
        
        val recentUser = User.create("[email protected]", "Recent User", "pass", testRoleId)
        
        repository.save(oldUser)
        repository.save(recentUser)

        // When - find users older than 7 days
        val cutoffDate = Instant.now().minusSeconds(86400 * 7)
        val oldUnverified = repository.findUnverifiedUsersBefore(cutoffDate)

        // Then
        Assertions.assertEquals(1, oldUnverified.size)
        Assertions.assertEquals("[email protected]", oldUnverified[0].email)
    }

    // ============================================================
    // Delete Tests
    // ============================================================

    @Test
    fun `deleteById - should remove user`() {
        // Given
        val user = User.create("[email protected]", "Test User", "pass", testRoleId)
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
        repository.save(User.create("[email protected]", "User 1", "pass1", testRoleId))
        repository.save(User.create("[email protected]", "User 2", "pass2", testRoleId))
        
        Assertions.assertEquals(2, repository.count())

        // When
        repository.deleteAll()

        // Then
        Assertions.assertEquals(0, repository.count())
    }

    // ============================================================
    // Count Tests
    // ============================================================

    @Test
    fun `count - should return correct user count`() {
        // Given
        Assertions.assertEquals(0, repository.count())
        
        repository.save(User.create("[email protected]", "User 1", "pass1", testRoleId))
        repository.save(User.create("[email protected]", "User 2", "pass2", testRoleId))

        // When
        val count = repository.count()

        // Then
        Assertions.assertEquals(2, count)
    }
}
