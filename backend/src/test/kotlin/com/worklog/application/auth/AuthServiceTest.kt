package com.worklog.application.auth

import com.worklog.domain.user.User
import com.worklog.domain.user.UserId
import com.worklog.fixtures.UserFixtures
import com.worklog.infrastructure.persistence.JdbcUserRepository
import com.worklog.infrastructure.persistence.UserSessionRepository
import com.worklog.infrastructure.email.EmailService
import com.worklog.application.token.EmailVerificationTokenStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Unit tests for AuthService (US1 - Account Creation & Login)
 * 
 * Tests cover:
 * - T027: signup() - verify user creation and email sent
 * - T028: login() success case - verify session created and last_login_at updated
 * - T029: login() failed attempts - verify account locks after 5 failures for 15 minutes
 * - T030: verifyEmail() - verify email_verified_at set and account_status updated
 * 
 * TDD: These tests are written FIRST and will FAIL until AuthService is implemented.
 */
class AuthServiceTest {

    private val userRepository: JdbcUserRepository = mockk(relaxed = true)
    private val sessionRepository: UserSessionRepository = mockk(relaxed = true)
    private val emailService: EmailService = mockk(relaxed = true)
    private val passwordEncoder: BCryptPasswordEncoder = BCryptPasswordEncoder()
    private val tokenStore: EmailVerificationTokenStore = EmailVerificationTokenStore()
    
    // Real implementation under test
    private lateinit var authService: AuthService
    
    @BeforeEach
    fun setUp() {
        tokenStore.clear()
        authService = AuthServiceImpl(
            userRepository,
            sessionRepository,
            emailService,
            passwordEncoder,
            tokenStore
        )
    }

    // ============================================================
    // T027: signup() - User Creation and Email Verification
    // ============================================================

    @Test
    fun `signup should create user with hashed password`() {
        // Given
        val request = UserFixtures.createRegistrationRequest(
            email = "newuser@example.com",
            name = "New User",
            password = "Password123"
        )
        
        every { userRepository.existsByEmail(any()) } returns false
        
        val userSlot = slot<User>()
        every { userRepository.save(capture(userSlot)) } answers { userSlot.captured }
        
        // When
        val result = authService.signup(request)
        
        // Then
        assertEquals("newuser@example.com", result.email)
        assertEquals("New User", result.name)
        assertEquals(User.AccountStatus.UNVERIFIED, result.accountStatus)
        assertTrue(passwordEncoder.matches("Password123", userSlot.captured.hashedPassword))
        
        verify(exactly = 1) { userRepository.save(any()) }
        verify(exactly = 1) { emailService.sendVerificationEmail(any(), any()) }
    }

    @Test
    fun `signup should reject duplicate email`() {
        // Given
        val request = UserFixtures.createRegistrationRequest(
            email = "existing@example.com",
            name = "User",
            password = "Password123"
        )
        
        every { userRepository.existsByEmail("existing@example.com") } returns true
        
        // When/Then
        val exception = assertThrows<IllegalArgumentException> {
            authService.signup(request)
        }
        
        assertEquals("Email already registered", exception.message)
        verify(exactly = 0) { userRepository.save(any()) }
        verify(exactly = 0) { emailService.sendVerificationEmail(any(), any()) }
    }

    @Test
    fun `signup should normalize email to lowercase`() {
        // Given
        val request = UserFixtures.createRegistrationRequest(
            email = "NewUser@EXAMPLE.COM",
            name = "User",
            password = "Password123"
        )
        
        every { userRepository.existsByEmail(any()) } returns false
        
        val userSlot = slot<User>()
        every { userRepository.save(capture(userSlot)) } answers { userSlot.captured }
        
        // When
        authService.signup(request)
        
        // Then
        assertEquals("newuser@example.com", userSlot.captured.email)
    }

    @Test
    fun `signup should reject weak password`() {
        // Given
        val weakPasswords = listOf(
            "short1A",        // Less than 8 chars
            "nouppercase1",   // No uppercase
            "NoDigitsHere",   // No digits
            "12345678"        // Only digits, no uppercase
        )
        
        weakPasswords.forEach { password ->
            val request = UserFixtures.createRegistrationRequest(
                email = "user@example.com",
                name = "User",
                password = password
            )
            
            // When/Then
            assertThrows<IllegalArgumentException>("Should reject password: $password") {
                authService.signup(request)
            }
        }
    }

