package com.worklog.api

import com.worklog.application.auth.AuthService
import com.worklog.application.password.PasswordResetService
import com.worklog.application.service.UserContextService
import com.worklog.infrastructure.config.LoggingProperties
import com.worklog.infrastructure.config.RateLimitProperties
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * CSRF protection tests for password reset endpoints (TC-3.1 to TC-3.4).
 *
 * Uses @WebMvcTest WITH security auto-configuration enabled and a custom
 * SecurityFilterChain that enables CSRF protection. This verifies that
 * password reset endpoints enforce CSRF tokens, which is required in production
 * where CSRF is enabled via CookieCsrfTokenRepository.
 */
@WebMvcTest(
    controllers = [AuthController::class],
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
class PasswordResetCsrfTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @TestConfiguration
    class CsrfTestConfig {
        @Bean
        @Primary
        fun csrfSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
            http
                .csrf { csrf ->
                    csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                }
                .authorizeHttpRequests { auth ->
                    auth.anyRequest().permitAll()
                }

            return http.build()
        }

        @Bean
        fun corsConfigurationSource(): CorsConfigurationSource = UrlBasedCorsConfigurationSource()

        @Bean
        @Primary
        fun authService(): AuthService = mockk(relaxed = true)

        @Bean
        @Primary
        fun passwordResetService(): PasswordResetService = mockk<PasswordResetService>(relaxed = true).also {
            every { it.requestReset(any()) } returns Unit
            every { it.confirmReset(any(), any()) } returns Unit
        }

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
    // TC-3.1: POST /request without CSRF token → 403
    // ============================================================

    @Test
    fun `passwordResetRequest without CSRF token should return 403`() {
        val requestBody = """{"email":"user@example.com"}"""

        mockMvc
            .perform(
                post("/api/v1/auth/password-reset/request")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isForbidden)
    }

    // ============================================================
    // TC-3.2: POST /request with CSRF token → 200
    // ============================================================

    @Test
    fun `passwordResetRequest with CSRF token should return 200`() {
        val requestBody = """{"email":"user@example.com"}"""

        mockMvc
            .perform(
                post("/api/v1/auth/password-reset/request")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(csrf()),
            ).andExpect(status().isOk)
    }

    // ============================================================
    // TC-3.3: POST /confirm without CSRF token → 403
    // ============================================================

    @Test
    fun `passwordResetConfirm without CSRF token should return 403`() {
        val requestBody = """{"token":"valid-token-123","newPassword":"NewPassword123"}"""

        mockMvc
            .perform(
                post("/api/v1/auth/password-reset/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isForbidden)
    }

    // ============================================================
    // TC-3.4: POST /confirm with CSRF token → 200
    // ============================================================

    @Test
    fun `passwordResetConfirm with CSRF token should return 200`() {
        val requestBody = """{"token":"valid-token-123","newPassword":"NewPassword123"}"""

        mockMvc
            .perform(
                post("/api/v1/auth/password-reset/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(csrf()),
            ).andExpect(status().isOk)
    }
}
