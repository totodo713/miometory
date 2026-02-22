package com.worklog.infrastructure.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * CORS configuration for Miometry application.
 *
 * Allowed origins are configurable via the CORS_ALLOWED_ORIGINS environment variable
 * (comma-separated). Defaults to localhost:3000 and localhost:3001 for development.
 */
@Configuration
class CorsConfig(
    @Value("\${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:3001}")
    private val allowedOriginsConfig: String,
) {
    companion object {
        private const val CORS_MAX_AGE_SECONDS = 3600L
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()

        configuration.allowedOrigins = allowedOriginsConfig.split(",").map { it.trim() }.filter { it.isNotBlank() }

        // Allow common HTTP methods
        configuration.allowedMethods =
            listOf(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS",
            )

        // Allow common headers
        configuration.allowedHeaders =
            listOf(
                "Content-Type",
                "Authorization",
                "If-Match", // For optimistic locking
                "If-None-Match", // For caching
                "X-XSRF-TOKEN", // For CSRF protection
            )

        // Expose ETag header for optimistic locking
        configuration.exposedHeaders =
            listOf(
                "ETag",
                "Location", // For POST responses
            )

        // Allow credentials (cookies, authorization headers)
        configuration.allowCredentials = true

        // Cache preflight requests for 1 hour
        configuration.maxAge = CORS_MAX_AGE_SECONDS

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/api/**", configuration)

        return source
    }
}
