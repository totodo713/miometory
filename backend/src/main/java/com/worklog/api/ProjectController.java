package com.worklog.api;

import com.worklog.api.dto.PreviousMonthProjectsResponse;
import com.worklog.application.command.CopyFromPreviousMonthCommand;
import com.worklog.application.service.WorkLogEntryService;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.shared.FiscalMonthPeriod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for project-related operations.
 * 
 * Provides endpoints for retrieving project information,
 * including the "Copy from Previous Month" feature.
 */
@RestController
@RequestMapping("/api/v1/worklog/projects")
public class ProjectController {

    private final WorkLogEntryService workLogEntryService;

    public ProjectController(WorkLogEntryService workLogEntryService) {
        this.workLogEntryService = workLogEntryService;
    }

    /**
     * Get unique projects from the previous fiscal month.
     * 
     * This endpoint supports the "Copy from Previous Month" feature (FR-016),
     * allowing users to quickly populate a new month with projects they worked on previously.
     * 
     * GET /api/v1/worklog/projects/previous-month?year=2026&month=2&memberId=...
     * 
     * @param year Target year (the year of the month the user wants to populate)
     * @param month Target month (1-12, the month the user wants to populate)
     * @param memberId Member ID to get previous projects for
     * @return List of unique project IDs from the previous fiscal month
     */
    @GetMapping("/previous-month")
    public ResponseEntity<PreviousMonthProjectsResponse> getPreviousMonthProjects(
        @RequestParam int year,
        @RequestParam int month,
        @RequestParam UUID memberId
    ) {
        // Validate inputs
        if (month < 1 || month > 12) {
            throw new DomainException("INVALID_MONTH", "Month must be between 1 and 12");
        }
        if (year < 2000 || year > 2100) {
            throw new DomainException("INVALID_YEAR", "Year must be between 2000 and 2100");
        }

        CopyFromPreviousMonthCommand command = new CopyFromPreviousMonthCommand(
            memberId,
            year,
            month
        );

        List<UUID> projectIds = workLogEntryService.getProjectsFromPreviousMonth(command);
        FiscalMonthPeriod previousPeriod = workLogEntryService.getPreviousFiscalMonth(year, month);

        PreviousMonthProjectsResponse response = new PreviousMonthProjectsResponse(
            projectIds,
            previousPeriod.startDate(),
            previousPeriod.endDate(),
            projectIds.size()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Exception handler for DomainException.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException ex) {
        ErrorResponse error = ErrorResponse.of(ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.badRequest().body(error);
    }
}