    @Test
    fun `signup should send verification email with token`() {
        // Given
        val request = UserFixtures.createRegistrationRequest(
            email = "user@example.com",
            name = "Test User",
            password = "Password123"
        )
        
        every { userRepository.existsByEmail(any()) } returns false
        every { userRepository.save(any()) } answers { firstArg() }
        every { sessionRepository.save(any()) } answers { firstArg() }
        
        val emailSlot = slot<String>()
        val tokenSlot = slot<String>()
        every { emailService.sendVerificationEmail(capture(emailSlot), capture(tokenSlot)) } returns Unit
        
        // When
        authService.signup(request)
        
        // Then
        assertEquals("user@example.com", emailSlot.captured)
        assertTrue(tokenSlot.captured.length >= 32, "Token should be at least 32 characters")
    }

    // ============================================================
    // T028: login() Success Case
    // ============================================================

    @Test
    fun `login should succeed with valid credentials`() {
        // Given
        val password = "Password123"
        val hashedPassword = passwordEncoder.encode(password)
        val user = UserFixtures.createActiveUser(hashedPassword = hashedPassword)
        
        val request = UserFixtures.createLoginRequest(
            email = user.email,
            password = password,
            rememberMe = false
        )
        
        every { userRepository.findByEmail(user.email) } returns Optional.of(user)
        every { userRepository.save(any()) } answers { firstArg() }
        every { sessionRepository.save(any()) } answers { firstArg() }
        every { sessionRepository.save(any()) } answers { firstArg() }
        
        // When
        val result = authService.login(request)
        
        // Then
        assertNotNull(result)
        assertEquals(user.email, result.user.email)
        assertNotNull(result.sessionId)
        
        // Verify user state updated
        val updatedUserSlot = slot<User>()
        verify { userRepository.save(capture(updatedUserSlot)) }
        assertEquals(0, updatedUserSlot.captured.failedLoginAttempts)
        assertNotNull(updatedUserSlot.captured.lastLoginAt)
    }

    @Test
    fun `login should reset failed attempts on success`() {
        // Given
        val password = "Password123"
        val hashedPassword = passwordEncoder.encode(password)
        val user = UserFixtures.createActiveUser(hashedPassword = hashedPassword)
        
        // Simulate previous failed attempts
        user.recordFailedLogin(5, 15)
        user.recordFailedLogin(5, 15)
        assertEquals(2, user.failedLoginAttempts)
        
        val request = UserFixtures.createLoginRequest(
            email = user.email,
            password = password,
            rememberMe = false
        )
        
        every { userRepository.findByEmail(user.email) } returns Optional.of(user)
        every { userRepository.save(any()) } answers { firstArg() }
        every { sessionRepository.save(any()) } answers { firstArg() }
        
        // When
        authService.login(request)
        
        // Then
        val updatedUserSlot = slot<User>()
        verify { userRepository.save(capture(updatedUserSlot)) }
        assertEquals(0, updatedUserSlot.captured.failedLoginAttempts)
    }

    @Test
    fun `login should create session with remember-me token`() {
        // Given
        val password = "Password123"
        val hashedPassword = passwordEncoder.encode(password)
        val user = UserFixtures.createActiveUser(hashedPassword = hashedPassword)
        
        val request = UserFixtures.createLoginRequest(
            email = user.email,
            password = password,
            rememberMe = true
        )
        
        every { userRepository.findByEmail(user.email) } returns Optional.of(user)
        every { userRepository.save(any()) } answers { firstArg() }
        every { sessionRepository.save(any()) } answers { firstArg() }
        
        // When
        val result = authService.login(request)
        
        // Then
        assertNotNull(result.rememberMeToken, "Remember-me token should be present")
        assertTrue(result.rememberMeToken!!.length >= 32)
    }

    // ============================================================
    // T029: login() Failed Attempts and Account Locking
    // ============================================================

    @Test
    fun `login should increment failed attempts on wrong password`() {
        // Given
        val correctPassword = "Password123"
        val hashedPassword = passwordEncoder.encode(correctPassword)
        val user = UserFixtures.createActiveUser(hashedPassword = hashedPassword)
        
        val request = UserFixtures.createLoginRequest(
            email = user.email,
            password = "WrongPassword123",
            rememberMe = false
        )
        
        every { userRepository.findByEmail(user.email) } returns Optional.of(user)
        every { userRepository.save(any()) } answers { firstArg() }
        every { sessionRepository.save(any()) } answers { firstArg() }
        
        // When/Then
        assertThrows<IllegalArgumentException>("Should throw on wrong password") {
            authService.login(request)
        }
        
        val updatedUserSlot = slot<User>()
        verify { userRepository.save(capture(updatedUserSlot)) }
        assertEquals(1, updatedUserSlot.captured.failedLoginAttempts)
    }

