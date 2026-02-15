package com.worklog.domain.user

import com.worklog.domain.role.RoleId
import com.worklog.fixtures.UserFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Unit tests for User entity business logic.
 * Tests all domain behaviors without database dependencies.
 *
 * Note: User is a mutable entity with void methods that modify state in-place.
 */
@DisplayName("User Entity Tests")
class UserTest {
    @Nested
    @DisplayName("User Creation and Validation")
    inner class CreationAndValidation {
        @Test
        fun `should create new user with valid data`() {
            // Arrange & Act
            val user =
                User.create(
                    UserFixtures.validEmail(),
                    UserFixtures.validName(),
                    UserFixtures.validHashedPassword(),
                    RoleId.generate(),
                )

            // Assert
            assertNotNull(user.id)
            assertNotNull(user.email)
            assertNotNull(user.name)
            assertNotNull(user.hashedPassword)
            assertEquals(User.AccountStatus.UNVERIFIED, user.accountStatus) // New users start unverified
            assertEquals(0, user.failedLoginAttempts)
            assertNull(user.lockedUntil)
            assertNull(user.emailVerifiedAt)
        }

        @Test
        fun `should reject invalid email format`() {
            // Arrange & Act & Assert
            UserFixtures.invalidEmails.forEach { invalidEmail ->
                assertThrows<IllegalArgumentException>("Should reject email: $invalidEmail") {
                    User.create(
                        invalidEmail,
                        UserFixtures.validName(),
                        UserFixtures.validHashedPassword(),
                        RoleId.generate(),
                    )
                }
            }
        }

        @Test
        fun `should reject invalid name - too long`() {
            // Arrange
            val tooLongName = "a".repeat(101)

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                User.create(
                    UserFixtures.validEmail(),
                    tooLongName,
                    UserFixtures.validHashedPassword(),
                    RoleId.generate(),
                )
            }
        }

        @Test
        fun `should reject blank name`() {
            // Arrange & Act & Assert
            UserFixtures.invalidNames.forEach { invalidName ->
                assertThrows<IllegalArgumentException>("Should reject name: '$invalidName'") {
                    User.create(
                        UserFixtures.validEmail(),
                        invalidName,
                        UserFixtures.validHashedPassword(),
                        RoleId.generate(),
                    )
                }
            }
        }

