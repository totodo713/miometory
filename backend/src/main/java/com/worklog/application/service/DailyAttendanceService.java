package com.worklog.application.service;

import com.worklog.domain.attendance.DailyAttendance;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.JdbcDailyAttendanceRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DailyAttendanceService {

    private final JdbcDailyAttendanceRepository repository;

    public DailyAttendanceService(JdbcDailyAttendanceRepository repository) {
        this.repository = repository;
    }

    public void saveAttendance(
            TenantId tenantId,
            MemberId memberId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            String remarks,
            int expectedVersion) {
        var existing = repository.findByMemberAndDate(memberId, date);

        if (existing.isPresent()) {
            var attendance = existing.get();
            if (attendance.getVersion() != expectedVersion) {
                throw new DomainException(
                        "OPTIMISTIC_LOCK_FAILURE", "Attendance record has been modified by another user");
            }
            attendance.update(startTime, endTime, remarks);
            repository.save(attendance);
        } else {
            var attendance = DailyAttendance.create(tenantId, memberId, date);
            attendance.update(startTime, endTime, remarks);
            repository.save(attendance);
        }
    }

    public void deleteAttendance(MemberId memberId, LocalDate date) {
        repository.deleteByMemberAndDate(memberId, date);
    }

    @Transactional(readOnly = true)
    public List<DailyAttendance> findByMemberAndDateRange(MemberId memberId, LocalDate startDate, LocalDate endDate) {
        return repository.findByMemberAndDateRange(memberId, startDate, endDate);
    }
}
