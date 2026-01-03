package com.worklog.infrastructure.projection;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Projection for manager's approval queue view.
 * 
 * Provides a list of pending monthly approvals that require manager review.
 * Shows team members who have submitted their time entries for a fiscal month,
 * along with summary information to help the manager make approval decisions.
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
                    occurred_at
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
                COALESCE(m.name, 'Unknown Member') as member_name,
                pa.fiscal_month_start,
                pa.fiscal_month_end,
                pa.submitted_at,
                COALESCE(sm.name, 'Unknown') as submitted_by_name
            FROM pending_approvals pa
            LEFT JOIN members m ON pa.member_id = m.id
            LEFT JOIN members sm ON pa.submitted_by = sm.id
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

            // Calculate total work hours for this fiscal month
            BigDecimal totalWorkHours = getTotalWorkHours(memberId, fiscalMonthStart, fiscalMonthEnd);

            // Calculate total absence hours for this fiscal month
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
     * @param memberId Member ID
     * @param startDate Fiscal month start date
     * @param endDate Fiscal month end date
     * @return Total work hours
     */
    private BigDecimal getTotalWorkHours(UUID memberId, LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT COALESCE(SUM(CAST(e.payload->>'hours' AS DECIMAL)), 0) as total_hours
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
     * @param memberId Member ID
     * @param startDate Fiscal month start date
     * @param endDate Fiscal month end date
     * @return Total absence hours
     */
    private BigDecimal getTotalAbsenceHours(UUID memberId, LocalDate startDate, LocalDate endDate) {
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
}
