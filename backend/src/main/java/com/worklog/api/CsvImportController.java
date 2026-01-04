package com.worklog.api;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * REST API controller for CSV import operations.
 * Provides endpoints for template download and CSV file import.
 */
@RestController
@RequestMapping("/api/v1/worklog/csv")
public class CsvImportController {

    /**
     * Download the CSV template file for work log entries.
     * 
     * GET /api/v1/worklog/csv/template
     * 
     * @return CSV template file as downloadable resource
     */
    @GetMapping("/template")
    public ResponseEntity<Resource> downloadTemplate() throws IOException {
        Resource resource = new ClassPathResource("csv-templates/worklog-template.csv");
        
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"worklog-template.csv\"")
                .body(resource);
    }
}
