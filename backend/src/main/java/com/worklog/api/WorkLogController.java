package com.worklog.api;

import com.worklog.api.dto.*;
import com.worklog.application.command.CreateWorkLogEntryCommand;
import com.worklog.application.command.DeleteWorkLogEntryCommand;
import com.worklog.application.command.UpdateWorkLogEntryCommand;
import com.worklog.application.service.WorkLogEntryService;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.worklog.WorkLogEntry;
import com.worklog.domain.worklog.WorkLogStatus;
import com.worklog.infrastructure.repository.JdbcWorkLogRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for work log entry operations.
 * 
 * Provides CRUD endpoints for managing work log entries.
 */
@RestController
@RequestMapping("/api/v1/worklog/entries")
public class WorkLogController {

    private final WorkLogEntryService workLogService;
    private final JdbcWorkLogRepository workLogRepository;

    public WorkLogController(
        WorkLogEntryService workLogService,
        JdbcWorkLogRepository workLogRepository
    ) {
        this.workLogService = workLogService;
        this.workLogRepository = workLogRepository;
    }

    /**
     * Creates a new work log entry.
     * 
     * POST /api/v1/worklog/entries
     * 
     * @return 201 Created with entry details and ETag header
     */
    @PostMapping
    public ResponseEntity<WorkLogEntryResponse> createEntry(@RequestBody CreateWorkLogEntryRequest request) {
        // For now, use enteredBy from request. In production, get from SecurityContext
        UUID enteredBy = request.enteredBy() != null 
            ? request.enteredBy() 
            : request.memberId(); // Default to member if not specified

        CreateWorkLogEntryCommand command = new CreateWorkLogEntryCommand(
            request.memberId(),
            request.projectId(),
            request.date(),
            request.hours(),
            request.comment(),
            enteredBy
        );

        UUID entryId = workLogService.createEntry(command);
        WorkLogEntry entry = workLogService.findById(entryId);

        if (entry == null) {
            throw new DomainException("ENTRY_NOT_FOUND", "Failed to retrieve created entry");
        }

        WorkLogEntryResponse response = toResponse(entry);
        
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .header(HttpHeaders.ETAG, String.valueOf(entry.getVersion()))
            .body(response);
    }

    /**
     * Get work log entries with optional filtering.
     * 
     * GET /api/v1/worklog/entries?startDate=2026-01-01&endDate=2026-01-31&memberId=...&status=DRAFT
     * 
     * @return 200 OK with list of entries
     */
    @GetMapping
    public ResponseEntity<WorkLogEntriesResponse> getEntries(
        @RequestParam LocalDate startDate,
        @RequestParam LocalDate endDate,
        @RequestParam(required = false) UUID memberId,
        @RequestParam(required = false) WorkLogStatus status
    ) {
        // For now, if memberId not specified, require it. In production, get from SecurityContext
        if (memberId == null) {
            throw new DomainException("MEMBER_ID_REQUIRED", "memberId parameter is required");
        }

        List<WorkLogEntry> entries = workLogRepository.findByDateRange(
            memberId,
            startDate,
            endDate,
            status
        );

        List<WorkLogEntryResponse> responses = entries.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());

