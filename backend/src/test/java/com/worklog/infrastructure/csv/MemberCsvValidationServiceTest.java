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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
                    new MemberCsvRow(1, "alice@example.com", "Alice"), new MemberCsvRow(2, "bob@example.com", "Bob"));

            when(userRepository.findExistingEmails(anyCollection())).thenReturn(Set.of());
            when(memberRepository.findExistingEmailsInTenant(any(), anyCollection()))
                    .thenReturn(Set.of());

            MemberCsvResult result = service.validate(rows, TENANT_ID);

            assertEquals(2, result.validRows().size());
            assertFalse(result.hasErrors());
        }

        @Test
        @DisplayName("should reject empty email")
        void emptyEmail() {
            List<MemberCsvRow> rows = List.of(new MemberCsvRow(1, "", "Name"));

            MemberCsvResult result = service.validate(rows, TENANT_ID);

            assertTrue(result.validRows().isEmpty());
            assertTrue(result.hasErrors());
            assertEquals(1, result.errors().size());
            assertEquals("email", result.errors().get(0).field());
            assertEquals(1, result.errors().get(0).rowNumber());
        }

        @Test
        @DisplayName("should reject invalid email format")
        void invalidEmailFormat() {
            List<MemberCsvRow> rows = List.of(new MemberCsvRow(1, "not-an-email", "Name"));

            MemberCsvResult result = service.validate(rows, TENANT_ID);

            assertTrue(result.validRows().isEmpty());
            assertEquals("email", result.errors().get(0).field());
        }

        @Test
        @DisplayName("should reject email exceeding 255 characters")
        void emailTooLong() {
            String tooLongEmail = "a".repeat(250) + "@b.com"; // 256 chars
            List<MemberCsvRow> rows = List.of(new MemberCsvRow(1, tooLongEmail, "Name"));

            MemberCsvResult result = service.validate(rows, TENANT_ID);

            assertTrue(result.validRows().isEmpty());
            assertEquals("email", result.errors().get(0).field());
        }

        @Test
        @DisplayName("should reject empty displayName")
        void emptyDisplayName() {
            List<MemberCsvRow> rows = List.of(new MemberCsvRow(1, "valid@example.com", ""));

            MemberCsvResult result = service.validate(rows, TENANT_ID);

            assertTrue(result.validRows().isEmpty());
            assertEquals("displayName", result.errors().get(0).field());
        }

        @Test
        @DisplayName("should reject displayName exceeding 100 characters")
        void displayNameTooLong() {
            String longName = "A".repeat(101);
            List<MemberCsvRow> rows = List.of(new MemberCsvRow(1, "valid@example.com", longName));

            MemberCsvResult result = service.validate(rows, TENANT_ID);

            assertTrue(result.validRows().isEmpty());
            assertEquals("displayName", result.errors().get(0).field());
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "user..name@example.com",
                    ".user@example.com",
                    "user.@example.com",
                    "user@-example.com",
                    "user@example-.com",
                    "@example.com"
                })
        @DisplayName("should reject emails with invalid format patterns")
        void invalidEmailEdgeCases(String invalidEmail) {
            List<MemberCsvRow> rows = List.of(new MemberCsvRow(1, invalidEmail, "Name"));

            MemberCsvResult result = service.validate(rows, TENANT_ID);

            assertTrue(result.validRows().isEmpty());
            assertEquals("email", result.errors().get(0).field());
        }

        @Test
        @DisplayName("should collect multiple errors for the same row")
        void multipleErrorsSameRow() {
            List<MemberCsvRow> rows = List.of(new MemberCsvRow(1, "", ""));

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
            when(memberRepository.findExistingEmailsInTenant(any(), anyCollection()))
                    .thenReturn(Set.of());

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
            when(memberRepository.findExistingEmailsInTenant(any(), anyCollection()))
                    .thenReturn(Set.of());

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
            when(userRepository.findExistingEmails(anyCollection())).thenReturn(Set.of("existing@example.com"));
            when(memberRepository.findExistingEmailsInTenant(any(), anyCollection()))
                    .thenReturn(Set.of());

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
