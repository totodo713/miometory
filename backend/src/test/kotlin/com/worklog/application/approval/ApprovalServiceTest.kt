package com.worklog.application.approval

import com.worklog.domain.absence.Absence
import com.worklog.domain.absence.AbsenceId
import com.worklog.domain.absence.AbsenceStatus
import com.worklog.domain.absence.AbsenceType
import com.worklog.domain.approval.MonthlyApproval
import com.worklog.domain.approval.MonthlyApprovalId
import com.worklog.domain.member.MemberId
import com.worklog.domain.project.ProjectId
import com.worklog.domain.shared.DomainException
import com.worklog.domain.shared.FiscalMonthPeriod
import com.worklog.domain.shared.TimeAmount
import com.worklog.domain.worklog.WorkLogEntry
import com.worklog.domain.worklog.WorkLogEntryId
import com.worklog.domain.worklog.WorkLogStatus
import com.worklog.infrastructure.repository.JdbcAbsenceRepository
import com.worklog.infrastructure.repository.JdbcApprovalRepository
import com.worklog.infrastructure.repository.JdbcWorkLogRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Unit tests for ApprovalService.
 *
 * Tests the approval workflow coordination across aggregates:
 * - MonthlyApproval (approval record)
 * - WorkLogEntry (work hours)
 * - Absence (absence hours)
 *
 * Uses Mockito to mock repository dependencies.
 */
@ExtendWith(MockitoExtension::class)
class ApprovalServiceTest {
    @Mock
    private lateinit var approvalRepository: JdbcApprovalRepository

    @Mock
    private lateinit var workLogRepository: JdbcWorkLogRepository

    @Mock
    private lateinit var absenceRepository: JdbcAbsenceRepository

    private lateinit var approvalService: ApprovalService

    // Test fixtures
    private val memberId = MemberId.of(UUID.randomUUID())
    private val managerId = MemberId.of(UUID.randomUUID())
    private val projectId = ProjectId.of(UUID.randomUUID())
    private val fiscalMonth =
        FiscalMonthPeriod(
            LocalDate.of(2024, 1, 21),
            LocalDate.of(2024, 2, 20),
        )

    @BeforeEach
    fun setUp() {
        approvalService =
            ApprovalService(
                approvalRepository,
                workLogRepository,
                absenceRepository,
            )
    }

