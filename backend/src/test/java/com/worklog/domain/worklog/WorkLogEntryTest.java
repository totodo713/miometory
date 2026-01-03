package com.worklog.domain.worklog;

import com.worklog.domain.member.MemberId;
import com.worklog.domain.project.ProjectId;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.shared.TimeAmount;
import com.worklog.domain.worklog.events.WorkLogEntryCreated;
import com.worklog.domain.worklog.events.WorkLogEntryDeleted;
import com.worklog.domain.worklog.events.WorkLogEntryStatusChanged;
import com.worklog.domain.worklog.events.WorkLogEntryUpdated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WorkLogEntry aggregate root.
 * 
 * Tests business logic for:
 * - T058: WorkLogEntry creation, updates, deletion, and status transitions
 * 
 * These are pure unit tests with no dependencies on Spring or database.
 */
@DisplayName("WorkLogEntry aggregate")
class WorkLogEntryTest {
    
    private MemberId testMemberId;
    private ProjectId testProjectId;
    private LocalDate testDate;
    private TimeAmount testHours;
    private MemberId enteredBy;
    
    @BeforeEach
    void setUp() {
        testMemberId = MemberId.of(UUID.randomUUID());
        testProjectId = ProjectId.of(UUID.randomUUID());
        testDate = LocalDate.now().minusDays(1); // Yesterday (valid)
        testHours = TimeAmount.of(8.0);
        enteredBy = testMemberId; // Same member by default
    }
    
    @Nested
    @DisplayName("Creation")
    class Creation {
        
        @Test
        @DisplayName("should create new work log entry with valid data")
        void shouldCreateValidEntry() {
            WorkLogEntry entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                "Test comment",
                enteredBy
            );
            
            assertNotNull(entry.getId());
            assertEquals(testMemberId, entry.getMemberId());
            assertEquals(testProjectId, entry.getProjectId());
            assertEquals(testDate, entry.getDate());
            assertEquals(testHours, entry.getHours());
            assertEquals("Test comment", entry.getComment());
            assertEquals(WorkLogStatus.DRAFT, entry.getStatus());
            assertEquals(enteredBy, entry.getEnteredBy());
            assertNotNull(entry.getCreatedAt());
            assertNotNull(entry.getUpdatedAt());
            assertEquals(entry.getCreatedAt(), entry.getUpdatedAt());
        }
        
        @Test
        @DisplayName("should create entry with null comment")
        void shouldCreateWithNullComment() {
            WorkLogEntry entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                null,
                enteredBy
            );
            
