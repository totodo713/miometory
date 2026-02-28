package com.worklog.infrastructure.csv;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CsvParseException")
class CsvParseExceptionTest {

    @Test
    @DisplayName("should preserve message and cause")
    void messageAndCause() {
        IOException cause = new IOException("io error");
        CsvParseException ex = new CsvParseException("parse failed", cause);

        assertEquals("parse failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}
