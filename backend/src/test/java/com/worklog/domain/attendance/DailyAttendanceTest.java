package com.worklog.domain.attendance;

import static org.junit.jupiter.api.Assertions.*;

import com.worklog.domain.member.MemberId;
import com.worklog.domain.tenant.TenantId;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DailyAttendance entity")
class DailyAttendanceTest {

    private static final TenantId TENANT = TenantId.of("550e8400-e29b-41d4-a716-446655440001");
    private static final MemberId MEMBER = MemberId.of("660e8400-e29b-41d4-a716-446655440001");
    private static final LocalDate DATE = LocalDate.of(2026, 3, 2);

    @Test
    @DisplayName("create sets defaults")
    void create_setsDefaults() {
        DailyAttendance attendance = DailyAttendance.create(TENANT, MEMBER, DATE);

        assertNotNull(attendance.getId());
        assertEquals(TENANT, attendance.getTenantId());
        assertEquals(MEMBER, attendance.getMemberId());
        assertEquals(DATE, attendance.getDate());
        assertNull(attendance.getStartTime());
        assertNull(attendance.getEndTime());
        assertNull(attendance.getRemarks());
        assertEquals(0, attendance.getVersion());
    }

    @Test
    @DisplayName("update changes mutable fields")
    void update_changesMutableFields() {
        DailyAttendance attendance = DailyAttendance.create(TENANT, MEMBER, DATE);

        attendance.update(LocalTime.of(9, 0), LocalTime.of(18, 0), "Meeting day");

        assertEquals(LocalTime.of(9, 0), attendance.getStartTime());
        assertEquals(LocalTime.of(18, 0), attendance.getEndTime());
        assertEquals("Meeting day", attendance.getRemarks());
    }

    @Test
    @DisplayName("update rejects end before start")
    void update_rejectsEndBeforeStart() {
        DailyAttendance attendance = DailyAttendance.create(TENANT, MEMBER, DATE);

        assertThrows(
                IllegalArgumentException.class, () -> attendance.update(LocalTime.of(18, 0), LocalTime.of(9, 0), null));
    }

    @Test
    @DisplayName("update allows start time only")
    void update_allowsStartTimeOnly() {
        DailyAttendance attendance = DailyAttendance.create(TENANT, MEMBER, DATE);

        attendance.update(LocalTime.of(9, 0), null, null);

        assertEquals(LocalTime.of(9, 0), attendance.getStartTime());
        assertNull(attendance.getEndTime());
    }

    @Test
    @DisplayName("update rejects long remarks")
    void update_rejectsLongRemarks() {
        DailyAttendance attendance = DailyAttendance.create(TENANT, MEMBER, DATE);
        String longRemarks = "a".repeat(501);

        assertThrows(
                IllegalArgumentException.class,
                () -> attendance.update(LocalTime.of(9, 0), LocalTime.of(18, 0), longRemarks));
    }

    @Test
    @DisplayName("update allows 500 char remarks")
    void update_allows500CharRemarks() {
        DailyAttendance attendance = DailyAttendance.create(TENANT, MEMBER, DATE);
        String remarks500 = "a".repeat(500);

        attendance.update(LocalTime.of(9, 0), LocalTime.of(18, 0), remarks500);

        assertEquals(remarks500, attendance.getRemarks());
    }

    @Test
    @DisplayName("update allows nulls")
    void update_allowsNulls() {
        DailyAttendance attendance = DailyAttendance.create(TENANT, MEMBER, DATE);
        attendance.update(LocalTime.of(9, 0), LocalTime.of(18, 0), "Meeting day");

        attendance.update(null, null, null);

        assertNull(attendance.getStartTime());
        assertNull(attendance.getEndTime());
        assertNull(attendance.getRemarks());
    }
}