    @Nested
    @DisplayName("submitMonth")
    inner class SubmitMonthTests {
        @Test
        fun `should create new approval when none exists and submit successfully`() {
            // Given
            val command = SubmitMonthForApprovalCommand(memberId, fiscalMonth, memberId)
            val workLogEntryId = UUID.randomUUID()
            val absenceId = UUID.randomUUID()

            `when`(approvalRepository.findByMemberAndFiscalMonth(memberId, fiscalMonth))
                .thenReturn(Optional.empty())

            `when`(
                approvalRepository.findWorkLogEntryIds(
                    memberId.value(),
                    fiscalMonth.startDate(),
                    fiscalMonth.endDate(),
                ),
            ).thenReturn(listOf(workLogEntryId))

            `when`(
                approvalRepository.findAbsenceIds(
                    memberId.value(),
                    fiscalMonth.startDate(),
                    fiscalMonth.endDate(),
                ),
            ).thenReturn(listOf(absenceId))

            val workLogEntry = createWorkLogEntry()
            `when`(workLogRepository.findById(WorkLogEntryId.of(workLogEntryId)))
                .thenReturn(Optional.of(workLogEntry))

            val absence = createAbsence()
            `when`(absenceRepository.findById(AbsenceId.of(absenceId)))
                .thenReturn(Optional.of(absence))

            // When
            val result = approvalService.submitMonth(command)

            // Then
            assertNotNull(result)
            verify(approvalRepository).save(any())
            verify(workLogRepository).save(any())
            verify(absenceRepository).save(any())
        }

        @Test
        fun `should use existing approval when one exists`() {
            // Given
            val command = SubmitMonthForApprovalCommand(memberId, fiscalMonth, memberId)
            val existingApproval = MonthlyApproval.create(memberId, fiscalMonth)

            `when`(approvalRepository.findByMemberAndFiscalMonth(memberId, fiscalMonth))
                .thenReturn(Optional.of(existingApproval))

            `when`(
                approvalRepository.findWorkLogEntryIds(
                    memberId.value(),
                    fiscalMonth.startDate(),
                    fiscalMonth.endDate(),
                ),
            ).thenReturn(emptyList())

            `when`(
                approvalRepository.findAbsenceIds(
                    memberId.value(),
                    fiscalMonth.startDate(),
                    fiscalMonth.endDate(),
                ),
            ).thenReturn(emptyList())

            // When
            val result = approvalService.submitMonth(command)

            // Then
            assertNotNull(result)
            assertEquals(existingApproval.id, result)
        }

        @Test
        fun `should submit month with no entries successfully`() {
            // Given
            val command = SubmitMonthForApprovalCommand(memberId, fiscalMonth, memberId)

            `when`(approvalRepository.findByMemberAndFiscalMonth(memberId, fiscalMonth))
                .thenReturn(Optional.empty())

            `when`(
                approvalRepository.findWorkLogEntryIds(
                    memberId.value(),
                    fiscalMonth.startDate(),
                    fiscalMonth.endDate(),
                ),
            ).thenReturn(emptyList())

            `when`(
                approvalRepository.findAbsenceIds(
                    memberId.value(),
                    fiscalMonth.startDate(),
                    fiscalMonth.endDate(),
                ),
            ).thenReturn(emptyList())

            // When
            val result = approvalService.submitMonth(command)

            // Then
            assertNotNull(result)
            verify(approvalRepository).save(any())
        }

        @Test
        fun `should throw exception when work log entry not found`() {
            // Given
            val command = SubmitMonthForApprovalCommand(memberId, fiscalMonth, memberId)
            val workLogEntryId = UUID.randomUUID()

            `when`(approvalRepository.findByMemberAndFiscalMonth(memberId, fiscalMonth))
                .thenReturn(Optional.empty())

            `when`(
                approvalRepository.findWorkLogEntryIds(
                    memberId.value(),
                    fiscalMonth.startDate(),
                    fiscalMonth.endDate(),
                ),
            ).thenReturn(listOf(workLogEntryId))

            `when`(
                approvalRepository.findAbsenceIds(
                    memberId.value(),
                    fiscalMonth.startDate(),
                    fiscalMonth.endDate(),
                ),
            ).thenReturn(emptyList())

            `when`(workLogRepository.findById(WorkLogEntryId.of(workLogEntryId)))
                .thenReturn(Optional.empty())

            // When/Then
            val exception =
                assertFailsWith<DomainException> {
                    approvalService.submitMonth(command)
                }
            assertEquals("WORK_LOG_ENTRY_NOT_FOUND", exception.errorCode)
        }

        @Test
        fun `should throw exception when absence not found`() {
            // Given
            val command = SubmitMonthForApprovalCommand(memberId, fiscalMonth, memberId)
            val absenceId = UUID.randomUUID()

            `when`(approvalRepository.findByMemberAndFiscalMonth(memberId, fiscalMonth))
                .thenReturn(Optional.empty())

            `when`(
                approvalRepository.findWorkLogEntryIds(
                    memberId.value(),
                    fiscalMonth.startDate(),
                    fiscalMonth.endDate(),
                ),
            ).thenReturn(emptyList())

            `when`(
                approvalRepository.findAbsenceIds(
                    memberId.value(),
                    fiscalMonth.startDate(),
                    fiscalMonth.endDate(),
                ),
            ).thenReturn(listOf(absenceId))

            `when`(absenceRepository.findById(AbsenceId.of(absenceId)))
                .thenReturn(Optional.empty())

            // When/Then
            val exception =
                assertFailsWith<DomainException> {
                    approvalService.submitMonth(command)
                }
            assertEquals("ABSENCE_NOT_FOUND", exception.errorCode)
        }

        @Test
        fun `should submit month with multiple work log entries`() {
            // Given
            val command = SubmitMonthForApprovalCommand(memberId, fiscalMonth, memberId)
            val workLogEntryId1 = UUID.randomUUID()
            val workLogEntryId2 = UUID.randomUUID()
            val workLogEntryId3 = UUID.randomUUID()

            `when`(approvalRepository.findByMemberAndFiscalMonth(memberId, fiscalMonth))
                .thenReturn(Optional.empty())

            `when`(
                approvalRepository.findWorkLogEntryIds(
                    memberId.value(),
                    fiscalMonth.startDate(),
                    fiscalMonth.endDate(),
                ),
            ).thenReturn(listOf(workLogEntryId1, workLogEntryId2, workLogEntryId3))

            `when`(
                approvalRepository.findAbsenceIds(
                    memberId.value(),
                    fiscalMonth.startDate(),
                    fiscalMonth.endDate(),
                ),
            ).thenReturn(emptyList())

            `when`(workLogRepository.findById(WorkLogEntryId.of(workLogEntryId1)))
                .thenReturn(Optional.of(createWorkLogEntry()))
            `when`(workLogRepository.findById(WorkLogEntryId.of(workLogEntryId2)))
                .thenReturn(Optional.of(createWorkLogEntry()))
            `when`(workLogRepository.findById(WorkLogEntryId.of(workLogEntryId3)))
                .thenReturn(Optional.of(createWorkLogEntry()))

            // When
            val result = approvalService.submitMonth(command)

            // Then
            assertNotNull(result)
            verify(workLogRepository, times(3)).save(any())
        }
    }

