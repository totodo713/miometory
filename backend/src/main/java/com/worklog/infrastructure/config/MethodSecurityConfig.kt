package com.worklog.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

/**
 * Method-level security configuration.
 * 
 * Enables @PreAuthorize, @PostAuthorize, @PreFilter, @PostFilter annotations
 * for fine-grained authorization control on service methods and controllers.
 * 
 * Example usage:
 * ```kotlin
 * @PreAuthorize("hasPermission(null, 'user.view')")
 * fun getUser(userId: UUID): User {
 *     // Only users with 'user.view' permission can execute this
 * }
 * ```
 */
@Configuration
@EnableMethodSecurity(
    prePostEnabled = true, // Enable @PreAuthorize and @PostAuthorize
    securedEnabled = true, // Enable @Secured
    jsr250Enabled = true, // Enable @RolesAllowed
)
class MethodSecurityConfig
