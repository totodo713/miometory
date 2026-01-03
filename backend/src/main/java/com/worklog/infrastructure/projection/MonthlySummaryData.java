package com.worklog.infrastructure.projection;

import java.math.BigDecimal;
import java.util.List;

/**
 * Data transfer object for monthly summary view.
 * 
 * Contains aggregated statistics for a member's work log entries
 * within a specific month, including project-level breakdown and approval status.
 */
public record MonthlySummaryData(
    int year,
    int month,
    BigDecimal totalWorkHours,
    BigDecimal totalAbsenceHours,
    int totalBusinessDays,
    List<ProjectSummary> projects,
    String approvalStatus,  // PENDING, SUBMITTED, APPROVED, REJECTED, or null if no approval record
    String rejectionReason   // Reason if status is REJECTED, null otherwise
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