    @Nested
    @DisplayName("approveMonth")
    inner class ApproveMonthTests {
        @Test
        fun `should approve submitted month successfully`() {
            // Given
            val approval = createSubmittedApproval()
            val approvalId = approval.id
            val command = ApproveMonthCommand(approvalId, managerId)

            `when`(approvalRepository.findById(approvalId))
                .thenReturn(Optional.of(approval))

            // When
            approvalService.approveMonth(command)

            // Then
            verify(approvalRepository).save(any())
        }

        @Test
        fun `should throw exception when approval not found`() {
            // Given
            val approvalId = MonthlyApprovalId.generate()
            val command = ApproveMonthCommand(approvalId, managerId)

            `when`(approvalRepository.findById(approvalId))
                .thenReturn(Optional.empty())

            // When/Then
            val exception =
                assertFailsWith<DomainException> {
                    approvalService.approveMonth(command)
                }
            assertEquals("APPROVAL_NOT_FOUND", exception.errorCode)
        }

        @Test
        fun `should update all work log entries to APPROVED status`() {
            // Given
            val approval = createSubmittedApprovalWithEntries()
            val approvalId = approval.id
            val command = ApproveMonthCommand(approvalId, managerId)

            `when`(approvalRepository.findById(approvalId))
                .thenReturn(Optional.of(approval))

            for (entryId in approval.workLogEntryIds) {
                val entry = createSubmittedWorkLogEntry()
                `when`(workLogRepository.findById(WorkLogEntryId.of(entryId)))
                    .thenReturn(Optional.of(entry))
            }

            for (absenceId in approval.absenceIds) {
                val absence = createSubmittedAbsence()
                `when`(absenceRepository.findById(AbsenceId.of(absenceId)))
                    .thenReturn(Optional.of(absence))
            }

            // When
            approvalService.approveMonth(command)

            // Then
            verify(approvalRepository).save(any())
        }

        @Test
        fun `should throw exception when work log entry not found during approval`() {
            // Given
            val approval = createSubmittedApprovalWithEntries()
            val approvalId = approval.id
            val command = ApproveMonthCommand(approvalId, managerId)

            `when`(approvalRepository.findById(approvalId))
                .thenReturn(Optional.of(approval))

            // Return empty for first entry
            val firstEntryId = approval.workLogEntryIds.first()
            `when`(workLogRepository.findById(WorkLogEntryId.of(firstEntryId)))
                .thenReturn(Optional.empty())

            // When/Then
            val exception =
                assertFailsWith<DomainException> {
                    approvalService.approveMonth(command)
                }
            assertEquals("WORK_LOG_ENTRY_NOT_FOUND", exception.errorCode)
        }
    }

