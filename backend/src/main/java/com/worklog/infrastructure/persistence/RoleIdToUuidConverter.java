package com.worklog.infrastructure.persistence;

import com.worklog.domain.role.RoleId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@WritingConverter
public class RoleIdToUuidConverter implements Converter<RoleId, UUID> {
    @Override
    public UUID convert(RoleId source) {
        return source.value();
    }
}