            assertNull(entry.getComment());
        }
        
        @Test
        @DisplayName("should create entry with empty comment")
        void shouldCreateWithEmptyComment() {
            WorkLogEntry entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                "",
                enteredBy
            );
            
            assertEquals("", entry.getComment());
        }
        
        @Test
        @DisplayName("should create entry for today")
        void shouldCreateEntryForToday() {
            LocalDate today = LocalDate.now();
            
            WorkLogEntry entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                today,
                testHours,
                "Today's work",
                enteredBy
            );
            
            assertEquals(today, entry.getDate());
        }
        
        @Test
        @DisplayName("should raise WorkLogEntryCreated event")
        void shouldRaiseCreatedEvent() {
            WorkLogEntry entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                "Test comment",
                enteredBy
            );
            
            List<DomainEvent> events = entry.getUncommittedEvents();
            assertEquals(1, events.size());
            
            DomainEvent event = events.get(0);
            assertInstanceOf(WorkLogEntryCreated.class, event);
            
            WorkLogEntryCreated createdEvent = (WorkLogEntryCreated) event;
            assertEquals(entry.getId().value(), createdEvent.aggregateId());
            assertEquals(testMemberId.value(), createdEvent.memberId());
            assertEquals(testProjectId.value(), createdEvent.projectId());
            assertEquals(testDate, createdEvent.date());
            assertEquals(testHours.hours().doubleValue(), createdEvent.hours());
            assertEquals("Test comment", createdEvent.comment());
            assertEquals(enteredBy.value(), createdEvent.enteredBy());
        }
        
        @Test
        @DisplayName("should throw exception for null date")
        void shouldRejectNullDate() {
            DomainException exception = assertThrows(
                DomainException.class,
                () -> WorkLogEntry.create(
                    testMemberId,
                    testProjectId,
                    null,
                    testHours,
                    "Comment",
                    enteredBy
                )
            );
            
            assertEquals("DATE_REQUIRED", exception.getErrorCode());
            assertEquals("Date cannot be null", exception.getMessage());
        }
        
        @Test
        @DisplayName("should throw exception for future date")
        void shouldRejectFutureDate() {
            LocalDate futureDate = LocalDate.now().plusDays(1);
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> WorkLogEntry.create(
                    testMemberId,
                    testProjectId,
                    futureDate,
                    testHours,
                    "Comment",
                    enteredBy
                )
            );
            
            assertEquals("DATE_IN_FUTURE", exception.getErrorCode());
            assertEquals("Date cannot be in the future", exception.getMessage());
        }
        
        @Test
        @DisplayName("should throw exception for comment exceeding 500 characters")
        void shouldRejectLongComment() {
            String longComment = "x".repeat(501);
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> WorkLogEntry.create(
                    testMemberId,
                    testProjectId,
                    testDate,
                    testHours,
                    longComment,
                    enteredBy
                )
            );
            
            assertEquals("COMMENT_TOO_LONG", exception.getErrorCode());
            assertEquals("Comment cannot exceed 500 characters", exception.getMessage());
        }
        
        @Test
        @DisplayName("should accept comment with exactly 500 characters")
        void shouldAcceptMaxLengthComment() {
            String maxComment = "x".repeat(500);
            
            WorkLogEntry entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                maxComment,
                enteredBy
            );
            
            assertEquals(maxComment, entry.getComment());
        }
    }
    
    @Nested
    @DisplayName("Update")
    class Update {
        
        private WorkLogEntry entry;
        private MemberId updater;
        
        @BeforeEach
        void setUp() {
            entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                "Original comment",
                enteredBy
            );
            entry.clearUncommittedEvents();
            updater = testMemberId;
        }
        
        @Test
        @DisplayName("should update hours and comment in DRAFT status")
        void shouldUpdateDraftEntry() {
            TimeAmount newHours = TimeAmount.of(6.5);
            String newComment = "Updated comment";
            
            entry.update(newHours, newComment, updater);
            
            assertEquals(newHours, entry.getHours());
            assertEquals(newComment, entry.getComment());
        }
        
        @Test
        @DisplayName("should raise WorkLogEntryUpdated event")
        void shouldRaiseUpdatedEvent() {
            TimeAmount newHours = TimeAmount.of(6.5);
            String newComment = "Updated comment";
            
            entry.update(newHours, newComment, updater);
            
            List<DomainEvent> events = entry.getUncommittedEvents();
            assertEquals(1, events.size());
            
            DomainEvent event = events.get(0);
            assertInstanceOf(WorkLogEntryUpdated.class, event);
            
            WorkLogEntryUpdated updatedEvent = (WorkLogEntryUpdated) event;
            assertEquals(entry.getId().value(), updatedEvent.aggregateId());
            assertEquals(newHours.hours().doubleValue(), updatedEvent.hours());
            assertEquals(newComment, updatedEvent.comment());
            assertEquals(updater.value(), updatedEvent.updatedBy());
        }
        
        @Test
        @DisplayName("should update with null comment")
        void shouldUpdateWithNullComment() {
            entry.update(testHours, null, updater);
            
            assertNull(entry.getComment());
        }
        
        @Test
        @DisplayName("should throw exception when updating SUBMITTED entry")
        void shouldRejectUpdateOfSubmittedEntry() {
            entry.changeStatus(WorkLogStatus.SUBMITTED, updater);
            entry.clearUncommittedEvents();
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> entry.update(TimeAmount.of(7.0), "New comment", updater)
            );
            
            assertEquals("ENTRY_NOT_EDITABLE", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("SUBMITTED"));
        }
        
        @Test
        @DisplayName("should throw exception when updating APPROVED entry")
        void shouldRejectUpdateOfApprovedEntry() {
            entry.changeStatus(WorkLogStatus.SUBMITTED, updater);
            entry.clearUncommittedEvents();
            entry.changeStatus(WorkLogStatus.APPROVED, updater);
            entry.clearUncommittedEvents();
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> entry.update(TimeAmount.of(7.0), "New comment", updater)
            );
            
            assertEquals("ENTRY_NOT_EDITABLE", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("APPROVED"));
        }
        
        @Test
        @DisplayName("should throw exception for comment exceeding 500 characters")
        void shouldRejectLongCommentOnUpdate() {
            String longComment = "x".repeat(501);
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> entry.update(testHours, longComment, updater)
            );
            
            assertEquals("COMMENT_TOO_LONG", exception.getErrorCode());
        }
    }
    
    @Nested
    @DisplayName("Status transitions")
    class StatusTransitions {
        
        private WorkLogEntry entry;
        private MemberId statusChanger;
        
        @BeforeEach
        void setUp() {
            entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                "Test comment",
                enteredBy
            );
            entry.clearUncommittedEvents();
            statusChanger = testMemberId;
        }
        
        @Test
        @DisplayName("should transition from DRAFT to SUBMITTED")
        void shouldTransitionDraftToSubmitted() {
            entry.changeStatus(WorkLogStatus.SUBMITTED, statusChanger);
            
            assertEquals(WorkLogStatus.SUBMITTED, entry.getStatus());
        }
        
        @Test
        @DisplayName("should transition from SUBMITTED to APPROVED")
        void shouldTransitionSubmittedToApproved() {
            entry.changeStatus(WorkLogStatus.SUBMITTED, statusChanger);
            entry.clearUncommittedEvents();
            
            entry.changeStatus(WorkLogStatus.APPROVED, statusChanger);
            
            assertEquals(WorkLogStatus.APPROVED, entry.getStatus());
        }
        
        @Test
        @DisplayName("should transition from SUBMITTED to REJECTED")
        void shouldTransitionSubmittedToRejected() {
            entry.changeStatus(WorkLogStatus.SUBMITTED, statusChanger);
            entry.clearUncommittedEvents();
            
            entry.changeStatus(WorkLogStatus.REJECTED, statusChanger);
            
            assertEquals(WorkLogStatus.REJECTED, entry.getStatus());
        }
        
        @Test
        @DisplayName("should transition from REJECTED to DRAFT")
        void shouldTransitionRejectedToDraft() {
            entry.changeStatus(WorkLogStatus.SUBMITTED, statusChanger);
            entry.clearUncommittedEvents();
            entry.changeStatus(WorkLogStatus.REJECTED, statusChanger);
            entry.clearUncommittedEvents();
            
            entry.changeStatus(WorkLogStatus.DRAFT, statusChanger);
            
            assertEquals(WorkLogStatus.DRAFT, entry.getStatus());
        }
        
        @Test
        @DisplayName("should raise WorkLogEntryStatusChanged event")
        void shouldRaiseStatusChangedEvent() {
            entry.changeStatus(WorkLogStatus.SUBMITTED, statusChanger);
            
            List<DomainEvent> events = entry.getUncommittedEvents();
            assertEquals(1, events.size());
            
            DomainEvent event = events.get(0);
            assertInstanceOf(WorkLogEntryStatusChanged.class, event);
            
            WorkLogEntryStatusChanged statusEvent = (WorkLogEntryStatusChanged) event;
            assertEquals(entry.getId().value(), statusEvent.aggregateId());
            assertEquals("DRAFT", statusEvent.fromStatus());
            assertEquals("SUBMITTED", statusEvent.toStatus());
            assertEquals(statusChanger.value(), statusEvent.changedBy());
        }
        
        @Test
        @DisplayName("should throw exception for invalid transition: DRAFT to APPROVED")
        void shouldRejectDraftToApproved() {
            DomainException exception = assertThrows(
                DomainException.class,
                () -> entry.changeStatus(WorkLogStatus.APPROVED, statusChanger)
            );
            
            assertEquals("INVALID_STATUS_TRANSITION", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("DRAFT"));
            assertTrue(exception.getMessage().contains("APPROVED"));
        }
        
        @Test
        @DisplayName("should throw exception for invalid transition: DRAFT to REJECTED")
        void shouldRejectDraftToRejected() {
            DomainException exception = assertThrows(
                DomainException.class,
                () -> entry.changeStatus(WorkLogStatus.REJECTED, statusChanger)
            );
            
            assertEquals("INVALID_STATUS_TRANSITION", exception.getErrorCode());
        }
        
        @Test
        @DisplayName("should throw exception for invalid transition: APPROVED to any status")
        void shouldRejectApprovedTransition() {
            entry.changeStatus(WorkLogStatus.SUBMITTED, statusChanger);
            entry.clearUncommittedEvents();
            entry.changeStatus(WorkLogStatus.APPROVED, statusChanger);
            entry.clearUncommittedEvents();
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> entry.changeStatus(WorkLogStatus.DRAFT, statusChanger)
            );
            
            assertEquals("INVALID_STATUS_TRANSITION", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("APPROVED"));
        }
        
        @Test
        @DisplayName("should throw exception for invalid transition: SUBMITTED to DRAFT")
        void shouldRejectSubmittedToDraft() {
            entry.changeStatus(WorkLogStatus.SUBMITTED, statusChanger);
            entry.clearUncommittedEvents();
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> entry.changeStatus(WorkLogStatus.DRAFT, statusChanger)
            );
            
            assertEquals("INVALID_STATUS_TRANSITION", exception.getErrorCode());
        }
        
        @Test
        @DisplayName("should throw exception for invalid transition: REJECTED to APPROVED")
        void shouldRejectRejectedToApproved() {
            entry.changeStatus(WorkLogStatus.SUBMITTED, statusChanger);
            entry.clearUncommittedEvents();
            entry.changeStatus(WorkLogStatus.REJECTED, statusChanger);
            entry.clearUncommittedEvents();
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> entry.changeStatus(WorkLogStatus.APPROVED, statusChanger)
            );
            
            assertEquals("INVALID_STATUS_TRANSITION", exception.getErrorCode());
        }
    }
    
    @Nested
    @DisplayName("Deletion")
    class Deletion {
        
        private WorkLogEntry entry;
        private MemberId deleter;
        
        @BeforeEach
        void setUp() {
            entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                "Test comment",
                enteredBy
            );
            entry.clearUncommittedEvents();
            deleter = testMemberId;
        }
        
        @Test
        @DisplayName("should delete entry in DRAFT status")
        void shouldDeleteDraftEntry() {
            entry.delete(deleter);
            
            // Deletion is recorded as an event
            List<DomainEvent> events = entry.getUncommittedEvents();
            assertEquals(1, events.size());
            assertInstanceOf(WorkLogEntryDeleted.class, events.get(0));
        }
        
        @Test
        @DisplayName("should raise WorkLogEntryDeleted event")
        void shouldRaiseDeletedEvent() {
            entry.delete(deleter);
            
            List<DomainEvent> events = entry.getUncommittedEvents();
            DomainEvent event = events.get(0);
            
            WorkLogEntryDeleted deletedEvent = (WorkLogEntryDeleted) event;
            assertEquals(entry.getId().value(), deletedEvent.aggregateId());
            assertEquals(deleter.value(), deletedEvent.deletedBy());
        }
        
        @Test
        @DisplayName("should throw exception when deleting SUBMITTED entry")
        void shouldRejectDeleteOfSubmittedEntry() {
            entry.changeStatus(WorkLogStatus.SUBMITTED, deleter);
            entry.clearUncommittedEvents();
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> entry.delete(deleter)
            );
            
            assertEquals("ENTRY_NOT_DELETABLE", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("SUBMITTED"));
        }
        
        @Test
        @DisplayName("should throw exception when deleting APPROVED entry")
        void shouldRejectDeleteOfApprovedEntry() {
            entry.changeStatus(WorkLogStatus.SUBMITTED, deleter);
            entry.clearUncommittedEvents();
            entry.changeStatus(WorkLogStatus.APPROVED, deleter);
            entry.clearUncommittedEvents();
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> entry.delete(deleter)
            );
            
            assertEquals("ENTRY_NOT_DELETABLE", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("APPROVED"));
        }
        
        @Test
        @DisplayName("should throw exception when deleting REJECTED entry")
        void shouldRejectDeleteOfRejectedEntry() {
            entry.changeStatus(WorkLogStatus.SUBMITTED, deleter);
            entry.clearUncommittedEvents();
            entry.changeStatus(WorkLogStatus.REJECTED, deleter);
            entry.clearUncommittedEvents();
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> entry.delete(deleter)
            );
            
            assertEquals("ENTRY_NOT_DELETABLE", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("REJECTED"));
        }
    }
    
    @Nested
    @DisplayName("Business rules")
    class BusinessRules {
        
        @Test
        @DisplayName("should identify entry as editable when DRAFT")
        void shouldIdentifyDraftAsEditable() {
            WorkLogEntry entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                "Comment",
                enteredBy
            );
            
            assertTrue(entry.isEditable());
        }
        
        @Test
        @DisplayName("should identify entry as not editable when SUBMITTED")
        void shouldIdentifySubmittedAsNotEditable() {
            WorkLogEntry entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                "Comment",
                enteredBy
            );
            entry.clearUncommittedEvents();
            entry.changeStatus(WorkLogStatus.SUBMITTED, testMemberId);
            
            assertFalse(entry.isEditable());
        }
        
        @Test
        @DisplayName("should identify entry as not editable when APPROVED")
        void shouldIdentifyApprovedAsNotEditable() {
            WorkLogEntry entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                "Comment",
                enteredBy
            );
            entry.clearUncommittedEvents();
            entry.changeStatus(WorkLogStatus.SUBMITTED, testMemberId);
            entry.clearUncommittedEvents();
            entry.changeStatus(WorkLogStatus.APPROVED, testMemberId);
            
            assertFalse(entry.isEditable());
        }
        
        @Test
        @DisplayName("should identify entry as deletable when DRAFT")
        void shouldIdentifyDraftAsDeletable() {
            WorkLogEntry entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                "Comment",
                enteredBy
            );
            
            assertTrue(entry.isDeletable());
        }
        
        @Test
        @DisplayName("should identify entry as not deletable when SUBMITTED")
        void shouldIdentifySubmittedAsNotDeletable() {
            WorkLogEntry entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                "Comment",
                enteredBy
            );
            entry.clearUncommittedEvents();
            entry.changeStatus(WorkLogStatus.SUBMITTED, testMemberId);
            
            assertFalse(entry.isDeletable());
        }
        
        @Test
        @DisplayName("should identify self-entry (not proxy)")
        void shouldIdentifySelfEntry() {
            WorkLogEntry entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                "Comment",
                testMemberId // Same as memberId
            );
            
            assertFalse(entry.isProxyEntry());
        }
        
        @Test
        @DisplayName("should identify proxy entry")
        void shouldIdentifyProxyEntry() {
            MemberId differentMember = MemberId.of(UUID.randomUUID());
            
            WorkLogEntry entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                "Proxy entry",
                differentMember // Different from memberId
            );
            
            assertTrue(entry.isProxyEntry());
        }
    }
    
    @Nested
    @DisplayName("Aggregate metadata")
    class AggregateMetadata {
        
        @Test
        @DisplayName("should have correct aggregate type")
        void shouldHaveCorrectAggregateType() {
            WorkLogEntry entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                "Comment",
                enteredBy
            );
            
            assertEquals("WorkLogEntry", entry.getAggregateType());
        }
        
        @Test
        @DisplayName("should start with version 0")
        void shouldStartWithVersionZero() {
            WorkLogEntry entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                "Comment",
                enteredBy
            );
            
            assertEquals(0, entry.getVersion());
        }
        
        @Test
        @DisplayName("should track uncommitted events")
        void shouldTrackUncommittedEvents() {
            WorkLogEntry entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                "Comment",
                enteredBy
            );
            
            // After creation, one event
            assertEquals(1, entry.getUncommittedEvents().size());
            
            entry.clearUncommittedEvents();
            assertEquals(0, entry.getUncommittedEvents().size());
            
            // After update, one new event
            entry.update(TimeAmount.of(7.0), "Updated", testMemberId);
            assertEquals(1, entry.getUncommittedEvents().size());
        }
        
        @Test
        @DisplayName("should update updatedAt timestamp on changes")
        void shouldUpdateTimestamp() throws InterruptedException {
            WorkLogEntry entry = WorkLogEntry.create(
                testMemberId,
                testProjectId,
                testDate,
                testHours,
                "Comment",
                enteredBy
            );
            entry.clearUncommittedEvents();
            
            var originalUpdatedAt = entry.getUpdatedAt();
            
            // Small delay to ensure timestamp changes
            Thread.sleep(10);
            
            entry.update(TimeAmount.of(7.0), "Updated", testMemberId);
            
            assertTrue(entry.getUpdatedAt().isAfter(originalUpdatedAt));
        }
    }
}
