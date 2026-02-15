package com.worklog.infrastructure.persistence;

import com.worklog.domain.user.UserId;
import org.springframework.core.convert.converter.Converter;

import org.springframework.stereotype.Component;
import org.springframework.data.convert.ReadingConverter;
import java.util.UUID;

@Component
@ReadingConverter
public class UuidToUserIdConverter implements Converter<UUID, UserId> {
    @Override
    public UserId convert(UUID source) {
        return new UserId(source);
    }
}
