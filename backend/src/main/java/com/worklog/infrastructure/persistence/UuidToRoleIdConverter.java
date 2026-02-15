package com.worklog.infrastructure.persistence;

import com.worklog.domain.role.RoleId;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.stereotype.Component;

@Component
@ReadingConverter
public class UuidToRoleIdConverter implements Converter<UUID, RoleId> {
    @Override
    public RoleId convert(UUID source) {
        return new RoleId(source);
    }
}
