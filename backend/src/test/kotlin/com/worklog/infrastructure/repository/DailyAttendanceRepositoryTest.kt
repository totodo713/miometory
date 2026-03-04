package com.worklog.infrastructure.repository

import com.worklog.IntegrationTestBase
import com.worklog.domain.attendance.DailyAttendance
import com.worklog.domain.member.MemberId
import com.worklog.domain.tenant.TenantId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@DisplayName("JdbcDailyAttendanceRepository")
class DailyAttendanceRepositoryTest : IntegrationTestBase() {

    @Autowired
    private lateinit var repository: JdbcDailyAttendanceRepository

    private val tenantId = TenantId.of(UUID.fromString(TEST_TENANT_ID))

    @Test
    @DisplayName("save should insert new attendance")
    fun saveShouldInsertNewAttendance() {
        val memberId = MemberId.of(UUID.randomUUID())
        createTestMember(memberId.value())

        val attendance = DailyAttendance.create(
            tenantId,
            memberId,
            LocalDate.of(2026, 3, 1),
            LocalTime.of(9, 0),
            LocalTime.of(18, 0),
            "Normal workday",
        )

        executeInNewTransaction { repository.save(attendance) }

        val found = repository.findByMemberAndDate(memberId, LocalDate.of(2026, 3, 1))
        assertNotNull(found)
        assertEquals(attendance.getId().value(), found!!.getId().value())
        assertEquals(memberId.value(), found.getMemberId().value())
        assertEquals(LocalDate.of(2026, 3, 1), found.getAttendanceDate())
        assertEquals(LocalTime.of(9, 0), found.getStartTime())
        assertEquals(LocalTime.of(18, 0), found.getEndTime())
        assertEquals("Normal workday", found.getRemarks())
        assertEquals(0, found.getVersion())
    }

    @Test
    @DisplayName("save should upsert on conflict")
    fun saveShouldUpsertOnConflict() {
        val memberId = MemberId.of(UUID.randomUUID())
        createTestMember(memberId.value())
        val date = LocalDate.of(2026, 3, 2)

        val first = DailyAttendance.create(
            tenantId,
            memberId,
            date,
            LocalTime.of(9, 0),
            LocalTime.of(17, 0),
            "First entry",
        )
        executeInNewTransaction { repository.save(first) }

        // Save again with same member+date but different times/remarks
        val second = DailyAttendance.create(
            tenantId,
            memberId,
            date,
            LocalTime.of(10, 0),
            LocalTime.of(19, 0),
            "Updated entry",
        )
        executeInNewTransaction { repository.save(second) }

        val found = repository.findByMemberAndDate(memberId, date)
        assertNotNull(found)
        // The upsert should have updated the fields
        assertEquals(LocalTime.of(10, 0), found!!.getStartTime())
        assertEquals(LocalTime.of(19, 0), found.getEndTime())
        assertEquals("Updated entry", found.getRemarks())
        // Version should have been incremented by the ON CONFLICT clause
        assertEquals(1, found.getVersion())
    }

    @Test
    @DisplayName("findByMemberAndDateRange should return entries in range")
    fun findByMemberAndDateRangeShouldReturnEntriesInRange() {
        val memberId = MemberId.of(UUID.randomUUID())
        createTestMember(memberId.value())

        // Insert 5 days: March 1-5
        for (day in 1..5) {
            val attendance = DailyAttendance.create(
                tenantId,
                memberId,
                LocalDate.of(2026, 3, day),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                "Day $day",
            )
            executeInNewTransaction { repository.save(attendance) }
        }

        // Query range March 2-4 (3 days)
        val results = repository.findByMemberAndDateRange(
            memberId,
            LocalDate.of(2026, 3, 2),
            LocalDate.of(2026, 3, 4),
        )

        assertEquals(3, results.size)
        assertEquals(LocalDate.of(2026, 3, 2), results[0].getAttendanceDate())
        assertEquals(LocalDate.of(2026, 3, 3), results[1].getAttendanceDate())
        assertEquals(LocalDate.of(2026, 3, 4), results[2].getAttendanceDate())
    }

    @Test
    @DisplayName("deleteByMemberAndDate should remove entry")
    fun deleteByMemberAndDateShouldRemoveEntry() {
        val memberId = MemberId.of(UUID.randomUUID())
        createTestMember(memberId.value())
        val date = LocalDate.of(2026, 3, 10)

        val attendance = DailyAttendance.create(
            tenantId,
            memberId,
            date,
            LocalTime.of(9, 0),
            LocalTime.of(18, 0),
            null,
        )
        executeInNewTransaction { repository.save(attendance) }

        // Verify it exists
        assertNotNull(repository.findByMemberAndDate(memberId, date))

        // Delete
        executeInNewTransaction { repository.deleteByMemberAndDate(memberId, date) }

        // Verify it's gone
        assertNull(repository.findByMemberAndDate(memberId, date))
    }

    @Test
    @DisplayName("findByMemberAndDate should return null when not found")
    fun findByMemberAndDateShouldReturnNullWhenNotFound() {
        val memberId = MemberId.of(UUID.randomUUID())
        createTestMember(memberId.value())

        val result = repository.findByMemberAndDate(memberId, LocalDate.of(2026, 12, 31))
        assertNull(result)
    }
}
