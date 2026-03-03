package com.worklog.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.worklog.application.service.HolidayResolutionService;
import com.worklog.domain.attendance.DailyAttendance;
import com.worklog.domain.attendance.DailyAttendanceId;
import com.worklog.domain.member.Member;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.model.HolidayInfo;
import com.worklog.domain.project.ProjectId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.projection.MonthlyCalendarProjection;
import com.worklog.infrastructure.repository.JdbcDailyAttendanceRepository;
import com.worklog.infrastructure.repository.JdbcMemberProjectAssignmentRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetTimesheetUseCase")
class GetTimesheetUseCaseTest {

    @Mock
    private JdbcMemberRepository memberRepository;

    @Mock
    private JdbcDailyAttendanceRepository attendanceRepository;

    @Mock
    private MonthlyCalendarProjection calendarProjection;

    @Mock
    private JdbcMemberProjectAssignmentRepository assignmentRepository;

    @Mock
    private HolidayResolutionService holidayResolutionService;

    @InjectMocks
    private GetTimesheetUseCase useCase;

    private static final TenantId TENANT_ID = TenantId.of("550e8400-e29b-41d4-a716-446655440001");
    private static final MemberId TARGET_MEMBER_ID = MemberId.of("660e8400-e29b-41d4-a716-446655440001");
    private static final MemberId REQUESTING_MEMBER_ID = MemberId.of("660e8400-e29b-41d4-a716-446655440002");
    private static final ProjectId PROJECT_ID = ProjectId.of("770e8400-e29b-41d4-a716-446655440001");

    private Member createTargetMember() {
        return new Member(
                TARGET_MEMBER_ID, TENANT_ID, null, "test@example.com", "Test User", null, true, Instant.now());
    }

