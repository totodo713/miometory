package com.worklog.infrastructure.persistence;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;

/**
 * Ensure custom converters are registered for Spring Data JDBC.
 * This makes converter registration explicit and avoids relying on
 * component scan timing differences across environments.
 */
@Configuration
public class PersistenceConfig {
    @Bean
    public JdbcCustomConversions jdbcCustomConversions(
            RoleIdToUuidConverter roleIdToUuidConverter,
            UuidToRoleIdConverter uuidToRoleIdConverter,
            UserIdToUuidConverter userIdToUuidConverter,
            UuidToUserIdConverter uuidToUserIdConverter) {
        return new JdbcCustomConversions(
                List.of(roleIdToUuidConverter, uuidToRoleIdConverter, userIdToUuidConverter, uuidToUserIdConverter));
    }
}
