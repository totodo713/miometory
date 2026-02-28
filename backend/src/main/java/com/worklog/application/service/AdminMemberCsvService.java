package com.worklog.application.service;

import com.worklog.application.command.InviteMemberCommand;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.csv.CsvParseException;
import com.worklog.infrastructure.csv.CsvValidationError;
import com.worklog.infrastructure.csv.MemberCsvProcessor;
import com.worklog.infrastructure.csv.MemberCsvResult;
import com.worklog.infrastructure.csv.MemberCsvRow;
import com.worklog.infrastructure.csv.MemberCsvValidationService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminMemberCsvService {

    private static final Logger log = LoggerFactory.getLogger(AdminMemberCsvService.class);
    private static final long SESSION_TTL_MINUTES = 30;
    private static final long MAX_FILE_SIZE_BYTES = 1_048_576; // 1MB
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final MemberCsvProcessor csvProcessor;
    private final MemberCsvValidationService validationService;
    private final AdminMemberService adminMemberService;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, DryRunSession> sessions = new ConcurrentHashMap<>();

    public AdminMemberCsvService(
            MemberCsvProcessor csvProcessor,
            MemberCsvValidationService validationService,
            AdminMemberService adminMemberService,
            JdbcTemplate jdbcTemplate) {
        this.csvProcessor = csvProcessor;
        this.validationService = validationService;
        this.adminMemberService = adminMemberService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public DryRunResult dryRun(MultipartFile file, UUID organizationId, UUID tenantId, UUID invitedBy) {
        validateFile(file);

        if (!organizationExistsInProjection(organizationId, tenantId)) {
            throw new DomainException("ORGANIZATION_NOT_FOUND", "Organization not found");
        }

        List<MemberCsvRow> rows;
        try {
            rows = csvProcessor.parse(file.getBytes());
        } catch (CsvParseException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read CSV file", e);
        }

        MemberCsvResult result = validationService.validate(rows, TenantId.of(tenantId));

        String sessionId = UUID.randomUUID().toString();
        sessions.put(
                sessionId,
                new DryRunSession(
                        sessionId,
                        tenantId,
                        organizationId,
                        invitedBy,
                        result.validRows(),
                        result.errors(),
                        Instant.now()));

        return buildDryRunResult(sessionId, rows, result);
    }

    @Transactional
    public byte[] executeImport(String sessionId, UUID tenantId) {
        DryRunSession session = sessions.get(sessionId);
        if (session == null || session.isExpired()) {
            if (session != null) {
                sessions.remove(sessionId);
            }
            throw new DomainException("SESSION_NOT_FOUND", "Import session not found or expired");
        }

        if (!session.tenantId().equals(tenantId)) {
            // Return same error as not-found to avoid leaking session existence across tenants
            throw new DomainException("SESSION_NOT_FOUND", "Import session not found or expired");
        }

        // Re-validate to catch changes since dry-run
        MemberCsvResult revalidation = validationService.validate(session.validRows(), TenantId.of(tenantId));
        if (revalidation.hasErrors()) {
            throw new DomainException(
                    "IMPORT_VALIDATION_CHANGED",
                    "Validation results have changed since dry-run. Please run dry-run again.");
        }

        List<ImportedRow> importedRows = new ArrayList<>();
        for (MemberCsvRow row : revalidation.validRows()) {
            try {
                var command = new InviteMemberCommand(
                        row.email(), row.displayName(), session.organizationId(), null, session.invitedBy());
                var result = adminMemberService.inviteMember(command, tenantId);
                importedRows.add(new ImportedRow(row.email(), row.displayName(), result.temporaryPassword()));
            } catch (DomainException e) {
                throw new DomainException(
                        "IMPORT_VALIDATION_CHANGED",
                        "Failed to import row " + row.rowNumber() + " (" + row.email() + "): " + e.getMessage(),
                        e);
            }
        }

        // Remove session after transaction commits
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sessions.remove(sessionId);
            }
        });

        log.info("CSV import completed: {} members imported for tenant {}", importedRows.size(), tenantId);
        return buildResultCsv(importedRows);
    }

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void cleanupExpiredSessions() {
        Iterator<Map.Entry<String, DryRunSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isExpired()) {
                it.remove();
            }
        }
    }

    private boolean organizationExistsInProjection(UUID organizationId, UUID tenantId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM organization WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'",
                Integer.class,
                organizationId,
                tenantId);
        return count != null && count > 0;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required and must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("CSV file must not exceed 1MB");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("File must be a CSV file (.csv)");
        }
    }

    private DryRunResult buildDryRunResult(String sessionId, List<MemberCsvRow> allRows, MemberCsvResult result) {
        List<DryRunRowResult> rowResults = new ArrayList<>();

        for (MemberCsvRow row : result.validRows()) {
            rowResults.add(new DryRunRowResult(row.rowNumber(), row.email(), row.displayName(), "VALID", List.of()));
        }

        for (CsvValidationError error : result.errors()) {
            MemberCsvRow matchingRow = allRows.stream()
                    .filter(r -> r.rowNumber() == error.rowNumber())
                    .findFirst()
                    .orElse(null);

            String email = matchingRow != null ? matchingRow.email() : "";
            String displayName = matchingRow != null ? matchingRow.displayName() : "";

            // Group errors by row number
            DryRunRowResult existing = rowResults.stream()
                    .filter(r -> r.rowNumber() == error.rowNumber())
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                List<String> errors = new ArrayList<>(existing.errors());
                errors.add(error.field() + ": " + error.message());
                rowResults.remove(existing);
                rowResults.add(new DryRunRowResult(error.rowNumber(), email, displayName, "ERROR", errors));
            } else {
                rowResults.add(new DryRunRowResult(
                        error.rowNumber(),
                        email,
                        displayName,
                        "ERROR",
                        List.of(error.field() + ": " + error.message())));
            }
        }

        rowResults.sort((a, b) -> Integer.compare(a.rowNumber(), b.rowNumber()));

        return new DryRunResult(
                sessionId,
                allRows.size(),
                result.validRows().size(),
                (int) rowResults.stream()
                        .filter(r -> "ERROR".equals(r.status()))
                        .count(),
                rowResults);
    }

    private byte[] buildResultCsv(List<ImportedRow> rows) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            baos.write(UTF8_BOM);
            writer.write("email,displayName,status,temporaryPassword\n");
            for (ImportedRow row : rows) {
                writer.write(escapeCsvField(row.email()) + ","
                        + escapeCsvField(row.displayName()) + ","
                        + "SUCCESS,"
                        + escapeCsvField(row.temporaryPassword()) + "\n");
            }
            writer.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate result CSV", e);
        }
    }

    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        String sanitized = field;
        // Prevent CSV formula injection
        if (!sanitized.isEmpty()) {
            char first = sanitized.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
                sanitized = "'" + sanitized;
            }
        }
        if (sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\n")) {
            return "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }

    // Inner records

    record DryRunSession(
            String sessionId,
            UUID tenantId,
            UUID organizationId,
            UUID invitedBy,
            List<MemberCsvRow> validRows,
            List<CsvValidationError> errors,
            Instant createdAt) {
        boolean isExpired() {
            return Instant.now().isAfter(createdAt.plusSeconds(SESSION_TTL_MINUTES * 60));
        }
    }

    public record DryRunResult(
            String sessionId, int totalRows, int validRows, int errorRows, List<DryRunRowResult> rows) {}

    public record DryRunRowResult(
            int rowNumber, String email, String displayName, String status, List<String> errors) {}

    private record ImportedRow(String email, String displayName, String temporaryPassword) {}
}
