package com.worklog.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.worklog.application.command.SubmitDailyEntriesCommand;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.project.ProjectId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.shared.TimeAmount;
import com.worklog.domain.worklog.WorkLogEntry;
import com.worklog.domain.worklog.WorkLogStatus;
import com.worklog.eventsourcing.EventStore;
import com.worklog.infrastructure.repository.JdbcApprovalRepository;
import com.worklog.infrastructure.repository.JdbcDailyRejectionLogRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import com.worklog.infrastructure.repository.JdbcWorkLogRepository;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link WorkLogEntryService#submitDailyEntries}.
 *
 * Tests the submit-daily-entries use case with mocked repositories.
 * These are pure unit tests with no Spring context.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkLogEntryService — submitDailyEntries")
class WorkLogEntryServiceSubmitTest {

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
    private static final LocalDate WORK_DATE = LocalDate.of(2026, 2, 10);

    @BeforeEach
    void setUp() {
        service = new WorkLogEntryService(
                workLogRepository, memberRepository, approvalRepository, dailyRejectionLogRepository, eventStore);
    }

    @Test
    @DisplayName("should submit all DRAFT entries and return them as SUBMITTED")
    void shouldSubmitAllDraftEntries() {
        // Arrange: 3 DRAFT entries for the same member and date
        List<WorkLogEntry> draftEntries = List.of(
                createDraftEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 2.0),
                createDraftEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 3.5),
                createDraftEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 1.25));

        when(workLogRepository.findByDateRange(MEMBER_ID, WORK_DATE, WORK_DATE, WorkLogStatus.DRAFT))
                .thenReturn(draftEntries);

        SubmitDailyEntriesCommand command = new SubmitDailyEntriesCommand(MEMBER_ID, WORK_DATE, MEMBER_ID);

        // Act
        List<WorkLogEntry> result = service.submitDailyEntries(command);

        // Assert
        assertEquals(3, result.size());
        for (WorkLogEntry entry : result) {
            assertEquals(WorkLogStatus.SUBMITTED, entry.getStatus());
        }
    }

    @Test
    @DisplayName("should throw PROXY_ENTRY_NOT_ALLOWED when submittedBy is not authorized")
    void shouldThrowWhenSubmittedByDifferentMember() {
        // Arrange: submittedBy is a different member who is not a manager
        UUID otherMemberId = UUID.randomUUID();
        when(memberRepository.isSubordinateOf(MemberId.of(otherMemberId), MemberId.of(MEMBER_ID)))
                .thenReturn(false);

        SubmitDailyEntriesCommand command = new SubmitDailyEntriesCommand(MEMBER_ID, WORK_DATE, otherMemberId);

        // Act & Assert
        DomainException exception = assertThrows(DomainException.class, () -> service.submitDailyEntries(command));

        assertEquals("PROXY_ENTRY_NOT_ALLOWED", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("does not have permission"));

        // Work log repository should never be called
        verify(workLogRepository, never()).findByDateRange(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should throw NO_DRAFT_ENTRIES when no DRAFT entries found for the date")
    void shouldThrowWhenNoDraftEntries() {
        // Arrange: no draft entries
        when(workLogRepository.findByDateRange(MEMBER_ID, WORK_DATE, WORK_DATE, WorkLogStatus.DRAFT))
                .thenReturn(Collections.emptyList());

        SubmitDailyEntriesCommand command = new SubmitDailyEntriesCommand(MEMBER_ID, WORK_DATE, MEMBER_ID);

        // Act & Assert
        DomainException exception = assertThrows(DomainException.class, () -> service.submitDailyEntries(command));

        assertEquals("NO_DRAFT_ENTRIES", exception.getErrorCode());
        assertTrue(exception.getMessage().contains(MEMBER_ID.toString()));
        assertTrue(exception.getMessage().contains(WORK_DATE.toString()));

        // save() should never be called
        verify(workLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("should save each entry to the event store via repository")
    void shouldSaveEachEntryToEventStore() {
        // Arrange: 3 DRAFT entries
        WorkLogEntry entry1 = createDraftEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 1.0);
        WorkLogEntry entry2 = createDraftEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 2.0);
        WorkLogEntry entry3 = createDraftEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 0.5);

        List<WorkLogEntry> draftEntries = List.of(entry1, entry2, entry3);

        when(workLogRepository.findByDateRange(MEMBER_ID, WORK_DATE, WORK_DATE, WorkLogStatus.DRAFT))
                .thenReturn(draftEntries);

        SubmitDailyEntriesCommand command = new SubmitDailyEntriesCommand(MEMBER_ID, WORK_DATE, MEMBER_ID);

        // Act
        service.submitDailyEntries(command);

        // Assert: save() called once per entry
        verify(workLogRepository, times(3)).save(any(WorkLogEntry.class));
        verify(workLogRepository).save(entry1);
        verify(workLogRepository).save(entry2);
        verify(workLogRepository).save(entry3);
    }

    /**
     * Helper to create a real WorkLogEntry aggregate in DRAFT status.
     * Uses the domain factory method which raises a WorkLogEntryCreated event.
     */
    private WorkLogEntry createDraftEntry(UUID memberId, UUID projectId, LocalDate date, double hours) {
        WorkLogEntry entry = WorkLogEntry.create(
                MemberId.of(memberId),
                ProjectId.of(projectId),
                date,
                TimeAmount.of(hours),
                "test comment",
                MemberId.of(memberId));
        // Clear the creation event so save() only captures the status-change event
        entry.clearUncommittedEvents();
        return entry;
    }
}
