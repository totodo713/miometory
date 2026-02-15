package com.worklog.infrastructure.persistence;

import com.worklog.domain.role.RoleId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@ReadingConverter
public class UuidToRoleIdConverter implements Converter<UUID, RoleId> {
    @Override
    public RoleId convert(UUID source) {
        return new RoleId(source);
    }
}