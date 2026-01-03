package com.worklog.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfigurationSource

/**
 * Security configuration for Work-Log application.
 *
 * Development mode: All endpoints are public for rapid prototyping.
 * 
 * TODO: Enable OAuth2/OIDC and SAML2 authentication for production:
 * - OAuth2/OIDC login for modern SSO providers (Azure AD, Google, etc.)
 * - SAML2 login for enterprise SAML providers (Okta, etc.)
 * - Session management with 30-minute timeout
 * - Role-based access control
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val corsConfigurationSource: CorsConfigurationSource
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource) }
            .authorizeHttpRequests { auth ->
                auth
                    // Allow all requests during development
                    .anyRequest().permitAll()
            }
            // Session Management (30-minute timeout)
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            }

        return http.build()
    }
}
