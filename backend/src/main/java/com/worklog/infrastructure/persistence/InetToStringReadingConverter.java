package com.worklog.infrastructure.persistence;

import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.stereotype.Component;

/**
 * Converts a PostgreSQL INET {@link PGobject} back to a Java {@link String} when reading from the database.
 * Required because Spring Data JDBC returns PGobject instances for INET columns.
 */
@Component
@ReadingConverter
public class InetToStringReadingConverter implements Converter<PGobject, String> {
    @Override
    public String convert(PGobject source) {
        return source.getValue();
    }
}
