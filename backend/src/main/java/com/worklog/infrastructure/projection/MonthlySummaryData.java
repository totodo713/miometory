package com.worklog.infrastructure.projection;

import java.math.BigDecimal;
import java.util.List;

/**
 * Data transfer object for monthly summary view.
 * 
 * Contains aggregated statistics for a member's work log entries
 * within a specific month, including project-level breakdown.
 */
public record MonthlySummaryData(
    int year,
    int month,
    BigDecimal totalWorkHours,
    BigDecimal totalAbsenceHours,
    int totalBusinessDays,
    List<ProjectSummary> projects
) {
    /**
     * Individual project summary within a month.
     */
    public record ProjectSummary(
        String projectId,
        String projectName,
        BigDecimal totalHours,
        BigDecimal percentage
    ) {}
}
