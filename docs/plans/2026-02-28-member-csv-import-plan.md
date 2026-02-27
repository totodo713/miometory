# Member CSV Import Phase 1 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement CSV parser with encoding auto-detection and validation service for member batch registration (Issue #52).

**Architecture:** Batch processing approach — `byte[]` → encoding detection → CSV parse → validate → return results. All errors collected in one pass. Repository extensions use `IN` clause for efficient batch email lookups.

**Tech Stack:** Java 21, Apache Commons CSV 1.11.0, Spring Boot 3.5.9, JUnit 5, Testcontainers (PostgreSQL 16)

---

### Task 1: Create data type records

**Files:**
- Create: `backend/src/main/java/com/worklog/infrastructure/csv/MemberCsvRow.java`
- Create: `backend/src/main/java/com/worklog/infrastructure/csv/CsvValidationError.java`
- Create: `backend/src/main/java/com/worklog/infrastructure/csv/MemberCsvResult.java`

**Step 1: Create MemberCsvRow record**

```java
package com.worklog.infrastructure.csv;

/**
 * Represents a single parsed row from a member CSV file.
 *
 * @param rowNumber 1-based row number (excluding header)
 * @param email     email address from CSV
 * @param displayName display name from CSV
 */
public record MemberCsvRow(int rowNumber, String email, String displayName) {}
```

**Step 2: Create CsvValidationError record**

```java
package com.worklog.infrastructure.csv;

/**
 * Represents a validation error found in a CSV row.
 *
 * @param rowNumber 1-based row number (excluding header)
 * @param field     the field name that failed validation
 * @param message   human-readable error description
 */
public record CsvValidationError(int rowNumber, String field, String message) {}
```

**Step 3: Create MemberCsvResult record**

```java
package com.worklog.infrastructure.csv;

import java.util.List;

/**
 * Result of processing and validating a member CSV file.
 *
 * @param validRows parsed rows that passed all validation
 * @param errors    validation errors found across all rows
 */
public record MemberCsvResult(List<MemberCsvRow> validRows, List<CsvValidationError> errors) {

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
```

**Step 4: Commit**

```bash
git add backend/src/main/java/com/worklog/infrastructure/csv/MemberCsvRow.java \
       backend/src/main/java/com/worklog/infrastructure/csv/CsvValidationError.java \
       backend/src/main/java/com/worklog/infrastructure/csv/MemberCsvResult.java
git commit -m "feat(csv): add data type records for member CSV import (#52)"
```

---

### Task 2: Write MemberCsvProcessor tests

**Files:**
- Create: `backend/src/test/java/com/worklog/infrastructure/csv/MemberCsvProcessorTest.java`

**Context:** This is a pure unit test — no Spring context needed. The processor converts `byte[]` to `List<MemberCsvRow>`. Test encoding detection (UTF-8, BOM, Shift_JIS) and edge cases.

**Step 1: Write the test file**

```java
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
        byte[] csv = "email,displayName\ntaro@example.com,山田太郎\nhanako@example.com,田中花子\n"
                .getBytes(StandardCharsets.UTF_8);

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
        byte[] csv = "email,displayName\na@ex.com,A\nb@ex.com,B\nc@ex.com,C\n"
                .getBytes(StandardCharsets.UTF_8);

        List<MemberCsvRow> rows = processor.parse(csv);

        assertEquals(1, rows.get(0).rowNumber());
        assertEquals(2, rows.get(1).rowNumber());
        assertEquals(3, rows.get(2).rowNumber());
    }

    @Test
    @DisplayName("should trim whitespace from values")
    void trimValues() {
        byte[] csv = "email,displayName\n  spaced@example.com , Spaced Name \n"
                .getBytes(StandardCharsets.UTF_8);

        List<MemberCsvRow> rows = processor.parse(csv);

        assertEquals("spaced@example.com", rows.get(0).email());
        assertEquals("Spaced Name", rows.get(0).displayName());
    }
}
```

