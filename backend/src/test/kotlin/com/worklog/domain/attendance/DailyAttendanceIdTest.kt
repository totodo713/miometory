package com.worklog.domain.attendance

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * Unit tests for DailyAttendanceId value object.
 */
class DailyAttendanceIdTest {

    @Test
    fun `constructor should reject null value`() {
        assertFailsWith<IllegalArgumentException> {
            DailyAttendanceId(null)
        }.also {
            assertEquals("DailyAttendanceId value cannot be null", it.message)
        }
    }

    @Test
    fun `constructor should accept valid UUID`() {
        val uuid = UUID.randomUUID()
        val id = DailyAttendanceId(uuid)
        assertEquals(uuid, id.value())
    }

    @Test
    fun `generate should create unique id`() {
        val id1 = DailyAttendanceId.generate()
        val id2 = DailyAttendanceId.generate()

        assertNotNull(id1.value())
        assertNotNull(id2.value())
        assertNotEquals(id1, id2)
    }

    @Test
    fun `of UUID should wrap value`() {
        val uuid = UUID.randomUUID()
        val id = DailyAttendanceId.of(uuid)

        assertEquals(uuid, id.value())
    }

    @Test
    fun `of String should parse valid UUID string`() {
        val uuid = UUID.randomUUID()
        val id = DailyAttendanceId.of(uuid.toString())

        assertEquals(uuid, id.value())
    }

    @Test
    fun `of String should reject invalid UUID string`() {
        assertFailsWith<IllegalArgumentException> {
            DailyAttendanceId.of("not-a-uuid")
        }
    }

    @Test
    fun `toString should return UUID string representation`() {
        val uuid = UUID.randomUUID()
        val id = DailyAttendanceId(uuid)

        assertEquals(uuid.toString(), id.toString())
    }

    @Test
    fun `equals should be true for same value`() {
        val uuid = UUID.randomUUID()
        val id1 = DailyAttendanceId(uuid)
        val id2 = DailyAttendanceId(uuid)

        assertEquals(id1, id2)
    }

    @Test
    fun `equals should be false for different values`() {
        val id1 = DailyAttendanceId.generate()
        val id2 = DailyAttendanceId.generate()

        assertNotEquals(id1, id2)
    }

    @Test
    fun `hashCode should be consistent for same value`() {
        val uuid = UUID.randomUUID()
        val id1 = DailyAttendanceId(uuid)
        val id2 = DailyAttendanceId(uuid)

        assertEquals(id1.hashCode(), id2.hashCode())
    }
}
