package com.worklog.api;

import com.worklog.api.dto.*;
import com.worklog.application.command.CreateAbsenceCommand;
import com.worklog.application.command.DeleteAbsenceCommand;
import com.worklog.application.command.UpdateAbsenceCommand;
import com.worklog.application.service.AbsenceService;
import com.worklog.domain.absence.Absence;
import com.worklog.domain.absence.AbsenceStatus;
import com.worklog.domain.shared.DomainException;
import com.worklog.infrastructure.repository.JdbcAbsenceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for absence operations.
 *
 * Provides CRUD endpoints for managing absence records (vacation, sick leave, special leave).
 */
@RestController
@RequestMapping("/api/v1/absences")
public class AbsenceController {

    private final AbsenceService absenceService;
    private final JdbcAbsenceRepository absenceRepository;

    public AbsenceController(AbsenceService absenceService, JdbcAbsenceRepository absenceRepository) {
        this.absenceService = absenceService;
        this.absenceRepository = absenceRepository;
    }

    /**
     * Creates a new absence record.
     *
     * POST /api/v1/absences
     *
     * @return 201 Created with absence details and ETag header
     */
    @PostMapping
    public ResponseEntity<AbsenceResponse> createAbsence(@RequestBody CreateAbsenceRequest request) {
        // For now, use recordedBy from request. In production, get from SecurityContext
        UUID recordedBy = request.recordedBy() != null
                ? request.recordedBy()
                : request.memberId(); // Default to member if not specified

        CreateAbsenceCommand command = new CreateAbsenceCommand(
                request.memberId(),
                request.date(),
                request.hours(),
                request.absenceType(),
                request.reason(),
                recordedBy);

        UUID absenceId = absenceService.recordAbsence(command);
        Absence absence = absenceService.findById(absenceId);

        if (absence == null) {
            throw new DomainException("ABSENCE_NOT_FOUND", "Failed to retrieve created absence");
        }

        AbsenceResponse response = toResponse(absence);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.ETAG, String.valueOf(absence.getVersion()))
                .body(response);
    }

    /**
     * Get absences with optional filtering.
     *
     * GET /api/v1/absences?startDate=2026-01-01&endDate=2026-01-31&memberId=...&status=DRAFT
     *
     * @return 200 OK with list of absences
     */
    @GetMapping
    public ResponseEntity<AbsencesResponse> getAbsences(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam(required = false) UUID memberId,
            @RequestParam(required = false) AbsenceStatus status) {
        // For now, if memberId not specified, require it. In production, get from SecurityContext
        if (memberId == null) {
            throw new DomainException("MEMBER_ID_REQUIRED", "memberId parameter is required");
        }

        List<Absence> absences = absenceRepository.findByDateRange(memberId, startDate, endDate, status);

        List<AbsenceResponse> responses =
                absences.stream().map(this::toResponse).collect(Collectors.toList());

        AbsencesResponse response = new AbsencesResponse(responses, responses.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Get a single absence record by ID.
     *
     * GET /api/v1/absences/{id}
     *
     * @return 200 OK with absence details and ETag header, or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<AbsenceResponse> getAbsence(@PathVariable UUID id) {
        Absence absence = absenceService.findById(id);

        if (absence == null) {
            return ResponseEntity.notFound().build();
        }

        AbsenceResponse response = toResponse(absence);

        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, String.valueOf(absence.getVersion()))
                .body(response);
    }

    /**
     * Update an absence record (PATCH).
     *
     * PATCH /api/v1/absences/{id}
     * Requires If-Match header with version for optimistic locking
     *
     * @return 204 No Content with ETag header, 409 Conflict on version mismatch, or 404 Not Found
     */
    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateAbsence(
            @PathVariable UUID id,
            @RequestBody PatchAbsenceRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) Long version) {
        if (version == null) {
            throw new DomainException("VERSION_REQUIRED", "If-Match header with version is required");
        }

        // Fetch existing absence to fill in null values from PATCH request
        Absence existingAbsence = absenceService.findById(id);
        if (existingAbsence == null) {
            return ResponseEntity.notFound().build();
        }

        // For now, use a stub user. In production, get from SecurityContext
        UUID updatedBy = UUID.randomUUID(); // TODO: Get from SecurityContext

        // Use existing values for fields not provided in the request
        UpdateAbsenceCommand command = new UpdateAbsenceCommand(
                id,
                request.hours() != null
                        ? request.hours()
                        : existingAbsence.getHours().hours(),
                request.absenceType() != null
                        ? request.absenceType()
                        : existingAbsence.getAbsenceType().name(),
                request.reason() != null ? request.reason() : existingAbsence.getReason(),
                updatedBy,
                version);

        absenceService.updateAbsence(command);

        // Fetch updated absence to get new version
        Absence absence = absenceService.findById(id);
        if (absence == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent()
                .header(HttpHeaders.ETAG, String.valueOf(absence.getVersion()))
                .build();
    }

    /**
     * Delete an absence record.
     *
     * DELETE /api/v1/absences/{id}
     *
     * @return 204 No Content or 404 Not Found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAbsence(@PathVariable UUID id) {
        // For now, use a stub user. In production, get from SecurityContext
        UUID deletedBy = UUID.randomUUID(); // TODO: Get from SecurityContext

        DeleteAbsenceCommand command = new DeleteAbsenceCommand(id, deletedBy);
        absenceService.deleteAbsence(command);

        return ResponseEntity.noContent().build();
    }

    /**
     * Exception handler for DomainException, specifically handling validation errors
     * like the 24-hour daily limit (DAILY_LIMIT_EXCEEDED) and invalid absence types.
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
        } else if (errorCode.contains("LIMIT")
                || errorCode.contains("VALIDATION")
                || errorCode.contains("INVALID")
                || errorCode.contains("NEGATIVE")
                || errorCode.contains("EXCEEDS")
                || errorCode.contains("FUTURE")
                || errorCode.contains("TOO_LONG")
                || errorCode.contains("NOT_EDITABLE")
                || errorCode.contains("NOT_DELETABLE")
                || errorCode.contains("INCREMENT")) {
            status = HttpStatus.UNPROCESSABLE_ENTITY;
        } else {
            status = HttpStatus.BAD_REQUEST;
        }

        return ResponseEntity.status(status).body(error);
    }

    /**
     * Converts an Absence aggregate to a response DTO.
     */
    private AbsenceResponse toResponse(Absence absence) {
        return new AbsenceResponse(
                absence.getId().value(),
                absence.getMemberId().value(),
                absence.getDate(),
                absence.getHours().hours(),
                absence.getAbsenceType().toString(),
                absence.getReason(),
                absence.getStatus().toString(),
                absence.getRecordedBy().value(),
                absence.getCreatedAt(),
                absence.getUpdatedAt(),
                absence.getVersion());
    }
}