    @Nested
    @DisplayName("rejectMonth")
    inner class RejectMonthTests {
        @Test
        fun `should reject submitted month successfully`() {
            // Given
            val approval = createSubmittedApproval()
            val approvalId = approval.id
            val command = RejectMonthCommand(approvalId, managerId, "Missing project details")

            `when`(approvalRepository.findById(approvalId))
                .thenReturn(Optional.of(approval))

            // When
            approvalService.rejectMonth(command)

            // Then
            verify(approvalRepository).save(any())
        }

        @Test
        fun `should throw exception when approval not found for rejection`() {
            // Given
            val approvalId = MonthlyApprovalId.generate()
            val command = RejectMonthCommand(approvalId, managerId, "Rejection reason")

            `when`(approvalRepository.findById(approvalId))
                .thenReturn(Optional.empty())

            // When/Then
            val exception =
                assertFailsWith<DomainException> {
                    approvalService.rejectMonth(command)
                }
            assertEquals("APPROVAL_NOT_FOUND", exception.errorCode)
        }

        @Test
        fun `should update all work log entries back to DRAFT status on rejection`() {
            // Given
            val approval = createSubmittedApprovalWithEntries()
            val approvalId = approval.id
            val command = RejectMonthCommand(approvalId, managerId, "Hours seem incorrect")

            `when`(approvalRepository.findById(approvalId))
                .thenReturn(Optional.of(approval))

            for (entryId in approval.workLogEntryIds) {
                val entry = createSubmittedWorkLogEntry()
                `when`(workLogRepository.findById(WorkLogEntryId.of(entryId)))
                    .thenReturn(Optional.of(entry))
            }

            for (absenceId in approval.absenceIds) {
                val absence = createSubmittedAbsence()
                `when`(absenceRepository.findById(AbsenceId.of(absenceId)))
                    .thenReturn(Optional.of(absence))
            }

            // When
            approvalService.rejectMonth(command)

            // Then
            verify(approvalRepository).save(any())
        }

        @Test
        fun `should throw exception when absence not found during rejection`() {
            // Given
            val approval = createSubmittedApprovalWithEntries()
            val approvalId = approval.id
            val command = RejectMonthCommand(approvalId, managerId, "Need more details")

            `when`(approvalRepository.findById(approvalId))
                .thenReturn(Optional.of(approval))

            // Work log entries found successfully
            for (entryId in approval.workLogEntryIds) {
                val entry = createSubmittedWorkLogEntry()
                `when`(workLogRepository.findById(WorkLogEntryId.of(entryId)))
                    .thenReturn(Optional.of(entry))
            }

            // Return empty for first absence
            val firstAbsenceId = approval.absenceIds.first()
            `when`(absenceRepository.findById(AbsenceId.of(firstAbsenceId)))
                .thenReturn(Optional.empty())

            // When/Then
            val exception =
                assertFailsWith<DomainException> {
                    approvalService.rejectMonth(command)
                }
            assertEquals("ABSENCE_NOT_FOUND", exception.errorCode)
        }
    }

