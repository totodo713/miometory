package com.worklog.application.service;

import com.worklog.domain.attendance.DailyAttendance;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.JdbcDailyAttendanceRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for DailyAttendance operations.
 *
 * Coordinates daily attendance use cases including save (create/update),
 * delete, and range queries. Enforces optimistic locking on updates.
 */
@Service
public class DailyAttendanceService {

    private final JdbcDailyAttendanceRepository attendanceRepository;

    public DailyAttendanceService(JdbcDailyAttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    /**
     * Saves a daily attendance record. Creates a new record if none exists
     * for the given member and date, or updates the existing one.
     *
     * @param tenantId        The tenant ID
     * @param memberId        The member ID
     * @param date            The attendance date
     * @param startTime       Start time (nullable)
     * @param endTime         End time (nullable)
     * @param remarks         Remarks (nullable)
     * @param expectedVersion Expected version for optimistic locking (nullable for creates)
     * @return The ID of the saved attendance record
     * @throws IllegalStateException if expectedVersion does not match the current version
     */
    @Transactional
    public UUID saveAttendance(
            TenantId tenantId,
            MemberId memberId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            String remarks,
            Integer expectedVersion) {

        DailyAttendance existing = attendanceRepository.findByMemberAndDate(memberId, date);

        if (existing == null) {
            // Create new attendance record
            DailyAttendance attendance = DailyAttendance.create(tenantId, memberId, date, startTime, endTime, remarks);
            attendanceRepository.save(attendance);
            return attendance.getId().value();
        }

        // Update existing record with optimistic locking check
        if (expectedVersion != null && expectedVersion != existing.getVersion()) {
            throw new IllegalStateException("Version mismatch for attendance record. Expected: " + expectedVersion
                    + ", Actual: " + existing.getVersion());
        }

        existing.update(startTime, endTime, remarks);
        attendanceRepository.save(existing);
        return existing.getId().value();
    }

    /**
     * Deletes a daily attendance record for a member on a specific date.
     *
     * @param memberId The member ID
     * @param date     The attendance date
     */
    @Transactional
    public void deleteAttendance(MemberId memberId, LocalDate date) {
        attendanceRepository.deleteByMemberAndDate(memberId, date);
    }

    /**
     * Retrieves daily attendance records for a member within a date range.
     *
     * @param memberId  The member ID
     * @param startDate The start date (inclusive)
     * @param endDate   The end date (inclusive)
     * @return List of attendance records ordered by date
     */
    @Transactional(readOnly = true)
    public List<DailyAttendance> getAttendanceRange(MemberId memberId, LocalDate startDate, LocalDate endDate) {
        return attendanceRepository.findByMemberAndDateRange(memberId, startDate, endDate);
    }
}