        @Test
        fun `should normalize email to lowercase`() {
            // Arrange
            val mixedCaseEmail = "Test.User@EXAMPLE.COM"

            // Act
            val user =
                User.create(
                    mixedCaseEmail,
                    UserFixtures.validName(),
                    UserFixtures.validHashedPassword(),
                    RoleId.generate(),
                )

            // Assert
            assertEquals("test.user@example.com", user.email)
        }
    }

    @Nested
    @DisplayName("Login Tracking and Account Locking")
    inner class LoginTrackingAndLocking {
        @Test
        fun `should record successful login and reset failed attempts`() {
            // Arrange
            val user = UserFixtures.createActiveUser()
            // Simulate some failed attempts
            user.recordFailedLogin(5, 30)
            user.recordFailedLogin(5, 30)
            user.recordFailedLogin(5, 30)
            assertEquals(3, user.failedLoginAttempts)

            // Act
            user.recordSuccessfulLogin()

            // Assert
            assertEquals(0, user.failedLoginAttempts)
            assertNotNull(user.lastLoginAt)
            assertTrue(user.lastLoginAt!!.isAfter(Instant.now().minus(1, ChronoUnit.MINUTES)))
        }

        @Test
        fun `should increment failed login attempts`() {
            // Arrange
            val user = UserFixtures.createActiveUser()
            val maxAttempts = 5
            val lockDurationMinutes = 30

            // Act
            user.recordFailedLogin(maxAttempts, lockDurationMinutes)

            // Assert
            assertEquals(1, user.failedLoginAttempts)
            assertNull(user.lockedUntil) // Not locked yet
        }

        @Test
        fun `should lock account after max failed attempts`() {
            // Arrange
            val user = UserFixtures.createActiveUser()
            val maxAttempts = 5
            val lockDurationMinutes = 30

            // Act - Fail 5 times
            repeat(5) {
                user.recordFailedLogin(maxAttempts, lockDurationMinutes)
            }

            // Assert
            assertEquals(5, user.failedLoginAttempts)
            assertNotNull(user.lockedUntil)
            assertEquals(User.AccountStatus.LOCKED, user.accountStatus)

            // Verify lock duration is approximately 30 minutes
            val expectedUnlockTime = Instant.now().plus(lockDurationMinutes.toLong(), ChronoUnit.MINUTES)
            val actualUnlockTime = user.lockedUntil!!
            val diffSeconds = ChronoUnit.SECONDS.between(expectedUnlockTime, actualUnlockTime)
            assertTrue(Math.abs(diffSeconds) < 5, "Lock duration should be approximately $lockDurationMinutes minutes")
        }

        @Test
        fun `should manually lock account with custom duration`() {
            // Arrange
            val user = UserFixtures.createActiveUser()
            val lockDurationMinutes = 60

            // Act
            user.lock(lockDurationMinutes)

            // Assert
            assertEquals(User.AccountStatus.LOCKED, user.accountStatus)
            assertNotNull(user.lockedUntil)

            val expectedUnlockTime = Instant.now().plus(lockDurationMinutes.toLong(), ChronoUnit.MINUTES)
            val actualUnlockTime = user.lockedUntil!!
            val diffSeconds = ChronoUnit.SECONDS.between(expectedUnlockTime, actualUnlockTime)
            assertTrue(Math.abs(diffSeconds) < 5)
        }

        @Test
        fun `should unlock account and reset failed attempts`() {
            // Arrange
            val user = UserFixtures.createLockedUser()

            // Act
            user.unlock()

            // Assert
            assertEquals(User.AccountStatus.ACTIVE, user.accountStatus)
            assertNull(user.lockedUntil)
            assertEquals(0, user.failedLoginAttempts)
        }

        @Test
        fun `isLocked should return true when locked and lock has not expired`() {
            // Arrange
            val user = UserFixtures.createLockedUser()

            // Act & Assert
            assertTrue(user.isLocked())
        }

        @Test
        fun `isLocked should return false when lock has expired`() {
            // Arrange - Create user with expired lock (1 hour ago)
            val user =
                User(
                    UserId.generate(),
                    UserFixtures.validEmail(),
                    UserFixtures.validName(),
                    UserFixtures.validHashedPassword(),
                    RoleId.generate(),
                    User.AccountStatus.LOCKED,
                    5,
                    Instant.now().minus(1, ChronoUnit.HOURS), // Lock expired
                    Instant.now(),
                    Instant.now(),
                    null,
                    null,
                )

            // Act & Assert
            assertFalse(user.isLocked())
        }

        @Test
        fun `isLocked should return false for active account`() {
            // Arrange
            val activeUser = UserFixtures.createActiveUser()

            // Act & Assert
            assertFalse(activeUser.isLocked())
        }
    }

    @Nested
    @DisplayName("Email Verification")
    inner class EmailVerification {
        @Test
        fun `should verify email and activate account`() {
            // Arrange
            val unverifiedUser = UserFixtures.createUnverifiedUser()

            // Act
            unverifiedUser.verifyEmail()

            // Assert
            assertNotNull(unverifiedUser.emailVerifiedAt)
            assertEquals(User.AccountStatus.ACTIVE, unverifiedUser.accountStatus)
        }

        @Test
        fun `should throw when verifying already verified email`() {
            // Arrange
            val alreadyVerified = UserFixtures.createActiveUser()

            // Act & Assert
            assertThrows<IllegalStateException> {
                alreadyVerified.verifyEmail()
            }
        }

        @Test
        fun `isVerified should return true for verified user`() {
            // Arrange
            val verifiedUser = UserFixtures.createActiveUser()

            // Act & Assert
            assertTrue(verifiedUser.isVerified())
        }

        @Test
        fun `isVerified should return false for unverified user`() {
            // Arrange
            val unverifiedUser = UserFixtures.createUnverifiedUser()

            // Act & Assert
            assertFalse(unverifiedUser.isVerified())
        }
    }

    @Nested
    @DisplayName("Password Management")
    inner class PasswordManagement {
        @Test
        fun `should change password successfully`() {
            // Arrange
            val user = UserFixtures.createActiveUser()
            val newPasswordHash = "new-hashed-password-123"

            // Act
            user.changePassword(newPasswordHash)

            // Assert
            assertEquals(newPasswordHash, user.hashedPassword)
        }

        @Test
        fun `should reject blank password hash`() {
            // Arrange
            val user = UserFixtures.createActiveUser()

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                user.changePassword("")
            }
        }
    }

    @Nested
    @DisplayName("Account Soft Delete")
    inner class AccountSoftDelete {
        @Test
        fun `should soft delete account`() {
            // Arrange
            val user = UserFixtures.createActiveUser()

            // Act
            user.delete()

            // Assert
            assertEquals(User.AccountStatus.DELETED, user.accountStatus)
        }

        @Test
        fun `should restore deleted account`() {
            // Arrange
            val deletedUser = UserFixtures.createDeletedUser()

            // Act
            deletedUser.restore()

            // Assert
            assertEquals(User.AccountStatus.ACTIVE, deletedUser.accountStatus) // Restored to ACTIVE if email verified
        }
    }

    @Nested
    @DisplayName("Login Eligibility Checks")
    inner class LoginEligibilityChecks {
        @Test
        fun `canLogin should return true for active verified user`() {
            // Arrange
            val user = UserFixtures.createActiveUser()

            // Act & Assert
            assertTrue(user.canLogin())
        }

        @Test
        fun `canLogin should return false for unverified user`() {
            // Arrange
            val user = UserFixtures.createUnverifiedUser()

            // Act & Assert
            assertFalse(user.canLogin())
        }

        @Test
        fun `canLogin should return false for locked user`() {
            // Arrange
            val user = UserFixtures.createLockedUser()

            // Act & Assert
            assertFalse(user.canLogin())
        }

        @Test
        fun `canLogin should return false for deleted user`() {
            // Arrange
            val user = UserFixtures.createDeletedUser()

            // Act & Assert
            assertFalse(user.canLogin())
        }

        @Test
        fun `canLogin should return true for user with expired lock`() {
            // Arrange - User with expired lock
            val user =
                User(
                    UserId.generate(),
                    UserFixtures.validEmail(),
                    UserFixtures.validName(),
                    UserFixtures.validHashedPassword(),
                    RoleId.generate(),
                    User.AccountStatus.LOCKED,
                    5,
                    Instant.now().minus(1, ChronoUnit.HOURS), // Expired lock
                    Instant.now(),
                    Instant.now(),
                    null,
                    Instant.now().minus(1, ChronoUnit.DAYS), // Verified
                )

            // Act & Assert
            assertTrue(user.canLogin()) // Lock expired, should be able to login
        }
    }

    @Nested
    @DisplayName("Account Status Transitions")
    inner class AccountStatusTransitions {
        @Test
        fun `should transition from UNVERIFIED to ACTIVE on email verification`() {
            // Arrange
            val unverifiedUser = UserFixtures.createUnverifiedUser()
            assertEquals(User.AccountStatus.UNVERIFIED, unverifiedUser.accountStatus)

            // Act
            unverifiedUser.verifyEmail()

            // Assert
            assertEquals(User.AccountStatus.ACTIVE, unverifiedUser.accountStatus)
        }

        @Test
        fun `should transition from ACTIVE to LOCKED on manual lock`() {
            // Arrange
            val activeUser = UserFixtures.createActiveUser()

            // Act
            activeUser.lock(30)

            // Assert
            assertEquals(User.AccountStatus.LOCKED, activeUser.accountStatus)
        }

        @Test
        fun `should transition from LOCKED to ACTIVE on unlock`() {
            // Arrange
            val lockedUser = UserFixtures.createLockedUser()

            // Act
            lockedUser.unlock()

            // Assert
            assertEquals(User.AccountStatus.ACTIVE, lockedUser.accountStatus)
        }

        @Test
        fun `should transition to DELETED on delete`() {
            // Arrange
            val activeUser = UserFixtures.createActiveUser()

            // Act
            activeUser.delete()

            // Assert
            assertEquals(User.AccountStatus.DELETED, activeUser.accountStatus)
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    inner class EdgeCasesAndBoundaryConditions {
        @Test
        fun `should handle maximum name length (100 characters)`() {
            // Arrange
            val maxLengthName = "a".repeat(100)

            // Act
            val user =
                User.create(
                    UserFixtures.validEmail(),
                    maxLengthName,
                    UserFixtures.validHashedPassword(),
                    RoleId.generate(),
                )

            // Assert
            assertEquals(maxLengthName, user.name)
        }

        @Test
        fun `should handle maximum email length (255 characters)`() {
            // Arrange - Create email with 255 characters
            val localPart = "a".repeat(243) // 243 + "@" + "example.com" (11) = 255
            val maxLengthEmail = "$localPart@example.com"

            // Act
            val user =
                User.create(
                    maxLengthEmail,
                    UserFixtures.validName(),
                    UserFixtures.validHashedPassword(),
                    RoleId.generate(),
                )

            // Assert
            assertEquals(maxLengthEmail.lowercase(), user.email)
        }

        @Test
        fun `should handle zero failed login attempts`() {
            // Arrange
            val user = UserFixtures.createActiveUser()
            assertEquals(0, user.failedLoginAttempts)

            // Act
            user.recordSuccessfulLogin()

            // Assert
            assertEquals(0, user.failedLoginAttempts)
        }

        @Test
        fun `should handle lock duration of 0 minutes (immediate unlock)`() {
            // Arrange
            val user = UserFixtures.createActiveUser()

            // Act
            user.lock(0)

            // Assert
            assertEquals(User.AccountStatus.LOCKED, user.accountStatus)
            assertNotNull(user.lockedUntil)
            // Should be locked until approximately now (immediate unlock)
            val diffSeconds = ChronoUnit.SECONDS.between(Instant.now(), user.lockedUntil)
            assertTrue(Math.abs(diffSeconds) < 5)
        }

        @Test
        fun `should handle very long lock duration (permanent lock)`() {
            // Arrange
            val user = UserFixtures.createActiveUser()
            val oneYearInMinutes = 365 * 24 * 60

            // Act
            user.lock(oneYearInMinutes)

            // Assert
            assertEquals(User.AccountStatus.LOCKED, user.accountStatus)
            assertNotNull(user.lockedUntil)
            assertTrue(user.lockedUntil!!.isAfter(Instant.now().plus(364, ChronoUnit.DAYS)))
        }
    }
}
