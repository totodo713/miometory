package com.worklog.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.SecurityFilterChain

/**
 * Security configuration for Work-Log application.
 *
 * Configures:
 * - OAuth2/OIDC login for modern SSO providers (Azure AD, Google, etc.)
 * - SAML2 login for enterprise SAML providers (Okta, etc.)
 * - Session management with 30-minute timeout
 * - Public endpoints for health checks and tenant management
 *
 * Session timeout warning is handled client-side (28-min warning + 2-min countdown).
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints
                    .requestMatchers(
                        "/",
                        "/login/**",
                        "/api/v1/health",
                        "/api/v1/health/**",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/api/v1/tenants",
                        "/api/v1/tenants/**",
                    ).permitAll()
                    // All other endpoints require authentication
                    .anyRequest()
                    .authenticated()
            }
            // OAuth2 Login for OIDC providers (Azure AD, Google, etc.)
            .oauth2Login { oauth2 ->
                oauth2
                    .loginPage("/login")
                    .userInfoEndpoint { userInfo ->
                        userInfo.userService(oauth2UserService())
                    }
            }
            // SAML2 Login for SAML providers (Okta, etc.)
            .saml2Login { saml2 ->
                saml2.loginPage("/login")
            }
            // Session Management (30-minute timeout)
            .sessionManagement { session ->
                session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .maximumSessions(1)
                    .maxSessionsPreventsLogin(false) // Allow new session, invalidate old one
            }
            // Logout configuration
            .logout { logout ->
                logout
                    .logoutSuccessUrl("/login?logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
            }
            // Keep httpBasic for development/testing
            .httpBasic { }
            .formLogin { it.disable() }

        return http.build()
    }

    /**
     * Custom OAuth2UserService to extract roles/authorities from OAuth2 token claims.
     *
     * Maps roles from HR system (e.g., "employee", "manager", "admin") to Spring Security
     * authorities with ROLE_ prefix (e.g., "ROLE_EMPLOYEE", "ROLE_MANAGER").
     */
    @Bean
    fun oauth2UserService(): OAuth2UserService<OAuth2UserRequest, OAuth2User> {
        val delegate = DefaultOAuth2UserService()
        return OAuth2UserService { request ->
            val user = delegate.loadUser(request)
            // Map roles from HR system claims to Spring Security authorities
            val authorities = extractAuthorities(user)
            DefaultOAuth2User(authorities, user.attributes, "email")
        }
    }

    /**
     * Extracts authorities from OAuth2User attributes.
     *
     * Expected claim structure:
     * - "roles": ["employee", "manager"] (array of role strings)
     * or
     * - "groups": ["employees", "managers"] (array of group names)
     *
     * Maps to Spring Security authorities: ROLE_EMPLOYEE, ROLE_MANAGER
     */
    private fun extractAuthorities(user: OAuth2User): Set<GrantedAuthority> {
        // Try to extract roles from "roles" claim first
        val roles: List<String> =
            user.getAttribute<List<String>>("roles")
                ?: user.getAttribute<List<String>>("groups")
                ?: emptyList()

        // Map to Spring Security authorities with ROLE_ prefix
        return roles
            .map { role ->
                SimpleGrantedAuthority("ROLE_${role.uppercase()}")
            }
            .toSet()
            .ifEmpty {
                // Default role if no roles found in claims
                setOf(SimpleGrantedAuthority("ROLE_EMPLOYEE"))
            }
    }
}
