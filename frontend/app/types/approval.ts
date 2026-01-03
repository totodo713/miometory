/**
 * TypeScript type definitions for Monthly Approval workflow
 *
 * Mirrors the backend approval DTOs and domain model.
 */

/**
 * Approval status enum matching backend ApprovalStatus
 */
export type ApprovalStatus = "PENDING" | "SUBMITTED" | "APPROVED" | "REJECTED";

/**
 * Request to submit a fiscal month for approval
 */
export interface SubmitMonthRequest {
  memberId: string;
  fiscalMonthStart: string; // ISO date format (YYYY-MM-DD)
  fiscalMonthEnd: string; // ISO date format (YYYY-MM-DD)
  submittedBy?: string; // Defaults to memberId if not provided
}

/**
 * Response after submitting a month
 */
export interface SubmitMonthResponse {
  approvalId: string;
}

/**
 * Individual pending approval in manager's queue
 */
export interface PendingApproval {
  approvalId: string;
  memberId: string;
  memberName: string;
  fiscalMonthStart: string; // ISO date format
  fiscalMonthEnd: string; // ISO date format
  totalWorkHours: number;
  totalAbsenceHours: number;
  submittedAt: string; // ISO timestamp
  submittedByName: string;
}

/**
 * Manager's approval queue response
 */
export interface ApprovalQueueResponse {
  pendingApprovals: PendingApproval[];
  totalCount: number;
}

/**
 * Request to approve a submitted month
 */
export interface ApproveMonthRequest {
  reviewedBy: string;
}

/**
 * Request to reject a submitted month with reason
 */
export interface RejectMonthRequest {
  reviewedBy: string;
  rejectionReason: string; // Required, max 1000 chars
}

/**
 * Approval status data in monthly summary
 */
export interface ApprovalStatusData {
  approvalStatus: ApprovalStatus | null;
  rejectionReason: string | null;
}
