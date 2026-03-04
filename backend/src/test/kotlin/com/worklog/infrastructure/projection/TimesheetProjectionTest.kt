package com.worklog.infrastructure.projection

import com.worklog.application.service.DailyAttendanceService
import com.worklog.domain.attendance.DailyAttendance
import com.worklog.domain.attendance.DailyAttendanceId
import com.worklog.domain.member.MemberId
import com.worklog.domain.tenant.TenantId
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for TimesheetProjection.
 */
class TimesheetProjectionTest {

    private val jdbcTemplate = mockk<JdbcTemplate>()
    private val dailyAttendanceService = mockk<DailyAttendanceService>()
    private lateinit var projection: TimesheetProjection

    private val memberId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val tenantId = TenantId.of(UUID.randomUUID())
    private val memberIdDomain = MemberId.of(memberId)

    @BeforeEach
    fun setup() {
        clearMocks(jdbcTemplate, dailyAttendanceService)
        projection = TimesheetProjection(jdbcTemplate, dailyAttendanceService)
    }

    private fun mockNoWorkHours() {
        every {
            jdbcTemplate.queryForList(any<String>(), *anyVararg())
        } returns emptyList<Map<String, Any>>()
    }

    private fun mockNoAttendance() {
        every {
            dailyAttendanceService.getAttendanceRange(any(), any(), any())
        } returns emptyList()
    }

    @Test
    fun `buildRows should create one row per day in range`() {
        mockNoWorkHours()
        mockNoAttendance()

        val start = LocalDate.of(2026, 3, 1) // Sunday
        val end = LocalDate.of(2026, 3, 3) // Tuesday

        val rows = projection.buildRows(
            memberId,
            projectId,
            start,
            end,
            null,
            null,
            emptySet(),
            emptyMap(),
        )

        assertEquals(3, rows.size)
        assertEquals(LocalDate.of(2026, 3, 1), rows[0].date())
        assertEquals(LocalDate.of(2026, 3, 2), rows[1].date())
        assertEquals(LocalDate.of(2026, 3, 3), rows[2].date())
    }

    @Test
    fun `buildRows should mark weekends correctly`() {
        mockNoWorkHours()
        mockNoAttendance()

        // 2026-03-07 = Saturday, 2026-03-08 = Sunday, 2026-03-09 = Monday
        val start = LocalDate.of(2026, 3, 7)
        val end = LocalDate.of(2026, 3, 9)

        val rows = projection.buildRows(
            memberId,
            projectId,
            start,
            end,
            null,
            null,
            emptySet(),
            emptyMap(),
        )

        assertTrue(rows[0].isWeekend(), "Saturday should be weekend")
        assertTrue(rows[1].isWeekend(), "Sunday should be weekend")
        assertFalse(rows[2].isWeekend(), "Monday should not be weekend")
    }

    @Test
    fun `buildRows should mark holidays correctly`() {
        mockNoWorkHours()
        mockNoAttendance()

        val start = LocalDate.of(2026, 3, 2)
        val end = LocalDate.of(2026, 3, 3)
        val holidays = setOf(LocalDate.of(2026, 3, 3))
        val holidayNames = mapOf(LocalDate.of(2026, 3, 3) to "National Day")

        val rows = projection.buildRows(
            memberId,
            projectId,
            start,
            end,
            null,
            null,
            holidays,
            holidayNames,
        )

        assertFalse(rows[0].isHoliday())
        assertNull(rows[0].holidayName())
        assertTrue(rows[1].isHoliday())
        assertEquals("National Day", rows[1].holidayName())
    }

    @Test
    fun `buildRows should include day of week names`() {
        mockNoWorkHours()
        mockNoAttendance()

        // 2026-03-02 = Monday
        val start = LocalDate.of(2026, 3, 2)
        val end = LocalDate.of(2026, 3, 2)

        val rows = projection.buildRows(
            memberId,
            projectId,
            start,
            end,
            null,
            null,
            emptySet(),
            emptyMap(),
        )

        assertEquals("Mon", rows[0].dayOfWeek())
    }

    @Test
    fun `buildRows should include work hours from projection`() {
        val date = LocalDate.of(2026, 3, 2)

        every {
            jdbcTemplate.queryForList(any<String>(), *anyVararg())
        } returns listOf<Map<String, Any>>(
            mapOf(
                "work_date" to java.sql.Date.valueOf(date),
                "total_hours" to BigDecimal("8.00"),
            ),
        )
        mockNoAttendance()

        val rows = projection.buildRows(
            memberId,
            projectId,
            date,
            date,
            null,
            null,
            emptySet(),
            emptyMap(),
        )

        assertEquals(BigDecimal("8.00"), rows[0].workingHours())
    }

