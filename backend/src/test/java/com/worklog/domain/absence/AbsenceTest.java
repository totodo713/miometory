package com.worklog.domain.absence;

import com.worklog.domain.absence.events.AbsenceDeleted;
import com.worklog.domain.absence.events.AbsenceRecorded;
import com.worklog.domain.absence.events.AbsenceStatusChanged;
import com.worklog.domain.absence.events.AbsenceUpdated;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.shared.TimeAmount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Absence aggregate root.
 * 
 * Tests business logic for:
 * - T096: Absence recording, updates, deletion, and status transitions
 * 
 * These are pure unit tests with no dependencies on Spring or database.
 */
@DisplayName("Absence aggregate")
class AbsenceTest {
    
    private MemberId testMemberId;
    private LocalDate testDate;
    private TimeAmount testHours;
    private AbsenceType testAbsenceType;
    private String testReason;
    private MemberId recordedBy;
    
    @BeforeEach
    void setUp() {
        testMemberId = MemberId.of(UUID.randomUUID());
        testDate = LocalDate.now().minusDays(1); // Yesterday (valid)
        testHours = TimeAmount.of(8.0);
        testAbsenceType = AbsenceType.PAID_LEAVE;
        testReason = "Annual vacation";
        recordedBy = testMemberId;
    }
    
    @Nested
    @DisplayName("Recording")
    class Recording {
        
        @Test
        @DisplayName("should record new absence with valid data")
        void shouldRecordValidAbsence() {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                testReason,
                recordedBy
            );
            
            assertNotNull(absence.getId());
            assertEquals(testMemberId, absence.getMemberId());
            assertEquals(testDate, absence.getDate());
            assertEquals(testHours, absence.getHours());
            assertEquals(testAbsenceType, absence.getAbsenceType());
            assertEquals(testReason, absence.getReason());
            assertEquals(AbsenceStatus.DRAFT, absence.getStatus());
            assertEquals(recordedBy, absence.getRecordedBy());
            assertNotNull(absence.getCreatedAt());
            assertNotNull(absence.getUpdatedAt());
            assertEquals(absence.getCreatedAt(), absence.getUpdatedAt());
            assertFalse(absence.isDeleted());
        }
        
        @Test
        @DisplayName("should record absence with null reason")
        void shouldRecordWithNullReason() {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                null,
                recordedBy
            );
            
