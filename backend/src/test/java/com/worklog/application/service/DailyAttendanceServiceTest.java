package com.worklog.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.worklog.domain.attendance.DailyAttendance;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.JdbcDailyAttendanceRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyAttendanceService")
class DailyAttendanceServiceTest {

    @Mock
    private JdbcDailyAttendanceRepository repository;

    @InjectMocks
    private DailyAttendanceService service;

    private static final TenantId TENANT = TenantId.of("550e8400-e29b-41d4-a716-446655440001");
    private static final MemberId MEMBER = MemberId.of("660e8400-e29b-41d4-a716-446655440001");

    @Test
    @DisplayName("saveAttendance should create new record when none exists")
    void saveAttendance_createsNew() {
        var date = LocalDate.of(2026, 3, 2);
        when(repository.findByMemberAndDate(MEMBER, date)).thenReturn(Optional.empty());

        service.saveAttendance(TENANT, MEMBER, date, LocalTime.of(9, 0), LocalTime.of(18, 0), "Test", 0);

        verify(repository).save(any(DailyAttendance.class));
    }

    @Test
    @DisplayName("saveAttendance should update existing with matching version")
    void saveAttendance_updatesExisting() {
        var date = LocalDate.of(2026, 3, 2);
        var existing = DailyAttendance.create(TENANT, MEMBER, date);
        when(repository.findByMemberAndDate(MEMBER, date)).thenReturn(Optional.of(existing));

        service.saveAttendance(TENANT, MEMBER, date, LocalTime.of(10, 0), LocalTime.of(19, 0), null, 0);

        verify(repository).save(existing);
        assertEquals(LocalTime.of(10, 0), existing.getStartTime());
    }

    @Test
    @DisplayName("saveAttendance should throw on version mismatch")
    void saveAttendance_throwsOnVersionMismatch() {
        var date = LocalDate.of(2026, 3, 2);
        var existing = DailyAttendance.create(TENANT, MEMBER, date);
        when(repository.findByMemberAndDate(MEMBER, date)).thenReturn(Optional.of(existing));

        // existing.version is 0, but we pass version 5
        assertThrows(
                DomainException.class,
                () -> service.saveAttendance(TENANT, MEMBER, date, LocalTime.of(9, 0), LocalTime.of(18, 0), null, 5));
    }

    @Test
    @DisplayName("deleteAttendance should delegate to repository")
    void deleteAttendance_delegates() {
        var date = LocalDate.of(2026, 3, 2);
        service.deleteAttendance(MEMBER, date);
        verify(repository).deleteByMemberAndDate(MEMBER, date);
    }
}
