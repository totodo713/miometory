package com.worklog.infrastructure.persistence;

import com.worklog.domain.role.RoleId;
import com.worklog.domain.user.UserId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;

import java.util.List;

@Configuration
public class JdbcConverterConfig {
    @Bean
    public JdbcCustomConversions jdbcCustomConversions(
        RoleIdToUuidConverter roleIdToUuidConverter,
        UuidToRoleIdConverter uuidToRoleIdConverter,
        UserIdToUuidConverter userIdToUuidConverter,
        UuidToUserIdConverter uuidToUserIdConverter
    ) {
        return new JdbcCustomConversions(List.of(
            roleIdToUuidConverter,
            uuidToRoleIdConverter,
            userIdToUuidConverter,
            uuidToUserIdConverter
        ));
    }
}