            assertNull(absence.getReason());
        }
        
        @Test
        @DisplayName("should record absence with empty reason")
        void shouldRecordWithEmptyReason() {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                "",
                recordedBy
            );
            
            assertEquals("", absence.getReason());
        }
        
        @Test
        @DisplayName("should record absence for today")
        void shouldRecordAbsenceForToday() {
            LocalDate today = LocalDate.now();
            
            Absence absence = Absence.record(
                testMemberId,
                today,
                testHours,
                testAbsenceType,
                testReason,
                recordedBy
            );
            
            assertEquals(today, absence.getDate());
        }
        
        @Test
        @DisplayName("should record with SICK_LEAVE type")
        void shouldRecordSickLeave() {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                AbsenceType.SICK_LEAVE,
                "Flu",
                recordedBy
            );
            
            assertEquals(AbsenceType.SICK_LEAVE, absence.getAbsenceType());
        }
        
        @Test
        @DisplayName("should record with SPECIAL_LEAVE type")
        void shouldRecordSpecialLeave() {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                AbsenceType.SPECIAL_LEAVE,
                "Family emergency",
                recordedBy
            );
            
            assertEquals(AbsenceType.SPECIAL_LEAVE, absence.getAbsenceType());
        }
        
        @Test
        @DisplayName("should record with OTHER type")
        void shouldRecordOtherType() {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                AbsenceType.OTHER,
                "Personal reasons",
                recordedBy
            );
            
            assertEquals(AbsenceType.OTHER, absence.getAbsenceType());
        }
        
        @Test
        @DisplayName("should raise AbsenceRecorded event")
        void shouldRaiseRecordedEvent() {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                testReason,
                recordedBy
            );
            
            List<DomainEvent> events = absence.getUncommittedEvents();
            assertEquals(1, events.size());
            
            DomainEvent event = events.get(0);
            assertInstanceOf(AbsenceRecorded.class, event);
            
            AbsenceRecorded recordedEvent = (AbsenceRecorded) event;
            assertEquals(absence.getId().value(), recordedEvent.aggregateId());
            assertEquals(testMemberId.value(), recordedEvent.memberId());
            assertEquals(testDate, recordedEvent.date());
            assertEquals(testHours.hours().doubleValue(), recordedEvent.hours());
            assertEquals(testAbsenceType.name(), recordedEvent.absenceType());
            assertEquals(testReason, recordedEvent.reason());
            assertEquals(recordedBy.value(), recordedEvent.recordedBy());
        }
        
        @Test
        @DisplayName("should throw exception for null date")
        void shouldRejectNullDate() {
            DomainException exception = assertThrows(
                DomainException.class,
                () -> Absence.record(
                    testMemberId,
                    null,
                    testHours,
                    testAbsenceType,
                    testReason,
                    recordedBy
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
                () -> Absence.record(
                    testMemberId,
                    futureDate,
                    testHours,
                    testAbsenceType,
                    testReason,
                    recordedBy
                )
            );
            
            assertEquals("DATE_IN_FUTURE", exception.getErrorCode());
            assertEquals("Date cannot be in the future", exception.getMessage());
        }
        
        @Test
        @DisplayName("should throw exception for reason exceeding 500 characters")
        void shouldRejectLongReason() {
            String longReason = "x".repeat(501);
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> Absence.record(
                    testMemberId,
                    testDate,
                    testHours,
                    testAbsenceType,
                    longReason,
                    recordedBy
                )
            );
            
            assertEquals("REASON_TOO_LONG", exception.getErrorCode());
            assertEquals("Reason cannot exceed 500 characters", exception.getMessage());
        }
        
        @Test
        @DisplayName("should accept reason with exactly 500 characters")
        void shouldAcceptMaxLengthReason() {
            String maxReason = "x".repeat(500);
            
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                maxReason,
                recordedBy
            );
            
            assertEquals(maxReason, absence.getReason());
        }
        
        @Test
        @DisplayName("should accept hours in 0.25 increments")
        void shouldAcceptQuarterHourIncrements() {
            TimeAmount quarterHour = TimeAmount.of(0.25);
            TimeAmount halfHour = TimeAmount.of(0.5);
            TimeAmount threeQuarters = TimeAmount.of(0.75);
            
            Absence absence1 = Absence.record(testMemberId, testDate, quarterHour, testAbsenceType, testReason, recordedBy);
            assertEquals(quarterHour, absence1.getHours());
            
            Absence absence2 = Absence.record(testMemberId, testDate, halfHour, testAbsenceType, testReason, recordedBy);
            assertEquals(halfHour, absence2.getHours());
            
            Absence absence3 = Absence.record(testMemberId, testDate, threeQuarters, testAbsenceType, testReason, recordedBy);
            assertEquals(threeQuarters, absence3.getHours());
        }
    }
    
    @Nested
    @DisplayName("Update")
    class Update {
        
        private Absence absence;
        private MemberId updater;
        
        @BeforeEach
        void setUp() {
            absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                testReason,
                recordedBy
            );
            absence.clearUncommittedEvents();
            updater = testMemberId;
        }
        
        @Test
        @DisplayName("should update hours, type, and reason in DRAFT status")
        void shouldUpdateDraftAbsence() {
            TimeAmount newHours = TimeAmount.of(4.0);
            AbsenceType newType = AbsenceType.SICK_LEAVE;
            String newReason = "Updated: feeling sick";
            
            absence.update(newHours, newType, newReason, updater);
            
            assertEquals(newHours, absence.getHours());
            assertEquals(newType, absence.getAbsenceType());
            assertEquals(newReason, absence.getReason());
        }
        
        @Test
        @DisplayName("should raise AbsenceUpdated event")
        void shouldRaiseUpdatedEvent() {
            TimeAmount newHours = TimeAmount.of(4.0);
            AbsenceType newType = AbsenceType.SICK_LEAVE;
            String newReason = "Updated reason";
            
            absence.update(newHours, newType, newReason, updater);
            
            List<DomainEvent> events = absence.getUncommittedEvents();
            assertEquals(1, events.size());
            
            DomainEvent event = events.get(0);
            assertInstanceOf(AbsenceUpdated.class, event);
            
            AbsenceUpdated updatedEvent = (AbsenceUpdated) event;
            assertEquals(absence.getId().value(), updatedEvent.aggregateId());
            assertEquals(newHours.hours().doubleValue(), updatedEvent.hours());
            assertEquals(newType.name(), updatedEvent.absenceType());
            assertEquals(newReason, updatedEvent.reason());
            assertEquals(updater.value(), updatedEvent.updatedBy());
        }
        
        @Test
        @DisplayName("should update with null reason")
        void shouldUpdateWithNullReason() {
            absence.update(testHours, testAbsenceType, null, updater);
            
            assertNull(absence.getReason());
        }
        
        @Test
        @DisplayName("should throw exception when updating SUBMITTED absence")
        void shouldRejectUpdateOfSubmittedAbsence() {
            absence.changeStatus(AbsenceStatus.SUBMITTED, updater);
            absence.clearUncommittedEvents();
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> absence.update(TimeAmount.of(4.0), AbsenceType.SICK_LEAVE, "New reason", updater)
            );
            
            assertEquals("ABSENCE_NOT_EDITABLE", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("SUBMITTED"));
        }
        
        @Test
        @DisplayName("should throw exception when updating APPROVED absence")
        void shouldRejectUpdateOfApprovedAbsence() {
            absence.changeStatus(AbsenceStatus.SUBMITTED, updater);
            absence.clearUncommittedEvents();
            absence.changeStatus(AbsenceStatus.APPROVED, updater);
            absence.clearUncommittedEvents();
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> absence.update(TimeAmount.of(4.0), AbsenceType.SICK_LEAVE, "New reason", updater)
            );
            
            assertEquals("ABSENCE_NOT_EDITABLE", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("APPROVED"));
        }
        
        @Test
        @DisplayName("should allow update of REJECTED absence")
        void shouldAllowUpdateOfRejectedAbsence() {
            absence.changeStatus(AbsenceStatus.SUBMITTED, updater);
            absence.clearUncommittedEvents();
            absence.changeStatus(AbsenceStatus.REJECTED, updater);
            absence.clearUncommittedEvents();
            
            TimeAmount newHours = TimeAmount.of(4.0);
            absence.update(newHours, AbsenceType.SICK_LEAVE, "Corrected", updater);
            
            assertEquals(newHours, absence.getHours());
        }
        
        @Test
        @DisplayName("should throw exception for reason exceeding 500 characters")
        void shouldRejectLongReasonOnUpdate() {
            String longReason = "x".repeat(501);
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> absence.update(testHours, testAbsenceType, longReason, updater)
            );
            
            assertEquals("REASON_TOO_LONG", exception.getErrorCode());
        }
    }
    
    @Nested
    @DisplayName("Status transitions")
    class StatusTransitions {
        
        private Absence absence;
        private MemberId statusChanger;
        
        @BeforeEach
        void setUp() {
            absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                testReason,
                recordedBy
            );
            absence.clearUncommittedEvents();
            statusChanger = testMemberId;
        }
        
        @Test
        @DisplayName("should transition from DRAFT to SUBMITTED")
        void shouldTransitionDraftToSubmitted() {
            absence.changeStatus(AbsenceStatus.SUBMITTED, statusChanger);
            
            assertEquals(AbsenceStatus.SUBMITTED, absence.getStatus());
        }
        
        @Test
        @DisplayName("should transition from SUBMITTED to APPROVED")
        void shouldTransitionSubmittedToApproved() {
            absence.changeStatus(AbsenceStatus.SUBMITTED, statusChanger);
            absence.clearUncommittedEvents();
            
            absence.changeStatus(AbsenceStatus.APPROVED, statusChanger);
            
            assertEquals(AbsenceStatus.APPROVED, absence.getStatus());
        }
        
        @Test
        @DisplayName("should transition from SUBMITTED to REJECTED")
        void shouldTransitionSubmittedToRejected() {
            absence.changeStatus(AbsenceStatus.SUBMITTED, statusChanger);
            absence.clearUncommittedEvents();
            
            absence.changeStatus(AbsenceStatus.REJECTED, statusChanger);
            
            assertEquals(AbsenceStatus.REJECTED, absence.getStatus());
        }
        
        @Test
        @DisplayName("should transition from REJECTED to DRAFT")
        void shouldTransitionRejectedToDraft() {
            absence.changeStatus(AbsenceStatus.SUBMITTED, statusChanger);
            absence.clearUncommittedEvents();
            absence.changeStatus(AbsenceStatus.REJECTED, statusChanger);
            absence.clearUncommittedEvents();
            
            absence.changeStatus(AbsenceStatus.DRAFT, statusChanger);
            
            assertEquals(AbsenceStatus.DRAFT, absence.getStatus());
        }
        
        @Test
        @DisplayName("should raise AbsenceStatusChanged event")
        void shouldRaiseStatusChangedEvent() {
            absence.changeStatus(AbsenceStatus.SUBMITTED, statusChanger);
            
            List<DomainEvent> events = absence.getUncommittedEvents();
            assertEquals(1, events.size());
            
            DomainEvent event = events.get(0);
            assertInstanceOf(AbsenceStatusChanged.class, event);
            
            AbsenceStatusChanged statusEvent = (AbsenceStatusChanged) event;
            assertEquals(absence.getId().value(), statusEvent.aggregateId());
            assertEquals("DRAFT", statusEvent.fromStatus());
            assertEquals("SUBMITTED", statusEvent.toStatus());
            assertEquals(statusChanger.value(), statusEvent.changedBy());
        }
        
        @Test
        @DisplayName("should throw exception for invalid transition: DRAFT to APPROVED")
        void shouldRejectDraftToApproved() {
            DomainException exception = assertThrows(
                DomainException.class,
                () -> absence.changeStatus(AbsenceStatus.APPROVED, statusChanger)
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
                () -> absence.changeStatus(AbsenceStatus.REJECTED, statusChanger)
            );
            
            assertEquals("INVALID_STATUS_TRANSITION", exception.getErrorCode());
        }
        
        @Test
        @DisplayName("should throw exception for invalid transition: APPROVED to any status")
        void shouldRejectApprovedTransition() {
            absence.changeStatus(AbsenceStatus.SUBMITTED, statusChanger);
            absence.clearUncommittedEvents();
            absence.changeStatus(AbsenceStatus.APPROVED, statusChanger);
            absence.clearUncommittedEvents();
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> absence.changeStatus(AbsenceStatus.DRAFT, statusChanger)
            );
            
            assertEquals("INVALID_STATUS_TRANSITION", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("APPROVED"));
        }
        
        @Test
        @DisplayName("should throw exception for invalid transition: SUBMITTED to DRAFT")
        void shouldRejectSubmittedToDraft() {
            absence.changeStatus(AbsenceStatus.SUBMITTED, statusChanger);
            absence.clearUncommittedEvents();
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> absence.changeStatus(AbsenceStatus.DRAFT, statusChanger)
            );
            
            assertEquals("INVALID_STATUS_TRANSITION", exception.getErrorCode());
        }
        
        @Test
        @DisplayName("should throw exception for invalid transition: REJECTED to APPROVED")
        void shouldRejectRejectedToApproved() {
            absence.changeStatus(AbsenceStatus.SUBMITTED, statusChanger);
            absence.clearUncommittedEvents();
            absence.changeStatus(AbsenceStatus.REJECTED, statusChanger);
            absence.clearUncommittedEvents();
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> absence.changeStatus(AbsenceStatus.APPROVED, statusChanger)
            );
            
            assertEquals("INVALID_STATUS_TRANSITION", exception.getErrorCode());
        }
    }
    
    @Nested
    @DisplayName("Deletion")
    class Deletion {
        
        private Absence absence;
        private MemberId deleter;
        
        @BeforeEach
        void setUp() {
            absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                testReason,
                recordedBy
            );
            absence.clearUncommittedEvents();
            deleter = testMemberId;
        }
        
        @Test
        @DisplayName("should delete absence in DRAFT status")
        void shouldDeleteDraftAbsence() {
            absence.delete(deleter);
            
            List<DomainEvent> events = absence.getUncommittedEvents();
            assertEquals(1, events.size());
            assertInstanceOf(AbsenceDeleted.class, events.get(0));
        }
        
        @Test
        @DisplayName("should raise AbsenceDeleted event")
        void shouldRaiseDeletedEvent() {
            absence.delete(deleter);
            
            List<DomainEvent> events = absence.getUncommittedEvents();
            DomainEvent event = events.get(0);
            
            AbsenceDeleted deletedEvent = (AbsenceDeleted) event;
            assertEquals(absence.getId().value(), deletedEvent.aggregateId());
            assertEquals(deleter.value(), deletedEvent.deletedBy());
        }
        
        @Test
        @DisplayName("should allow delete of REJECTED absence")
        void shouldAllowDeleteOfRejectedAbsence() {
            absence.changeStatus(AbsenceStatus.SUBMITTED, deleter);
            absence.clearUncommittedEvents();
            absence.changeStatus(AbsenceStatus.REJECTED, deleter);
            absence.clearUncommittedEvents();
            
            absence.delete(deleter);
            
            List<DomainEvent> events = absence.getUncommittedEvents();
            assertEquals(1, events.size());
            assertInstanceOf(AbsenceDeleted.class, events.get(0));
        }
        
        @Test
        @DisplayName("should throw exception when deleting SUBMITTED absence")
        void shouldRejectDeleteOfSubmittedAbsence() {
            absence.changeStatus(AbsenceStatus.SUBMITTED, deleter);
            absence.clearUncommittedEvents();
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> absence.delete(deleter)
            );
            
            assertEquals("ABSENCE_NOT_DELETABLE", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("SUBMITTED"));
        }
        
        @Test
        @DisplayName("should throw exception when deleting APPROVED absence")
        void shouldRejectDeleteOfApprovedAbsence() {
            absence.changeStatus(AbsenceStatus.SUBMITTED, deleter);
            absence.clearUncommittedEvents();
            absence.changeStatus(AbsenceStatus.APPROVED, deleter);
            absence.clearUncommittedEvents();
            
            DomainException exception = assertThrows(
                DomainException.class,
                () -> absence.delete(deleter)
            );
            
            assertEquals("ABSENCE_NOT_DELETABLE", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("APPROVED"));
        }
    }
    
    @Nested
    @DisplayName("Business rules")
    class BusinessRules {
        
        @Test
        @DisplayName("should identify absence as editable when DRAFT")
        void shouldIdentifyDraftAsEditable() {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                testReason,
                recordedBy
            );
            
            assertTrue(absence.isEditable());
        }
        
        @Test
        @DisplayName("should identify absence as not editable when SUBMITTED")
        void shouldIdentifySubmittedAsNotEditable() {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                testReason,
                recordedBy
            );
            absence.clearUncommittedEvents();
            absence.changeStatus(AbsenceStatus.SUBMITTED, testMemberId);
            
            assertFalse(absence.isEditable());
        }
        
        @Test
        @DisplayName("should identify absence as not editable when APPROVED")
        void shouldIdentifyApprovedAsNotEditable() {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                testReason,
                recordedBy
            );
            absence.clearUncommittedEvents();
            absence.changeStatus(AbsenceStatus.SUBMITTED, testMemberId);
            absence.clearUncommittedEvents();
            absence.changeStatus(AbsenceStatus.APPROVED, testMemberId);
            
            assertFalse(absence.isEditable());
        }
        
        @Test
        @DisplayName("should identify absence as editable when REJECTED")
        void shouldIdentifyRejectedAsEditable() {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                testReason,
                recordedBy
            );
            absence.clearUncommittedEvents();
            absence.changeStatus(AbsenceStatus.SUBMITTED, testMemberId);
            absence.clearUncommittedEvents();
            absence.changeStatus(AbsenceStatus.REJECTED, testMemberId);
            
            assertTrue(absence.isEditable());
        }
        
        @Test
        @DisplayName("should identify absence as deletable when DRAFT")
        void shouldIdentifyDraftAsDeletable() {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                testReason,
                recordedBy
            );
            
            assertTrue(absence.isDeletable());
        }
        
        @Test
        @DisplayName("should identify absence as not deletable when SUBMITTED")
        void shouldIdentifySubmittedAsNotDeletable() {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                testReason,
                recordedBy
            );
            absence.clearUncommittedEvents();
            absence.changeStatus(AbsenceStatus.SUBMITTED, testMemberId);
            
            assertFalse(absence.isDeletable());
        }
        
        @Test
        @DisplayName("should identify absence as not deletable when APPROVED")
        void shouldIdentifyApprovedAsNotDeletable() {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                testReason,
                recordedBy
            );
            absence.clearUncommittedEvents();
            absence.changeStatus(AbsenceStatus.SUBMITTED, testMemberId);
            absence.clearUncommittedEvents();
            absence.changeStatus(AbsenceStatus.APPROVED, testMemberId);
            
            assertFalse(absence.isDeletable());
        }
        
        @Test
        @DisplayName("should identify absence as deletable when REJECTED")
        void shouldIdentifyRejectedAsDeletable() {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                testReason,
                recordedBy
            );
            absence.clearUncommittedEvents();
            absence.changeStatus(AbsenceStatus.SUBMITTED, testMemberId);
            absence.clearUncommittedEvents();
            absence.changeStatus(AbsenceStatus.REJECTED, testMemberId);
            
            assertTrue(absence.isDeletable());
        }
    }
    
    @Nested
    @DisplayName("Aggregate metadata")
    class AggregateMetadata {
        
        @Test
        @DisplayName("should have correct aggregate type")
        void shouldHaveCorrectAggregateType() {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                testReason,
                recordedBy
            );
            
            assertEquals("Absence", absence.getAggregateType());
        }
        
        @Test
        @DisplayName("should start with version 0")
        void shouldStartWithVersionZero() {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                testReason,
                recordedBy
            );
            
            assertEquals(0, absence.getVersion());
        }
        
        @Test
        @DisplayName("should track uncommitted events")
        void shouldTrackUncommittedEvents() {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                testReason,
                recordedBy
            );
            
            // After creation, one event
            assertEquals(1, absence.getUncommittedEvents().size());
            
            absence.clearUncommittedEvents();
            assertEquals(0, absence.getUncommittedEvents().size());
            
            // After update, one new event
            absence.update(TimeAmount.of(4.0), AbsenceType.SICK_LEAVE, "Updated", testMemberId);
            assertEquals(1, absence.getUncommittedEvents().size());
        }
        
        @Test
        @DisplayName("should update updatedAt timestamp on changes")
        void shouldUpdateTimestamp() throws InterruptedException {
            Absence absence = Absence.record(
                testMemberId,
                testDate,
                testHours,
                testAbsenceType,
                testReason,
                recordedBy
            );
            absence.clearUncommittedEvents();
            
            var originalUpdatedAt = absence.getUpdatedAt();
            
            // Small delay to ensure timestamp changes
            Thread.sleep(10);
            
            absence.update(TimeAmount.of(4.0), AbsenceType.SICK_LEAVE, "Updated", testMemberId);
            
            assertTrue(absence.getUpdatedAt().isAfter(originalUpdatedAt));
        }
    }
}
