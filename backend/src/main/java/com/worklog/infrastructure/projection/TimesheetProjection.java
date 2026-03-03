package com.worklog.infrastructure.projection;

import com.worklog.api.dto.TimesheetResponse.TimesheetRow;
import com.worklog.application.service.DailyAttendanceService;
import com.worklog.domain.attendance.DailyAttendance;
import com.worklog.domain.member.MemberId;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Projection for building timesheet rows.
 *
 * Queries work_log_entries_projection for hours per date and combines
 * with attendance records to produce daily timesheet rows.
 */
@Component
public class TimesheetProjection {

    private final JdbcTemplate jdbcTemplate;
    private final DailyAttendanceService dailyAttendanceService;

    public TimesheetProjection(JdbcTemplate jdbcTemplate, DailyAttendanceService dailyAttendanceService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dailyAttendanceService = dailyAttendanceService;
    }

    /**
     * Builds timesheet rows for a member/project over a date range.
     *
     * @param memberId The member ID
     * @param projectId The project ID
     * @param start Start date (inclusive)
     * @param end End date (inclusive)
     * @param defaultStart Default start time from assignment (nullable)
     * @param defaultEnd Default end time from assignment (nullable)
     * @param holidays Set of holiday dates
     * @param holidayNames Map of holiday dates to holiday names
     * @return List of timesheet rows, one per day in the range
     */
    public List<TimesheetRow> buildRows(
            UUID memberId,
            UUID projectId,
            LocalDate start,
            LocalDate end,
            LocalTime defaultStart,
            LocalTime defaultEnd,
            Set<LocalDate> holidays,
            Map<LocalDate, String> holidayNames) {

        // Query work hours per date for this member+project
        Map<LocalDate, BigDecimal> hoursByDate = getWorkHoursByDate(memberId, projectId, start, end);

        // Get attendance records for this member in the date range
        Map<LocalDate, DailyAttendance> attendanceByDate = getAttendanceByDate(memberId, start, end);

        // Build a row for each day in the range
        List<TimesheetRow> rows = new ArrayList<>();
        LocalDate current = start;

        while (!current.isAfter(end)) {
            boolean isWeekend =
                    current.getDayOfWeek() == DayOfWeek.SATURDAY || current.getDayOfWeek() == DayOfWeek.SUNDAY;
            boolean isHoliday = holidays.contains(current);
            String holidayName = holidayNames.get(current);
            String dayOfWeek = current.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);

            BigDecimal workingHours = hoursByDate.getOrDefault(current, BigDecimal.ZERO);

            DailyAttendance attendance = attendanceByDate.get(current);
            boolean hasAttendanceRecord = attendance != null;
            LocalTime startTime = hasAttendanceRecord ? attendance.getStartTime() : null;
            LocalTime endTime = hasAttendanceRecord ? attendance.getEndTime() : null;
            String remarks = hasAttendanceRecord ? attendance.getRemarks() : null;
            UUID attendanceId = hasAttendanceRecord ? attendance.getId().value() : null;
            Integer attendanceVersion = hasAttendanceRecord ? attendance.getVersion() : null;

            rows.add(new TimesheetRow(
                    current,
                    dayOfWeek,
                    isWeekend,
                    isHoliday,
                    holidayName,
                    startTime,
                    endTime,
                    workingHours,
                    remarks,
                    defaultStart,
                    defaultEnd,
                    hasAttendanceRecord,
                    attendanceId,
                    attendanceVersion));

            current = current.plusDays(1);
        }

        return rows;
    }

    /**
     * Queries work_log_entries_projection for total hours per date,
     * filtered by member, project, and date range.
     */
    private Map<LocalDate, BigDecimal> getWorkHoursByDate(
            UUID memberId, UUID projectId, LocalDate start, LocalDate end) {
        String sql = """
            SELECT
                work_date,
                SUM(hours) as total_hours
            FROM work_log_entries_projection
            WHERE member_id = ?
            AND project_id = ?
            AND work_date BETWEEN ? AND ?
            GROUP BY work_date
            ORDER BY work_date
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, memberId, projectId, start, end);

        Map<LocalDate, BigDecimal> hoursByDate = new HashMap<>();
        for (Map<String, Object> row : results) {
            LocalDate date = ((java.sql.Date) row.get("work_date")).toLocalDate();
            BigDecimal hours = (BigDecimal) row.get("total_hours");
            hoursByDate.put(date, hours);
        }

        return hoursByDate;
    }

    /**
     * Gets attendance records for a member in a date range, indexed by date.
     */
    private Map<LocalDate, DailyAttendance> getAttendanceByDate(UUID memberId, LocalDate start, LocalDate end) {
        List<DailyAttendance> attendances =
                dailyAttendanceService.getAttendanceRange(MemberId.of(memberId), start, end);

        Map<LocalDate, DailyAttendance> byDate = new HashMap<>();
        for (DailyAttendance a : attendances) {
            byDate.put(a.getAttendanceDate(), a);
        }
        return byDate;
    }
}
