package com.worklog.api;

import com.worklog.api.dto.SaveAttendanceRequest;
import com.worklog.api.dto.TimesheetResponse;
import com.worklog.application.service.DailyAttendanceService;
import com.worklog.application.service.UserContextService;
import com.worklog.application.usecase.GetTimesheetUseCase;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.project.ProjectId;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for timesheet operations.
 *
 * Provides endpoints for viewing timesheet data and managing daily attendance records.
 */
@RestController
@RequestMapping("/api/v1/worklog/timesheet")
public class TimesheetController {

    private final GetTimesheetUseCase getTimesheetUseCase;
    private final DailyAttendanceService attendanceService;
    private final UserContextService userContextService;
    private final JdbcMemberRepository memberRepository;
    private final JdbcTemplate jdbcTemplate;

    public TimesheetController(
            GetTimesheetUseCase getTimesheetUseCase,
            DailyAttendanceService attendanceService,
            UserContextService userContextService,
            JdbcMemberRepository memberRepository,
            JdbcTemplate jdbcTemplate) {
        this.getTimesheetUseCase = getTimesheetUseCase;
        this.attendanceService = attendanceService;
        this.userContextService = userContextService;
        this.memberRepository = memberRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Get timesheet for a member and project.
     *
     * GET /api/v1/worklog/timesheet/{year}/{month}?projectId=...&memberId=...&periodType=...
     *
     * @param year Year (2020-2100)
     * @param month Month (1-12)
     * @param projectId Project ID (required)
     * @param memberId Target member ID (defaults to authenticated user)
     * @param periodType "calendar" (1st-last) or "fiscal" (21st prev month - 20th current month)
     * @param auth Authentication context
     * @return Timesheet response with daily rows and summary
     */
    @GetMapping("/{year}/{month}")
    public ResponseEntity<TimesheetResponse> getTimesheet(
            @PathVariable int year,
            @PathVariable int month,
            @RequestParam UUID projectId,
            @RequestParam(required = false) UUID memberId,
            @RequestParam(defaultValue = "calendar") String periodType,
            Authentication auth) {

        UUID requestingMemberId = userContextService.resolveUserMemberId(auth.getName());
        UUID targetMemberId = memberId != null ? memberId : requestingMemberId;
        UUID tenantId = userContextService.resolveUserTenantId(auth.getName());

        // Calculate period based on periodType
        LocalDate periodStart;
        LocalDate periodEnd;
        if ("fiscal".equals(periodType)) {
            YearMonth ym = YearMonth.of(year, month);
            periodStart = ym.minusMonths(1).atDay(21);
            periodEnd = ym.atDay(20);
        } else {
            YearMonth ym = YearMonth.of(year, month);
            periodStart = ym.atDay(1);
            periodEnd = ym.atEndOfMonth();
        }

        String projectName = resolveProjectName(projectId);

        TimesheetResponse response = getTimesheetUseCase.execute(
                MemberId.of(targetMemberId),
                ProjectId.of(projectId),
                projectName,
                periodStart,
                periodEnd,
                periodType,
                MemberId.of(requestingMemberId));

        return ResponseEntity.ok(response);
    }

    /**
     * Save (create or update) an attendance record.
     *
     * PUT /api/v1/worklog/timesheet/attendance
     *
     * @param request The attendance data to save
     * @param auth Authentication context
     * @return 200 OK on success, 403 if not authorized
     */
    @PutMapping("/attendance")
    public ResponseEntity<Void> saveAttendance(@RequestBody SaveAttendanceRequest request, Authentication auth) {

        UUID requestingMemberId = userContextService.resolveUserMemberId(auth.getName());
        UUID tenantId = userContextService.resolveUserTenantId(auth.getName());
        UUID targetMemberId = request.memberId() != null ? request.memberId() : requestingMemberId;

        // Access check: self or manager of target
        if (!targetMemberId.equals(requestingMemberId)) {
            boolean isManager =
                    memberRepository.isSubordinateOf(MemberId.of(requestingMemberId), MemberId.of(targetMemberId));
            if (!isManager) {
                return ResponseEntity.status(403).build();
            }
        }

        attendanceService.saveAttendance(
                TenantId.of(tenantId),
                MemberId.of(targetMemberId),
                request.date(),
                request.startTime(),
                request.endTime(),
                request.remarks(),
                request.version());

        return ResponseEntity.ok().build();
    }

    /**
     * Delete an attendance record.
     *
     * DELETE /api/v1/worklog/timesheet/attendance/{memberId}/{date}
     *
     * @param memberId Target member ID
     * @param date The attendance date
     * @param auth Authentication context
     * @return 200 OK on success, 403 if not authorized
     */
    @DeleteMapping("/attendance/{memberId}/{date}")
    public ResponseEntity<Void> deleteAttendance(
            @PathVariable UUID memberId, @PathVariable LocalDate date, Authentication auth) {

        UUID requestingMemberId = userContextService.resolveUserMemberId(auth.getName());

        // Access check
        if (!memberId.equals(requestingMemberId)) {
            boolean isManager =
                    memberRepository.isSubordinateOf(MemberId.of(requestingMemberId), MemberId.of(memberId));
            if (!isManager) {
                return ResponseEntity.status(403).build();
            }
        }

        attendanceService.deleteAttendance(MemberId.of(memberId), date);
        return ResponseEntity.ok().build();
    }

    private String resolveProjectName(UUID projectId) {
        try {
            return jdbcTemplate.queryForObject("SELECT name FROM projects WHERE id = ?", String.class, projectId);
        } catch (Exception e) {
            return "";
        }
    }
}
