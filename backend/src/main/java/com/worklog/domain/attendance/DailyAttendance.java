package com.worklog.domain.attendance;

import com.worklog.domain.member.MemberId;
import com.worklog.domain.tenant.TenantId;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * DailyAttendance entity.
 *
 * Represents a member's attendance record for a single day,
 * including start time, end time, and optional remarks.
 */
public class DailyAttendance {

    private final DailyAttendanceId id;
    private final TenantId tenantId;
    private final MemberId memberId;
    private final LocalDate attendanceDate;
    private LocalTime startTime; // Nullable
    private LocalTime endTime; // Nullable
    private String remarks; // Nullable
    private final int version;

    /**
     * Constructor for reconstituting from persistence.
     */
    public DailyAttendance(
            DailyAttendanceId id,
            TenantId tenantId,
            MemberId memberId,
            LocalDate attendanceDate,
            LocalTime startTime,
            LocalTime endTime,
            String remarks,
            int version) {
        this.id = Objects.requireNonNull(id, "Attendance ID cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID cannot be null");
        this.memberId = Objects.requireNonNull(memberId, "Member ID cannot be null");
        this.attendanceDate = Objects.requireNonNull(attendanceDate, "Attendance date cannot be null");
        validateTimes(startTime, endTime);
        validateRemarks(remarks);
        this.startTime = startTime;
        this.endTime = endTime;
        this.remarks = remarks;
        this.version = version;
    }

    /**
     * Factory method for creating a new daily attendance record.
     */
    public static DailyAttendance create(
            TenantId tenantId,
            MemberId memberId,
            LocalDate attendanceDate,
            LocalTime startTime,
            LocalTime endTime,
            String remarks) {
        return new DailyAttendance(
                DailyAttendanceId.generate(), tenantId, memberId, attendanceDate, startTime, endTime, remarks, 0);
    }

    /**
     * Updates the mutable fields of this attendance record.
     */
    public void update(LocalTime startTime, LocalTime endTime, String remarks) {
        validateTimes(startTime, endTime);
        validateRemarks(remarks);
        this.startTime = startTime;
        this.endTime = endTime;
        this.remarks = remarks;
    }

    private static void validateTimes(LocalTime startTime, LocalTime endTime) {
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("End time cannot be before start time");
        }
    }

    private static void validateRemarks(String remarks) {
        if (remarks != null && remarks.length() > 500) {
            throw new IllegalArgumentException("Remarks cannot exceed 500 characters");
        }
    }

    // Getters

    public DailyAttendanceId getId() {
        return id;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public MemberId getMemberId() {
        return memberId;
    }

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getRemarks() {
        return remarks;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DailyAttendance that = (DailyAttendance) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DailyAttendance{" + "id="
                + id + ", memberId="
                + memberId + ", attendanceDate="
                + attendanceDate + ", startTime="
                + startTime + ", endTime="
                + endTime + '}';
    }
}
