package com.worklog.infrastructure.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.worklog.IntegrationTestBase;
import com.worklog.domain.attendance.DailyAttendance;
import com.worklog.domain.attendance.DailyAttendanceId;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.tenant.TenantId;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("JdbcDailyAttendanceRepository")
class JdbcDailyAttendanceRepositoryTest extends IntegrationTestBase {

    @Autowired
    private JdbcDailyAttendanceRepository repository;

    private UUID testMemberId;
    private MemberId memberId;
    private TenantId tenantId;

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 3, 1);

    @BeforeEach
    void setUp() {
        testMemberId = UUID.randomUUID();
        memberId = MemberId.of(testMemberId);
        tenantId = TenantId.of(TEST_TENANT_ID);

        createTestMember(testMemberId, "attendance-" + testMemberId + "@example.com");

        executeInNewTransaction(() -> {
            baseJdbcTemplate.update("DELETE FROM daily_attendance WHERE member_id = ?", testMemberId);
            return kotlin.Unit.INSTANCE;
        });
    }

    @Test
    @DisplayName("save should insert a new attendance record")
    void save_insertsNew() {
        DailyAttendance attendance = new DailyAttendance(
                DailyAttendanceId.generate(),
                tenantId,
                memberId,
                BASE_DATE,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                "Test day",
                0);

        repository.save(attendance);

        var found = repository.findByMemberAndDate(memberId, BASE_DATE);
        assertTrue(found.isPresent());
        assertEquals(attendance.getId(), found.get().getId());
        assertEquals(tenantId, found.get().getTenantId());
        assertEquals(memberId, found.get().getMemberId());
        assertEquals(BASE_DATE, found.get().getDate());
        assertEquals(LocalTime.of(9, 0), found.get().getStartTime());
        assertEquals(LocalTime.of(18, 0), found.get().getEndTime());
        assertEquals("Test day", found.get().getRemarks());
        assertEquals(0, found.get().getVersion());
    }

    @Test
    @DisplayName("save should upsert on conflict with same member and date")
    void save_upsertsOnConflict() {
        DailyAttendance first = new DailyAttendance(
                DailyAttendanceId.generate(),
                tenantId,
                memberId,
                BASE_DATE,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                "First save",
                0);

        repository.save(first);

        DailyAttendance second = new DailyAttendance(
                DailyAttendanceId.generate(),
                tenantId,
                memberId,
                BASE_DATE,
                LocalTime.of(10, 0),
                LocalTime.of(19, 0),
                "Second save",
                0);

        repository.save(second);

        var found = repository.findByMemberAndDate(memberId, BASE_DATE);
        assertTrue(found.isPresent());
        assertEquals(LocalTime.of(10, 0), found.get().getStartTime());
        assertEquals(LocalTime.of(19, 0), found.get().getEndTime());
        assertEquals("Second save", found.get().getRemarks());
        assertEquals(1, found.get().getVersion());
    }

    @Test
    @DisplayName("findByMemberAndDate should return empty for non-existent date")
    void findByMemberAndDate_returnsEmpty() {
        var found = repository.findByMemberAndDate(memberId, BASE_DATE);
        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("findByMemberAndDateRange should return records within range ordered by date")
    void findByMemberAndDateRange_returnsInRange() {
        for (int day = 1; day <= 5; day++) {
            LocalDate date = BASE_DATE.plusDays(day - 1);
            DailyAttendance attendance = new DailyAttendance(
                    DailyAttendanceId.generate(),
                    tenantId,
                    memberId,
                    date,
                    LocalTime.of(9, 0),
                    LocalTime.of(18, 0),
                    "Day " + day,
                    0);
            repository.save(attendance);
        }

        LocalDate rangeStart = BASE_DATE.plusDays(1); // day 2
        LocalDate rangeEnd = BASE_DATE.plusDays(3); // day 4

        var results = repository.findByMemberAndDateRange(memberId, rangeStart, rangeEnd);

        assertEquals(3, results.size());
        assertEquals(rangeStart, results.get(0).getDate());
        assertEquals(BASE_DATE.plusDays(2), results.get(1).getDate());
        assertEquals(rangeEnd, results.get(2).getDate());
    }

    @Test
    @DisplayName("deleteByMemberAndDate should remove the record")
    void deleteByMemberAndDate_removesRecord() {
        DailyAttendance attendance = new DailyAttendance(
                DailyAttendanceId.generate(),
                tenantId,
                memberId,
                BASE_DATE,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                "To be deleted",
                0);

        repository.save(attendance);
        assertTrue(repository.findByMemberAndDate(memberId, BASE_DATE).isPresent());

        repository.deleteByMemberAndDate(memberId, BASE_DATE);
        assertTrue(repository.findByMemberAndDate(memberId, BASE_DATE).isEmpty());
    }

    @Test
    @DisplayName("save should handle null start and end times")
    void save_handlesNullTimes() {
        DailyAttendance attendance = new DailyAttendance(
                DailyAttendanceId.generate(), tenantId, memberId, BASE_DATE, null, null, "Null times test", 0);

        repository.save(attendance);

        var found = repository.findByMemberAndDate(memberId, BASE_DATE);
        assertTrue(found.isPresent());
        assertNull(found.get().getStartTime());
        assertNull(found.get().getEndTime());
        assertEquals("Null times test", found.get().getRemarks());
    }
}
