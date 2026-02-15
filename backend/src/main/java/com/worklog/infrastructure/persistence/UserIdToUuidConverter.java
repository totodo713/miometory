package com.worklog.infrastructure.persistence;

import com.worklog.domain.user.UserId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.stereotype.Component;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@WritingConverter
public class UserIdToUuidConverter implements Converter<UserId, UUID> {
    @Override
    public UUID convert(UserId source) {
        return source.value();
    }
}