**Step 2: Verify tests fail**

Run: `cd backend && ./gradlew test --tests "com.worklog.infrastructure.csv.MemberCsvProcessorTest" --info 2>&1 | tail -20`

Expected: Compilation error — `MemberCsvProcessor` has no `parse` method yet, and `CsvParseException` doesn't exist.

---

### Task 3: Implement MemberCsvProcessor

**Files:**
- Create: `backend/src/main/java/com/worklog/infrastructure/csv/CsvParseException.java`
- Create: `backend/src/main/java/com/worklog/infrastructure/csv/MemberCsvProcessor.java`

**Step 1: Create CsvParseException**

```java
package com.worklog.infrastructure.csv;

/**
 * Thrown when a CSV file cannot be parsed due to structural issues
 * (e.g., missing required columns, invalid format).
 */
public class CsvParseException extends RuntimeException {

    public CsvParseException(String message) {
        super(message);
    }

    public CsvParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**Step 2: Implement MemberCsvProcessor**

```java
package com.worklog.infrastructure.csv;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Parses member CSV files with automatic encoding detection.
 *
 * Encoding detection algorithm:
 * 1. UTF-8 BOM (EF BB BF) detected → UTF-8, strip BOM
 * 2. Byte sequence is valid UTF-8 multibyte → UTF-8
 * 3. Fallback → Windows-31J (Japanese Excel default)
 */
@Component
public class MemberCsvProcessor {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final Charset WINDOWS_31J = Charset.forName("Windows-31J");

    private static final String HEADER_EMAIL = "email";
    private static final String HEADER_DISPLAY_NAME = "displayName";

    /**
     * Parses CSV bytes into a list of member rows.
     *
     * @param data raw CSV file bytes
     * @return parsed rows with 1-based row numbers (excluding header)
     * @throws CsvParseException if the CSV structure is invalid
     */
    public List<MemberCsvRow> parse(byte[] data) {
        if (data == null || data.length == 0) {
            return List.of();
        }

        byte[] content = stripBomIfPresent(data);
        Charset charset = detectEncoding(data, content != data);
        String csvText = new String(content, charset);

        return parseCsvText(csvText);
    }

    private byte[] stripBomIfPresent(byte[] data) {
        if (data.length >= 3
                && data[0] == UTF8_BOM[0]
                && data[1] == UTF8_BOM[1]
                && data[2] == UTF8_BOM[2]) {
            byte[] stripped = new byte[data.length - 3];
            System.arraycopy(data, 3, stripped, 0, stripped.length);
            return stripped;
        }
        return data;
    }

    private Charset detectEncoding(byte[] originalData, boolean hadBom) {
        if (hadBom) {
            return StandardCharsets.UTF_8;
        }
        if (isValidUtf8(originalData)) {
            return StandardCharsets.UTF_8;
        }
        return WINDOWS_31J;
    }

    private boolean isValidUtf8(byte[] data) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(data));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private List<MemberCsvRow> parseCsvText(String csvText) {
        CSVFormat format = CSVFormat.DEFAULT
                .builder()
                .setHeader(HEADER_EMAIL, HEADER_DISPLAY_NAME)
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        List<MemberCsvRow> rows = new ArrayList<>();

        try (CSVParser parser = new CSVParser(new StringReader(csvText), format)) {
            for (CSVRecord record : parser) {
                if (!record.isConsistent()) {
                    throw new CsvParseException(
                            "Row " + record.getRecordNumber() + " has incorrect number of columns");
                }
                int rowNumber = (int) record.getRecordNumber() - 1;
                String email = record.get(HEADER_EMAIL);
                String displayName = record.get(HEADER_DISPLAY_NAME);
                rows.add(new MemberCsvRow(rowNumber, email, displayName));
            }
        } catch (IOException e) {
            throw new CsvParseException("Failed to parse CSV", e);
        }

        return rows;
    }
}
```

**Step 3: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.worklog.infrastructure.csv.MemberCsvProcessorTest" --info 2>&1 | tail -20`

