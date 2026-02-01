package com.worklog.infrastructure.projection;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Projection for manager's approval queue view.
 * 
 * Provides a list of pending monthly approvals that require manager review.
 * Shows team members who have submitted their time entries for a fiscal month,
 * along with summary information to help the manager make approval decisions.
 * 
 * Note: Currently queries event_store directly. Will be optimized to use
 * projection tables once event handlers are implemented.
 */
@Component
public class ApprovalQueueProjection {

    private final JdbcTemplate jdbcTemplate;

    public ApprovalQueueProjection(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Gets pending approvals for a manager to review.
     * 
     * @param managerId Manager ID (currently not filtered by manager relationship)
     * @return Approval queue data with pending approvals
     */
    public ApprovalQueueData getPendingApprovals(UUID managerId) {
        // TODO: Filter by manager relationship when Member aggregate includes manager field
        // For now, returns all pending approvals in the system
        
        List<ApprovalQueueData.PendingApproval> pendingApprovals = getPendingApprovalsList();
        return new ApprovalQueueData(pendingApprovals);
    }

    /**
     * Gets list of all pending monthly approvals with summary information.
     * 
     * @return List of pending approvals
     */
    private List<ApprovalQueueData.PendingApproval> getPendingApprovalsList() {
        String sql = """
            WITH approval_created AS (
                SELECT 
                    aggregate_id,
                    CAST(payload->>'memberId' AS UUID) as member_id,
                    CAST(payload->>'fiscalMonthStart' AS DATE) as fiscal_month_start,
                    CAST(payload->>'fiscalMonthEnd' AS DATE) as fiscal_month_end,
                    created_at
                FROM event_store
                WHERE aggregate_type = 'MonthlyApproval'
                AND event_type = 'MonthlyApprovalCreated'
            ),
            approval_submitted AS (
                SELECT 
                    aggregate_id,
                    CAST(payload->>'submittedAt' AS TIMESTAMP) as submitted_at,
                    CAST(payload->>'submittedBy' AS UUID) as submitted_by
                FROM event_store
                WHERE aggregate_type = 'MonthlyApproval'
                AND event_type = 'MonthSubmittedForApproval'
            ),
            approval_finalized AS (
                SELECT DISTINCT aggregate_id
                FROM event_store
                WHERE aggregate_type = 'MonthlyApproval'
                AND event_type IN ('MonthApproved', 'MonthRejected')
            ),
            pending_approvals AS (
                SELECT 
                    ac.aggregate_id,
                    ac.member_id,
                    ac.fiscal_month_start,
                    ac.fiscal_month_end,
                    asub.submitted_at,
                    asub.submitted_by
                FROM approval_created ac
                INNER JOIN approval_submitted asub ON ac.aggregate_id = asub.aggregate_id
                WHERE ac.aggregate_id NOT IN (SELECT aggregate_id FROM approval_finalized)
            )
            SELECT 
                pa.aggregate_id,
                pa.member_id,
                pa.member_id::text as member_name,
                pa.fiscal_month_start,
                pa.fiscal_month_end,
                pa.submitted_at,
                pa.submitted_by::text as submitted_by_name
            FROM pending_approvals pa
            ORDER BY pa.submitted_at ASC
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        List<ApprovalQueueData.PendingApproval> approvals = new ArrayList<>();
        for (Map<String, Object> row : results) {
            UUID approvalId = (UUID) row.get("aggregate_id");
            UUID memberId = (UUID) row.get("member_id");
            String memberName = (String) row.get("member_name");
            LocalDate fiscalMonthStart = ((java.sql.Date) row.get("fiscal_month_start")).toLocalDate();
            LocalDate fiscalMonthEnd = ((java.sql.Date) row.get("fiscal_month_end")).toLocalDate();
            Timestamp submittedAtTimestamp = (Timestamp) row.get("submitted_at");
            Instant submittedAt = submittedAtTimestamp.toInstant();
            String submittedByName = (String) row.get("submitted_by_name");

            // Calculate total work hours for this fiscal month using projection table
            BigDecimal totalWorkHours = getTotalWorkHours(memberId, fiscalMonthStart, fiscalMonthEnd);

            // Calculate total absence hours for this fiscal month using projection table
            BigDecimal totalAbsenceHours = getTotalAbsenceHours(memberId, fiscalMonthStart, fiscalMonthEnd);

            approvals.add(new ApprovalQueueData.PendingApproval(
                approvalId.toString(),
                memberId.toString(),
                memberName,
                fiscalMonthStart,
                fiscalMonthEnd,
                totalWorkHours,
                totalAbsenceHours,
                submittedAt,
                submittedByName
            ));
        }

        return approvals;
    }

    /**
     * Gets total work hours for a member within a fiscal month.
     * 
     * Uses work_log_entries_projection for efficient aggregation.
     * 
     * @param memberId Member ID
     * @param startDate Fiscal month start date
     * @param endDate Fiscal month end date
     * @return Total work hours
     */
    private BigDecimal getTotalWorkHours(UUID memberId, LocalDate startDate, LocalDate endDate) {
        // Uses idx_work_log_entries_member_date for efficient query
        String sql = """
            SELECT COALESCE(SUM(hours), 0) as total_hours
            FROM work_log_entries_projection
            WHERE member_id = ?
            AND work_date BETWEEN ? AND ?
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
     * Gets total absence hours for a member within a fiscal month.
     * 
     * Uses absences_projection for efficient aggregation.
     * 
     * @param memberId Member ID
     * @param startDate Fiscal month start date
     * @param endDate Fiscal month end date
     * @return Total absence hours
     */
    private BigDecimal getTotalAbsenceHours(UUID memberId, LocalDate startDate, LocalDate endDate) {
        // Uses idx_absences_overlap for efficient overlap detection
        String sql = """
            SELECT 
                id,
                start_date,
                end_date,
                hours_per_day
            FROM absences_projection
            WHERE member_id = ?
            AND start_date <= ?
            AND end_date >= ?
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
            sql,
            memberId,
            endDate,
            startDate
        );

        BigDecimal totalHours = BigDecimal.ZERO;
        
        for (Map<String, Object> row : results) {
            LocalDate absenceStart = ((java.sql.Date) row.get("start_date")).toLocalDate();
            LocalDate absenceEnd = ((java.sql.Date) row.get("end_date")).toLocalDate();
            BigDecimal hoursPerDay = (BigDecimal) row.get("hours_per_day");
            
            // Calculate intersection with requested date range
            LocalDate effectiveStart = absenceStart.isBefore(startDate) ? startDate : absenceStart;
            LocalDate effectiveEnd = absenceEnd.isAfter(endDate) ? endDate : absenceEnd;
            
            // Count days in the effective range
            long days = ChronoUnit.DAYS.between(effectiveStart, effectiveEnd) + 1;
            
            totalHours = totalHours.add(hoursPerDay.multiply(BigDecimal.valueOf(days)));
        }

        return totalHours;
    }
}
