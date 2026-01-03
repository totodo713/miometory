package com.worklog.infrastructure.projection;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Projection for monthly summary view.
 * 
 * Provides aggregated statistics for a member's work entries and absences within a month,
 * including project-level breakdown with hours and percentages.
 */
@Component
public class MonthlySummaryProjection {

    private final JdbcTemplate jdbcTemplate;

    public MonthlySummaryProjection(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Gets monthly summary for a member.
     * 
     * @param memberId Member ID
     * @param year Year
     * @param month Month (1-12)
     * @return Monthly summary data including project breakdown
     */
    public MonthlySummaryData getMonthlySummary(UUID memberId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // Get project breakdown
        List<MonthlySummaryData.ProjectSummary> projects = getProjectSummaries(memberId, startDate, endDate);

        // Calculate total work hours
        BigDecimal totalWorkHours = projects.stream()
            .map(MonthlySummaryData.ProjectSummary::totalHours)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate business days (excluding weekends)
        int totalBusinessDays = countBusinessDays(startDate, endDate);

        // Get total absence hours for the month
        BigDecimal totalAbsenceHours = getTotalAbsenceHours(memberId, startDate, endDate);

        // Get approval status for the month
        ApprovalStatusData approvalStatus = getApprovalStatus(memberId, startDate, endDate);

        return new MonthlySummaryData(
            year,
            month,
            totalWorkHours,
            totalAbsenceHours,
            totalBusinessDays,
            projects,
            approvalStatus.status(),
            approvalStatus.rejectionReason()
        );
    }

    /**
     * Gets project-level summaries with hours and percentages.
     * 
     * @param memberId Member ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of project summaries
     */
    private List<MonthlySummaryData.ProjectSummary> getProjectSummaries(
        UUID memberId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        String sql = """
            WITH project_hours AS (
                SELECT 
                    CAST(e.payload->>'projectId' AS UUID) as project_id,
                    SUM(CAST(e.payload->>'hours' AS DECIMAL)) as total_hours
                FROM event_store e
                WHERE e.aggregate_type = 'WorkLogEntry'
                AND e.event_type = 'WorkLogEntryCreated'
                AND CAST(e.payload->>'memberId' AS UUID) = ?
                AND CAST(e.payload->>'date' AS DATE) BETWEEN ? AND ?
                AND e.aggregate_id NOT IN (
                    SELECT aggregate_id 
                    FROM event_store 
                    WHERE aggregate_type = 'WorkLogEntry'
                    AND event_type = 'WorkLogEntryDeleted'
                )
                GROUP BY project_id
            ),
            total AS (
                SELECT COALESCE(SUM(total_hours), 0) as sum
                FROM project_hours
            )
            SELECT 
                ph.project_id,
                p.name as project_name,
                ph.total_hours,
                CASE 
                    WHEN t.sum > 0 THEN (ph.total_hours / t.sum * 100)
                    ELSE 0
                END as percentage
            FROM project_hours ph
            CROSS JOIN total t
            LEFT JOIN projects p ON ph.project_id = p.id
            ORDER BY ph.total_hours DESC
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
            sql,
            memberId,
            startDate,
            endDate
        );

        List<MonthlySummaryData.ProjectSummary> summaries = new ArrayList<>();
        for (Map<String, Object> row : results) {
            UUID projectId = (UUID) row.get("project_id");
            String projectName = (String) row.get("project_name");
            BigDecimal totalHours = (BigDecimal) row.get("total_hours");
            BigDecimal percentage = (BigDecimal) row.get("percentage");

            // Handle case where project might have been deleted
            if (projectName == null) {
                projectName = "Unknown Project";
            }

            // Round to 2 decimal places
            totalHours = totalHours.setScale(2, RoundingMode.HALF_UP);
            percentage = percentage.setScale(2, RoundingMode.HALF_UP);

            summaries.add(new MonthlySummaryData.ProjectSummary(
                projectId.toString(),
                projectName,
                totalHours,
                percentage
            ));
        }

        return summaries;
    }

    /**
     * Gets total absence hours for a member within a date range.
     * 
     * @param memberId Member ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Total absence hours
     */
    private BigDecimal getTotalAbsenceHours(
        UUID memberId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        String sql = """
            SELECT COALESCE(SUM(CAST(e.payload->>'hours' AS DECIMAL)), 0) as total_hours
            FROM event_store e
            WHERE e.aggregate_type = 'Absence'
            AND e.event_type = 'AbsenceRecorded'
            AND CAST(e.payload->>'memberId' AS UUID) = ?
            AND CAST(e.payload->>'date' AS DATE) BETWEEN ? AND ?
            AND e.aggregate_id NOT IN (
                SELECT aggregate_id 
                FROM event_store 
                WHERE aggregate_type = 'Absence'
                AND event_type = 'AbsenceDeleted'
            )
            """;

        BigDecimal total = jdbcTemplate.queryForObject(
            sql,
            BigDecimal.class,
            memberId,
            startDate,
            endDate
        );

        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Gets approval status for a member's month.
     * 
     * @param memberId Member ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Approval status data (status and rejection reason)
     */
    private ApprovalStatusData getApprovalStatus(
        UUID memberId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        String sql = """
            WITH latest_approval AS (
                SELECT 
                    aggregate_id,
                    occurred_at
                FROM event_store
                WHERE aggregate_type = 'MonthlyApproval'
                AND event_type = 'MonthlyApprovalCreated'
                AND CAST(payload->>'memberId' AS UUID) = ?
                AND CAST(payload->>'fiscalMonthStart' AS DATE) = ?
                AND CAST(payload->>'fiscalMonthEnd' AS DATE) = ?
                ORDER BY occurred_at DESC
                LIMIT 1
            ),
            latest_status AS (
                SELECT 
                    e.event_type,
                    e.payload,
                    e.occurred_at
                FROM event_store e
                INNER JOIN latest_approval la ON e.aggregate_id = la.aggregate_id
                WHERE e.aggregate_type = 'MonthlyApproval'
                AND e.event_type IN ('MonthlyApprovalCreated', 'MonthSubmittedForApproval', 'MonthApproved', 'MonthRejected')
                ORDER BY e.occurred_at DESC
                LIMIT 1
            )
            SELECT 
                event_type,
                payload->>'rejectionReason' as rejection_reason
            FROM latest_status
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
            sql,
            memberId,
            startDate,
            endDate
        );

        if (results.isEmpty()) {
            return new ApprovalStatusData(null, null);
        }

        Map<String, Object> row = results.get(0);
        String eventType = (String) row.get("event_type");
        String rejectionReason = (String) row.get("rejection_reason");

        String status = switch (eventType) {
            case "MonthlyApprovalCreated" -> "PENDING";
            case "MonthSubmittedForApproval" -> "SUBMITTED";
            case "MonthApproved" -> "APPROVED";
            case "MonthRejected" -> "REJECTED";
            default -> null;
        };

        return new ApprovalStatusData(status, rejectionReason);
    }

    /**
     * Helper record for approval status data.
     */
    private record ApprovalStatusData(String status, String rejectionReason) {}

    /**
     * Counts business days (Monday-Friday) in a date range.
     * 
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Number of business days
     */
    private int countBusinessDays(LocalDate startDate, LocalDate endDate) {
        int count = 0;
        LocalDate current = startDate;
        
        while (!current.isAfter(endDate)) {
            DayOfWeek day = current.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                count++;
            }
            current = current.plusDays(1);
        }
        
        return count;
    }
}
