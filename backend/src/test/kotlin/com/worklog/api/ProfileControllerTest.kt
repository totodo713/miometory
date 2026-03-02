package com.worklog.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.worklog.api.dto.ProfileResponse
import com.worklog.api.dto.UpdateProfileResponse
import com.worklog.application.service.ProfileService
import com.worklog.domain.shared.DomainException
import com.worklog.infrastructure.config.LoggingProperties
import com.worklog.infrastructure.config.RateLimitProperties
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Integration tests for ProfileController.
 *
 * Tests cover:
 * - GET /api/v1/profile - retrieve profile for authenticated user
 * - PUT /api/v1/profile - update profile for authenticated user
 * - Authentication requirement
 * - Error handling (MEMBER_NOT_FOUND, DUPLICATE_EMAIL, validation)
 *
 * Uses MockMvc with mocked ProfileService.
 * Authentication is injected via .principal() since SecurityAutoConfiguration is excluded.
 */
@WebMvcTest(
    controllers = [ProfileController::class],
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
class ProfileControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var profileService: ProfileService

    private val mockAuth =
        UsernamePasswordAuthenticationToken.authenticated("user@example.com", null, emptyList())

    @BeforeEach
    fun setup() {
        clearMocks(profileService)
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun profileService(): ProfileService = mockk(relaxed = true)

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
    // GET /api/v1/profile - Get Profile
    // ============================================================

    @Test
    fun `getProfile returns 200 with profile data`() {
        // Given
        val profileResponse = ProfileResponse(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "user@example.com",
            "Test User",
            "Test Org",
            "Manager Name",
            true,
        )
        every { profileService.getProfile("user@example.com") } returns profileResponse

        // When/Then
        mockMvc
            .perform(
                get("/api/v1/profile")
                    .principal(mockAuth),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value("11111111-1111-1111-1111-111111111111"))
            .andExpect(jsonPath("$.email").value("user@example.com"))
            .andExpect(jsonPath("$.displayName").value("Test User"))
            .andExpect(jsonPath("$.organizationName").value("Test Org"))
            .andExpect(jsonPath("$.managerName").value("Manager Name"))
            .andExpect(jsonPath("$.isActive").value(true))
    }

    @Test
    fun `getProfile returns 404 when member not found`() {
        // Given
        every { profileService.getProfile("user@example.com") } throws
            DomainException("MEMBER_NOT_FOUND", "Member not found for email: user@example.com")

        // When/Then
        mockMvc
            .perform(
                get("/api/v1/profile")
                    .principal(mockAuth),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value("MEMBER_NOT_FOUND"))
    }

    @Test
    fun `getProfile returns 401 without authentication`() {
        // Given - no principal set, so authentication is null.
        // The 401 comes from the controller's manual null check, not Spring Security
        // (SecurityAutoConfiguration is excluded).

        // When/Then
        mockMvc
            .perform(
                get("/api/v1/profile"),
            ).andExpect(status().isUnauthorized)
    }

    // ============================================================
    // PUT /api/v1/profile - Update Profile
    // ============================================================

    @Test
    fun `updateProfile returns 204 when no email change`() {
        // Given
        val requestBody = mapOf("email" to "user@example.com", "displayName" to "Updated Name")
        every {
            profileService.updateProfile("user@example.com", "Updated Name", "user@example.com")
        } returns UpdateProfileResponse(false)

        // When/Then
        mockMvc
            .perform(
                put("/api/v1/profile")
                    .principal(mockAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .with(csrf()),
            ).andExpect(status().isNoContent)
    }

    @Test
    fun `updateProfile returns 200 with emailChanged true when email changes`() {
        // Given
        val requestBody = mapOf("email" to "new@example.com", "displayName" to "Test User")
        every {
            profileService.updateProfile("user@example.com", "Test User", "new@example.com")
        } returns UpdateProfileResponse(true)

        // When/Then
        mockMvc
            .perform(
                put("/api/v1/profile")
                    .principal(mockAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .with(csrf()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.emailChanged").value(true))
    }

    @Test
    fun `updateProfile returns 409 for duplicate email`() {
        // Given
        val requestBody = mapOf("email" to "taken@example.com", "displayName" to "Test User")
        every {
            profileService.updateProfile("user@example.com", "Test User", "taken@example.com")
        } throws DomainException("DUPLICATE_EMAIL", "A member with this email already exists in this tenant")

        // When/Then
        mockMvc
            .perform(
                put("/api/v1/profile")
                    .principal(mockAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .with(csrf()),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value("DUPLICATE_EMAIL"))
    }

    @Test
    fun `updateProfile returns 400 for invalid request body`() {
        // Given - empty email and displayName violate @NotBlank
        val requestBody = mapOf("email" to "", "displayName" to "")

        // When/Then
        mockMvc
            .perform(
                put("/api/v1/profile")
                    .principal(mockAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .with(csrf()),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `updateProfile returns 401 without authentication`() {
        // Given - no principal set, so authentication is null.
        val requestBody = mapOf("email" to "user@example.com", "displayName" to "Test User")

        // When/Then
        mockMvc
            .perform(
                put("/api/v1/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .with(csrf()),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `updateProfile verifies service is called with correct params`() {
        // Given
        val requestBody = mapOf("email" to "new@example.com", "displayName" to "New Name")
        every {
            profileService.updateProfile("user@example.com", "New Name", "new@example.com")
        } returns UpdateProfileResponse(true)

        // When
        mockMvc
            .perform(
                put("/api/v1/profile")
                    .principal(mockAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .with(csrf()),
            ).andExpect(status().isOk)

        // Then
        verify(exactly = 1) { profileService.updateProfile("user@example.com", "New Name", "new@example.com") }
    }
}
