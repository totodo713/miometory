package com.worklog.infrastructure.config;

import com.worklog.application.token.EmailVerificationTokenStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for authentication-related beans.
 */
@Configuration
public class AuthConfig {
    
    @Bean
    public EmailVerificationTokenStore emailVerificationTokenStore() {
        return new EmailVerificationTokenStore();
    }
}