Expected: All 8 tests PASS.

**Step 4: Commit**

```bash
git add backend/src/main/java/com/worklog/infrastructure/csv/MemberCsvProcessor.java \
       backend/src/main/java/com/worklog/infrastructure/csv/CsvParseException.java \
       backend/src/test/java/com/worklog/infrastructure/csv/MemberCsvProcessorTest.java
git commit -m "feat(csv): implement MemberCsvProcessor with encoding detection (#52)"
```

---

### Task 4: Write repository extension tests and implement

**Files:**
- Modify: `backend/src/main/java/com/worklog/infrastructure/persistence/JdbcUserRepository.java`
- Modify: `backend/src/test/kotlin/com/worklog/infrastructure/persistence/JdbcUserRepositoryTest.kt`
- Modify: `backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberRepository.java`
- Modify: `backend/src/test/java/com/worklog/infrastructure/repository/JdbcMemberRepositoryTest.java`

**Context:** Follow the existing `findDisplayNamesByIds` pattern in `JdbcMemberRepository` (line 300-317) for the `IN` clause batch query approach. Tests use `IntegrationTestBase` (Java tests) and standalone Testcontainers (Kotlin tests).

**Step 1: Add tests to JdbcUserRepositoryTest.kt**

Append these test methods inside the `JdbcUserRepositoryTest` class (after the existing `count` test at line 446):

```kotlin
// ============================================================
// FindExistingEmails Tests
// ============================================================

@Test
fun `findExistingEmails - should return matching emails`() {
    // Given
    repository.save(User.create("alice@example.com", "Alice", "pass1", testRoleId))
    repository.save(User.create("bob@example.com", "Bob", "pass2", testRoleId))

    // When
    val result = repository.findExistingEmails(listOf("alice@example.com", "charlie@example.com"))

    // Then
    Assertions.assertEquals(setOf("alice@example.com"), result)
}

@Test
fun `findExistingEmails - should be case-insensitive`() {
    // Given
    repository.save(User.create("alice@example.com", "Alice", "pass", testRoleId))

    // When
    val result = repository.findExistingEmails(listOf("ALICE@EXAMPLE.COM"))

    // Then
    Assertions.assertEquals(1, result.size)
    Assertions.assertTrue(result.contains("alice@example.com"))
}

@Test
fun `findExistingEmails - should return empty set for empty input`() {
    // When
    val result = repository.findExistingEmails(emptyList())

    // Then
    Assertions.assertTrue(result.isEmpty())
}

@Test
fun `findExistingEmails - should return empty set when no matches`() {
    // When
    val result = repository.findExistingEmails(listOf("nobody@example.com"))

    // Then
    Assertions.assertTrue(result.isEmpty())
}
```

**Step 2: Implement findExistingEmails in JdbcUserRepository.java**

Add this method after `existsByEmail` (line 80), before `findByAccountStatus`:

```java
/**
 * Finds which of the given emails already exist in the users table (case-insensitive).
 *
 * @param emails Collection of email addresses to check
 * @return Set of existing emails (lowercased)
 */
public Set<String> findExistingEmails(Collection<String> emails) {
    if (emails.isEmpty()) {
        return Set.of();
    }

    String placeholders = emails.stream().map(e -> "?").collect(Collectors.joining(", "));
    String sql = "SELECT LOWER(email) FROM users WHERE LOWER(email) IN (" + placeholders + ")";
    Object[] params = emails.stream().map(String::toLowerCase).toArray();

    return new HashSet<>(jdbcTemplate.queryForList(sql, String.class, params));
}
```

Add required imports to JdbcUserRepository.java:

```java
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
```

**Step 3: Run user repository tests**

