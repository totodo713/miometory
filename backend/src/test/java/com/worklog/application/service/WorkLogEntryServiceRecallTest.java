package com.worklog.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.worklog.application.command.RecallDailyEntriesCommand;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link WorkLogEntryService#recallDailyEntries}.
 *
 * Tests the recall-daily-entries use case with mocked repositories.
 * These are pure unit tests with no Spring context.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkLogEntryService — recallDailyEntries")
class WorkLogEntryServiceRecallTest {

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
    private static final LocalDate WORK_DATE = LocalDate.of(2026, 1, 15);

    @BeforeEach
    void setUp() {
        service = new WorkLogEntryService(
                workLogRepository, memberRepository, approvalRepository, dailyRejectionLogRepository, eventStore);
    }

    @Test
    @DisplayName("should recall all SUBMITTED entries and return them as DRAFT")
    void shouldRecallAllSubmittedEntries() {
        // Arrange: 2 SUBMITTED entries for the same member and date
        WorkLogEntry entry1 = createSubmittedEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 4.0);
        WorkLogEntry entry2 = createSubmittedEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 3.5);

        when(workLogRepository.findByDateRange(MEMBER_ID, WORK_DATE, WORK_DATE, WorkLogStatus.SUBMITTED))
                .thenReturn(List.of(entry1, entry2));

        FiscalMonthPeriod fiscalMonth = FiscalMonthPeriod.forDate(WORK_DATE);
        when(approvalRepository.findByMemberAndFiscalMonth(MemberId.of(MEMBER_ID), fiscalMonth))
                .thenReturn(Optional.empty());

        RecallDailyEntriesCommand command = new RecallDailyEntriesCommand(MEMBER_ID, WORK_DATE, MEMBER_ID);

        // Act
        List<WorkLogEntry> result = service.recallDailyEntries(command);

        // Assert
        assertEquals(2, result.size());
        for (WorkLogEntry entry : result) {
            assertEquals(WorkLogStatus.DRAFT, entry.getStatus());
        }
        verify(workLogRepository, times(2)).save(any(WorkLogEntry.class));
    }

    @Test
    @DisplayName("should throw PROXY_ENTRY_NOT_ALLOWED when recalledBy is not authorized")
    void shouldThrowWhenRecalledByDifferentMember() {
        // Arrange: recalledBy is a different member who is not a manager
        UUID otherMemberId = UUID.randomUUID();
        when(memberRepository.isSubordinateOf(MemberId.of(otherMemberId), MemberId.of(MEMBER_ID)))
                .thenReturn(false);

        RecallDailyEntriesCommand command = new RecallDailyEntriesCommand(MEMBER_ID, WORK_DATE, otherMemberId);

        // Act & Assert
        DomainException exception = assertThrows(DomainException.class, () -> service.recallDailyEntries(command));

        assertEquals("PROXY_ENTRY_NOT_ALLOWED", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("does not have permission"));

        // Work log repository should never be called
        verify(workLogRepository, never()).findByDateRange(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should throw NO_SUBMITTED_ENTRIES when no SUBMITTED entries found for the date")
    void shouldThrowWhenNoSubmittedEntries() {
        // Arrange: no submitted entries
        when(workLogRepository.findByDateRange(MEMBER_ID, WORK_DATE, WORK_DATE, WorkLogStatus.SUBMITTED))
                .thenReturn(Collections.emptyList());

        RecallDailyEntriesCommand command = new RecallDailyEntriesCommand(MEMBER_ID, WORK_DATE, MEMBER_ID);

        // Act & Assert
        DomainException exception = assertThrows(DomainException.class, () -> service.recallDailyEntries(command));

        assertEquals("NO_SUBMITTED_ENTRIES", exception.getErrorCode());
        assertTrue(exception.getMessage().contains(MEMBER_ID.toString()));
        assertTrue(exception.getMessage().contains(WORK_DATE.toString()));

        // save() should never be called
        verify(workLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw RECALL_BLOCKED_BY_APPROVAL when blocked by non-PENDING approval")
    void shouldThrowWhenBlockedByApproval() {
        // Arrange: SUBMITTED entries exist
        WorkLogEntry entry1 = createSubmittedEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 4.0);

        when(workLogRepository.findByDateRange(MEMBER_ID, WORK_DATE, WORK_DATE, WorkLogStatus.SUBMITTED))
                .thenReturn(List.of(entry1));

        // Create a MonthlyApproval and submit it so its status becomes SUBMITTED (non-PENDING)
        FiscalMonthPeriod fiscalMonth = FiscalMonthPeriod.forDate(WORK_DATE);
        MonthlyApproval approval = MonthlyApproval.create(MemberId.of(MEMBER_ID), fiscalMonth);
        approval.submit(
                MemberId.of(MEMBER_ID), Set.of(WorkLogEntryId.of(entry1.getId().value())), Set.of());
        approval.clearUncommittedEvents();

        when(approvalRepository.findByMemberAndFiscalMonth(MemberId.of(MEMBER_ID), fiscalMonth))
                .thenReturn(Optional.of(approval));

        // No daily rejection log exists — entries were never daily-rejected
        when(dailyRejectionLogRepository.existsByMemberIdAndDate(MEMBER_ID, WORK_DATE))
                .thenReturn(false);

        RecallDailyEntriesCommand command = new RecallDailyEntriesCommand(MEMBER_ID, WORK_DATE, MEMBER_ID);

        // Act & Assert
        DomainException exception = assertThrows(DomainException.class, () -> service.recallDailyEntries(command));

        assertEquals("RECALL_BLOCKED_BY_APPROVAL", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("SUBMITTED"));

        // save() should never be called — recall was blocked
        verify(workLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("should allow recall when entries were daily-rejected (rejection log exists)")
    void shouldAllowRecallWhenDailyRejected() {
        // Arrange: SUBMITTED entry that overlaps with a SUBMITTED MonthlyApproval
        WorkLogEntry entry1 = createSubmittedEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 4.0);

        when(workLogRepository.findByDateRange(MEMBER_ID, WORK_DATE, WORK_DATE, WorkLogStatus.SUBMITTED))
                .thenReturn(List.of(entry1));

        FiscalMonthPeriod fiscalMonth = FiscalMonthPeriod.forDate(WORK_DATE);
        MonthlyApproval approval = MonthlyApproval.create(MemberId.of(MEMBER_ID), fiscalMonth);
        approval.submit(
                MemberId.of(MEMBER_ID), Set.of(WorkLogEntryId.of(entry1.getId().value())), Set.of());
        approval.clearUncommittedEvents();

        when(approvalRepository.findByMemberAndFiscalMonth(MemberId.of(MEMBER_ID), fiscalMonth))
                .thenReturn(Optional.of(approval));

        // Daily rejection log EXISTS — entries went through a daily rejection cycle
        when(dailyRejectionLogRepository.existsByMemberIdAndDate(MEMBER_ID, WORK_DATE))
                .thenReturn(true);

        RecallDailyEntriesCommand command = new RecallDailyEntriesCommand(MEMBER_ID, WORK_DATE, MEMBER_ID);

        // Act
        List<WorkLogEntry> result = service.recallDailyEntries(command);

        // Assert: recall succeeds, entries are DRAFT
        assertEquals(1, result.size());
        assertEquals(WorkLogStatus.DRAFT, result.getFirst().getStatus());
        verify(workLogRepository, times(1)).save(any(WorkLogEntry.class));
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
        // Clear all uncommitted events so save() only captures the recall status-change event
        entry.clearUncommittedEvents();
        return entry;
    }
}
