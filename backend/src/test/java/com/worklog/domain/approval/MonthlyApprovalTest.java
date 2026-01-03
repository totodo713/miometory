package com.worklog.domain.approval;

import com.worklog.domain.absence.AbsenceId;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.shared.FiscalMonthPeriod;
import com.worklog.domain.worklog.WorkLogEntryId;
import com.worklog.domain.approval.events.MonthApproved;
import com.worklog.domain.approval.events.MonthRejected;
import com.worklog.domain.approval.events.MonthSubmittedForApproval;
import com.worklog.domain.approval.events.MonthlyApprovalCreated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MonthlyApproval aggregate root.
 * 
 * Tests business logic for:
 * - T125: MonthlyApproval creation, submission, approval, rejection
 * 
 * These are pure unit tests with no dependencies on Spring or database.
 */
@DisplayName("MonthlyApproval aggregate")
class MonthlyApprovalTest {
    
    private MemberId testMemberId;
    private FiscalMonthPeriod fiscalMonth;
    private MemberId submittedBy;
    private MemberId reviewedBy;
    private Set<WorkLogEntryId> workLogEntryIds;
    private Set<AbsenceId> absenceIds;
    
    @BeforeEach
    void setUp() {
        testMemberId = MemberId.of(UUID.randomUUID());
        fiscalMonth = new FiscalMonthPeriod(
            LocalDate.of(2026, 1, 21),
            LocalDate.of(2026, 2, 20)
        );
        submittedBy = testMemberId;
        reviewedBy = MemberId.of(UUID.randomUUID()); // Different member (manager)
        
        workLogEntryIds = new HashSet<>();
        workLogEntryIds.add(WorkLogEntryId.of(UUID.randomUUID()));
        workLogEntryIds.add(WorkLogEntryId.of(UUID.randomUUID()));
        
        absenceIds = new HashSet<>();
        absenceIds.add(AbsenceId.of(UUID.randomUUID()));
    }
    
    @Nested
    @DisplayName("Creation")
    class Creation {
        
        @Test
        @DisplayName("should create new monthly approval with valid data")
        void shouldCreateValidApproval() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            assertNotNull(approval.getId());
            assertEquals(testMemberId, approval.getMemberId());
            assertEquals(fiscalMonth, approval.getFiscalMonth());
            assertEquals(ApprovalStatus.PENDING, approval.getStatus());
            assertNull(approval.getSubmittedAt());
            assertNull(approval.getSubmittedBy());
            assertNull(approval.getReviewedAt());
            assertNull(approval.getReviewedBy());
            assertNull(approval.getRejectionReason());
            assertTrue(approval.getWorkLogEntryIds().isEmpty());
            assertTrue(approval.getAbsenceIds().isEmpty());
        }
        
        @Test
        @DisplayName("should raise MonthlyApprovalCreated event on creation")
        void shouldRaiseCreatedEvent() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            var events = approval.getUncommittedEvents();
            assertEquals(1, events.size());
            assertTrue(events.get(0) instanceof MonthlyApprovalCreated);
            
