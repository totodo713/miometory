package com.worklog.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.worklog.application.command.RecallDailyEntriesCommand;
import com.worklog.application.command.SubmitDailyEntriesCommand;
import com.worklog.application.service.WorkLogEntryService;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.project.ProjectId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.shared.FiscalMonthPeriod;
import com.worklog.domain.shared.TimeAmount;
import com.worklog.domain.worklog.WorkLogEntry;
import com.worklog.domain.worklog.WorkLogStatus;
import com.worklog.eventsourcing.EventStore;
import com.worklog.infrastructure.repository.JdbcApprovalRepository;
import com.worklog.infrastructure.repository.JdbcDailyRejectionLogRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import com.worklog.infrastructure.repository.JdbcWorkLogRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for proxy permission checks in {@link WorkLogEntryService}.
 *
 * Tests submitDailyEntries and recallDailyEntries when the acting user
 * differs from the target member (proxy submission/recall by a manager).
 * These are pure unit tests with no Spring context.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkLogEntryService — Proxy Permission Checks")
class WorkLogEntryServiceProxyTest {

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

    @BeforeEach
    void setUp() {
        service = new WorkLogEntryService(
                workLogRepository, memberRepository, approvalRepository, dailyRejectionLogRepository, eventStore);
    }

    @Nested
    @DisplayName("submitDailyEntries — proxy submission")
    class SubmitProxy {

        @Test
        @DisplayName("should allow self-submission without proxy check")
        void shouldAllowSelfSubmissionWithoutProxyCheck() {
            // Arrange: memberId == submittedBy (self-submission)
            List<WorkLogEntry> draftEntries = List.of(createDraftEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 2.0));

            when(workLogRepository.findByDateRange(MEMBER_ID, WORK_DATE, WORK_DATE, WorkLogStatus.DRAFT))
                    .thenReturn(draftEntries);

            SubmitDailyEntriesCommand command = new SubmitDailyEntriesCommand(MEMBER_ID, WORK_DATE, MEMBER_ID);

            // Act
            List<WorkLogEntry> result = service.submitDailyEntries(command);

            // Assert: succeeds without calling isSubordinateOf
            assertEquals(1, result.size());
            assertEquals(WorkLogStatus.SUBMITTED, result.get(0).getStatus());
            verify(memberRepository, never()).isSubordinateOf(any(), any());
        }

        @Test
        @DisplayName("should allow proxy submission when manager is subordinate's manager")
        void shouldAllowProxySubmissionWhenManagerIsAuthorized() {
            // Arrange: manager submits on behalf of member, isSubordinateOf returns true
            when(memberRepository.isSubordinateOf(MemberId.of(MANAGER_ID), MemberId.of(MEMBER_ID)))
                    .thenReturn(true);

            List<WorkLogEntry> draftEntries = List.of(createDraftEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 3.5));

            when(workLogRepository.findByDateRange(MEMBER_ID, WORK_DATE, WORK_DATE, WorkLogStatus.DRAFT))
                    .thenReturn(draftEntries);

            SubmitDailyEntriesCommand command = new SubmitDailyEntriesCommand(MEMBER_ID, WORK_DATE, MANAGER_ID);

            // Act
            List<WorkLogEntry> result = service.submitDailyEntries(command);

