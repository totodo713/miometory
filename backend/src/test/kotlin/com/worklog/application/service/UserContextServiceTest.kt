package com.worklog.application.service

import com.worklog.fixtures.UserFixtures
import com.worklog.infrastructure.persistence.JdbcUserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for UserContextService.updatePreferredLocale().
 *
 * Tests cover locale validation, user lookup, and repository interaction.
 */
class UserContextServiceTest {
    // jdbcTemplate is only needed to satisfy the UserContextService constructor
    private val jdbcTemplate: JdbcTemplate = mockk(relaxed = true)
    private val userRepository: JdbcUserRepository = mockk(relaxed = true)

    private lateinit var service: UserContextService

    @BeforeEach
    fun setUp() {
        service = UserContextService(jdbcTemplate, userRepository)
    }

    @Test
    fun `valid en locale should succeed`() {
        // Given
        val user = UserFixtures.createActiveUser(email = "user@example.com")
        every { userRepository.findByEmail("user@example.com") } returns Optional.of(user)

        // When
        service.updatePreferredLocale("user@example.com", "en")

        // Then
        verify(exactly = 1) { userRepository.updateLocale(user.id, "en") }
    }

    @Test
    fun `valid ja locale should succeed`() {
        // Given
        val user = UserFixtures.createActiveUser(email = "user@example.com")
        every { userRepository.findByEmail("user@example.com") } returns Optional.of(user)

        // When
        service.updatePreferredLocale("user@example.com", "ja")

        // Then
        verify(exactly = 1) { userRepository.updateLocale(user.id, "ja") }
    }

    @Test
    fun `null locale throws IllegalArgumentException`() {
        // When/Then
        val exception =
            assertThrows<IllegalArgumentException> {
                service.updatePreferredLocale("user@example.com", null)
            }

        assertTrue(exception.message!!.contains("Invalid locale"))
        verify(exactly = 0) { userRepository.findByEmail(any()) }
    }

    @Test
    fun `unsupported locale throws IllegalArgumentException`() {
        // When/Then
        val exception =
            assertThrows<IllegalArgumentException> {
                service.updatePreferredLocale("user@example.com", "fr")
            }

        assertEquals("Invalid locale: fr", exception.message)
        verify(exactly = 0) { userRepository.findByEmail(any()) }
    }

    @Test
    fun `empty locale throws IllegalArgumentException`() {
        // When/Then
        val exception =
            assertThrows<IllegalArgumentException> {
                service.updatePreferredLocale("user@example.com", "")
            }

        assertTrue(exception.message!!.contains("Invalid locale"))
        verify(exactly = 0) { userRepository.findByEmail(any()) }
    }

    @Test
    fun `non-existent user throws IllegalStateException`() {
        // Given
        every { userRepository.findByEmail("unknown@example.com") } returns Optional.empty()

        // When/Then
        val exception =
            assertThrows<IllegalStateException> {
                service.updatePreferredLocale("unknown@example.com", "en")
            }

        assertTrue(exception.message!!.contains("User not found"))
    }
}