Run: `cd backend && ./gradlew test --tests "com.worklog.infrastructure.persistence.JdbcUserRepositoryTest" --info 2>&1 | tail -30`

Expected: All tests PASS (including the 4 new ones).

**Step 4: Add tests to JdbcMemberRepositoryTest.java**

Append these test methods inside `JdbcMemberRepositoryTest` (after line 77):

```java
@Test
@DisplayName("findExistingEmailsInTenant should return empty set for empty input")
void findExistingEmailsInTenant_emptyInput_returnsEmptySet() {
    Set<String> result = memberRepository.findExistingEmailsInTenant(
            TenantId.of(UUID.fromString(TEST_TENANT_ID)), List.of());
    assertTrue(result.isEmpty());
}

@Test
@DisplayName("findExistingEmailsInTenant should return matching emails")
void findExistingEmailsInTenant_existingMembers_returnsEmails() {
    // Arrange
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    String email1 = "member1-" + id1 + "@example.com";
    String email2 = "member2-" + id2 + "@example.com";
    createTestMember(id1, email1);
    createTestMember(id2, email2);

    // Act
    Set<String> result = memberRepository.findExistingEmailsInTenant(
            TenantId.of(UUID.fromString(TEST_TENANT_ID)),
            List.of(email1, "nonexistent@example.com"));

    // Assert
    assertEquals(1, result.size());
    assertTrue(result.contains(email1.toLowerCase()));
}

@Test
@DisplayName("findExistingEmailsInTenant should be case-insensitive")
void findExistingEmailsInTenant_caseInsensitive_returnsMatch() {
    // Arrange
    UUID id = UUID.randomUUID();
    String email = "CaseTest-" + id + "@example.com";
    createTestMember(id, email);

    // Act
    Set<String> result = memberRepository.findExistingEmailsInTenant(
            TenantId.of(UUID.fromString(TEST_TENANT_ID)),
            List.of(email.toUpperCase()));

    // Assert
    assertEquals(1, result.size());
}

@Test
@DisplayName("findExistingEmailsInTenant should not return members from other tenants")
void findExistingEmailsInTenant_otherTenant_returnsEmpty() {
    // Arrange
    UUID id = UUID.randomUUID();
    String email = "tenant-test-" + id + "@example.com";
    createTestMember(id, email);

    // Act — search in a different (non-existent) tenant
    Set<String> result = memberRepository.findExistingEmailsInTenant(
            TenantId.of(UUID.randomUUID()),
            List.of(email));

    // Assert
    assertTrue(result.isEmpty());
}
```

Add imports to the test file:

```java
import com.worklog.domain.tenant.TenantId;
import java.util.List;
```

**Step 5: Implement findExistingEmailsInTenant in JdbcMemberRepository.java**

Add this method after `findDisplayNamesByIds` (line 317), before the `MemberRowMapper`:

```java
/**
 * Finds which of the given emails already exist in the members table for a specific tenant
 * (case-insensitive).
 *
 * @param tenantId Tenant ID to scope the search
 * @param emails Collection of email addresses to check
 * @return Set of existing emails (lowercased)
 */
public Set<String> findExistingEmailsInTenant(TenantId tenantId, Collection<String> emails) {
    if (emails.isEmpty()) {
        return Set.of();
    }

    String placeholders = emails.stream().map(e -> "?").collect(java.util.stream.Collectors.joining(", "));
    String sql = "SELECT LOWER(email) FROM members WHERE tenant_id = ? AND LOWER(email) IN (" + placeholders + ")";

    Object[] params = new Object[1 + emails.size()];
    params[0] = tenantId.value();
    int i = 1;
    for (String email : emails) {
        params[i++] = email.toLowerCase();
    }

    return new java.util.HashSet<>(jdbcTemplate.queryForList(sql, String.class, params));
}
```

Add required imports to JdbcMemberRepository.java:

```java
import java.util.Collection;
```

