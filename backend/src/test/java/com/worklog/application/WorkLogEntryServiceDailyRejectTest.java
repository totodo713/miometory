package com.worklog.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.worklog.application.command.RejectDailyEntriesCommand;
import com.worklog.application.service.WorkLogEntryService;
import com.worklog.domain.approval.MonthlyApproval;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.project.ProjectId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.shared.FiscalMonthPeriod;
import com.worklog.domain.shared.TimeAmount;
import com.worklog.domain.worklog.WorkLogEntry;
import com.worklog.domain.worklog.WorkLogEntryId;
import com.worklog.domain.worklog.WorkLogStatus;
import com.worklog.eventsourcing.EventStore;
import com.worklog.infrastructure.repository.JdbcApprovalRepository;
import com.worklog.infrastructure.repository.JdbcDailyRejectionLogRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import com.worklog.infrastructure.repository.JdbcWorkLogRepository;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link WorkLogEntryService#rejectDailyEntries}.
 *
 * Tests the daily rejection use case with mocked repositories.
 * These are pure unit tests with no Spring context.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkLogEntryService — rejectDailyEntries")
class WorkLogEntryServiceDailyRejectTest {

    @Mock
    private JdbcWorkLogRepository workLogRepository;

    @Mock
    private JdbcMemberRepository memberRepository;

    @Mock
    private JdbcApprovalRepository approvalRepository;

    @Mock
    private JdbcDailyRejectionLogRepository dailyRejectionLogRepository;

    @Mock
    private EventStore eventStore;

    private WorkLogEntryService service;

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final LocalDate WORK_DATE = LocalDate.of(2026, 2, 10);
    private static final String REJECTION_REASON = "Incorrect project assignment, please revise";

    @BeforeEach
    void setUp() {
        service = new WorkLogEntryService(
                workLogRepository, memberRepository, approvalRepository, dailyRejectionLogRepository, eventStore);
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("should reject all SUBMITTED entries for date")
        void shouldRejectAllSubmittedEntriesForDate() {
            // Arrange: 2 SUBMITTED entries for the same member and date
            WorkLogEntry entry1 = createSubmittedEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 4.0);
            WorkLogEntry entry2 = createSubmittedEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 3.5);

            when(workLogRepository.findByDateRange(MEMBER_ID, WORK_DATE, WORK_DATE, WorkLogStatus.SUBMITTED))
                    .thenReturn(List.of(entry1, entry2));

            RejectDailyEntriesCommand command =
                    new RejectDailyEntriesCommand(MEMBER_ID, WORK_DATE, MANAGER_ID, REJECTION_REASON);

            // Act
            List<WorkLogEntry> result = service.rejectDailyEntries(command);

            // Assert: all entries transitioned to DRAFT
            assertEquals(2, result.size());
            for (WorkLogEntry entry : result) {
                assertEquals(WorkLogStatus.DRAFT, entry.getStatus());
            }

            // Verify each entry was saved
            verify(workLogRepository, times(2)).save(any(WorkLogEntry.class));

            // Verify dailyRejectionLogRepository.save() was called
            verify(dailyRejectionLogRepository)
                    .save(eq(MEMBER_ID), eq(WORK_DATE), eq(MANAGER_ID), eq(REJECTION_REASON), anySet());

