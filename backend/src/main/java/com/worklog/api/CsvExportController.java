package com.worklog.api;

import com.worklog.domain.member.MemberId;
import com.worklog.domain.worklog.WorkLogStatus;
import com.worklog.infrastructure.csv.ProjectCodeResolver;
import com.worklog.infrastructure.repository.JdbcWorkLogRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * REST API controller for CSV export operations.
 * Provides endpoint for exporting work log entries to CSV format.
 */
@RestController
@RequestMapping("/api/v1/worklog/csv")
public class CsvExportController {

    private final JdbcWorkLogRepository workLogRepository;

    public CsvExportController(JdbcWorkLogRepository workLogRepository) {
        this.workLogRepository = workLogRepository;
    }

    /**
     * Export work log entries for a specific month to CSV format.
     * Exports all entries regardless of status (DRAFT, SUBMITTED, APPROVED).
     * 
     * GET /api/v1/worklog/csv/export/{year}/{month}?memberId={memberId}
     * 
     * @param year Year (e.g., 2026)
     * @param month Month (1-12)
     * @param memberId Member ID to export entries for
     * @return CSV file as downloadable resource
     */
    @GetMapping("/export/{year}/{month}")
    public ResponseEntity<String> exportCsv(
            @PathVariable int year,
            @PathVariable int month,
            @RequestParam UUID memberId) throws IOException {
        
        // Calculate fiscal month date range (21st of previous month to 20th of current month)
        // This aligns with CalendarController's fiscal month logic
        LocalDate startDate = YearMonth.of(year, month).atDay(1).minusMonths(1).withDayOfMonth(21);
        LocalDate endDate = YearMonth.of(year, month).atDay(20);
        
        // Get entries for the date range (all statuses)
        var entries = workLogRepository.findByDateRange(
                memberId,
                startDate,
                endDate,
                null // null = all statuses
        );
        
        // Build CSV
        StringWriter writer = new StringWriter();
        try (CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                .builder()
                .setHeader("Date", "Project Code", "Hours", "Notes")
                .build())) {

            // Write data rows
            for (var entry : entries) {
                csvPrinter.printRecord(
                        entry.getDate(),
                        ProjectCodeResolver.resolveIdToCode(entry.getProjectId()),
                        entry.getHours().hours(),
                        entry.getComment() != null ? entry.getComment() : ""
                );
            }
        }

        String filename = String.format("worklog-%04d-%02d.csv", year, month);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(writer.toString());
    }
}