**Step 6: Run member repository tests**

Run: `cd backend && ./gradlew test --tests "com.worklog.infrastructure.repository.JdbcMemberRepositoryTest" --info 2>&1 | tail -30`

Expected: All tests PASS (including the 4 new ones).

**Step 7: Commit**

```bash
git add backend/src/main/java/com/worklog/infrastructure/persistence/JdbcUserRepository.java \
       backend/src/test/kotlin/com/worklog/infrastructure/persistence/JdbcUserRepositoryTest.kt \
       backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberRepository.java \
       backend/src/test/java/com/worklog/infrastructure/repository/JdbcMemberRepositoryTest.java
git commit -m "feat(csv): add batch email existence check to repositories (#52)"
```

---

### Task 5: Write MemberCsvValidationService tests

**Files:**
- Create: `backend/src/test/java/com/worklog/infrastructure/csv/MemberCsvValidationServiceTest.java`

**Context:** This is a unit test. Mock `JdbcUserRepository` and `JdbcMemberRepository` with Mockito. Follow the existing `CsvValidationServiceTest.java` pattern for assertion style.

**Step 1: Write the test file**

```java
package com.worklog.infrastructure.csv;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("MemberCsvValidationService")
@ExtendWith(MockitoExtension.class)
class MemberCsvValidationServiceTest {

    @Mock
    private JdbcUserRepository userRepository;

    @Mock
    private JdbcMemberRepository memberRepository;

    private MemberCsvValidationService service;

    private static final TenantId TENANT_ID = TenantId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new MemberCsvValidationService(userRepository, memberRepository);
    }

    @Nested
    @DisplayName("per-row validation")
    class PerRowValidation {

        @Test
        @DisplayName("should accept valid rows")
        void validRows() {
            List<MemberCsvRow> rows = List.of(
                    new MemberCsvRow(1, "alice@example.com", "Alice"),
                    new MemberCsvRow(2, "bob@example.com", "Bob"));

            when(userRepository.findExistingEmails(anyCollection())).thenReturn(Set.of());
            when(memberRepository.findExistingEmailsInTenant(any(), anyCollection())).thenReturn(Set.of());

            MemberCsvResult result = service.validate(rows, TENANT_ID);

            assertEquals(2, result.validRows().size());
            assertTrue(result.errors().isEmpty());
        }

        @Test
        @DisplayName("should reject empty email")
        void emptyEmail() {
            List<MemberCsvRow> rows = List.of(new MemberCsvRow(1, "", "Name"));
            when(userRepository.findExistingEmails(anyCollection())).thenReturn(Set.of());
            when(memberRepository.findExistingEmailsInTenant(any(), anyCollection())).thenReturn(Set.of());

            MemberCsvResult result = service.validate(rows, TENANT_ID);

            assertTrue(result.validRows().isEmpty());
            assertEquals(1, result.errors().size());
            assertEquals("email", result.errors().get(0).field());
            assertEquals(1, result.errors().get(0).rowNumber());
        }

        @Test
        @DisplayName("should reject invalid email format")
        void invalidEmailFormat() {
            List<MemberCsvRow> rows = List.of(new MemberCsvRow(1, "not-an-email", "Name"));
            when(userRepository.findExistingEmails(anyCollection())).thenReturn(Set.of());
            when(memberRepository.findExistingEmailsInTenant(any(), anyCollection())).thenReturn(Set.of());

            MemberCsvResult result = service.validate(rows, TENANT_ID);

            assertTrue(result.validRows().isEmpty());
            assertEquals("email", result.errors().get(0).field());
        }

        @Test
        @DisplayName("should reject email exceeding 255 characters")
        void emailTooLong() {
            String longEmail = "a".repeat(246) + "@b.com"; // 252 chars
            // This is valid length. Now test one that's too long:
            String tooLongEmail = "a".repeat(250) + "@b.com"; // 256 chars
            List<MemberCsvRow> rows = List.of(new MemberCsvRow(1, tooLongEmail, "Name"));
            when(userRepository.findExistingEmails(anyCollection())).thenReturn(Set.of());
            when(memberRepository.findExistingEmailsInTenant(any(), anyCollection())).thenReturn(Set.of());

            MemberCsvResult result = service.validate(rows, TENANT_ID);

            assertTrue(result.validRows().isEmpty());
            assertEquals("email", result.errors().get(0).field());
        }

        @Test
        @DisplayName("should reject empty displayName")
        void emptyDisplayName() {
            List<MemberCsvRow> rows = List.of(new MemberCsvRow(1, "valid@example.com", ""));
            when(userRepository.findExistingEmails(anyCollection())).thenReturn(Set.of());
            when(memberRepository.findExistingEmailsInTenant(any(), anyCollection())).thenReturn(Set.of());

            MemberCsvResult result = service.validate(rows, TENANT_ID);

            assertTrue(result.validRows().isEmpty());
            assertEquals("displayName", result.errors().get(0).field());
        }

        @Test
        @DisplayName("should reject displayName exceeding 100 characters")
        void displayNameTooLong() {
            String longName = "A".repeat(101);
            List<MemberCsvRow> rows = List.of(new MemberCsvRow(1, "valid@example.com", longName));
            when(userRepository.findExistingEmails(anyCollection())).thenReturn(Set.of());
            when(memberRepository.findExistingEmailsInTenant(any(), anyCollection())).thenReturn(Set.of());

            MemberCsvResult result = service.validate(rows, TENANT_ID);

            assertTrue(result.validRows().isEmpty());
            assertEquals("displayName", result.errors().get(0).field());
        }

        @Test
        @DisplayName("should collect multiple errors for the same row")
        void multipleErrorsSameRow() {
            List<MemberCsvRow> rows = List.of(new MemberCsvRow(1, "", ""));
            when(userRepository.findExistingEmails(anyCollection())).thenReturn(Set.of());
            when(memberRepository.findExistingEmailsInTenant(any(), anyCollection())).thenReturn(Set.of());

            MemberCsvResult result = service.validate(rows, TENANT_ID);

            assertTrue(result.validRows().isEmpty());
            assertEquals(2, result.errors().size());
        }
    }

    @Nested
    @DisplayName("CSV-internal duplicate check")
    class CsvDuplicateCheck {

        @Test
        @DisplayName("should reject duplicate emails within CSV")
        void duplicateEmailInCsv() {
            List<MemberCsvRow> rows = List.of(
                    new MemberCsvRow(1, "same@example.com", "First"),
                    new MemberCsvRow(2, "same@example.com", "Second"));
            when(userRepository.findExistingEmails(anyCollection())).thenReturn(Set.of());
            when(memberRepository.findExistingEmailsInTenant(any(), anyCollection())).thenReturn(Set.of());

            MemberCsvResult result = service.validate(rows, TENANT_ID);

            assertEquals(1, result.validRows().size());
            assertEquals(1, result.errors().size());
            assertEquals(2, result.errors().get(0).rowNumber());
            assertEquals("email", result.errors().get(0).field());
        }

        @Test
        @DisplayName("should detect case-insensitive duplicates within CSV")
        void caseInsensitiveDuplicate() {
            List<MemberCsvRow> rows = List.of(
                    new MemberCsvRow(1, "Test@Example.com", "First"),
                    new MemberCsvRow(2, "test@example.com", "Second"));
            when(userRepository.findExistingEmails(anyCollection())).thenReturn(Set.of());
            when(memberRepository.findExistingEmailsInTenant(any(), anyCollection())).thenReturn(Set.of());

            MemberCsvResult result = service.validate(rows, TENANT_ID);

            assertEquals(1, result.validRows().size());
            assertEquals(1, result.errors().size());
            assertEquals(2, result.errors().get(0).rowNumber());
        }
    }

    @Nested
    @DisplayName("DB duplicate check")
    class DbDuplicateCheck {

        @Test
        @DisplayName("should reject emails that exist in users table")
        void duplicateInUsersTable() {
            List<MemberCsvRow> rows = List.of(new MemberCsvRow(1, "existing@example.com", "Exists"));
            when(userRepository.findExistingEmails(anyCollection()))
                    .thenReturn(Set.of("existing@example.com"));
            when(memberRepository.findExistingEmailsInTenant(any(), anyCollection())).thenReturn(Set.of());

            MemberCsvResult result = service.validate(rows, TENANT_ID);

            assertTrue(result.validRows().isEmpty());
            assertEquals(1, result.errors().size());
            assertEquals("email", result.errors().get(0).field());
        }

        @Test
        @DisplayName("should reject emails that exist in members table for the same tenant")
        void duplicateInMembersTable() {
            List<MemberCsvRow> rows = List.of(new MemberCsvRow(1, "member@example.com", "Member"));
            when(userRepository.findExistingEmails(anyCollection())).thenReturn(Set.of());
            when(memberRepository.findExistingEmailsInTenant(any(), anyCollection()))
                    .thenReturn(Set.of("member@example.com"));

            MemberCsvResult result = service.validate(rows, TENANT_ID);

            assertTrue(result.validRows().isEmpty());
            assertEquals(1, result.errors().size());
            assertEquals("email", result.errors().get(0).field());
        }
    }
}
```

