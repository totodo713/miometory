package com.worklog.infrastructure.csv;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MemberCsvProcessor")
class MemberCsvProcessorTest {

    private MemberCsvProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new MemberCsvProcessor();
    }

    @Test
    @DisplayName("should parse UTF-8 CSV with Japanese names")
    void parseUtf8Csv() {
        byte[] csv =
                "email,displayName\ntaro@example.com,山田太郎\nhanako@example.com,田中花子\n".getBytes(StandardCharsets.UTF_8);

        List<MemberCsvRow> rows = processor.parse(csv);

        assertEquals(2, rows.size());
        assertEquals(new MemberCsvRow(1, "taro@example.com", "山田太郎"), rows.get(0));
        assertEquals(new MemberCsvRow(2, "hanako@example.com", "田中花子"), rows.get(1));
    }

    @Test
    @DisplayName("should parse Shift_JIS (Windows-31J) encoded CSV")
    void parseShiftJisCsv() throws Exception {
        Charset windows31j = Charset.forName("Windows-31J");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(baos, windows31j)) {
            writer.write("email,displayName\ntaro@example.com,山田太郎\n");
        }

        List<MemberCsvRow> rows = processor.parse(baos.toByteArray());

        assertEquals(1, rows.size());
        assertEquals("山田太郎", rows.get(0).displayName());
    }

    @Test
    @DisplayName("should strip UTF-8 BOM and parse correctly")
    void parseUtf8WithBom() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] csvContent = "email,displayName\nbom@example.com,BOM User\n".getBytes(StandardCharsets.UTF_8);
        byte[] csv = new byte[bom.length + csvContent.length];
        System.arraycopy(bom, 0, csv, 0, bom.length);
        System.arraycopy(csvContent, 0, csv, bom.length, csvContent.length);

        List<MemberCsvRow> rows = processor.parse(csv);

        assertEquals(1, rows.size());
        assertEquals("bom@example.com", rows.get(0).email());
    }

    @Test
    @DisplayName("should return empty list for empty file")
    void parseEmptyFile() {
        List<MemberCsvRow> rows = processor.parse(new byte[0]);

        assertTrue(rows.isEmpty());
    }

    @Test
    @DisplayName("should return empty list for header-only CSV")
    void parseHeaderOnly() {
        byte[] csv = "email,displayName\n".getBytes(StandardCharsets.UTF_8);

        List<MemberCsvRow> rows = processor.parse(csv);

        assertTrue(rows.isEmpty());
    }

    @Test
    @DisplayName("should throw on CSV with missing columns")
    void parseMissingColumns() {
        byte[] csv = "email\ntaro@example.com\n".getBytes(StandardCharsets.UTF_8);

        assertThrows(CsvParseException.class, () -> processor.parse(csv));
    }

    @Test
    @DisplayName("should assign 1-based row numbers excluding header")
    void rowNumbering() {
        byte[] csv = "email,displayName\na@ex.com,A\nb@ex.com,B\nc@ex.com,C\n".getBytes(StandardCharsets.UTF_8);

        List<MemberCsvRow> rows = processor.parse(csv);

        assertEquals(1, rows.get(0).rowNumber());
        assertEquals(2, rows.get(1).rowNumber());
        assertEquals(3, rows.get(2).rowNumber());
    }

    @Test
    @DisplayName("should trim whitespace from values")
    void trimValues() {
        byte[] csv = "email,displayName\n  spaced@example.com , Spaced Name \n".getBytes(StandardCharsets.UTF_8);

        List<MemberCsvRow> rows = processor.parse(csv);

        assertEquals("spaced@example.com", rows.get(0).email());
        assertEquals("Spaced Name", rows.get(0).displayName());
    }

    @Test
    @DisplayName("should throw when CSV exceeds 1000 rows")
    void parseExceedingMaxRows() {
        StringBuilder sb = new StringBuilder("email,displayName\n");
        for (int i = 1; i <= 1001; i++) {
            sb.append("user").append(i).append("@example.com,User ").append(i).append("\n");
        }
        byte[] csv = sb.toString().getBytes(StandardCharsets.UTF_8);

        CsvParseException ex = assertThrows(CsvParseException.class, () -> processor.parse(csv));
        assertTrue(ex.getMessage().contains("1000"));
    }

    @Test
    @DisplayName("should lowercase email addresses")
    void lowercaseEmails() {
        byte[] csv = "email,displayName\nTEST@EXAMPLE.COM,Test User\n".getBytes(StandardCharsets.UTF_8);

        List<MemberCsvRow> rows = processor.parse(csv);

        assertEquals("test@example.com", rows.get(0).email());
    }
}