            // Assert: succeeds and proxy check was performed
            assertEquals(1, result.size());
            assertEquals(WorkLogStatus.SUBMITTED, result.get(0).getStatus());
            verify(memberRepository).isSubordinateOf(MemberId.of(MANAGER_ID), MemberId.of(MEMBER_ID));
        }

        @Test
        @DisplayName("should reject proxy submission when not a manager")
        void shouldRejectProxySubmissionWhenNotManager() {
            // Arrange: non-manager attempts proxy submission, isSubordinateOf returns false
            UUID nonManagerId = UUID.randomUUID();
            when(memberRepository.isSubordinateOf(MemberId.of(nonManagerId), MemberId.of(MEMBER_ID)))
                    .thenReturn(false);

            SubmitDailyEntriesCommand command = new SubmitDailyEntriesCommand(MEMBER_ID, WORK_DATE, nonManagerId);

            // Act & Assert
            DomainException exception = assertThrows(DomainException.class, () -> service.submitDailyEntries(command));

            assertEquals("PROXY_ENTRY_NOT_ALLOWED", exception.getErrorCode());
            assertTrue(exception.getMessage().contains(nonManagerId.toString()));
            assertTrue(exception.getMessage().contains(MEMBER_ID.toString()));

            // Repository should never be called for entry lookup
            verify(workLogRepository, never()).findByDateRange(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("recallDailyEntries — proxy recall")
    class RecallProxy {

        @Test
        @DisplayName("should allow self-recall without proxy check")
        void shouldAllowSelfRecallWithoutProxyCheck() {
            // Arrange: memberId == recalledBy (self-recall)
            List<WorkLogEntry> submittedEntries =
                    List.of(createSubmittedEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 4.0));

            when(workLogRepository.findByDateRange(MEMBER_ID, WORK_DATE, WORK_DATE, WorkLogStatus.SUBMITTED))
                    .thenReturn(submittedEntries);

            FiscalMonthPeriod fiscalMonth = FiscalMonthPeriod.forDate(WORK_DATE);
            when(approvalRepository.findByMemberAndFiscalMonth(MemberId.of(MEMBER_ID), fiscalMonth))
                    .thenReturn(Optional.empty());

            RecallDailyEntriesCommand command = new RecallDailyEntriesCommand(MEMBER_ID, WORK_DATE, MEMBER_ID);

            // Act
            List<WorkLogEntry> result = service.recallDailyEntries(command);

            // Assert: succeeds without calling isSubordinateOf
            assertEquals(1, result.size());
            assertEquals(WorkLogStatus.DRAFT, result.get(0).getStatus());
            verify(memberRepository, never()).isSubordinateOf(any(), any());
        }

        @Test
        @DisplayName("should allow proxy recall when manager is subordinate's manager")
        void shouldAllowProxyRecallWhenManagerIsAuthorized() {
            // Arrange: manager recalls on behalf of member, isSubordinateOf returns true
            when(memberRepository.isSubordinateOf(MemberId.of(MANAGER_ID), MemberId.of(MEMBER_ID)))
                    .thenReturn(true);

            List<WorkLogEntry> submittedEntries =
                    List.of(createSubmittedEntry(MEMBER_ID, UUID.randomUUID(), WORK_DATE, 2.5));

            when(workLogRepository.findByDateRange(MEMBER_ID, WORK_DATE, WORK_DATE, WorkLogStatus.SUBMITTED))
                    .thenReturn(submittedEntries);

            FiscalMonthPeriod fiscalMonth = FiscalMonthPeriod.forDate(WORK_DATE);
            when(approvalRepository.findByMemberAndFiscalMonth(MemberId.of(MEMBER_ID), fiscalMonth))
                    .thenReturn(Optional.empty());

            RecallDailyEntriesCommand command = new RecallDailyEntriesCommand(MEMBER_ID, WORK_DATE, MANAGER_ID);

            // Act
            List<WorkLogEntry> result = service.recallDailyEntries(command);

            // Assert: succeeds and proxy check was performed
            assertEquals(1, result.size());
            assertEquals(WorkLogStatus.DRAFT, result.get(0).getStatus());
            verify(memberRepository).isSubordinateOf(MemberId.of(MANAGER_ID), MemberId.of(MEMBER_ID));
        }

        @Test
        @DisplayName("should reject proxy recall when not a manager")
        void shouldRejectProxyRecallWhenNotManager() {
            // Arrange: non-manager attempts proxy recall, isSubordinateOf returns false
            UUID nonManagerId = UUID.randomUUID();
            when(memberRepository.isSubordinateOf(MemberId.of(nonManagerId), MemberId.of(MEMBER_ID)))
                    .thenReturn(false);

            RecallDailyEntriesCommand command = new RecallDailyEntriesCommand(MEMBER_ID, WORK_DATE, nonManagerId);

            // Act & Assert
            DomainException exception = assertThrows(DomainException.class, () -> service.recallDailyEntries(command));

            assertEquals("PROXY_ENTRY_NOT_ALLOWED", exception.getErrorCode());
            assertTrue(exception.getMessage().contains(nonManagerId.toString()));
            assertTrue(exception.getMessage().contains(MEMBER_ID.toString()));

            // Repository should never be called for entry lookup
            verify(workLogRepository, never()).findByDateRange(any(), any(), any(), any());
        }
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