**Step 2: Verify tests fail**

Run: `cd backend && ./gradlew test --tests "com.worklog.infrastructure.csv.MemberCsvValidationServiceTest" --info 2>&1 | tail -20`

Expected: Compilation error — `MemberCsvValidationService` doesn't exist yet.

---

### Task 6: Implement MemberCsvValidationService

**Files:**
- Create: `backend/src/main/java/com/worklog/infrastructure/csv/MemberCsvValidationService.java`

**Step 1: Implement the service**

```java
package com.worklog.infrastructure.csv;

import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Validates parsed member CSV rows against format rules and database constraints.
 * Collects all errors in a single pass so users can fix everything at once.
 */
@Service
public class MemberCsvValidationService {

    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_DISPLAY_NAME_LENGTH = 100;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    private final JdbcUserRepository userRepository;
    private final JdbcMemberRepository memberRepository;

    public MemberCsvValidationService(JdbcUserRepository userRepository, JdbcMemberRepository memberRepository) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    /**
     * Validates all CSV rows: format, CSV-internal duplicates, and DB duplicates.
     *
     * @param rows     parsed CSV rows
     * @param tenantId tenant to check member duplicates against
     * @return result with valid rows and all errors
     */
    public MemberCsvResult validate(List<MemberCsvRow> rows, TenantId tenantId) {
        List<CsvValidationError> errors = new ArrayList<>();
        List<MemberCsvRow> validRows = new ArrayList<>();

        // Phase 1: Per-row format validation + CSV-internal duplicate detection
        Set<String> seenEmails = new HashSet<>();
        List<MemberCsvRow> formatValidRows = new ArrayList<>();

        for (MemberCsvRow row : rows) {
            List<CsvValidationError> rowErrors = validateRow(row);

            if (rowErrors.isEmpty() && !row.email().isBlank()) {
                String lowerEmail = row.email().toLowerCase();
                if (!seenEmails.add(lowerEmail)) {
                    rowErrors.add(new CsvValidationError(row.rowNumber(), "email", "Duplicate email in CSV"));
                }
            }

            if (rowErrors.isEmpty()) {
                formatValidRows.add(row);
            } else {
                errors.addAll(rowErrors);
            }
        }

        // Phase 2: Batch DB duplicate check (only for rows that passed format validation)
        if (!formatValidRows.isEmpty()) {
            Set<String> emailsToCheck =
                    formatValidRows.stream().map(r -> r.email().toLowerCase()).collect(Collectors.toSet());

            Set<String> existingUserEmails = userRepository.findExistingEmails(emailsToCheck);
            Set<String> existingMemberEmails = memberRepository.findExistingEmailsInTenant(tenantId, emailsToCheck);

            for (MemberCsvRow row : formatValidRows) {
                String lowerEmail = row.email().toLowerCase();
                if (existingUserEmails.contains(lowerEmail)) {
                    errors.add(new CsvValidationError(
                            row.rowNumber(), "email", "Email already exists as a user account"));
                } else if (existingMemberEmails.contains(lowerEmail)) {
                    errors.add(new CsvValidationError(
                            row.rowNumber(), "email", "Email already exists as a member in this tenant"));
                } else {
                    validRows.add(row);
                }
            }
        }

        return new MemberCsvResult(validRows, errors);
    }

    private List<CsvValidationError> validateRow(MemberCsvRow row) {
        List<CsvValidationError> errors = new ArrayList<>();

        // Validate email
        if (row.email() == null || row.email().isBlank()) {
            errors.add(new CsvValidationError(row.rowNumber(), "email", "Email is required"));
        } else {
            if (row.email().length() > MAX_EMAIL_LENGTH) {
                errors.add(new CsvValidationError(
                        row.rowNumber(), "email", "Email must not exceed " + MAX_EMAIL_LENGTH + " characters"));
            } else if (!EMAIL_PATTERN.matcher(row.email()).matches()) {
                errors.add(new CsvValidationError(row.rowNumber(), "email", "Invalid email format"));
            }
        }

        // Validate displayName
        if (row.displayName() == null || row.displayName().isBlank()) {
            errors.add(new CsvValidationError(row.rowNumber(), "displayName", "Display name is required"));
        } else if (row.displayName().length() > MAX_DISPLAY_NAME_LENGTH) {
            errors.add(new CsvValidationError(
                    row.rowNumber(),
                    "displayName",
                    "Display name must not exceed " + MAX_DISPLAY_NAME_LENGTH + " characters"));
        }

        return errors;
    }
}
```

