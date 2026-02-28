package com.worklog.infrastructure.csv;

import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MemberCsvValidationService {

    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_DISPLAY_NAME_LENGTH = 100;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_%+\\-]+(?:\\.[a-zA-Z0-9_%+\\-]+)*@[a-zA-Z0-9](?:[a-zA-Z0-9\\-]*[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9\\-]*[a-zA-Z0-9])?)*\\.[a-zA-Z]{2,}$");

    private final JdbcUserRepository userRepository;
    private final JdbcMemberRepository memberRepository;

    public MemberCsvValidationService(JdbcUserRepository userRepository, JdbcMemberRepository memberRepository) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    public MemberCsvResult validate(List<MemberCsvRow> rows, TenantId tenantId) {
        List<CsvValidationError> errors = new ArrayList<>();
        List<MemberCsvRow> validRows = new ArrayList<>();

        // Phase 1: Per-row format validation + CSV-internal duplicate detection
        Set<String> seenEmails = new HashSet<>();
        List<MemberCsvRow> formatValidRows = new ArrayList<>();

        for (MemberCsvRow row : rows) {
            List<CsvValidationError> rowErrors = validateRow(row);

            if (rowErrors.isEmpty() && !row.email().isBlank()) {
                String lowerEmail = row.email().toLowerCase(Locale.ROOT);
                if (!seenEmails.add(lowerEmail)) {
                    rowErrors.add(new CsvValidationError(
                            row.rowNumber(), "email", "Email appears multiple times in this CSV file"));
                }
            }

            if (rowErrors.isEmpty()) {
                formatValidRows.add(row);
            } else {
                errors.addAll(rowErrors);
            }
        }

        // Phase 2: Batch DB duplicate check
        if (!formatValidRows.isEmpty()) {
            Set<String> emailsToCheck =
                    formatValidRows.stream().map(r -> r.email().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

            Set<String> existingUserEmails = userRepository.findExistingEmails(emailsToCheck);
            Set<String> existingMemberEmails = memberRepository.findExistingEmailsInTenant(tenantId, emailsToCheck);

            for (MemberCsvRow row : formatValidRows) {
                String lowerEmail = row.email().toLowerCase(Locale.ROOT);
                if (existingUserEmails.contains(lowerEmail)) {
                    errors.add(
                            new CsvValidationError(row.rowNumber(), "email", "Email already exists as a user account"));
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

        if (row.email() == null || row.email().isBlank()) {
            errors.add(new CsvValidationError(row.rowNumber(), "email", "Email is required"));
        } else {
            if (row.email().length() > MAX_EMAIL_LENGTH) {
                errors.add(new CsvValidationError(
                        row.rowNumber(), "email", "Email must not exceed " + MAX_EMAIL_LENGTH + " characters"));
            } else if (!EMAIL_PATTERN.matcher(row.email()).matches()) {
                errors.add(new CsvValidationError(
                        row.rowNumber(), "email", "Invalid email format (expected: user@example.com)"));
            }
        }

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
