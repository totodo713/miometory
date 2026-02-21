package com.worklog.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.worklog.application.approval.ApprovalService;
import com.worklog.application.approval.SubmitMonthForApprovalCommand;
import com.worklog.domain.approval.MonthlyApprovalId;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.shared.FiscalMonthPeriod;
import com.worklog.infrastructure.repository.JdbcAbsenceRepository;
import com.worklog.infrastructure.repository.JdbcApprovalRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import com.worklog.infrastructure.repository.JdbcWorkLogRepository;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for proxy monthly submission in {@link ApprovalService#submitMonth}.
 *
 * Tests proxy permission validation when a manager submits a month
 * on behalf of a subordinate. These are pure unit tests with no Spring context.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalService — Proxy Monthly Submission")
class ApprovalServiceProxySubmitTest {

    @Mock
    private JdbcApprovalRepository approvalRepository;

    @Mock
    private JdbcWorkLogRepository workLogRepository;

    @Mock
    private JdbcAbsenceRepository absenceRepository;

    @Mock
    private JdbcMemberRepository memberRepository;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private ApprovalService service;

    private static final MemberId MEMBER_ID = MemberId.of(UUID.randomUUID());
    private static final MemberId MANAGER_ID = MemberId.of(UUID.randomUUID());
    private static final LocalDate FISCAL_MONTH_DATE = LocalDate.of(2026, 2, 10);
    private static final FiscalMonthPeriod FISCAL_MONTH = FiscalMonthPeriod.forDate(FISCAL_MONTH_DATE);

    @BeforeEach
    void setUp() {
        service = new ApprovalService(
                approvalRepository, workLogRepository, absenceRepository, memberRepository, jdbcTemplate);
    }

    @Test
    @DisplayName("should allow self-submission")
    void shouldAllowSelfSubmission() {
        // Arrange: memberId == submittedBy (self-submission)
        SubmitMonthForApprovalCommand command = new SubmitMonthForApprovalCommand(MEMBER_ID, FISCAL_MONTH, MEMBER_ID);

        // No existing approval for this member/month
        when(approvalRepository.findByMemberAndFiscalMonth(MEMBER_ID, FISCAL_MONTH))
                .thenReturn(Optional.empty());

        // At least one work log entry exists in the fiscal month
        UUID entryId = UUID.randomUUID();
        when(approvalRepository.findWorkLogEntryIds(
                        MEMBER_ID.value(), FISCAL_MONTH.startDate(), FISCAL_MONTH.endDate()))
                .thenReturn(List.of(entryId));

        // No absences
        when(approvalRepository.findAbsenceIds(MEMBER_ID.value(), FISCAL_MONTH.startDate(), FISCAL_MONTH.endDate()))
                .thenReturn(Collections.emptyList());

        // Mock the work log entry lookup for status transition
        com.worklog.domain.worklog.WorkLogEntry draftEntry = com.worklog.domain.worklog.WorkLogEntry.create(
                MEMBER_ID,
                com.worklog.domain.project.ProjectId.of(UUID.randomUUID()),
                FISCAL_MONTH_DATE,
                com.worklog.domain.shared.TimeAmount.of(4.0),
                "test comment",
                MEMBER_ID);
        draftEntry.clearUncommittedEvents();

        when(workLogRepository.findById(com.worklog.domain.worklog.WorkLogEntryId.of(entryId)))
                .thenReturn(Optional.of(draftEntry));

        // Act
        MonthlyApprovalId result = service.submitMonth(command);

        // Assert: succeeds without calling isSubordinateOf
        assertNotNull(result);
        verify(memberRepository, never()).isSubordinateOf(any(), any());
        verify(approvalRepository).save(any());
    }

    @Test
    @DisplayName("should allow proxy submission when manager is authorized")
    void shouldAllowProxySubmissionWhenManagerIsAuthorized() {
        // Arrange: manager submits on behalf of member, isSubordinateOf returns true
        SubmitMonthForApprovalCommand command = new SubmitMonthForApprovalCommand(MEMBER_ID, FISCAL_MONTH, MANAGER_ID);

        when(memberRepository.isSubordinateOf(MANAGER_ID, MEMBER_ID)).thenReturn(true);

        // No existing approval for this member/month
        when(approvalRepository.findByMemberAndFiscalMonth(MEMBER_ID, FISCAL_MONTH))
                .thenReturn(Optional.empty());

        // At least one work log entry exists in the fiscal month
        UUID entryId = UUID.randomUUID();
        when(approvalRepository.findWorkLogEntryIds(
                        MEMBER_ID.value(), FISCAL_MONTH.startDate(), FISCAL_MONTH.endDate()))
                .thenReturn(List.of(entryId));

        // No absences
        when(approvalRepository.findAbsenceIds(MEMBER_ID.value(), FISCAL_MONTH.startDate(), FISCAL_MONTH.endDate()))
                .thenReturn(Collections.emptyList());

        // Mock the work log entry lookup for status transition
        com.worklog.domain.worklog.WorkLogEntry draftEntry = com.worklog.domain.worklog.WorkLogEntry.create(
                MEMBER_ID,
                com.worklog.domain.project.ProjectId.of(UUID.randomUUID()),
                FISCAL_MONTH_DATE,
                com.worklog.domain.shared.TimeAmount.of(4.0),
                "test comment",
                MEMBER_ID);
        draftEntry.clearUncommittedEvents();

        when(workLogRepository.findById(com.worklog.domain.worklog.WorkLogEntryId.of(entryId)))
                .thenReturn(Optional.of(draftEntry));

        // Act
        MonthlyApprovalId result = service.submitMonth(command);

        // Assert: succeeds and proxy check was performed
        assertNotNull(result);
        verify(memberRepository).isSubordinateOf(MANAGER_ID, MEMBER_ID);
        verify(approvalRepository).save(any());
    }

    @Test
    @DisplayName("should reject proxy submission when not authorized")
    void shouldRejectProxySubmissionWhenNotAuthorized() {
        // Arrange: non-manager attempts proxy submission, isSubordinateOf returns false
        MemberId nonManagerId = MemberId.of(UUID.randomUUID());

        SubmitMonthForApprovalCommand command =
                new SubmitMonthForApprovalCommand(MEMBER_ID, FISCAL_MONTH, nonManagerId);

        when(memberRepository.isSubordinateOf(nonManagerId, MEMBER_ID)).thenReturn(false);

        // Act & Assert
        DomainException exception = assertThrows(DomainException.class, () -> service.submitMonth(command));

        assertEquals("PROXY_ENTRY_NOT_ALLOWED", exception.getErrorCode());
        assertTrue(exception.getMessage().contains(MEMBER_ID.toString()));

        // Approval repository should never be called
        verify(approvalRepository, never()).save(any());
        verify(approvalRepository, never()).findByMemberAndFiscalMonth(any(), any());
    }
}