**Step 2: Run validation service tests**

Run: `cd backend && ./gradlew test --tests "com.worklog.infrastructure.csv.MemberCsvValidationServiceTest" --info 2>&1 | tail -30`

Expected: All 11 tests PASS.

**Step 3: Commit**

```bash
git add backend/src/main/java/com/worklog/infrastructure/csv/MemberCsvValidationService.java \
       backend/src/test/java/com/worklog/infrastructure/csv/MemberCsvValidationServiceTest.java
git commit -m "feat(csv): implement MemberCsvValidationService with DB duplicate checks (#52)"
```

---

### Task 7: Run full test suite and format

**Step 1: Format all code**

Run: `cd backend && ./gradlew formatAll`

**Step 2: Run all tests**

Run: `cd backend && ./gradlew test`

Expected: All tests pass, no regressions.

**Step 3: Run detekt**

Run: `cd backend && ./gradlew detekt`

Expected: No new violations.

**Step 4: Commit any formatting changes**

```bash
git add -A
git status
# Only commit if there are formatting changes
git commit -m "style: apply spotless formatting to new CSV import files (#52)"
```

---

### Task 8: Check test coverage

**Step 1: Generate JaCoCo report**

Run: `cd backend && ./gradlew test jacocoTestReport`

**Step 2: Check coverage for changed packages**

Examine coverage for:
- `com.worklog.infrastructure.csv` — target: 80%+ line coverage
- `com.worklog.infrastructure.persistence` — no regression
- `com.worklog.infrastructure.repository` — no regression

If coverage is below 80% for the csv package, add additional tests as needed.

**Step 3: Final commit if any adjustments were made**

```bash
git add -A
git commit -m "test(csv): ensure coverage targets for member CSV import (#52)"
```
