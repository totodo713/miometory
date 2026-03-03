package com.worklog.application.usecase;

import com.worklog.api.dto.TimesheetResponse;
import com.worklog.api.dto.TimesheetResponse.TimesheetRow;
import com.worklog.api.dto.TimesheetResponse.TimesheetSummary;
import com.worklog.application.service.HolidayResolutionService;
import com.worklog.domain.attendance.DailyAttendance;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.model.HolidayInfo;
import com.worklog.domain.project.MemberProjectAssignment;
import com.worklog.domain.project.ProjectId;
import com.worklog.domain.shared.DomainException;
import com.worklog.infrastructure.projection.MonthlyCalendarProjection;
import com.worklog.infrastructure.repository.JdbcDailyAttendanceRepository;
import com.worklog.infrastructure.repository.JdbcMemberProjectAssignmentRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for retrieving timesheet data.
 *
 * Composes data from multiple sources (member, attendance, calendar projection,
 * project assignment, holidays) into a unified timesheet read model.
 */
@Service
public class GetTimesheetUseCase {

    private final JdbcMemberRepository memberRepository;
    private final JdbcDailyAttendanceRepository attendanceRepository;
    private final MonthlyCalendarProjection calendarProjection;
    private final JdbcMemberProjectAssignmentRepository assignmentRepository;
    private final HolidayResolutionService holidayResolutionService;

    public GetTimesheetUseCase(
            JdbcMemberRepository memberRepository,
            JdbcDailyAttendanceRepository attendanceRepository,
            MonthlyCalendarProjection calendarProjection,
            JdbcMemberProjectAssignmentRepository assignmentRepository,
            HolidayResolutionService holidayResolutionService) {
        this.memberRepository = memberRepository;
        this.attendanceRepository = attendanceRepository;
        this.calendarProjection = calendarProjection;
        this.assignmentRepository = assignmentRepository;
        this.holidayResolutionService = holidayResolutionService;
    }

    /**
     * Executes the timesheet retrieval use case.
     *
     * @param targetMemberId the member whose timesheet is requested
     * @param projectId the project to show hours for
     * @param projectName the display name of the project
     * @param periodStart the start date of the period (inclusive)
     * @param periodEnd the end date of the period (inclusive)
     * @param periodType the period type ("calendar" or "fiscal")
     * @param requestingMemberId the member making the request (for access control)
     * @return the composed timesheet response
     * @throws DomainException if the target member is not found
     */
    @Transactional(readOnly = true)
    public TimesheetResponse execute(
            MemberId targetMemberId,
            ProjectId projectId,
            String projectName,
            LocalDate periodStart,
            LocalDate periodEnd,
            String periodType,
            MemberId requestingMemberId) {

        // 1. Load target member
        var member = memberRepository
                .findById(targetMemberId)
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found"));

        // 2. Load holidays
        Map<LocalDate, HolidayInfo> holidays =
                holidayResolutionService.resolveHolidays(member.getTenantId().value(), periodStart, periodEnd);

        // 3. Load attendance records and index by date
        List<DailyAttendance> attendanceList =
                attendanceRepository.findByMemberAndDateRange(targetMemberId, periodStart, periodEnd);
        Map<LocalDate, DailyAttendance> attendanceByDate =
                attendanceList.stream().collect(Collectors.toMap(DailyAttendance::getDate, Function.identity()));

        // 4. Load daily work hours from projection
        Map<LocalDate, BigDecimal> dailyTotals =
                calendarProjection.getDailyTotals(targetMemberId.value(), periodStart, periodEnd);

        // 5. Load assignment defaults
        Optional<MemberProjectAssignment> assignment =
                assignmentRepository.findByMemberAndProject(member.getTenantId(), targetMemberId, projectId);
        LocalTime defaultStartTime =
                assignment.map(MemberProjectAssignment::getDefaultStartTime).orElse(null);
        LocalTime defaultEndTime =
                assignment.map(MemberProjectAssignment::getDefaultEndTime).orElse(null);

        // 6. Determine canEdit
        boolean canEdit;
        if (requestingMemberId.equals(targetMemberId)) {
            canEdit = true;
        } else {
            canEdit = memberRepository.isSubordinateOf(requestingMemberId, targetMemberId);
        }

        // 7. Build rows
        List<TimesheetRow> rows = new ArrayList<>();
        LocalDate current = periodStart;
        while (!current.isAfter(periodEnd)) {
            DayOfWeek dayOfWeek = current.getDayOfWeek();
            boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;

            HolidayInfo holidayInfo = holidays.get(current);
            boolean isHoliday = holidayInfo != null;
            String holidayName = holidayInfo != null ? holidayInfo.name() : null;

            DailyAttendance attendance = attendanceByDate.get(current);
            BigDecimal workingHours = dailyTotals.getOrDefault(current, BigDecimal.ZERO);

            rows.add(new TimesheetRow(
                    current,
                    dayOfWeek,
                    isWeekend,
                    isHoliday,
                    holidayName,
                    attendance != null ? attendance.getStartTime() : null,
                    attendance != null ? attendance.getEndTime() : null,
                    workingHours,
                    attendance != null ? attendance.getRemarks() : null,
                    defaultStartTime,
                    defaultEndTime,
                    attendance != null,
                    attendance != null ? attendance.getId().value() : null,
                    attendance != null ? attendance.getVersion() : 0));

            current = current.plusDays(1);
        }

        // 8. Build summary
        BigDecimal totalWorkingHours =
                rows.stream().map(TimesheetRow::workingHours).reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalWorkingDays = (int) rows.stream()
                .filter(r -> r.workingHours().compareTo(BigDecimal.ZERO) > 0)
                .count();
        int totalBusinessDays = (int)
                rows.stream().filter(r -> !r.isWeekend() && !r.isHoliday()).count();

        TimesheetSummary summary = new TimesheetSummary(totalWorkingHours, totalWorkingDays, totalBusinessDays);

        return new TimesheetResponse(
                targetMemberId.value(),
                member.getDisplayName(),
                projectId.value(),
                projectName,
                periodType,
                periodStart,
                periodEnd,
                canEdit,
                rows,
                summary);
    }
}
