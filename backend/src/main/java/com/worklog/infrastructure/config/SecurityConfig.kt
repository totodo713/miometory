package com.worklog.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/api/v1/health",
                        "/api/v1/health/**",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/api/v1/tenants",
                        "/api/v1/tenants/**",
                    ).permitAll()
                    .anyRequest()
                    .authenticated()
            }.httpBasic { }
            .formLogin { it.disable() }

        return http.build()
    }
}
