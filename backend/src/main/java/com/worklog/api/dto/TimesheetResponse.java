package com.worklog.api.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record TimesheetResponse(
        UUID memberId,
        String memberName,
        UUID projectId,
        String projectName,
        String periodType,
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean canEdit,
        List<TimesheetRow> rows,
        TimesheetSummary summary) {

    public record TimesheetRow(
            LocalDate date,
            DayOfWeek dayOfWeek,
            boolean isWeekend,
            boolean isHoliday,
            String holidayName,
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal workingHours,
            String remarks,
            LocalTime defaultStartTime,
            LocalTime defaultEndTime,
            boolean hasAttendanceRecord,
            UUID attendanceId,
            int attendanceVersion) {}

    public record TimesheetSummary(BigDecimal totalWorkingHours, int totalWorkingDays, int totalBusinessDays) {}
}
