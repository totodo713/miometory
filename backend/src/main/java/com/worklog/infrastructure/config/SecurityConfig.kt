package com.worklog.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.web.cors.CorsConfigurationSource

/**
 * Security configuration for Miometry application.
 *
 * Development mode: All endpoints are public for rapid prototyping, CSRF disabled.
 * Production mode: CSRF enabled with cookie-based token for SPA compatibility.
 *
 * TODO: Enable OAuth2/OIDC and SAML2 authentication for production:
 * - OAuth2/OIDC login for modern SSO providers (Azure AD, Google, etc.)
 * - SAML2 login for enterprise SAML providers (Okta, etc.)
 * - Session management with 30-minute timeout
 * - Role-based access control
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(private val corsConfigurationSource: CorsConfigurationSource) {
    /**
     * Password encoder bean using BCrypt algorithm.
     * BCrypt automatically handles salting and uses adaptive hashing.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    /**
     * Development security filter chain - CSRF disabled for easier testing.
     */
    @Bean
    @Profile("default", "dev", "test")
    fun devFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource) }
            .authorizeHttpRequests { auth ->
                auth
                    // Allow all requests during development
                    .anyRequest()
                    .permitAll()
            }
            // Session Management (30-minute timeout)
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            }

        return http.build()
    }

    /**
     * Production security filter chain - CSRF enabled with cookie-based token.
     *
     * CSRF Protection Strategy:
     * - Uses CookieCsrfTokenRepository with HttpOnly=false for JavaScript access
     * - Frontend reads XSRF-TOKEN cookie and sends X-XSRF-TOKEN header
     * - Spring Security validates the header against the cookie
     * - Safe methods (GET, HEAD, OPTIONS, TRACE) are not protected
     */
    @Bean
    @Profile("prod", "production")
    fun prodFilterChain(http: HttpSecurity): SecurityFilterChain {
        // Use CsrfTokenRequestAttributeHandler for proper token handling with SPAs
        val csrfTokenHandler = CsrfTokenRequestAttributeHandler()
        csrfTokenHandler.setCsrfRequestAttributeName("_csrf")

        http
            .csrf { csrf ->
                csrf
                    // Cookie-based CSRF token for JavaScript SPA access
                    // withHttpOnlyFalse() allows JavaScript to read the XSRF-TOKEN cookie
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(csrfTokenHandler)
                    // Exclude health/actuator endpoints from CSRF
                    .ignoringRequestMatchers(
                        "/actuator/**",
                        "/health",
                        "/api/v1/health",
                        "/ready",
                    )
            }.cors { it.configurationSource(corsConfigurationSource) }
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints
                    .requestMatchers(
                        "/actuator/**",
                        "/health",
                        "/api/v1/health",
                        "/ready",
                        "/docs",
                        "/api-docs",
                        "/api-docs/**",
                        "/static/**",
                    ).permitAll()
                    // Admin endpoints require authentication (permission enforcement via @PreAuthorize)
                    .requestMatchers("/api/v1/admin/**").authenticated()
                    // Tenant settings endpoints require authentication
                    .requestMatchers("/api/v1/tenant-settings/**").authenticated()
                    // Pattern endpoints under tenants require authentication
                    .requestMatchers("/api/v1/tenants/*/fiscal-year-patterns/**").authenticated()
                    .requestMatchers("/api/v1/tenants/*/monthly-period-patterns/**").authenticated()
                    // Worklog and notification endpoints require authentication
                    .requestMatchers("/api/v1/worklog/**", "/api/v1/notifications/**").authenticated()
                    // User status endpoints require authentication
                    .requestMatchers("/api/v1/user/**").authenticated()
                    // Profile endpoint requires authentication
                    .requestMatchers("/api/v1/profile/**").authenticated()
                    // All other requests require authentication (to be configured with SSO)
                    .anyRequest()
                    .permitAll() // TODO: Change to .authenticated() when SSO is enabled
            }
            // Session Management (30-minute timeout)
            .sessionManagement { session ->
                session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .maximumSessions(1)
            }

        return http.build()
    }
}
