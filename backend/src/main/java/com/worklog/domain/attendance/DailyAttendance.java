package com.worklog.domain.attendance;

import com.worklog.domain.member.MemberId;
import com.worklog.domain.tenant.TenantId;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

public class DailyAttendance {

    private final DailyAttendanceId id;
    private final TenantId tenantId;
    private final MemberId memberId;
    private final LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private String remarks;
    private int version;

    public DailyAttendance(
            DailyAttendanceId id,
            TenantId tenantId,
            MemberId memberId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            String remarks,
            int version) {
        this.id = Objects.requireNonNull(id, "DailyAttendanceId cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "TenantId cannot be null");
        this.memberId = Objects.requireNonNull(memberId, "MemberId cannot be null");
        this.date = Objects.requireNonNull(date, "Date cannot be null");
        this.startTime = startTime;
        this.endTime = endTime;
        this.remarks = remarks;
        this.version = version;
    }

    public static DailyAttendance create(TenantId tenantId, MemberId memberId, LocalDate date) {
        return new DailyAttendance(DailyAttendanceId.generate(), tenantId, memberId, date, null, null, null, 0);
    }

    public void update(LocalTime startTime, LocalTime endTime, String remarks) {
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        if (remarks != null && remarks.length() > 500) {
            throw new IllegalArgumentException("Remarks cannot exceed 500 characters");
        }
        this.startTime = startTime;
        this.endTime = endTime;
        this.remarks = remarks;
    }

    public DailyAttendanceId getId() {
        return id;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public MemberId getMemberId() {
        return memberId;
    }

    public LocalDate getDate() {
        return date;
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
}
