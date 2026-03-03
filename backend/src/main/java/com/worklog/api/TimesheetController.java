package com.worklog.api;

import com.worklog.api.dto.SaveAttendanceRequest;
import com.worklog.api.dto.TimesheetResponse;
import com.worklog.api.dto.TimesheetResponse.TimesheetRow;
import com.worklog.api.dto.TimesheetResponse.TimesheetSummary;
import com.worklog.application.service.DailyAttendanceService;
import com.worklog.application.service.HolidayResolutionService;
import com.worklog.domain.member.Member;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.model.HolidayInfo;
import com.worklog.domain.project.MemberProjectAssignment;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.projection.TimesheetProjection;
import com.worklog.infrastructure.repository.JdbcMemberProjectAssignmentRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for timesheet operations.
 *
 * Provides endpoints for viewing monthly timesheets with attendance data,
 * and for saving/deleting daily attendance records.
 */
@RestController
@RequestMapping("/api/v1/worklog/timesheet")
public class TimesheetController {

    private final TimesheetProjection timesheetProjection;
    private final JdbcMemberRepository memberRepository;
    private final JdbcMemberProjectAssignmentRepository assignmentRepository;
    private final HolidayResolutionService holidayResolutionService;
    private final DailyAttendanceService dailyAttendanceService;
    private final JdbcTemplate jdbcTemplate;

    public TimesheetController(
            TimesheetProjection timesheetProjection,
            JdbcMemberRepository memberRepository,
            JdbcMemberProjectAssignmentRepository assignmentRepository,
            HolidayResolutionService holidayResolutionService,
            DailyAttendanceService dailyAttendanceService,
            JdbcTemplate jdbcTemplate) {
        this.timesheetProjection = timesheetProjection;
        this.memberRepository = memberRepository;
        this.assignmentRepository = assignmentRepository;
        this.holidayResolutionService = holidayResolutionService;
        this.dailyAttendanceService = dailyAttendanceService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Get monthly timesheet for a member and project.
     *
     * GET /api/v1/worklog/timesheet/{year}/{month}?memberId=...&projectId=...&periodType=calendar
     *
     * @param year Year (2020-2100)
     * @param month Month (1-12)
     * @param memberId Member ID (required)
     * @param projectId Project ID (required)
     * @param periodType Period type: "calendar" (1st to last day) or "fiscal" (prev month 21st to current month 20th)
     * @return Timesheet response with daily rows and summary
     */
    @GetMapping("/{year}/{month}")
    public ResponseEntity<TimesheetResponse> getMonthlyTimesheet(
            @PathVariable int year,
            @PathVariable int month,
            @RequestParam UUID memberId,
            @RequestParam UUID projectId,
            @RequestParam(defaultValue = "calendar") String periodType) {

        // Validate year and month
        if (year < 2020 || year > 2100) {
            throw new DomainException("INVALID_YEAR", "Year must be between 2020 and 2100");
        }
        if (month < 1 || month > 12) {
            throw new DomainException("INVALID_MONTH", "Month must be between 1 and 12");
        }

        // Look up member
        Member member = memberRepository
                .findById(MemberId.of(memberId))
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found"));
        UUID tenantId = member.getTenantId().value();

        // Look up project name
        String projectName = queryProjectName(projectId);
        if (projectName == null) {
            throw new DomainException("PROJECT_NOT_FOUND", "Project not found");
        }

        // Calculate period bounds
        LocalDate periodStart;
        LocalDate periodEnd;
        if ("fiscal".equals(periodType)) {
            // Fiscal: previous month 21st to current month 20th
            periodStart = YearMonth.of(year, month).atDay(1).minusMonths(1).withDayOfMonth(21);
            periodEnd = YearMonth.of(year, month).atDay(20);
        } else {
            // Calendar: 1st to last day of month
            YearMonth ym = YearMonth.of(year, month);
            periodStart = ym.atDay(1);
            periodEnd = ym.atEndOfMonth();
        }

        // Get default times from assignment
        MemberProjectAssignment assignment =
                assignmentRepository.findActiveByMemberAndProject(MemberId.of(memberId), projectId);

        // Resolve holidays for this tenant and period (same pattern as CalendarController)
        Map<LocalDate, HolidayInfo> holidayMap =
                holidayResolutionService.resolveHolidays(tenantId, periodStart, periodEnd);
        Set<LocalDate> holidayDates = new HashSet<>(holidayMap.keySet());
        Map<LocalDate, String> holidayNames = holidayMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().name()));

        // Build timesheet rows via projection
        List<TimesheetRow> rows = timesheetProjection.buildRows(
                memberId,
                projectId,
                periodStart,
                periodEnd,
                assignment != null ? assignment.getDefaultStartTime() : null,
                assignment != null ? assignment.getDefaultEndTime() : null,
                holidayDates,
                holidayNames);

        // Build summary
        TimesheetSummary summary = buildSummary(rows, holidayDates);

        TimesheetResponse response = new TimesheetResponse(
                memberId,
                member.getDisplayName(),
                projectId,
                projectName,
                periodType,
                periodStart,
                periodEnd,
                rows,
                summary);

        return ResponseEntity.ok(response);
    }

    /**
     * Save (create or update) a daily attendance record.
     *
     * PUT /api/v1/worklog/timesheet/attendance?memberId=...&tenantId=...
     *
     * @param memberId Member ID (required)
     * @param tenantId Tenant ID (required)
     * @param request Attendance data
     * @return JSON with the saved attendance record ID
     */
    @PutMapping("/attendance")
    public ResponseEntity<Map<String, UUID>> saveAttendance(
            @RequestParam UUID memberId, @RequestParam UUID tenantId, @RequestBody SaveAttendanceRequest request) {

        UUID attendanceId = dailyAttendanceService.saveAttendance(
                TenantId.of(tenantId),
                MemberId.of(memberId),
                request.date(),
                request.startTime(),
                request.endTime(),
                request.remarks(),
                request.version());

        return ResponseEntity.ok(Map.of("id", attendanceId));
    }

    /**
     * Delete a daily attendance record.
     *
     * DELETE /api/v1/worklog/timesheet/attendance/{date}?memberId=...
     *
     * @param date The attendance date to delete
     * @param memberId Member ID (required)
     * @return 204 No Content
     */
    @DeleteMapping("/attendance/{date}")
    public ResponseEntity<Void> deleteAttendance(@PathVariable LocalDate date, @RequestParam UUID memberId) {

        dailyAttendanceService.deleteAttendance(MemberId.of(memberId), date);

        return ResponseEntity.noContent().build();
    }

    /**
     * Queries the project name by ID using a direct SQL query.
     */
    private String queryProjectName(UUID projectId) {
        List<String> results =
                jdbcTemplate.queryForList("SELECT name FROM projects WHERE id = ?", String.class, projectId);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Builds the timesheet summary from the rows.
     */
    private TimesheetSummary buildSummary(List<TimesheetRow> rows, Set<LocalDate> holidayDates) {
        BigDecimal totalWorkingHours = BigDecimal.ZERO;
        int totalWorkingDays = 0;
        int totalBusinessDays = 0;

        for (TimesheetRow row : rows) {
            totalWorkingHours = totalWorkingHours.add(row.workingHours());

            if (row.workingHours().compareTo(BigDecimal.ZERO) > 0) {
                totalWorkingDays++;
            }

            // Business days = weekdays that are not holidays
            if (!row.isWeekend() && !holidayDates.contains(row.date())) {
                totalBusinessDays++;
            }
        }

        return new TimesheetSummary(totalWorkingHours, totalWorkingDays, totalBusinessDays);
    }
}
