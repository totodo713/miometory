package com.worklog.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * CORS configuration for Miometry application.
 *
 * Development mode: Allow requests from frontend dev server (localhost:3000).
 *
 * TODO: For production:
 * - Restrict allowed origins to actual frontend domain
 * - Consider using environment variables for allowed origins
 * - Review allowed methods and headers
 */
@Configuration
class CorsConfig {
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()

        // Allow frontend dev server (Next.js default port)
        configuration.allowedOrigins =
            listOf(
                "http://localhost:3000",
                "http://localhost:3001", // Alternative port if 3000 is busy
            )

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
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/api/**", configuration)

        return source
    }
}
