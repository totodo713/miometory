package com.worklog.infrastructure.persistence;

import com.worklog.domain.user.UserId;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.stereotype.Component;

@Component
@ReadingConverter
public class UuidToUserIdConverter implements Converter<UUID, UserId> {
    @Override
    public UserId convert(UUID source) {
        return new UserId(source);
    }
}