            MonthlyApprovalCreated event = (MonthlyApprovalCreated) events.get(0);
            assertEquals(approval.getId().value(), event.aggregateId());
            assertEquals(testMemberId.value(), event.memberId());
            assertEquals(fiscalMonth.startDate(), event.fiscalMonthStart());
            assertEquals(fiscalMonth.endDate(), event.fiscalMonthEnd());
        }
        
        @Test
        @DisplayName("should reject null memberId")
        void shouldRejectNullMemberId() {
            assertThrows(NullPointerException.class, () ->
                MonthlyApproval.create(null, fiscalMonth)
            );
        }
        
        @Test
        @DisplayName("should reject null fiscal month")
        void shouldRejectNullFiscalMonth() {
            assertThrows(NullPointerException.class, () ->
                MonthlyApproval.create(testMemberId, null)
            );
        }
    }
    
    @Nested
    @DisplayName("Submission")
    class Submission {
        
        @Test
        @DisplayName("should submit pending approval with entry IDs")
        void shouldSubmitPendingApproval() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            
            assertEquals(ApprovalStatus.SUBMITTED, approval.getStatus());
            assertNotNull(approval.getSubmittedAt());
            assertEquals(submittedBy, approval.getSubmittedBy());
            assertEquals(2, approval.getWorkLogEntryIds().size());
            assertEquals(1, approval.getAbsenceIds().size());
        }
        
        @Test
        @DisplayName("should raise MonthSubmittedForApproval event on submission")
        void shouldRaiseSubmittedEvent() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            
            var events = approval.getUncommittedEvents();
            assertEquals(2, events.size()); // Created + Submitted
            assertTrue(events.get(1) instanceof MonthSubmittedForApproval);
            
            MonthSubmittedForApproval event = (MonthSubmittedForApproval) events.get(1);
            assertEquals(approval.getId().value(), event.aggregateId());
            assertEquals(2, event.workLogEntryIds().size());
            assertEquals(1, event.absenceIds().size());
            assertEquals(submittedBy.value(), event.submittedBy());
        }
        
        @Test
        @DisplayName("should allow resubmission after rejection")
        void shouldAllowResubmissionAfterRejection() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            // First submission
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            
            // Rejection
            approval.reject(reviewedBy, "Needs corrections");
            assertEquals(ApprovalStatus.REJECTED, approval.getStatus());
            
            // Resubmission should succeed
            Set<WorkLogEntryId> newEntryIds = new HashSet<>();
            newEntryIds.add(WorkLogEntryId.of(UUID.randomUUID()));
            
            assertDoesNotThrow(() -> approval.submit(submittedBy, newEntryIds, absenceIds));
            assertEquals(ApprovalStatus.SUBMITTED, approval.getStatus());
        }
        
        @Test
        @DisplayName("should reject submission if already submitted")
        void shouldRejectDoubleSubmission() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            
            assertThrows(DomainException.class, () ->
                approval.submit(submittedBy, workLogEntryIds, absenceIds)
            );
        }
        
        @Test
        @DisplayName("should reject submission if already approved")
        void shouldRejectSubmissionWhenApproved() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            approval.approve(reviewedBy);
            
            Set<WorkLogEntryId> newEntryIds = new HashSet<>();
            newEntryIds.add(WorkLogEntryId.of(UUID.randomUUID()));
            
            assertThrows(DomainException.class, () ->
                approval.submit(submittedBy, newEntryIds, absenceIds)
            );
        }
        
        @Test
        @DisplayName("should reject null submittedBy")
        void shouldRejectNullSubmittedBy() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            assertThrows(NullPointerException.class, () ->
                approval.submit(null, workLogEntryIds, absenceIds)
            );
        }
        
        @Test
        @DisplayName("should reject null workLogEntryIds")
        void shouldRejectNullWorkLogEntryIds() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            assertThrows(NullPointerException.class, () ->
                approval.submit(submittedBy, null, absenceIds)
            );
        }
        
        @Test
        @DisplayName("should reject null absenceIds")
        void shouldRejectNullAbsenceIds() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            assertThrows(NullPointerException.class, () ->
                approval.submit(submittedBy, workLogEntryIds, null)
            );
        }
    }
    
    @Nested
    @DisplayName("Approval")
    class Approval {
        
        @Test
        @DisplayName("should approve submitted month")
        void shouldApproveSubmittedMonth() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            approval.approve(reviewedBy);
            
            assertEquals(ApprovalStatus.APPROVED, approval.getStatus());
            assertNotNull(approval.getReviewedAt());
            assertEquals(reviewedBy, approval.getReviewedBy());
            assertNull(approval.getRejectionReason());
        }
        
        @Test
        @DisplayName("should raise MonthApproved event on approval")
        void shouldRaiseApprovedEvent() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            approval.approve(reviewedBy);
            
            var events = approval.getUncommittedEvents();
            assertEquals(3, events.size()); // Created + Submitted + Approved
            assertTrue(events.get(2) instanceof MonthApproved);
            
            MonthApproved event = (MonthApproved) events.get(2);
            assertEquals(approval.getId().value(), event.aggregateId());
            assertEquals(reviewedBy.value(), event.reviewedBy());
        }
        
        @Test
        @DisplayName("should reject approval if not submitted")
        void shouldRejectApprovalIfNotSubmitted() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            assertThrows(DomainException.class, () ->
                approval.approve(reviewedBy)
            );
        }
        
        @Test
        @DisplayName("should reject approval if already approved")
        void shouldRejectDoubleApproval() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            approval.approve(reviewedBy);
            
            assertThrows(DomainException.class, () ->
                approval.approve(reviewedBy)
            );
        }
        
        @Test
        @DisplayName("should reject null reviewedBy")
        void shouldRejectNullReviewedBy() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            
            assertThrows(NullPointerException.class, () ->
                approval.approve(null)
            );
        }
    }
    
    @Nested
    @DisplayName("Rejection")
    class Rejection {
        
        @Test
        @DisplayName("should reject submitted month with reason")
        void shouldRejectSubmittedMonth() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            
            String reason = "Hours do not match expected allocation";
            approval.reject(reviewedBy, reason);
            
            assertEquals(ApprovalStatus.REJECTED, approval.getStatus());
            assertNotNull(approval.getReviewedAt());
            assertEquals(reviewedBy, approval.getReviewedBy());
            assertEquals(reason, approval.getRejectionReason());
        }
        
        @Test
        @DisplayName("should raise MonthRejected event on rejection")
        void shouldRaiseRejectedEvent() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            
            String reason = "Missing project allocations";
            approval.reject(reviewedBy, reason);
            
            var events = approval.getUncommittedEvents();
            assertEquals(3, events.size()); // Created + Submitted + Rejected
            assertTrue(events.get(2) instanceof MonthRejected);
            
            MonthRejected event = (MonthRejected) events.get(2);
            assertEquals(approval.getId().value(), event.aggregateId());
            assertEquals(reviewedBy.value(), event.reviewedBy());
            assertEquals(reason, event.rejectionReason());
        }
        
        @Test
        @DisplayName("should reject rejection if not submitted")
        void shouldRejectRejectionIfNotSubmitted() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            assertThrows(DomainException.class, () ->
                approval.reject(reviewedBy, "Some reason")
            );
        }
        
        @Test
        @DisplayName("should reject rejection if already approved")
        void shouldRejectRejectionWhenApproved() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            approval.approve(reviewedBy);
            
            assertThrows(DomainException.class, () ->
                approval.reject(reviewedBy, "Too late")
            );
        }
        
        @Test
        @DisplayName("should reject null rejection reason")
        void shouldRejectNullReason() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            
            assertThrows(DomainException.class, () ->
                approval.reject(reviewedBy, null)
            );
        }
        
        @Test
        @DisplayName("should reject empty rejection reason")
        void shouldRejectEmptyReason() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            
            assertThrows(DomainException.class, () ->
                approval.reject(reviewedBy, "   ")
            );
        }
        
        @Test
        @DisplayName("should reject reason exceeding 1000 characters")
        void shouldRejectLongReason() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            
            String longReason = "a".repeat(1001);
            
            assertThrows(DomainException.class, () ->
                approval.reject(reviewedBy, longReason)
            );
        }
        
        @Test
        @DisplayName("should accept reason with exactly 1000 characters")
        void shouldAcceptMaxLengthReason() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            
            String maxReason = "a".repeat(1000);
            
            assertDoesNotThrow(() -> approval.reject(reviewedBy, maxReason));
            assertEquals(ApprovalStatus.REJECTED, approval.getStatus());
        }
        
        @Test
        @DisplayName("should reject null reviewedBy")
        void shouldRejectNullReviewedBy() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            
            assertThrows(NullPointerException.class, () ->
                approval.reject(null, "Some reason")
            );
        }
    }
    
    @Nested
    @DisplayName("Status Transitions")
    class StatusTransitions {
        
        @Test
        @DisplayName("should follow valid state machine: PENDING -> SUBMITTED -> APPROVED")
        void shouldFollowValidPathToApproval() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            assertEquals(ApprovalStatus.PENDING, approval.getStatus());
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            assertEquals(ApprovalStatus.SUBMITTED, approval.getStatus());
            
            approval.approve(reviewedBy);
            assertEquals(ApprovalStatus.APPROVED, approval.getStatus());
        }
        
        @Test
        @DisplayName("should follow valid state machine: PENDING -> SUBMITTED -> REJECTED -> SUBMITTED -> APPROVED")
        void shouldFollowValidPathWithRejection() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            assertEquals(ApprovalStatus.PENDING, approval.getStatus());
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            assertEquals(ApprovalStatus.SUBMITTED, approval.getStatus());
            
            approval.reject(reviewedBy, "Needs review");
            assertEquals(ApprovalStatus.REJECTED, approval.getStatus());
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            assertEquals(ApprovalStatus.SUBMITTED, approval.getStatus());
            
            approval.approve(reviewedBy);
            assertEquals(ApprovalStatus.APPROVED, approval.getStatus());
        }
        
        @Test
        @DisplayName("should be permanently locked after approval")
        void shouldBePermanentlyLockedAfterApproval() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            approval.submit(submittedBy, workLogEntryIds, absenceIds);
            approval.approve(reviewedBy);
            
            // Cannot submit again
            assertThrows(DomainException.class, () ->
                approval.submit(submittedBy, workLogEntryIds, absenceIds)
            );
            
            // Cannot reject
            assertThrows(DomainException.class, () ->
                approval.reject(reviewedBy, "Too late")
            );
            
            // Cannot approve again
            assertThrows(DomainException.class, () ->
                approval.approve(reviewedBy)
            );
        }
    }
    
    @Nested
    @DisplayName("Aggregate Type")
    class AggregateType {
        
        @Test
        @DisplayName("should return correct aggregate type")
        void shouldReturnCorrectAggregateType() {
            MonthlyApproval approval = MonthlyApproval.create(
                testMemberId,
                fiscalMonth
            );
            
            assertEquals("MonthlyApproval", approval.getAggregateType());
        }
    }
}
