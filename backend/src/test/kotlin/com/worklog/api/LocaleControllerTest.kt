package com.worklog.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.worklog.application.service.UserContextService
import com.worklog.infrastructure.config.LoggingProperties
import com.worklog.infrastructure.config.RateLimitProperties
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Integration tests for LocaleController.
 *
 * Tests cover:
 * - PATCH /api/v1/user/locale - locale update for authenticated users
 * - Validation of locale values (en, ja)
 * - Authentication requirement
 *
 * Uses MockMvc with mocked UserContextService.
 * Authentication is injected via .principal() since SecurityAutoConfiguration is excluded.
 */
@WebMvcTest(
    controllers = [LocaleController::class],
    excludeAutoConfiguration = [
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration::class,
    ],
    excludeFilters = [
        org.springframework.context.annotation.ComponentScan.Filter(
            type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
            classes = [
                com.worklog.infrastructure.config.RateLimitFilter::class,
                com.worklog.infrastructure.config.TenantStatusFilter::class,
            ],
        ),
    ],
)
class LocaleControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userContextService: UserContextService

    private val mockAuth =
        UsernamePasswordAuthenticationToken.authenticated("user@example.com", null, emptyList())

    @BeforeEach
    fun setup() {
        clearMocks(userContextService)
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun userContextService(): UserContextService = mockk(relaxed = true)

        @Bean
        fun loggingProperties(): LoggingProperties {
            val props = LoggingProperties()
            props.enabled = false
            return props
        }

        @Bean
        fun rateLimitProperties(): RateLimitProperties {
            val props = RateLimitProperties()
            props.enabled = false
            return props
        }
    }

    // ============================================================
    // PATCH /api/v1/user/locale - Locale Update
    // ============================================================

    @Test
    fun `updateLocale with valid en returns 204`() {
        // Given
        val requestBody = mapOf("locale" to "en")

        // When/Then
        mockMvc
            .perform(
                patch("/api/v1/user/locale")
                    .principal(mockAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .with(csrf()),
            ).andExpect(status().isNoContent)
    }

    @Test
    fun `updateLocale with valid ja returns 204`() {
        // Given
        val requestBody = mapOf("locale" to "ja")

        // When/Then
        mockMvc
            .perform(
                patch("/api/v1/user/locale")
                    .principal(mockAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .with(csrf()),
            ).andExpect(status().isNoContent)
    }

    @Test
    fun `updateLocale with invalid locale returns 400`() {
        // Given
        val requestBody = mapOf("locale" to "fr")
        every { userContextService.updatePreferredLocale(any(), any()) } throws
            IllegalArgumentException("Invalid locale: fr")

        // When/Then
        mockMvc
            .perform(
                patch("/api/v1/user/locale")
                    .principal(mockAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .with(csrf()),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `updateLocale with null locale returns 400`() {
        // Given
        val requestBody = """{"locale":null}"""
        every { userContextService.updatePreferredLocale(any(), any()) } throws
            IllegalArgumentException("Invalid locale: null")

        // When/Then
        mockMvc
            .perform(
                patch("/api/v1/user/locale")
                    .principal(mockAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(csrf()),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `updateLocale without auth returns 401`() {
        // Given - no principal set, so authentication is null.
        // The 401 comes from the controller's manual null check, not Spring Security
        // (SecurityAutoConfiguration is excluded).
        val requestBody = mapOf("locale" to "en")

        // When/Then
        mockMvc
            .perform(
                patch("/api/v1/user/locale")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .with(csrf()),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `updateLocale verifies service interaction`() {
        // Given
        val requestBody = mapOf("locale" to "en")

        // When
        mockMvc
            .perform(
                patch("/api/v1/user/locale")
                    .principal(mockAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .with(csrf()),
            ).andExpect(status().isNoContent)

        // Then
        verify(exactly = 1) { userContextService.updatePreferredLocale("user@example.com", "en") }
    }

    @Test
    fun `updateLocale with empty string returns 400`() {
        // Given
        val requestBody = mapOf("locale" to "")
        every { userContextService.updatePreferredLocale(any(), any()) } throws
            IllegalArgumentException("Invalid locale: ")

        // When/Then
        mockMvc
            .perform(
                patch("/api/v1/user/locale")
                    .principal(mockAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .with(csrf()),
            ).andExpect(status().isBadRequest)
    }
}