    @Test
    fun `login should lock account after 5 failed attempts for 15 minutes`() {
        // Given
        val correctPassword = "Password123"
        val hashedPassword = passwordEncoder.encode(correctPassword)
        val user = UserFixtures.createActiveUser(hashedPassword = hashedPassword)
        
        // Simulate 4 previous failures
        repeat(4) {
            user.recordFailedLogin(5, 15)
        }
        assertEquals(4, user.failedLoginAttempts)
        
        val request = UserFixtures.createLoginRequest(
            email = user.email,
            password = "WrongPassword123",
            rememberMe = false
        )
        
        every { userRepository.findByEmail(user.email) } returns Optional.of(user)
        every { userRepository.save(any()) } answers { firstArg() }
        every { sessionRepository.save(any()) } answers { firstArg() }
        
        // When/Then
        assertThrows<IllegalArgumentException> {
            authService.login(request)
        }
        
        val updatedUserSlot = slot<User>()
        verify { userRepository.save(capture(updatedUserSlot)) }
        
        // Verify account locked
        assertTrue(updatedUserSlot.captured.isLocked())
        assertNotNull(updatedUserSlot.captured.lockedUntil)
        
        // Verify locked for approximately 15 minutes
        val lockDuration = updatedUserSlot.captured.lockedUntil!!.epochSecond - Instant.now().epochSecond
        assertTrue(lockDuration in 14 * 60..16 * 60, "Lock duration should be ~15 minutes")
    }

    @Test
    fun `login should reject locked account`() {
        // Given
        val password = "Password123"
        val hashedPassword = passwordEncoder.encode(password)
        val user = UserFixtures.createLockedUser(hashedPassword = hashedPassword)
        
        val request = UserFixtures.createLoginRequest(
            email = user.email,
            password = password,
            rememberMe = false
        )
        
        every { userRepository.findByEmail(user.email) } returns Optional.of(user)
        
        // When/Then
        val exception = assertThrows<IllegalStateException> {
            authService.login(request)
        }
        
        assertTrue(exception.message!!.contains("locked"), "Error message should mention account is locked")
        verify(exactly = 0) { userRepository.save(any()) }
    }

    // TODO: Test for expired lock requires reflection or test constructor to manipulate lockedUntil
    // This test is temporarily disabled until we have a way to set past timestamps

    // ============================================================
    // T030: verifyEmail() - Email Verification
    // ============================================================

    @Test
    fun `verifyEmail should activate unverified account`() {
        // Given
        val user = UserFixtures.createUnverifiedUser()
        
        // Generate a real token
        val token = tokenStore.generateToken(user.id.value)
        
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { userRepository.save(any()) } answers { firstArg() }
        every { sessionRepository.save(any()) } answers { firstArg() }
        
        // When
        authService.verifyEmail(token)
        
        // Then
        val updatedUserSlot = slot<User>()
        verify { userRepository.save(capture(updatedUserSlot)) }
        
        assertTrue(updatedUserSlot.captured.isVerified())
        assertNotNull(updatedUserSlot.captured.emailVerifiedAt)
        assertFalse(updatedUserSlot.captured.accountStatus == User.AccountStatus.UNVERIFIED)
    }

    @Test
    fun `verifyEmail should reject invalid token`() {
        // Given
        val invalidToken = "invalid-token"
        
        // When/Then
        val exception = assertThrows<IllegalArgumentException> {
            authService.verifyEmail(invalidToken)
        }
        
        assertTrue(exception.message!!.contains("Invalid") || exception.message!!.contains("token"))
    }

    // TODO: Test for expired token requires time manipulation (e.g., @FreezeTime or manual time injection)
    // This test is temporarily disabled until we have time control in tests

    @Test
    fun `verifyEmail should be idempotent for already verified accounts`() {
        // Given
        val user = UserFixtures.createActiveUser() // Already verified
        
        // Generate a real token
        val token = tokenStore.generateToken(user.id.value)
        
        val originalVerifiedAt = user.emailVerifiedAt
        
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { userRepository.save(any()) } answers { firstArg() }
        every { sessionRepository.save(any()) } answers { firstArg() }
        
        // When
        authService.verifyEmail(token)
        
        // Then - should not call save for already verified user (idempotent)
        verify(exactly = 0) { userRepository.save(any()) }
    }
}
