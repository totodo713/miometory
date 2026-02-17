package com.worklog.fixtures

import com.worklog.application.auth.LoginRequest
import com.worklog.application.auth.RegistrationRequest
import com.worklog.domain.role.RoleId
import com.worklog.domain.user.User
import com.worklog.domain.user.UserId
import java.time.Instant
import java.util.UUID

/**
 * Test fixtures for User-related entities.
 * Provides helper methods for creating test users with various states.
 */
object UserFixtures {
    /**
     * Creates a valid email address for testing.
     */
    fun validEmail(prefix: String = "user"): String = "${prefix}_${UUID.randomUUID().toString().take(8)}@example.com"

    /**
     * Creates a valid user name.
     */
    fun validName(firstName: String = "Test", lastName: String = "User"): String = "$firstName $lastName"

    /**
     * Valid bcrypt hashed password (hash of "password123").
     */
    const val VALID_HASHED_PASSWORD: String = "\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"

    /**
     * Creates a new random user ID.
     */
    fun randomUserId(): UserId = UserId.generate()

    /**
     * Creates a new random role ID.
     */
    fun randomRoleId(): RoleId = RoleId.generate()

    /**
     * Creates an active verified user for testing.
     */
    fun createActiveUser(
        id: UserId = randomUserId(),
        email: String = validEmail(),
        name: String = validName(),
        hashedPassword: String = VALID_HASHED_PASSWORD,
        roleId: RoleId = randomRoleId(),
        emailVerifiedAt: Instant = Instant.now().minusSeconds(3600),
    ): User = User(
        id,
        email,
        name,
        hashedPassword,
        roleId,
        User.AccountStatus.ACTIVE,
        0,
        null,
        Instant.now().minusSeconds(86400),
        Instant.now().minusSeconds(3600),
        Instant.now().minusSeconds(600),
        emailVerifiedAt,
    )

    /**
     * Creates an unverified user for testing.
     */
    fun createUnverifiedUser(
        id: UserId = randomUserId(),
        email: String = validEmail(),
        name: String = validName(),
        hashedPassword: String = VALID_HASHED_PASSWORD,
        roleId: RoleId = randomRoleId(),
    ): User = User(
        id,
        email,
        name,
        hashedPassword,
        roleId,
        User.AccountStatus.UNVERIFIED,
        0,
        null,
        Instant.now(),
        Instant.now(),
        null,
        null,
    )

    /**
     * Creates a locked user for testing.
     */
    fun createLockedUser(
        id: UserId = randomUserId(),
        email: String = validEmail(),
        name: String = validName(),
        hashedPassword: String = VALID_HASHED_PASSWORD,
        roleId: RoleId = randomRoleId(),
        lockedUntil: Instant = Instant.now().plusSeconds(1800),
    ): User = User(
        id,
        email,
        name,
        hashedPassword,
        roleId,
        User.AccountStatus.LOCKED,
        5,
        lockedUntil,
        Instant.now().minusSeconds(86400),
        Instant.now(),
        Instant.now().minusSeconds(7200),
        Instant.now().minusSeconds(82800),
    )

    /**
     * Creates a deleted user for testing.
     */
    fun createDeletedUser(
        id: UserId = randomUserId(),
        email: String = validEmail(),
        name: String = validName(),
        hashedPassword: String = VALID_HASHED_PASSWORD,
        roleId: RoleId = randomRoleId(),
    ): User = User(
        id,
        email,
        name,
        hashedPassword,
        roleId,
        User.AccountStatus.DELETED,
        0,
        null,
        Instant.now().minusSeconds(86400),
        Instant.now(),
        Instant.now().minusSeconds(3600),
        Instant.now().minusSeconds(82800),
    )

    /**
     * Creates registration request.
     */
    fun createRegistrationRequest(
        email: String = validEmail(),
        name: String = "Test User",
        password: String = "password123",
    ): RegistrationRequest = RegistrationRequest(email, name, password)

    /**
     * Creates login request.
     */
    fun createLoginRequest(
        email: String = validEmail(),
        password: String = "password123",
        rememberMe: Boolean = false,
    ): LoginRequest = LoginRequest(email, password, rememberMe)

    /**
     * Invalid emails for validation testing.
     * Note: "user..name@example.com" is technically invalid per RFC 5321 but passes the simplified regex.
     */
    val invalidEmails =
        listOf(
            "", // Empty
            "   ", // Whitespace only
            "notanemail", // No @ symbol
            "@example.com", // No local part
            "user@", // No domain
            "user @example.com", // Space in email
            "a".repeat(256) + "@example.com", // Too long
            "user@domain", // No TLD
        )

    /**
     * Invalid names for validation testing.
     */
    val invalidNames =
        listOf(
            "", // Empty
            "   ", // Whitespace only
            "a".repeat(101), // Too long (max 100)
        )

    /**
     * Weak passwords for validation testing.
     */
    val weakPasswords =
        listOf(
            "", // Empty
            "short", // Too short (< 8 chars)
            "12345678", // No letters
            "password", // No numbers or special chars
            "Password", // No numbers or special chars
            "password123", // No special chars
            "PASSWORD123!", // No lowercase
        )
}