            // Verify eventStore.append() was called with DailyEntriesRejected event type
            verify(eventStore).append(any(UUID.class), eq("DailyRejection"), anyList(), eq(0L));
        }

        @Test
        @DisplayName("should return all affected entries")
        void shouldReturnAllAffectedEntries() {
            // Arrange: 3 SUBMITTED entries
            WorkLogEntry entry1 = createSubmittedEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 1.0);
            WorkLogEntry entry2 = createSubmittedEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 2.0);
            WorkLogEntry entry3 = createSubmittedEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 0.5);

            when(workLogRepository.findByDateRange(MEMBER_ID, WORK_DATE, WORK_DATE, WorkLogStatus.SUBMITTED))
                    .thenReturn(List.of(entry1, entry2, entry3));

            RejectDailyEntriesCommand command =
                    new RejectDailyEntriesCommand(MEMBER_ID, WORK_DATE, MANAGER_ID, REJECTION_REASON);

            // Act
            List<WorkLogEntry> result = service.rejectDailyEntries(command);

            // Assert: all 3 entries returned in DRAFT status
            assertEquals(3, result.size());
            assertTrue(result.contains(entry1));
            assertTrue(result.contains(entry2));
            assertTrue(result.contains(entry3));
            for (WorkLogEntry entry : result) {
                assertEquals(WorkLogStatus.DRAFT, entry.getStatus());
            }
        }
    }

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("should throw NO_SUBMITTED_ENTRIES_FOR_DATE when no SUBMITTED entries exist")
        void shouldThrowWhenNoSubmittedEntriesExist() {
            // Arrange: no submitted entries for the date
            when(workLogRepository.findByDateRange(MEMBER_ID, WORK_DATE, WORK_DATE, WorkLogStatus.SUBMITTED))
                    .thenReturn(Collections.emptyList());

            RejectDailyEntriesCommand command =
                    new RejectDailyEntriesCommand(MEMBER_ID, WORK_DATE, MANAGER_ID, REJECTION_REASON);

            // Act & Assert
            DomainException exception = assertThrows(DomainException.class, () -> service.rejectDailyEntries(command));

            assertEquals("NO_SUBMITTED_ENTRIES_FOR_DATE", exception.getErrorCode());
            assertTrue(exception.getMessage().contains(MEMBER_ID.toString()));
            assertTrue(exception.getMessage().contains(WORK_DATE.toString()));

            // save() should never be called
            verify(workLogRepository, never()).save(any());
            verify(dailyRejectionLogRepository, never()).save(any(), any(), any(), any(), anySet());
            verify(eventStore, never()).append(any(), any(), anyList(), anyLong());
        }

        @Test
        @DisplayName("should throw REJECT_BLOCKED_BY_APPROVAL when monthly approval is APPROVED")
        void shouldThrowWhenBlockedByApprovedApproval() {
            // Arrange: SUBMITTED entries exist
            WorkLogEntry entry1 = createSubmittedEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 4.0);

            when(workLogRepository.findByDateRange(MEMBER_ID, WORK_DATE, WORK_DATE, WorkLogStatus.SUBMITTED))
                    .thenReturn(List.of(entry1));

            // Create a MonthlyApproval that has been submitted and then approved
            FiscalMonthPeriod fiscalMonth = FiscalMonthPeriod.forDate(WORK_DATE);
            MonthlyApproval approval = MonthlyApproval.create(MemberId.of(MEMBER_ID), fiscalMonth);
            approval.submit(
                    MemberId.of(MEMBER_ID),
                    Set.of(WorkLogEntryId.of(entry1.getId().value())),
                    Set.of());
            approval.approve(MemberId.of(MANAGER_ID));
            approval.clearUncommittedEvents();

            when(approvalRepository.findByMemberAndFiscalMonth(MemberId.of(MEMBER_ID), fiscalMonth))
                    .thenReturn(Optional.of(approval));

            RejectDailyEntriesCommand command =
                    new RejectDailyEntriesCommand(MEMBER_ID, WORK_DATE, MANAGER_ID, REJECTION_REASON);

            // Act & Assert
            DomainException exception = assertThrows(DomainException.class, () -> service.rejectDailyEntries(command));

            assertEquals("REJECT_BLOCKED_BY_APPROVAL", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("approved"));

            // save() should never be called — rejection was blocked
            verify(workLogRepository, never()).save(any());
            verify(dailyRejectionLogRepository, never()).save(any(), any(), any(), any(), anySet());
            verify(eventStore, never()).append(any(), any(), anyList(), anyLong());
        }
    }

    /**
     * Helper to create a real WorkLogEntry aggregate in SUBMITTED status.
     * Creates via the domain factory method, then transitions to SUBMITTED.
     */
    private WorkLogEntry createSubmittedEntry(UUID memberId, UUID projectId, LocalDate date, double hours) {
        WorkLogEntry entry = WorkLogEntry.create(
                MemberId.of(memberId),
                ProjectId.of(projectId),
                date,
                TimeAmount.of(hours),
                "test comment",
                MemberId.of(memberId));
        entry.changeStatus(WorkLogStatus.SUBMITTED, MemberId.of(memberId));
        // Clear all uncommitted events so save() only captures the reject status-change event
        entry.clearUncommittedEvents();
        return entry;
    }
}
