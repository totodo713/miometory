package com.worklog.api;

import com.worklog.api.dto.ApprovalQueueResponse;
import com.worklog.api.dto.ApproveMonthRequest;
import com.worklog.api.dto.MemberApprovalResponse;
import com.worklog.api.dto.RejectMonthRequest;
import com.worklog.api.dto.SubmitMonthRequest;
import com.worklog.application.approval.ApprovalService;
import com.worklog.application.approval.ApproveMonthCommand;
import com.worklog.application.approval.RejectMonthCommand;
import com.worklog.application.approval.SubmitMonthForApprovalCommand;
import com.worklog.domain.approval.MonthlyApproval;
import com.worklog.domain.approval.MonthlyApprovalId;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.shared.FiscalMonthPeriod;
import com.worklog.infrastructure.projection.ApprovalQueueData;
import com.worklog.infrastructure.projection.ApprovalQueueProjection;
import com.worklog.infrastructure.repository.JdbcApprovalRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for monthly approval workflow operations.
 *
 * Endpoints:
 * - POST /api/v1/worklog/submissions - Engineer submits month for approval
 * - GET /api/v1/worklog/approvals/queue - Manager views pending approvals
 * - POST /api/v1/worklog/approvals/{id}/approve - Manager approves submission
 * - POST /api/v1/worklog/approvals/{id}/reject - Manager rejects submission
 */
