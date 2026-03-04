package com.worklog.domain.attendance

import com.worklog.domain.member.MemberId
import com.worklog.domain.tenant.TenantId
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `should allow only startTime set`() {
        val attendance =
            DailyAttendance.create(
                tenantId,
                memberId,
                attendanceDate,
                LocalTime.of(9, 0),
                null,
                null,
            )

        assertEquals(LocalTime.of(9, 0), attendance.startTime)
        assertNull(attendance.endTime)
    }

    @Test
    fun `should allow only endTime set`() {
        val attendance =
            DailyAttendance.create(
                tenantId,
                memberId,
                attendanceDate,
                null,
                LocalTime.of(18, 0),
                null,
            )

        assertNull(attendance.startTime)
        assertEquals(LocalTime.of(18, 0), attendance.endTime)
    }

    @Test
    fun `should allow remarks exactly 500 characters`() {
        val remarks = "A".repeat(500)

        val attendance =
            DailyAttendance.create(
                tenantId,
                memberId,
                attendanceDate,
                null,
                null,
                remarks,
            )

        assertEquals(500, attendance.remarks.length)
    }

    @Test
    fun `should allow empty remarks`() {
        val attendance =
            DailyAttendance.create(
                tenantId,
                memberId,
                attendanceDate,
                null,
                null,
                "",
            )

        assertEquals("", attendance.remarks)
    }

    @Test
    fun `constructor should reject null tenantId`() {
        assertFailsWith<NullPointerException> {
            DailyAttendance(
                DailyAttendanceId.generate(),
                null,
                memberId,
                attendanceDate,
                null,
                null,
                null,
                0,
            )
        }
    }

    @Test
    fun `constructor should reject null memberId`() {
        assertFailsWith<NullPointerException> {
            DailyAttendance(
                DailyAttendanceId.generate(),
                tenantId,
                null,
                attendanceDate,
                null,
                null,
                null,
                0,
            )
        }
    }

    @Test
    fun `constructor should reject null attendanceDate`() {
        assertFailsWith<NullPointerException> {
            DailyAttendance(
                DailyAttendanceId.generate(),
                tenantId,
                memberId,
                null,
                null,
                null,
                null,
                0,
            )
        }
    }

    @Test
    fun `update should change mutable fields`() {
        val attendance =
            DailyAttendance.create(
                tenantId,
                memberId,
                attendanceDate,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                "Original",
            )

        attendance.update(LocalTime.of(10, 0), LocalTime.of(19, 0), "Updated")

        assertEquals(LocalTime.of(10, 0), attendance.startTime)
        assertEquals(LocalTime.of(19, 0), attendance.endTime)
        assertEquals("Updated", attendance.remarks)
    }

    @Test
    fun `update should allow clearing times`() {
        val attendance =
            DailyAttendance.create(
                tenantId,
                memberId,
                attendanceDate,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                "note",
            )

        attendance.update(null, null, null)

        assertNull(attendance.startTime)
        assertNull(attendance.endTime)
        assertNull(attendance.remarks)
    }

    @Test
    fun `update should reject invalid time range`() {
        val attendance =
            DailyAttendance.create(
                tenantId,
                memberId,
                attendanceDate,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                null,
            )

        assertFailsWith<IllegalArgumentException> {
            attendance.update(LocalTime.of(18, 0), LocalTime.of(9, 0), null)
        }
    }

    @Test
    fun `update should reject long remarks`() {
        val attendance =
            DailyAttendance.create(
                tenantId,
                memberId,
                attendanceDate,
                null,
                null,
                null,
            )

        assertFailsWith<IllegalArgumentException> {
            attendance.update(null, null, "A".repeat(501))
        }
    }

    @Test
    fun `equals should be true for same id`() {
        val id = DailyAttendanceId.generate()
        val a1 = DailyAttendance(id, tenantId, memberId, attendanceDate, null, null, null, 0)
        val a2 = DailyAttendance(id, tenantId, memberId, attendanceDate, LocalTime.of(9, 0), null, "note", 1)

        assertEquals(a1, a2)
    }

    @Test
    fun `equals should be false for different id`() {
        val a1 =
            DailyAttendance(DailyAttendanceId.generate(), tenantId, memberId, attendanceDate, null, null, null, 0)
        val a2 =
            DailyAttendance(DailyAttendanceId.generate(), tenantId, memberId, attendanceDate, null, null, null, 0)

        assertNotEquals(a1, a2)
    }

    @Test
    fun `equals should be false for null`() {
        val a = DailyAttendance.create(tenantId, memberId, attendanceDate, null, null, null)

        assertNotEquals(null, a)
    }

    @Test
    fun `equals should be false for different type`() {
        val a = DailyAttendance.create(tenantId, memberId, attendanceDate, null, null, null)

        assertFalse(a.equals("not an attendance"))
    }

    @Test
    fun `hashCode should be consistent for same id`() {
        val id = DailyAttendanceId.generate()
        val a1 = DailyAttendance(id, tenantId, memberId, attendanceDate, null, null, null, 0)
        val a2 = DailyAttendance(id, tenantId, memberId, attendanceDate, null, null, null, 1)

        assertEquals(a1.hashCode(), a2.hashCode())
    }

    @Test
    fun `toString should contain key fields`() {
        val attendance =
            DailyAttendance.create(
                tenantId,
                memberId,
                attendanceDate,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                null,
            )
        val str = attendance.toString()

        assertTrue(str.contains("DailyAttendance"))
        assertTrue(str.contains(memberId.toString()))
        assertTrue(str.contains(attendanceDate.toString()))
    }
}
