package com.worklog.application.service

import com.worklog.domain.attendance.DailyAttendance
import com.worklog.domain.attendance.DailyAttendanceId
import com.worklog.domain.member.MemberId
import com.worklog.domain.tenant.TenantId
import com.worklog.infrastructure.repository.JdbcDailyAttendanceRepository
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class DailyAttendanceServiceTest {

    private val attendanceRepository = mockk<JdbcDailyAttendanceRepository>()
    private lateinit var service: DailyAttendanceService

    private val testTenantId = TenantId.of(UUID.randomUUID())
    private val testMemberId = MemberId.of(UUID.randomUUID())
    private val testDate = LocalDate.of(2026, 3, 1)
    private val testStartTime = LocalTime.of(9, 0)
    private val testEndTime = LocalTime.of(18, 0)

    @BeforeEach
    fun setup() {
        clearMocks(attendanceRepository)
        service = DailyAttendanceService(attendanceRepository)
    }

    @Test
    fun `saveAttendance should create new when none exists`() {
        every { attendanceRepository.findByMemberAndDate(testMemberId, testDate) } returns null
        every { attendanceRepository.save(any()) } just Runs

        val result = service.saveAttendance(
            testTenantId,
            testMemberId,
            testDate,
            testStartTime,
            testEndTime,
            "Test remarks",
            null,
        )

        assertNotNull(result)
        verify(exactly = 1) { attendanceRepository.save(any()) }
    }

    @Test
    fun `saveAttendance should update existing with matching version`() {
        val existingId = DailyAttendanceId.generate()
        val existing = DailyAttendance(
            existingId,
            testTenantId,
            testMemberId,
            testDate,
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
            "Old remarks",
            1,
        )

        every { attendanceRepository.findByMemberAndDate(testMemberId, testDate) } returns existing
        every { attendanceRepository.save(any()) } just Runs

        val result = service.saveAttendance(
            testTenantId,
            testMemberId,
            testDate,
            testStartTime,
            testEndTime,
            "Updated remarks",
            1,
        )

        assertEquals(existingId.value(), result)
        verify(exactly = 1) { attendanceRepository.save(any()) }
    }

    @Test
    fun `saveAttendance should throw on version mismatch`() {
        val existing = DailyAttendance(
            DailyAttendanceId.generate(),
            testTenantId,
            testMemberId,
            testDate,
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
            "Old remarks",
            2,
        )

        every { attendanceRepository.findByMemberAndDate(testMemberId, testDate) } returns existing

        assertThrows(IllegalStateException::class.java) {
            service.saveAttendance(
                testTenantId,
                testMemberId,
                testDate,
                testStartTime,
                testEndTime,
                "Updated remarks",
                1,
            )
        }

        verify(exactly = 0) { attendanceRepository.save(any()) }
    }

    @Test
    fun `deleteAttendance should delegate to repository`() {
        every { attendanceRepository.deleteByMemberAndDate(testMemberId, testDate) } just Runs

        service.deleteAttendance(testMemberId, testDate)

        verify(exactly = 1) { attendanceRepository.deleteByMemberAndDate(testMemberId, testDate) }
    }
}