        WorkLogEntriesResponse response = new WorkLogEntriesResponse(
            responses,
            responses.size()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get a single work log entry by ID.
     * 
     * GET /api/v1/worklog/entries/{id}
     * 
     * @return 200 OK with entry details and ETag header, or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<WorkLogEntryResponse> getEntry(@PathVariable UUID id) {
        WorkLogEntry entry = workLogService.findById(id);
        
        if (entry == null) {
            return ResponseEntity.notFound().build();
        }

        WorkLogEntryResponse response = toResponse(entry);
        
        return ResponseEntity
            .ok()
            .header(HttpHeaders.ETAG, String.valueOf(entry.getVersion()))
            .body(response);
    }

    /**
     * Update a work log entry (PATCH).
     * 
     * PATCH /api/v1/worklog/entries/{id}
     * Requires If-Match header with version for optimistic locking
     * 
     * @return 204 No Content with ETag header, 409 Conflict on version mismatch, or 404 Not Found
     */
    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateEntry(
        @PathVariable UUID id,
        @RequestBody PatchWorkLogEntryRequest request,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) Long version
    ) {
        if (version == null) {
            throw new DomainException("VERSION_REQUIRED", "If-Match header with version is required");
        }

        // For now, use a stub user. In production, get from SecurityContext
        UUID updatedBy = UUID.randomUUID(); // TODO: Get from SecurityContext

        UpdateWorkLogEntryCommand command = new UpdateWorkLogEntryCommand(
            id,
            request.hours(),
            request.comment(),
            updatedBy,
            version
        );

        workLogService.updateEntry(command);

        // Fetch updated entry to get new version
        WorkLogEntry entry = workLogService.findById(id);
        if (entry == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity
            .noContent()
            .header(HttpHeaders.ETAG, String.valueOf(entry.getVersion()))
            .build();
    }

    /**
     * Delete a work log entry.
     * 
     * DELETE /api/v1/worklog/entries/{id}
     * 
     * @return 204 No Content or 404 Not Found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntry(@PathVariable UUID id) {
        // For now, use a stub user. In production, get from SecurityContext
        UUID deletedBy = UUID.randomUUID(); // TODO: Get from SecurityContext

        DeleteWorkLogEntryCommand command = new DeleteWorkLogEntryCommand(id, deletedBy);
        workLogService.deleteEntry(command);

        return ResponseEntity.noContent().build();
    }

    /**
     * Exception handler for DomainException, specifically handling validation errors
     * like the 24-hour daily limit (DAILY_LIMIT_EXCEEDED).
     * 
     * Returns 422 Unprocessable Entity for validation errors.
     * Returns 400 Bad Request for other domain errors.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException ex) {
        ErrorResponse error = ErrorResponse.of(ex.getErrorCode(), ex.getMessage());
        
        // Use 422 for validation/business rule errors
        // Use 409 for conflict errors (version mismatch)
        // Use 404 for not found errors
        // Use 400 for other domain errors
        HttpStatus status;
        String errorCode = ex.getErrorCode();
        
        if (errorCode.contains("OPTIMISTIC_LOCK") || errorCode.contains("VERSION_MISMATCH")) {
            status = HttpStatus.CONFLICT;
        } else if (errorCode.contains("NOT_FOUND")) {
            status = HttpStatus.NOT_FOUND;
        } else if (errorCode.contains("LIMIT") || 
                   errorCode.contains("VALIDATION") ||
                   errorCode.contains("INVALID") ||
                   errorCode.contains("NEGATIVE") ||
                   errorCode.contains("EXCEEDS") ||
                   errorCode.contains("FUTURE") ||
                   errorCode.contains("TOO_LONG") ||
                   errorCode.contains("NOT_EDITABLE") ||
                   errorCode.contains("NOT_DELETABLE") ||
                   errorCode.contains("INCREMENT")) {
            status = HttpStatus.UNPROCESSABLE_ENTITY;
        } else {
            status = HttpStatus.BAD_REQUEST;
        }

        return ResponseEntity.status(status).body(error);
    }

    /**
     * Converts a WorkLogEntry aggregate to a response DTO.
     */
    private WorkLogEntryResponse toResponse(WorkLogEntry entry) {
        return new WorkLogEntryResponse(
            entry.getId().value(),
            entry.getMemberId().value(),
            entry.getProjectId().value(),
            entry.getDate(),
            entry.getHours().hours(),
            entry.getComment(),
            entry.getStatus().toString(),
            entry.getEnteredBy().value(),
            entry.getCreatedAt(),
            entry.getUpdatedAt(),
            entry.getVersion()
        );
    }
}
