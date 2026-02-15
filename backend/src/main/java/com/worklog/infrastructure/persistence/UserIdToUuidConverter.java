package com.worklog.infrastructure.persistence;

import com.worklog.domain.user.UserId;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

@Component
@WritingConverter
public class UserIdToUuidConverter implements Converter<UserId, UUID> {
    @Override
    public UUID convert(UserId source) {
        return source.value();
    }
}
