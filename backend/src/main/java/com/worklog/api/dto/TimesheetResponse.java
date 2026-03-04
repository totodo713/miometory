package com.worklog.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for monthly timesheet view.
 *
 * Contains member/project context, daily rows with attendance and work hours,
 * and a summary with totals.
 */
public record TimesheetResponse(
        UUID memberId,
        String memberName,
        UUID projectId,
        String projectName,
        String periodType,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<TimesheetRow> rows,
        TimesheetSummary summary) {

    /**
     * A single day row in the timesheet.
     *
     * @param date The calendar date
     * @param dayOfWeek Short day-of-week name (e.g. "Mon", "Tue")
     * @param isWeekend Whether the date falls on Saturday or Sunday
     * @param isHoliday Whether the date is a tenant holiday
     * @param holidayName The holiday name (nullable)
     * @param startTime Attendance start time (nullable)
     * @param endTime Attendance end time (nullable)
     * @param workingHours Total work hours from work_log_entries_projection
     * @param remarks Attendance remarks (nullable)
     * @param defaultStartTime Default start time from assignment (nullable)
     * @param defaultEndTime Default end time from assignment (nullable)
     * @param hasAttendanceRecord Whether an attendance record exists for this date
     * @param attendanceId The attendance record ID (nullable)
     * @param attendanceVersion The attendance record version for optimistic locking (nullable)
     */
    public record TimesheetRow(
            LocalDate date,
            String dayOfWeek,
            boolean isWeekend,
            boolean isHoliday,
            String holidayName,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            BigDecimal workingHours,
            String remarks,
            @JsonFormat(pattern = "HH:mm") LocalTime defaultStartTime,
            @JsonFormat(pattern = "HH:mm") LocalTime defaultEndTime,
            boolean hasAttendanceRecord,
            UUID attendanceId,
            Integer attendanceVersion) {}

    /**
     * Summary totals for the timesheet period.
     *
     * @param totalWorkingHours Sum of all working hours in the period
     * @param totalWorkingDays Number of days with actual work hours recorded
     * @param totalBusinessDays Number of weekdays that are not holidays
     */
    public record TimesheetSummary(BigDecimal totalWorkingHours, int totalWorkingDays, int totalBusinessDays) {}
}