    private void setupCommonMocks(Member member) {
        lenient().when(memberRepository.findById(TARGET_MEMBER_ID)).thenReturn(Optional.of(member));
        lenient()
                .when(holidayResolutionService.resolveHolidays(eq(TENANT_ID.value()), any(), any()))
                .thenReturn(Map.of());
        lenient()
                .when(attendanceRepository.findByMemberAndDateRange(eq(TARGET_MEMBER_ID), any(), any()))
                .thenReturn(List.of());
        lenient()
                .when(calendarProjection.getDailyTotals(eq(TARGET_MEMBER_ID.value()), any(), any()))
                .thenReturn(Map.of());
        lenient()
                .when(assignmentRepository.findByMemberAndProject(TENANT_ID, TARGET_MEMBER_ID, PROJECT_ID))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("execute should build correct rows for weekend, holiday, and normal days")
    void execute_buildsCorrectRows() {
        var member = createTargetMember();
        // Fri 2026-03-06, Sat 2026-03-07, Sun 2026-03-08
        LocalDate friday = LocalDate.of(2026, 3, 6);
        LocalDate saturday = LocalDate.of(2026, 3, 7);
        LocalDate sunday = LocalDate.of(2026, 3, 8);

        setupCommonMocks(member);

        // Override holidays to mark Friday as a holiday
        when(holidayResolutionService.resolveHolidays(TENANT_ID.value(), friday, sunday))
                .thenReturn(Map.of(friday, new HolidayInfo("Test Holiday", "テスト祝日", friday)));

        var response = useCase.execute(
                TARGET_MEMBER_ID, PROJECT_ID, "Project A", friday, sunday, "calendar", TARGET_MEMBER_ID);

        assertEquals(3, response.rows().size());

        // Friday: holiday, not weekend
        var fridayRow = response.rows().get(0);
        assertEquals(friday, fridayRow.date());
        assertEquals(DayOfWeek.FRIDAY, fridayRow.dayOfWeek());
        assertFalse(fridayRow.isWeekend());
        assertTrue(fridayRow.isHoliday());
        assertEquals("Test Holiday", fridayRow.holidayName());

        // Saturday: weekend, not holiday
        var saturdayRow = response.rows().get(1);
        assertEquals(saturday, saturdayRow.date());
        assertTrue(saturdayRow.isWeekend());
        assertFalse(saturdayRow.isHoliday());

        // Sunday: weekend, not holiday
        var sundayRow = response.rows().get(2);
        assertEquals(sunday, sundayRow.date());
        assertTrue(sundayRow.isWeekend());
        assertFalse(sundayRow.isHoliday());
    }

    @Test
    @DisplayName("execute should merge attendance data into rows")
    void execute_mergesAttendanceData() {
        var member = createTargetMember();
        LocalDate date = LocalDate.of(2026, 3, 2); // Monday

        setupCommonMocks(member);

        var attendanceId = DailyAttendanceId.of("880e8400-e29b-41d4-a716-446655440001");
        var attendance = new DailyAttendance(
                attendanceId,
                TENANT_ID,
                TARGET_MEMBER_ID,
                date,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                "Morning meeting",
                2);

        when(attendanceRepository.findByMemberAndDateRange(TARGET_MEMBER_ID, date, date))
                .thenReturn(List.of(attendance));

        var response =
                useCase.execute(TARGET_MEMBER_ID, PROJECT_ID, "Project A", date, date, "calendar", TARGET_MEMBER_ID);

        assertEquals(1, response.rows().size());
        var row = response.rows().get(0);
        assertEquals(LocalTime.of(9, 0), row.startTime());
        assertEquals(LocalTime.of(18, 0), row.endTime());
        assertEquals("Morning meeting", row.remarks());
        assertTrue(row.hasAttendanceRecord());
        assertEquals(attendanceId.value(), row.attendanceId());
        assertEquals(2, row.attendanceVersion());
    }

    @Test
    @DisplayName("execute should calculate summary correctly")
    void execute_calculatesSummary() {
        var member = createTargetMember();
        // Mon 2026-03-02 to Fri 2026-03-06 (5 weekdays)
        LocalDate monday = LocalDate.of(2026, 3, 2);
        LocalDate friday = LocalDate.of(2026, 3, 6);

        setupCommonMocks(member);

        // 3 days with work hours
        when(calendarProjection.getDailyTotals(TARGET_MEMBER_ID.value(), monday, friday))
                .thenReturn(Map.of(
                        monday,
                        new BigDecimal("8.00"),
                        LocalDate.of(2026, 3, 3),
                        new BigDecimal("7.50"),
                        LocalDate.of(2026, 3, 4),
                        new BigDecimal("8.00")));

        var response = useCase.execute(
                TARGET_MEMBER_ID, PROJECT_ID, "Project A", monday, friday, "calendar", TARGET_MEMBER_ID);

        assertEquals(5, response.rows().size());
        assertEquals(new BigDecimal("23.50"), response.summary().totalWorkingHours());
        assertEquals(3, response.summary().totalWorkingDays());
        assertEquals(5, response.summary().totalBusinessDays());
    }

    @Test
    @DisplayName("execute should set canEdit=true when requesting own timesheet")
    void execute_canEditSelf() {
        var member = createTargetMember();
        LocalDate date = LocalDate.of(2026, 3, 2);
        setupCommonMocks(member);

        var response =
                useCase.execute(TARGET_MEMBER_ID, PROJECT_ID, "Project A", date, date, "calendar", TARGET_MEMBER_ID);

        assertTrue(response.canEdit());
    }

    @Test
    @DisplayName("execute should set canEdit=true for manager viewing subordinate")
    void execute_canEditSubordinate() {
        var member = createTargetMember();
        LocalDate date = LocalDate.of(2026, 3, 2);
        setupCommonMocks(member);

        when(memberRepository.isSubordinateOf(REQUESTING_MEMBER_ID, TARGET_MEMBER_ID))
                .thenReturn(true);

        var response = useCase.execute(
                TARGET_MEMBER_ID, PROJECT_ID, "Project A", date, date, "calendar", REQUESTING_MEMBER_ID);

        assertTrue(response.canEdit());
    }

    @Test
    @DisplayName("execute should set canEdit=false for unrelated member")
    void execute_cannotEditUnrelated() {
        var member = createTargetMember();
        LocalDate date = LocalDate.of(2026, 3, 2);
        setupCommonMocks(member);

        when(memberRepository.isSubordinateOf(REQUESTING_MEMBER_ID, TARGET_MEMBER_ID))
                .thenReturn(false);

        var response = useCase.execute(
                TARGET_MEMBER_ID, PROJECT_ID, "Project A", date, date, "calendar", REQUESTING_MEMBER_ID);

        assertFalse(response.canEdit());
    }

    @Test
    @DisplayName("execute should throw DomainException when member not found")
    void execute_throwsOnMemberNotFound() {
        when(memberRepository.findById(TARGET_MEMBER_ID)).thenReturn(Optional.empty());

        var exception = assertThrows(
                DomainException.class,
                () -> useCase.execute(
                        TARGET_MEMBER_ID,
                        PROJECT_ID,
                        "Project A",
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 3, 31),
                        "calendar",
                        REQUESTING_MEMBER_ID));

        assertEquals("MEMBER_NOT_FOUND", exception.getErrorCode());
    }
}
