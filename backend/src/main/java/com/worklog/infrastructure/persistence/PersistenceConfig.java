package com.worklog.infrastructure.persistence;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;

/**
 * Ensure custom converters are registered for Spring Data JDBC.
 * This makes converter registration explicit and avoids relying on
 * component scan timing differences across environments.
 *
 * <p>Note: Only reading converters are registered for JSONB and INET types.
 * Writing is handled via explicit SQL casting in repository @Query methods,
 * because global String→PGobject writing converters would affect ALL String
 * fields across all entities.
 */
@Configuration
public class PersistenceConfig {
    @Bean
    public JdbcCustomConversions jdbcCustomConversions(
            RoleIdToUuidConverter roleIdToUuidConverter,
            UuidToRoleIdConverter uuidToRoleIdConverter,
            UserIdToUuidConverter userIdToUuidConverter,
            UuidToUserIdConverter uuidToUserIdConverter,
            JsonbToStringReadingConverter jsonbToStringReadingConverter,
            InetToStringReadingConverter inetToStringReadingConverter) {
        return new JdbcCustomConversions(List.of(
                roleIdToUuidConverter,
                uuidToRoleIdConverter,
                userIdToUuidConverter,
                uuidToUserIdConverter,
                jsonbToStringReadingConverter,
                inetToStringReadingConverter));
    }
}