    @Test
    fun `buildRows should return zero hours when no work logged`() {
        mockNoWorkHours()
        mockNoAttendance()

        val rows = projection.buildRows(
            memberId,
            projectId,
            LocalDate.of(2026, 3, 2),
            LocalDate.of(2026, 3, 2),
            null,
            null,
            emptySet(),
            emptyMap(),
        )

        assertEquals(BigDecimal.ZERO, rows[0].workingHours())
    }

    @Test
    fun `buildRows should include attendance data when present`() {
        mockNoWorkHours()

        val date = LocalDate.of(2026, 3, 2)
        val attendanceId = DailyAttendanceId.generate()
        val attendance = DailyAttendance(
            attendanceId,
            tenantId,
            memberIdDomain,
            date,
            LocalTime.of(9, 0),
            LocalTime.of(18, 0),
            "Good day",
            2,
        )

        every {
            dailyAttendanceService.getAttendanceRange(any(), any(), any())
        } returns listOf(attendance)

        val rows = projection.buildRows(
            memberId,
            projectId,
            date,
            date,
            null,
            null,
            emptySet(),
            emptyMap(),
        )

        assertTrue(rows[0].hasAttendanceRecord())
        assertEquals(LocalTime.of(9, 0), rows[0].startTime())
        assertEquals(LocalTime.of(18, 0), rows[0].endTime())
        assertEquals("Good day", rows[0].remarks())
        assertEquals(attendanceId.value(), rows[0].attendanceId())
        assertEquals(2, rows[0].attendanceVersion())
    }

    @Test
    fun `buildRows should handle no attendance record`() {
        mockNoWorkHours()
        mockNoAttendance()

        val rows = projection.buildRows(
            memberId,
            projectId,
            LocalDate.of(2026, 3, 2),
            LocalDate.of(2026, 3, 2),
            null,
            null,
            emptySet(),
            emptyMap(),
        )

        assertFalse(rows[0].hasAttendanceRecord())
        assertNull(rows[0].startTime())
        assertNull(rows[0].endTime())
        assertNull(rows[0].remarks())
        assertNull(rows[0].attendanceId())
        assertNull(rows[0].attendanceVersion())
    }

    @Test
    fun `buildRows should pass default times through to rows`() {
        mockNoWorkHours()
        mockNoAttendance()

        val defaultStart = LocalTime.of(9, 0)
        val defaultEnd = LocalTime.of(17, 30)

        val rows = projection.buildRows(
            memberId,
            projectId,
            LocalDate.of(2026, 3, 2),
            LocalDate.of(2026, 3, 2),
            defaultStart,
            defaultEnd,
            emptySet(),
            emptyMap(),
        )

        assertEquals(defaultStart, rows[0].defaultStartTime())
        assertEquals(defaultEnd, rows[0].defaultEndTime())
    }

    @Test
    fun `buildRows should pass null default times`() {
        mockNoWorkHours()
        mockNoAttendance()

        val rows = projection.buildRows(
            memberId,
            projectId,
            LocalDate.of(2026, 3, 2),
            LocalDate.of(2026, 3, 2),
            null,
            null,
            emptySet(),
            emptyMap(),
        )

        assertNull(rows[0].defaultStartTime())
        assertNull(rows[0].defaultEndTime())
    }

    @Test
    fun `buildRows should handle attendance with null times`() {
        mockNoWorkHours()

        val date = LocalDate.of(2026, 3, 2)
        val attendance = DailyAttendance(
            DailyAttendanceId.generate(),
            tenantId,
            memberIdDomain,
            date,
            null,
            null,
            "Holiday note",
            0,
        )

        every {
            dailyAttendanceService.getAttendanceRange(any(), any(), any())
        } returns listOf(attendance)

        val rows = projection.buildRows(
            memberId,
            projectId,
            date,
            date,
            null,
            null,
            emptySet(),
            emptyMap(),
        )

        assertTrue(rows[0].hasAttendanceRecord())
        assertNull(rows[0].startTime())
        assertNull(rows[0].endTime())
        assertEquals("Holiday note", rows[0].remarks())
    }

    @Test
    fun `buildRows should handle single day range`() {
        mockNoWorkHours()
        mockNoAttendance()

        val date = LocalDate.of(2026, 3, 2)

        val rows = projection.buildRows(
            memberId,
            projectId,
            date,
            date,
            null,
            null,
            emptySet(),
            emptyMap(),
        )

        assertEquals(1, rows.size)
        assertEquals(date, rows[0].date())
    }
}
