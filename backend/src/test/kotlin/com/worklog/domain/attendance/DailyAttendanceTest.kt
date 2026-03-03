package com.worklog.domain.attendance

import com.worklog.domain.member.MemberId
import com.worklog.domain.tenant.TenantId
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for DailyAttendance entity.
 */
class DailyAttendanceTest {
    private val tenantId = TenantId(UUID.randomUUID())
    private val memberId = MemberId(UUID.randomUUID())
    private val attendanceDate = LocalDate.of(2025, 6, 15)

    @Test
    fun `create should generate id and set defaults`() {
        val attendance =
            DailyAttendance.create(
                tenantId,
                memberId,
                attendanceDate,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                "Normal day",
            )

        assertNotNull(attendance.id)
        assertEquals(tenantId, attendance.tenantId)
        assertEquals(memberId, attendance.memberId)
        assertEquals(attendanceDate, attendance.attendanceDate)
        assertEquals(LocalTime.of(9, 0), attendance.startTime)
        assertEquals(LocalTime.of(18, 0), attendance.endTime)
        assertEquals("Normal day", attendance.remarks)
        assertEquals(0, attendance.version)
    }

    @Test
    fun `create should allow null times and remarks`() {
        val attendance =
            DailyAttendance.create(
                tenantId,
                memberId,
                attendanceDate,
                null,
                null,
                null,
            )

        assertNotNull(attendance.id)
        assertEquals(tenantId, attendance.tenantId)
        assertEquals(memberId, attendance.memberId)
        assertEquals(attendanceDate, attendance.attendanceDate)
        assertNull(attendance.startTime)
        assertNull(attendance.endTime)
        assertNull(attendance.remarks)
        assertEquals(0, attendance.version)
    }

    @Test
    fun `should reject endTime before startTime`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                DailyAttendance.create(
                    tenantId,
                    memberId,
                    attendanceDate,
                    LocalTime.of(18, 0),
                    LocalTime.of(9, 0),
                    null,
                )
            }

        assertEquals("End time cannot be before start time", exception.message)
    }

    @Test
    fun `should reject remarks over 500 characters`() {
        val longRemarks = "A".repeat(501)

        val exception =
            assertFailsWith<IllegalArgumentException> {
                DailyAttendance.create(
                    tenantId,
                    memberId,
                    attendanceDate,
                    null,
                    null,
                    longRemarks,
                )
            }

        assertEquals("Remarks cannot exceed 500 characters", exception.message)
    }

    @Test
    fun `should allow equal startTime and endTime`() {
        val time = LocalTime.of(12, 0)

        val attendance =
            DailyAttendance.create(
                tenantId,
                memberId,
                attendanceDate,
                time,
                time,
                null,
            )

        assertEquals(time, attendance.startTime)
        assertEquals(time, attendance.endTime)
    }
}
