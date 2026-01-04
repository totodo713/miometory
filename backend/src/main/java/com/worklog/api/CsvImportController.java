package com.worklog.api;

import com.worklog.application.command.CreateWorkLogEntryCommand;
import com.worklog.application.service.WorkLogEntryService;
import com.worklog.infrastructure.csv.ProjectCodeResolver;
import com.worklog.infrastructure.csv.StreamingCsvProcessor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST API controller for CSV import operations.
 * Provides endpoints for template download and CSV file import with real-time progress.
 */
@RestController
@RequestMapping("/api/v1/worklog/csv")
public class CsvImportController {

    private final StreamingCsvProcessor csvProcessor;
    private final WorkLogEntryService workLogEntryService;
    private final ExecutorService executorService;
    private final Map<String, ImportProgress> activeImports;

    public CsvImportController(
            StreamingCsvProcessor csvProcessor,
            WorkLogEntryService workLogEntryService) {
        this.csvProcessor = csvProcessor;
        this.workLogEntryService = workLogEntryService;
        this.executorService = Executors.newCachedThreadPool();
        this.activeImports = new ConcurrentHashMap<>();
    }

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

    /**
     * Import work log entries from CSV file with Server-Sent Events progress tracking.
     * 
     * POST /api/v1/worklog/csv/import
     * 
     * @param file CSV file to import
     * @param memberId Member ID for whom to create entries
     * @return Import ID for tracking progress via SSE
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, String>> importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("memberId") UUID memberId) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File is empty"));
        }

        if (!file.getOriginalFilename().endsWith(".csv")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File must be a CSV file"));
        }

        // Generate import ID for tracking
        String importId = UUID.randomUUID().toString();
        ImportProgress progress = new ImportProgress();
        activeImports.put(importId, progress);

        // Start async processing
        executorService.submit(() -> {
            try {
                StreamingCsvProcessor.ProcessingResult result = csvProcessor.processStream(
                        file.getInputStream(),
                        (date, projectCode, hours, notes) -> {
                            // Create work log entry for each valid CSV row
                            CreateWorkLogEntryCommand command = new CreateWorkLogEntryCommand(
                                    memberId,
                                    ProjectCodeResolver.resolveCodeToId(projectCode).value(),
                                    LocalDate.parse(date),
                                    new BigDecimal(hours),
                                    notes,
                                    memberId // enteredBy same as memberId for self-entry
                            );
                            workLogEntryService.createEntry(command);
                        },
                        (totalRows, validRows, errorRows) -> {
                            // Update progress
                            progress.update(totalRows, validRows, errorRows);
                        }
                );

                progress.complete(result);
            } catch (Exception e) {
                progress.fail(e.getMessage());
            }
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("importId", importId));
    }

    /**
     * Get real-time progress updates for CSV import via Server-Sent Events.
     * 
     * GET /api/v1/worklog/csv/import/{importId}/progress
     * 
     * @param importId Import ID from the import request
     * @return SSE emitter for progress updates
     */
    @GetMapping("/import/{importId}/progress")
    public SseEmitter getImportProgress(@PathVariable String importId) {
        ImportProgress progress = activeImports.get(importId);
        
        if (progress == null) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("error", "Import not found")));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        SseEmitter emitter = new SseEmitter(600000L); // 10 minute timeout
        progress.addEmitter(emitter);

        emitter.onCompletion(() -> {
            progress.removeEmitter(emitter);
            activeImports.remove(importId);
        });

        emitter.onTimeout(() -> {
            progress.removeEmitter(emitter);
            activeImports.remove(importId);
        });

        // Send initial state
        try {
            emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(progress.toMap()));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * Internal class to track import progress and notify SSE clients.
     */
    private static class ImportProgress {
        private int totalRows = 0;
        private int validRows = 0;
        private int errorRows = 0;
        private String status = "processing"; // processing, completed, failed
        private String errorMessage = null;
        private StreamingCsvProcessor.ProcessingResult result = null;
        private final java.util.List<SseEmitter> emitters = new java.util.ArrayList<>();

        public synchronized void update(int totalRows, int validRows, int errorRows) {
            this.totalRows = totalRows;
            this.validRows = validRows;
            this.errorRows = errorRows;
            notifyClients();
        }

        public synchronized void complete(StreamingCsvProcessor.ProcessingResult result) {
            this.status = "completed";
            this.result = result;
            this.totalRows = result.getTotalRows();
            this.validRows = result.getValidRows();
            this.errorRows = result.getErrorRows();
            notifyClients();
            completeEmitters();
        }

        public synchronized void fail(String errorMessage) {
            this.status = "failed";
            this.errorMessage = errorMessage;
            notifyClients();
            completeEmitters();
        }

        public synchronized void addEmitter(SseEmitter emitter) {
            emitters.add(emitter);
        }

        public synchronized void removeEmitter(SseEmitter emitter) {
            emitters.remove(emitter);
        }

        private void notifyClients() {
            java.util.List<SseEmitter> deadEmitters = new java.util.ArrayList<>();
            
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("progress")
                            .data(toMap()));
                } catch (IOException e) {
                    deadEmitters.add(emitter);
                    emitter.completeWithError(e);
                }
            }
            
            emitters.removeAll(deadEmitters);
        }

        private void completeEmitters() {
            for (SseEmitter emitter : emitters) {
                emitter.complete();
            }
            emitters.clear();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("totalRows", totalRows);
            map.put("validRows", validRows);
            map.put("errorRows", errorRows);
            map.put("status", status);
            
            if (errorMessage != null) {
                map.put("errorMessage", errorMessage);
            }
            
            if (result != null && result.hasErrors()) {
                map.put("errors", result.getValidationErrors().stream()
                        .map(e -> Map.of(
                                "row", e.getRowNumber(),
                                "errors", e.getErrors()
                        ))
                        .limit(100) // Limit to first 100 errors
                        .toList());
            }
            
            return map;
        }
    }
}