@RestController
@RequestMapping("/api/v1/worklog")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final ApprovalQueueProjection approvalQueueProjection;
    private final JdbcApprovalRepository approvalRepository;

    public ApprovalController(
            ApprovalService approvalService,
            ApprovalQueueProjection approvalQueueProjection,
            JdbcApprovalRepository approvalRepository) {
        this.approvalService = approvalService;
        this.approvalQueueProjection = approvalQueueProjection;
        this.approvalRepository = approvalRepository;
    }

    /**
     * Submit a month's time entries for manager approval.
     *
     * POST /api/v1/worklog/submissions
     *
     * Request body: { memberId, fiscalMonthStart, fiscalMonthEnd, submittedBy }
     *
     * Process:
     * - Creates or loads MonthlyApproval aggregate
     * - Finds all WorkLogEntry and Absence entries for fiscal month
     * - Transitions all entries to SUBMITTED status (read-only)
     * - Returns approval ID
     *
     * @return 201 Created with approval ID
     */
    @PostMapping("/submissions")
    public ResponseEntity<SubmitMonthResponse> submitMonth(@RequestBody SubmitMonthRequest request) {
        // Validate request
        if (request.memberId() == null) {
            throw new DomainException("MEMBER_ID_REQUIRED", "memberId is required");
        }
        if (request.fiscalMonthStart() == null || request.fiscalMonthEnd() == null) {
            throw new DomainException("FISCAL_MONTH_REQUIRED", "fiscalMonthStart and fiscalMonthEnd are required");
        }

        // For now, use submittedBy from request. In production, get from SecurityContext
        UUID submittedBy = request.submittedBy() != null
                ? request.submittedBy()
                : request.memberId(); // Default to member if not specified

        FiscalMonthPeriod fiscalMonth = new FiscalMonthPeriod(request.fiscalMonthStart(), request.fiscalMonthEnd());

        SubmitMonthForApprovalCommand command = new SubmitMonthForApprovalCommand(
                MemberId.of(request.memberId()), fiscalMonth, MemberId.of(submittedBy));

        MonthlyApprovalId approvalId = approvalService.submitMonth(command);

        SubmitMonthResponse response =
                new SubmitMonthResponse(approvalId.value().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get pending approvals queue for a manager.
     *
     * GET /api/v1/worklog/approvals/queue?managerId=...
     *
     * Returns list of team members who have submitted their time for approval,
     * with summary information (total hours, submission date, etc.)
     *
     * @param managerId Manager ID (optional, defaults to authenticated user)
     * @return 200 OK with list of pending approvals
     */
    @GetMapping("/approvals/queue")
    public ResponseEntity<ApprovalQueueResponse> getApprovalQueue(@RequestParam(required = false) UUID managerId) {
        // For now, if managerId not specified, require it. In production, get from SecurityContext
        if (managerId == null) {
            throw new DomainException("MANAGER_ID_REQUIRED", "managerId parameter is required");
        }

        ApprovalQueueData queueData = approvalQueueProjection.getPendingApprovals(managerId);

        ApprovalQueueResponse response = new ApprovalQueueResponse(
                queueData.pendingApprovals().stream()
                        .map(p -> new ApprovalQueueResponse.PendingApproval(
                                p.approvalId(),
                                p.memberId(),
                                p.memberName(),
                                p.fiscalMonthStart(),
                                p.fiscalMonthEnd(),
                                p.totalWorkHours(),
                                p.totalAbsenceHours(),
                                p.submittedAt(),
                                p.submittedByName()))
                        .toList(),
                queueData.pendingApprovals().size());

        return ResponseEntity.ok(response);
    }

    /**
     * Approve a submitted month.
     *
     * POST /api/v1/worklog/approvals/{id}/approve
     *
     * Request body: { reviewedBy }
     *
     * Process:
     * - Loads MonthlyApproval aggregate
     * - Validates manager permission (TODO)
     * - Transitions all entries to APPROVED status (permanently read-only)
     *
     * @param id Approval ID
     * @return 204 No Content on success
     */
    @PostMapping("/approvals/{id}/approve")
    public ResponseEntity<Void> approveMonth(@PathVariable UUID id, @RequestBody ApproveMonthRequest request) {
        // Validate request
        if (request.reviewedBy() == null) {
            throw new DomainException("REVIEWED_BY_REQUIRED", "reviewedBy is required");
        }

        ApproveMonthCommand command =
                new ApproveMonthCommand(MonthlyApprovalId.of(id), MemberId.of(request.reviewedBy()));

        approvalService.approveMonth(command);

        return ResponseEntity.noContent().build();
    }

    /**
     * Reject a submitted month with a reason.
     *
     * POST /api/v1/worklog/approvals/{id}/reject
     *
     * Request body: { reviewedBy, rejectionReason }
     *
     * Process:
     * - Loads MonthlyApproval aggregate
     * - Validates manager permission (TODO)
     * - Transitions all entries back to DRAFT status (editable)
     * - Stores rejection reason for engineer feedback
     *
     * @param id Approval ID
     * @return 204 No Content on success
     */
    @PostMapping("/approvals/{id}/reject")
    public ResponseEntity<Void> rejectMonth(@PathVariable UUID id, @RequestBody RejectMonthRequest request) {
        // Validate request
        if (request.reviewedBy() == null) {
            throw new DomainException("REVIEWED_BY_REQUIRED", "reviewedBy is required");
        }
        if (request.rejectionReason() == null || request.rejectionReason().isBlank()) {
            throw new DomainException("REJECTION_REASON_REQUIRED", "rejectionReason is required");
        }

        RejectMonthCommand command = new RejectMonthCommand(
                MonthlyApprovalId.of(id), MemberId.of(request.reviewedBy()), request.rejectionReason());

        approvalService.rejectMonth(command);

        return ResponseEntity.noContent().build();
    }

    /**
     * Get approval status for a member's fiscal month.
     *
     * GET /api/v1/worklog/approvals/member/{memberId}?fiscalMonthStart=...&fiscalMonthEnd=...
     *
     * Returns the current approval status including rejection reason if rejected.
     * Used by the member UI to display approval feedback.
     *
     * @param memberId Member ID
     * @param fiscalMonthStart Fiscal month start date
     * @param fiscalMonthEnd Fiscal month end date
     * @return 200 OK with approval details, or 404 if no approval record exists
     */
    @GetMapping("/approvals/member/{memberId}")
    public ResponseEntity<MemberApprovalResponse> getMemberApproval(
            @PathVariable UUID memberId,
            @RequestParam LocalDate fiscalMonthStart,
            @RequestParam LocalDate fiscalMonthEnd) {
        FiscalMonthPeriod fiscalMonth = new FiscalMonthPeriod(fiscalMonthStart, fiscalMonthEnd);

        Optional<MonthlyApproval> approval =
                approvalRepository.findByMemberAndFiscalMonth(MemberId.of(memberId), fiscalMonth);

        if (approval.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        MonthlyApproval a = approval.get();

        MemberApprovalResponse response = new MemberApprovalResponse(
                a.getId().value(),
                a.getMemberId().value(),
                a.getFiscalMonth().startDate(),
                a.getFiscalMonth().endDate(),
                a.getStatus().toString(),
                a.getSubmittedAt(),
                a.getReviewedAt(),
                a.getReviewedBy() != null ? a.getReviewedBy().value() : null,
                null, // TODO: Fetch reviewer name from member repository
                a.getRejectionReason());

        return ResponseEntity.ok(response);
    }

    /**
     * Get detailed monthly approval information including daily approval status.
     *
     * GET /api/v1/worklog/approvals/{id}/detail
     *
     * Returns enriched approval data with project breakdown, absence summary,
     * daily approval status counts, and unresolved daily rejections.
     */
    @GetMapping("/approvals/{id}/detail")
    @PreAuthorize("hasPermission(null, 'monthly_approval.view')")
    public ResponseEntity<ApprovalService.MonthlyApprovalDetail> getApprovalDetail(@PathVariable UUID id) {
        ApprovalService.MonthlyApprovalDetail detail =
                approvalService.getMonthlyApprovalDetail(MonthlyApprovalId.of(id));
        return ResponseEntity.ok(detail);
    }

    /**
     * Response DTO for submit month endpoint.
     */
    public record SubmitMonthResponse(String approvalId) {}
}
