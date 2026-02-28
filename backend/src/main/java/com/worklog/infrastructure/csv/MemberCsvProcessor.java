package com.worklog.infrastructure.csv;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Parses member CSV files with automatic encoding detection.
 *
 * <p>Encoding detection order:
 * <ol>
 *   <li>UTF-8 BOM (EF BB BF) — use UTF-8, strip BOM</li>
 *   <li>Valid UTF-8 multibyte — use UTF-8</li>
 *   <li>Fallback — Windows-31J (Shift_JIS superset)</li>
 * </ol>
 */
@Component
public class MemberCsvProcessor {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final Charset WINDOWS_31J = Charset.forName("Windows-31J");
    private static final int MAX_ROWS = 1000;

    /**
     * Parses raw CSV bytes into a list of {@link MemberCsvRow}.
     *
     * @param data raw CSV file content
     * @return parsed rows with 1-based row numbers (excluding header)
     * @throws CsvParseException if the CSV structure is invalid
     */
    public List<MemberCsvRow> parse(byte[] data) {
        if (data == null || data.length == 0) {
            return List.of();
        }

        byte[] content = data;
        Charset charset;

        if (hasUtf8Bom(content)) {
            content = Arrays.copyOfRange(content, UTF8_BOM.length, content.length);
            charset = StandardCharsets.UTF_8;
        } else if (isValidUtf8(content)) {
            charset = StandardCharsets.UTF_8;
        } else {
            charset = WINDOWS_31J;
        }

        return parseCsv(content, charset);
    }

    private boolean hasUtf8Bom(byte[] data) {
        if (data.length < UTF8_BOM.length) {
            return false;
        }
        return data[0] == UTF8_BOM[0] && data[1] == UTF8_BOM[1] && data[2] == UTF8_BOM[2];
    }

    private boolean isValidUtf8(byte[] data) {
        CharsetDecoder decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(data));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static final List<String> REQUIRED_HEADERS = List.of("email", "displayName");

    private List<MemberCsvRow> parseCsv(byte[] content, Charset charset) {
        CSVFormat format = CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        List<MemberCsvRow> rows = new ArrayList<>();

        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(content), charset);
                CSVParser parser = new CSVParser(reader, format)) {

            validateHeaders(parser.getHeaderNames());

            for (CSVRecord record : parser) {
                if (rows.size() >= MAX_ROWS) {
                    throw new CsvParseException("CSV exceeds maximum of " + MAX_ROWS + " rows");
                }
                if (!record.isConsistent()) {
                    throw new CsvParseException(
                            "Row " + record.getRecordNumber() + ": expected 2 columns but found " + record.size());
                }

                int rowNumber = (int) record.getRecordNumber();
                String email = record.get("email").toLowerCase(Locale.ROOT);
                String displayName = record.get("displayName");

                rows.add(new MemberCsvRow(rowNumber, email, displayName));
            }
        } catch (CsvParseException e) {
            throw e;
        } catch (IOException e) {
            throw new CsvParseException("Failed to read CSV data", e);
        }

        return rows;
    }

    private void validateHeaders(List<String> headerNames) {
        List<String> missing =
                REQUIRED_HEADERS.stream().filter(h -> !headerNames.contains(h)).toList();
        if (!missing.isEmpty()) {
            throw new CsvParseException(
                    "CSV header must contain columns: " + REQUIRED_HEADERS + ". Missing: " + missing);
        }
    }
}