    @Nested
    @DisplayName("Command validation")
    inner class CommandValidationTests {
        @Test
        fun `SubmitMonthForApprovalCommand should reject null memberId`() {
            assertFailsWith<IllegalArgumentException> {
                SubmitMonthForApprovalCommand(null, fiscalMonth, memberId)
            }
        }

        @Test
        fun `SubmitMonthForApprovalCommand should reject null fiscalMonth`() {
            assertFailsWith<IllegalArgumentException> {
                SubmitMonthForApprovalCommand(memberId, null, memberId)
            }
        }

        @Test
        fun `SubmitMonthForApprovalCommand should reject null submittedBy`() {
            assertFailsWith<IllegalArgumentException> {
                SubmitMonthForApprovalCommand(memberId, fiscalMonth, null)
            }
        }

        @Test
        fun `ApproveMonthCommand should reject null approvalId`() {
            assertFailsWith<IllegalArgumentException> {
                ApproveMonthCommand(null, managerId)
            }
        }

        @Test
        fun `ApproveMonthCommand should reject null reviewedBy`() {
            assertFailsWith<IllegalArgumentException> {
                ApproveMonthCommand(MonthlyApprovalId.generate(), null)
            }
        }

        @Test
        fun `RejectMonthCommand should reject null approvalId`() {
            assertFailsWith<IllegalArgumentException> {
                RejectMonthCommand(null, managerId, "Reason")
            }
        }

        @Test
        fun `RejectMonthCommand should reject null reviewedBy`() {
            assertFailsWith<IllegalArgumentException> {
                RejectMonthCommand(MonthlyApprovalId.generate(), null, "Reason")
            }
        }

        @Test
        fun `RejectMonthCommand should reject null rejectionReason`() {
            assertFailsWith<IllegalArgumentException> {
                RejectMonthCommand(MonthlyApprovalId.generate(), managerId, null)
            }
        }

        @Test
        fun `RejectMonthCommand should reject blank rejectionReason`() {
            assertFailsWith<IllegalArgumentException> {
                RejectMonthCommand(MonthlyApprovalId.generate(), managerId, "   ")
            }
        }

        @Test
        fun `RejectMonthCommand should reject rejectionReason exceeding 1000 characters`() {
            val longReason = "a".repeat(1001)
            assertFailsWith<IllegalArgumentException> {
                RejectMonthCommand(MonthlyApprovalId.generate(), managerId, longReason)
            }
        }

        @Test
        fun `RejectMonthCommand should accept rejectionReason of exactly 1000 characters`() {
            val maxReason = "a".repeat(1000)
            val command = RejectMonthCommand(MonthlyApprovalId.generate(), managerId, maxReason)
            assertEquals(1000, command.rejectionReason().length)
        }
    }

    // Helper methods to create test fixtures

    private fun createWorkLogEntry(): WorkLogEntry = WorkLogEntry.create(
        memberId,
        projectId,
        LocalDate.of(2024, 1, 25),
        TimeAmount.of(java.math.BigDecimal("8.00")),
        "Test work",
        memberId,
    )

    private fun createSubmittedWorkLogEntry(): WorkLogEntry {
        val entry =
            WorkLogEntry.create(
                memberId,
                projectId,
                LocalDate.of(2024, 1, 25),
                TimeAmount.of(java.math.BigDecimal("8.00")),
                "Test work",
                memberId,
            )
        entry.changeStatus(WorkLogStatus.SUBMITTED, memberId)
        return entry
    }

    private fun createAbsence(): Absence = Absence.record(
        memberId,
        LocalDate.of(2024, 1, 26),
        TimeAmount.of(java.math.BigDecimal("8.00")),
        AbsenceType.PAID_LEAVE,
        "Vacation",
        memberId,
    )

    private fun createSubmittedAbsence(): Absence {
        val absence =
            Absence.record(
                memberId,
                LocalDate.of(2024, 1, 26),
                TimeAmount.of(java.math.BigDecimal("8.00")),
                AbsenceType.PAID_LEAVE,
                "Vacation",
                memberId,
            )
        absence.changeStatus(AbsenceStatus.SUBMITTED, memberId)
        return absence
    }

    private fun createSubmittedApproval(): MonthlyApproval {
        val approval = MonthlyApproval.create(memberId, fiscalMonth)
        approval.submit(memberId, emptySet(), emptySet())
        return approval
    }

    private fun createSubmittedApprovalWithEntries(): MonthlyApproval {
        val approval = MonthlyApproval.create(memberId, fiscalMonth)
        val workLogIds =
            setOf(
                WorkLogEntryId.of(UUID.randomUUID()),
                WorkLogEntryId.of(UUID.randomUUID()),
            )
        val absenceIds =
            setOf(
                AbsenceId.of(UUID.randomUUID()),
            )
        approval.submit(memberId, workLogIds, absenceIds)
        return approval
    }
}
