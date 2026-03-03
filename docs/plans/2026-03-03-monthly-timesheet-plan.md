# Monthly Timesheet Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a project-scoped monthly timesheet page where users can view and inline-edit daily start/end times, working hours, and remarks.

**Architecture:** CRUD entity (DailyAttendance) with a GetTimesheetUseCase interactor that composes attendance records, WorkLogEntry hours, assignment defaults, and holidays into a single read model. Frontend uses an inline-editable table with dirty-row tracking.

**Tech Stack:** Spring Boot 3.5 (Kotlin config / Java domain), PostgreSQL + Flyway, Next.js 16, React 19, Zustand, Tailwind CSS, next-intl, Vitest, Playwright

**Related Issues:** #109 (parent), #110–#119 (sub-issues)

**Design Doc:** `docs/plans/2026-03-02-monthly-timesheet-design.md`

---

## Task 1: DB Migrations (Issue #110)

**Files:**
- Create: `backend/src/main/resources/db/migration/V32__daily_attendance.sql`
- Create: `backend/src/main/resources/db/migration/V33__assignment_default_times.sql`

**Step 1: Create V32 — daily_attendance table**

```sql
-- V32__daily_attendance.sql
CREATE TABLE IF NOT EXISTS daily_attendance (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    member_id UUID NOT NULL REFERENCES members(id),
    attendance_date DATE NOT NULL,
    start_time TIME,
    end_time TIME,
    remarks TEXT,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_daily_attendance_member_date UNIQUE (member_id, attendance_date)
);

CREATE INDEX idx_daily_attendance_member_date ON daily_attendance(member_id, attendance_date);
CREATE INDEX idx_daily_attendance_tenant ON daily_attendance(tenant_id);
```

**Step 2: Create V33 — assignment default times**

```sql
-- V33__assignment_default_times.sql
ALTER TABLE member_project_assignments
    ADD COLUMN default_start_time TIME,
    ADD COLUMN default_end_time TIME;
```

**Step 3: Verify migrations apply cleanly**

Run: `cd backend && ./gradlew test --tests "*.HealthControllerTest" -q`

Flyway runs at app startup during tests. If this passes, migrations are valid.

**Step 4: Commit**

```
feat(backend): add V32/V33 migrations for daily_attendance and assignment defaults

Closes #110
```

---

## Task 2: DailyAttendanceId Value Object (Issue #111)

**Files:**
- Create: `backend/src/main/java/com/worklog/domain/attendance/DailyAttendanceId.java`

**Reference pattern:** `MemberId.java` — record implementing `EntityId` with null-check, `generate()`, `of(UUID)`, `of(String)`.

**Step 1: Write DailyAttendanceId**

```java
package com.worklog.domain.attendance;

import com.worklog.domain.shared.EntityId;
import java.util.UUID;

public record DailyAttendanceId(UUID value) implements EntityId {

    public DailyAttendanceId {
        if (value == null) {
            throw new IllegalArgumentException("DailyAttendanceId value cannot be null");
        }
    }

    public static DailyAttendanceId generate() {
        return new DailyAttendanceId(UUID.randomUUID());
    }

    public static DailyAttendanceId of(UUID value) {
        return new DailyAttendanceId(value);
    }

    public static DailyAttendanceId of(String value) {
        return new DailyAttendanceId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
```

**Step 2: Commit** (will be grouped with Task 3)

---

## Task 3: DailyAttendance Entity (Issue #111)

**Files:**
- Create: `backend/src/main/java/com/worklog/domain/attendance/DailyAttendance.java`
- Test: `backend/src/test/java/com/worklog/domain/attendance/DailyAttendanceTest.java`

**Reference pattern:** `MemberProjectAssignment.java` — immutable ID fields, mutable business state, factory method, validation in setters.

**Step 1: Write failing unit tests for DailyAttendance**

```java
package com.worklog.domain.attendance;

import static org.junit.jupiter.api.Assertions.*;

import com.worklog.domain.member.MemberId;
import com.worklog.domain.tenant.TenantId;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DailyAttendance")
class DailyAttendanceTest {

    private static final TenantId TENANT = TenantId.of("550e8400-e29b-41d4-a716-446655440001");
    private static final MemberId MEMBER = MemberId.of("660e8400-e29b-41d4-a716-446655440001");
    private static final LocalDate DATE = LocalDate.of(2026, 3, 2);

    @Test
    @DisplayName("create should set defaults correctly")
    void create_setsDefaults() {
        var attendance = DailyAttendance.create(TENANT, MEMBER, DATE);

        assertNotNull(attendance.getId());
        assertEquals(TENANT, attendance.getTenantId());
        assertEquals(MEMBER, attendance.getMemberId());
        assertEquals(DATE, attendance.getDate());
        assertNull(attendance.getStartTime());
        assertNull(attendance.getEndTime());
        assertNull(attendance.getRemarks());
        assertEquals(0, attendance.getVersion());
    }

    @Test
    @DisplayName("update should change mutable fields")
    void update_changesMutableFields() {
        var attendance = DailyAttendance.create(TENANT, MEMBER, DATE);
        attendance.update(LocalTime.of(9, 0), LocalTime.of(18, 0), "Meeting day");

        assertEquals(LocalTime.of(9, 0), attendance.getStartTime());
        assertEquals(LocalTime.of(18, 0), attendance.getEndTime());
        assertEquals("Meeting day", attendance.getRemarks());
    }

    @Test
    @DisplayName("update should reject endTime before startTime")
    void update_rejectsEndBeforeStart() {
        var attendance = DailyAttendance.create(TENANT, MEMBER, DATE);
        assertThrows(IllegalArgumentException.class, () ->
            attendance.update(LocalTime.of(18, 0), LocalTime.of(9, 0), null));
    }

    @Test
    @DisplayName("update should allow only startTime without endTime")
    void update_allowsStartTimeOnly() {
        var attendance = DailyAttendance.create(TENANT, MEMBER, DATE);
        attendance.update(LocalTime.of(9, 0), null, null);

        assertEquals(LocalTime.of(9, 0), attendance.getStartTime());
        assertNull(attendance.getEndTime());
    }

    @Test
    @DisplayName("update should reject remarks exceeding 500 characters")
    void update_rejectsLongRemarks() {
        var attendance = DailyAttendance.create(TENANT, MEMBER, DATE);
        String longRemarks = "a".repeat(501);
        assertThrows(IllegalArgumentException.class, () ->
            attendance.update(null, null, longRemarks));
    }

    @Test
    @DisplayName("update should allow exactly 500 character remarks")
    void update_allows500CharRemarks() {
        var attendance = DailyAttendance.create(TENANT, MEMBER, DATE);
        String remarks500 = "a".repeat(500);
        attendance.update(null, null, remarks500);
        assertEquals(500, attendance.getRemarks().length());
    }

    @Test
    @DisplayName("update should allow null times and null remarks to clear fields")
    void update_allowsNulls() {
        var attendance = DailyAttendance.create(TENANT, MEMBER, DATE);
        attendance.update(LocalTime.of(9, 0), LocalTime.of(18, 0), "test");
        attendance.update(null, null, null);

        assertNull(attendance.getStartTime());
        assertNull(attendance.getEndTime());
        assertNull(attendance.getRemarks());
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "com.worklog.domain.attendance.DailyAttendanceTest" -q`

Expected: Compilation error — `DailyAttendance` class does not exist.

**Step 3: Implement DailyAttendance**

```java
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
        return new DailyAttendance(
                DailyAttendanceId.generate(), tenantId, memberId, date, null, null, null, 0);
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

    // Getters
    public DailyAttendanceId getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public MemberId getMemberId() { return memberId; }
    public LocalDate getDate() { return date; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getRemarks() { return remarks; }
    public int getVersion() { return version; }

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
```

**Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.worklog.domain.attendance.DailyAttendanceTest" -q`

Expected: All 7 tests PASS.

**Step 5: Commit**

```
feat(backend): add DailyAttendance domain entity and ID value object

Closes #111
```

---

## Task 4: JdbcDailyAttendanceRepository (Issue #111)

**Files:**
- Create: `backend/src/main/java/com/worklog/infrastructure/repository/JdbcDailyAttendanceRepository.java`
- Test: `backend/src/test/java/com/worklog/infrastructure/repository/JdbcDailyAttendanceRepositoryTest.java`

**Reference pattern:** `JdbcMemberProjectAssignmentRepository.java` — JdbcTemplate, RowMapper, UPSERT.

**Step 1: Write failing integration test**

```java
package com.worklog.infrastructure.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.worklog.IntegrationTestBase;
import com.worklog.domain.attendance.DailyAttendance;
import com.worklog.domain.attendance.DailyAttendanceId;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.tenant.TenantId;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("JdbcDailyAttendanceRepository")
class JdbcDailyAttendanceRepositoryTest extends IntegrationTestBase {

    @Autowired
    private JdbcDailyAttendanceRepository repository;

    private UUID memberId;
    private static final TenantId TENANT_ID =
            TenantId.of("550e8400-e29b-41d4-a716-446655440001");

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();
        createTestMember(memberId);
        // Clean up any existing attendance data for this member
        executeInNewTransaction(() ->
            baseJdbcTemplate.update(
                "DELETE FROM daily_attendance WHERE member_id = ?", memberId));
    }

    @Test
    @DisplayName("save should insert new attendance record")
    void save_insertsNew() {
        var attendance = DailyAttendance.create(
                TENANT_ID, MemberId.of(memberId), LocalDate.of(2026, 3, 2));
        attendance.update(LocalTime.of(9, 0), LocalTime.of(18, 0), "Test day");

        repository.save(attendance);

        var found = repository.findByMemberAndDate(
                MemberId.of(memberId), LocalDate.of(2026, 3, 2));
        assertTrue(found.isPresent());
        assertEquals(LocalTime.of(9, 0), found.get().getStartTime());
        assertEquals(LocalTime.of(18, 0), found.get().getEndTime());
        assertEquals("Test day", found.get().getRemarks());
        assertEquals(0, found.get().getVersion());
    }

    @Test
    @DisplayName("save should upsert on conflict (same member + date)")
    void save_upsertsOnConflict() {
        var date = LocalDate.of(2026, 3, 3);
        var first = DailyAttendance.create(TENANT_ID, MemberId.of(memberId), date);
        first.update(LocalTime.of(9, 0), LocalTime.of(17, 0), null);
        repository.save(first);

        var second = DailyAttendance.create(TENANT_ID, MemberId.of(memberId), date);
        second.update(LocalTime.of(10, 0), LocalTime.of(19, 0), "Updated");
        repository.save(second);

        var found = repository.findByMemberAndDate(MemberId.of(memberId), date);
        assertTrue(found.isPresent());
        assertEquals(LocalTime.of(10, 0), found.get().getStartTime());
        assertEquals("Updated", found.get().getRemarks());
    }

    @Test
    @DisplayName("findByMemberAndDate should return empty for nonexistent record")
    void findByMemberAndDate_returnsEmpty() {
        var found = repository.findByMemberAndDate(
                MemberId.of(memberId), LocalDate.of(2099, 1, 1));
        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("findByMemberAndDateRange should return records in range")
    void findByMemberAndDateRange_returnsInRange() {
        for (int day = 1; day <= 5; day++) {
            var att = DailyAttendance.create(
                    TENANT_ID, MemberId.of(memberId), LocalDate.of(2026, 3, day));
            att.update(LocalTime.of(9, 0), LocalTime.of(18, 0), null);
            repository.save(att);
        }

        List<DailyAttendance> results = repository.findByMemberAndDateRange(
                MemberId.of(memberId), LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 4));

        assertEquals(3, results.size());
        assertEquals(LocalDate.of(2026, 3, 2), results.get(0).getDate());
        assertEquals(LocalDate.of(2026, 3, 4), results.get(2).getDate());
    }

    @Test
    @DisplayName("deleteByMemberAndDate should remove record")
    void deleteByMemberAndDate_removesRecord() {
        var date = LocalDate.of(2026, 3, 10);
        var att = DailyAttendance.create(TENANT_ID, MemberId.of(memberId), date);
        repository.save(att);

        repository.deleteByMemberAndDate(MemberId.of(memberId), date);

        assertTrue(repository.findByMemberAndDate(MemberId.of(memberId), date).isEmpty());
    }

    @Test
    @DisplayName("save should handle null start/end times")
    void save_handlesNullTimes() {
        var att = DailyAttendance.create(
                TENANT_ID, MemberId.of(memberId), LocalDate.of(2026, 3, 15));
        att.update(null, null, "Remarks only");
        repository.save(att);

        var found = repository.findByMemberAndDate(
                MemberId.of(memberId), LocalDate.of(2026, 3, 15));
        assertTrue(found.isPresent());
        assertNull(found.get().getStartTime());
        assertNull(found.get().getEndTime());
        assertEquals("Remarks only", found.get().getRemarks());
    }
}
```

**Step 2: Run to verify failure**

Run: `cd backend && ./gradlew test --tests "com.worklog.infrastructure.repository.JdbcDailyAttendanceRepositoryTest" -q`

Expected: Compilation error — repository class does not exist.

**Step 3: Implement JdbcDailyAttendanceRepository**

```java
package com.worklog.infrastructure.repository;

import com.worklog.domain.attendance.DailyAttendance;
import com.worklog.domain.attendance.DailyAttendanceId;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.tenant.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDailyAttendanceRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDailyAttendanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(DailyAttendance attendance) {
        String sql = """
            INSERT INTO daily_attendance
                (id, tenant_id, member_id, attendance_date, start_time, end_time, remarks, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (member_id, attendance_date) DO UPDATE SET
                start_time = EXCLUDED.start_time,
                end_time = EXCLUDED.end_time,
                remarks = EXCLUDED.remarks,
                version = daily_attendance.version + 1,
                updated_at = CURRENT_TIMESTAMP
            """;

        jdbcTemplate.update(
                sql,
                attendance.getId().value(),
                attendance.getTenantId().value(),
                attendance.getMemberId().value(),
                attendance.getDate(),
                attendance.getStartTime() != null ? Time.valueOf(attendance.getStartTime()) : null,
                attendance.getEndTime() != null ? Time.valueOf(attendance.getEndTime()) : null,
                attendance.getRemarks(),
                attendance.getVersion());
    }

    public Optional<DailyAttendance> findByMemberAndDate(MemberId memberId, LocalDate date) {
        String sql = """
            SELECT id, tenant_id, member_id, attendance_date, start_time, end_time, remarks, version
            FROM daily_attendance
            WHERE member_id = ? AND attendance_date = ?
            """;

        List<DailyAttendance> results =
                jdbcTemplate.query(sql, new DailyAttendanceRowMapper(), memberId.value(), date);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<DailyAttendance> findByMemberAndDateRange(
            MemberId memberId, LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT id, tenant_id, member_id, attendance_date, start_time, end_time, remarks, version
            FROM daily_attendance
            WHERE member_id = ? AND attendance_date >= ? AND attendance_date <= ?
            ORDER BY attendance_date
            """;

        return jdbcTemplate.query(
                sql, new DailyAttendanceRowMapper(), memberId.value(), startDate, endDate);
    }

    public void deleteByMemberAndDate(MemberId memberId, LocalDate date) {
        String sql = "DELETE FROM daily_attendance WHERE member_id = ? AND attendance_date = ?";
        jdbcTemplate.update(sql, memberId.value(), date);
    }

    private static class DailyAttendanceRowMapper implements RowMapper<DailyAttendance> {
        @Override
        public DailyAttendance mapRow(ResultSet rs, int rowNum) throws SQLException {
            Time startTime = rs.getTime("start_time");
            Time endTime = rs.getTime("end_time");

            return new DailyAttendance(
                    DailyAttendanceId.of(rs.getObject("id", UUID.class)),
                    TenantId.of(rs.getObject("tenant_id", UUID.class)),
                    MemberId.of(rs.getObject("member_id", UUID.class)),
                    rs.getObject("attendance_date", LocalDate.class),
                    startTime != null ? startTime.toLocalTime() : null,
                    endTime != null ? endTime.toLocalTime() : null,
                    rs.getString("remarks"),
                    rs.getInt("version"));
        }
    }
}
```

**Step 4: Run tests**

Run: `cd backend && ./gradlew test --tests "com.worklog.infrastructure.repository.JdbcDailyAttendanceRepositoryTest" -q`

Expected: All 6 tests PASS.

**Step 5: Commit**

```
feat(backend): add JdbcDailyAttendanceRepository with UPSERT support
```

---

## Task 5: DailyAttendanceService (Issue #111)

**Files:**
- Create: `backend/src/main/java/com/worklog/application/service/DailyAttendanceService.java`
- Test: `backend/src/test/java/com/worklog/application/service/DailyAttendanceServiceTest.java`

This service handles CRUD only. The read-model composition is in GetTimesheetUseCase (Task 7).

**Step 1: Write failing unit test**

Test the optimistic locking behavior and delegation to repository.

```java
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
        assertThrows(DomainException.class, () ->
            service.saveAttendance(TENANT, MEMBER, date, LocalTime.of(9, 0), LocalTime.of(18, 0), null, 5));
    }

    @Test
    @DisplayName("deleteAttendance should delegate to repository")
    void deleteAttendance_delegates() {
        var date = LocalDate.of(2026, 3, 2);
        service.deleteAttendance(MEMBER, date);
        verify(repository).deleteByMemberAndDate(MEMBER, date);
    }
}
```

**Step 2: Run to verify failure**

Run: `cd backend && ./gradlew test --tests "com.worklog.application.service.DailyAttendanceServiceTest" -q`

**Step 3: Implement DailyAttendanceService**

```java
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
                        "OPTIMISTIC_LOCK_FAILURE",
                        "Attendance record has been modified by another user");
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
    public List<DailyAttendance> findByMemberAndDateRange(
            MemberId memberId, LocalDate startDate, LocalDate endDate) {
        return repository.findByMemberAndDateRange(memberId, startDate, endDate);
    }
}
```

**Step 4: Run tests**

Run: `cd backend && ./gradlew test --tests "com.worklog.application.service.DailyAttendanceServiceTest" -q`

Expected: All 4 tests PASS.

**Step 5: Commit**

```
feat(backend): add DailyAttendanceService with optimistic locking
```

---

## Task 6: MemberProjectAssignment Default Times (Issue #112)

**Files:**
- Modify: `backend/src/main/java/com/worklog/domain/project/MemberProjectAssignment.java`
- Modify: `backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberProjectAssignmentRepository.java`

Add `defaultStartTime` and `defaultEndTime` fields to the existing entity and repository.

**Step 1: Add fields to MemberProjectAssignment**

Add `LocalTime defaultStartTime` and `LocalTime defaultEndTime` as private mutable fields. Add them to the constructor, factory method (default null), and add getters/setters.

**Step 2: Update JdbcMemberProjectAssignmentRepository**

- Add the two columns to `save()` INSERT/UPDATE SQL
- Add them to `findById()` and `findByMemberAndProject()` SELECT SQL
- Add them to `MemberProjectAssignmentRowMapper.mapRow()`
- Add a new query: `findDefaultTimesForMemberProject(MemberId, ProjectId)` returning `Optional<DefaultTimes>` record

**Step 3: Run existing tests to verify no regression**

Run: `cd backend && ./gradlew test --tests "*.JdbcMemberProjectAssignmentRepository*" -q`

**Step 4: Commit**

```
feat(backend): add default start/end time fields to MemberProjectAssignment
```

---

## Task 7: TimesheetController DTOs + GetTimesheetUseCase (Issue #112)

**Files:**
- Create: `backend/src/main/java/com/worklog/api/dto/TimesheetResponse.java`
- Create: `backend/src/main/java/com/worklog/api/dto/SaveAttendanceRequest.java`
- Create: `backend/src/main/java/com/worklog/application/usecase/GetTimesheetUseCase.java`
- Create: `backend/src/main/java/com/worklog/api/TimesheetController.java`
- Test: `backend/src/test/java/com/worklog/application/usecase/GetTimesheetUseCaseTest.java`

This is the largest task. Split into sub-steps.

**Step 1: Create DTOs**

```java
// TimesheetResponse.java
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

    public record TimesheetSummary(
            BigDecimal totalWorkingHours,
            int totalWorkingDays,
            int totalBusinessDays) {}
}
```

```java
// SaveAttendanceRequest.java
package com.worklog.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record SaveAttendanceRequest(
        UUID memberId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String remarks,
        int version) {}
```

**Step 2: Write failing UseCase test**

```java
package com.worklog.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Test that GetTimesheetUseCase correctly composes data from:
// - DailyAttendanceService (attendance records)
// - MonthlyCalendarProjection (WorkLogEntry hours per date)
// - JdbcMemberProjectAssignmentRepository (default times)
// - HolidayResolutionService (holidays)
// - JdbcMemberRepository (member info)
// Focus on the composition logic, not individual sources.
```

Write a test that verifies:
- Rows are generated for every day in the period
- Weekend detection works
- Holiday info is merged
- Attendance records are merged with correct times
- Default times are populated from assignment
- WorkLogEntry hours are merged
- Summary totals are calculated
- canEdit is true for own data, true for manager's subordinate, false for admin

**Step 3: Implement GetTimesheetUseCase**

The UseCase takes `memberId`, `projectId`, `periodStart`, `periodEnd`, and `requestingMemberId` (for access control). It:
1. Loads member info (for display name + tenantId)
2. Loads holidays for the period
3. Loads DailyAttendance records for the period
4. Loads WorkLogEntry hours per date (from MonthlyCalendarProjection or a direct query)
5. Loads assignment defaults
6. Determines canEdit based on requester/target relationship
7. Builds TimesheetRow for each day in the period
8. Calculates summary

**Step 4: Implement TimesheetController**

```java
@RestController
@RequestMapping("/api/v1/worklog/timesheet")
public class TimesheetController {

    private final GetTimesheetUseCase getTimesheetUseCase;
    private final DailyAttendanceService attendanceService;
    private final JdbcMemberRepository memberRepository;

    @GetMapping("/{year}/{month}")
    public ResponseEntity<TimesheetResponse> getTimesheet(
            @PathVariable int year,
            @PathVariable int month,
            @RequestParam UUID projectId,
            @RequestParam(required = false) UUID memberId,
            @RequestParam(defaultValue = "calendar") String periodType) {
        // Validate, resolve memberId, calculate period, delegate to UseCase
    }

    @PutMapping("/attendance")
    public ResponseEntity<Void> saveAttendance(@RequestBody SaveAttendanceRequest request) {
        // Resolve memberId, check access, delegate to service
    }

    @DeleteMapping("/attendance/{memberId}/{date}")
    public ResponseEntity<Void> deleteAttendance(
            @PathVariable UUID memberId,
            @PathVariable LocalDate date) {
        // Check access, delegate to service
    }
}
```

**Step 5: Run tests**

Run: `cd backend && ./gradlew test --tests "com.worklog.application.usecase.GetTimesheetUseCaseTest" -q`

**Step 6: Commit**

```
feat(backend): add TimesheetController, DTOs, and GetTimesheetUseCase

Closes #112
```

---

## Task 8: Backend Integration Tests (Issue #113)

**Files:**
- Create: `backend/src/test/java/com/worklog/api/TimesheetControllerTest.java`

**Reference pattern:** Extend `IntegrationTestBase`, use `TestRestTemplate` for HTTP assertions.

Tests to write:
1. GET timesheet — returns 31 rows for March calendar period
2. GET timesheet — merges attendance data correctly
3. GET timesheet — fiscal period (21st-20th) returns correct date range
4. PUT attendance — creates new record
5. PUT attendance — updates existing record with version
6. PUT attendance — returns 409 Conflict on version mismatch
7. DELETE attendance — removes record
8. Access control — self can edit
9. Access control — manager can view and edit subordinate
10. Access control — admin can view but not edit
11. Access control — unrelated user gets 403

**Commit:**

```
test(backend): add TimesheetController integration tests

Closes #113
```

---

## Task 9: Frontend Types + API Client (Issue #114)

**Files:**
- Create: `frontend/app/types/timesheet.ts`
- Modify: `frontend/app/services/api.ts`

**Step 1: Create TypeScript types**

```typescript
// frontend/app/types/timesheet.ts
export type PeriodType = "calendar" | "fiscal";

export interface TimesheetRow {
  date: string;
  dayOfWeek: string;
  isWeekend: boolean;
  isHoliday: boolean;
  holidayName: string | null;
  startTime: string | null;   // "HH:mm"
  endTime: string | null;     // "HH:mm"
  workingHours: number;
  remarks: string | null;
  defaultStartTime: string | null;
  defaultEndTime: string | null;
  hasAttendanceRecord: boolean;
  attendanceId: string | null;
  attendanceVersion: number;
}

export interface TimesheetSummary {
  totalWorkingHours: number;
  totalWorkingDays: number;
  totalBusinessDays: number;
}

export interface TimesheetResponse {
  memberId: string;
  memberName: string;
  projectId: string;
  projectName: string;
  periodType: PeriodType;
  periodStart: string;
  periodEnd: string;
  canEdit: boolean;
  rows: TimesheetRow[];
  summary: TimesheetSummary;
}

export interface SaveAttendanceRequest {
  memberId?: string;
  date: string;
  startTime: string | null;
  endTime: string | null;
  remarks: string | null;
  version: number;
}
```

**Step 2: Add API endpoints to api.ts**

Add a `timesheet` object inside the existing `worklog` namespace:

```typescript
timesheet: {
  get: (params: {
    year: number;
    month: number;
    projectId: string;
    memberId?: string;
    periodType?: PeriodType;
  }) => {
    const query = new URLSearchParams({
      projectId: params.projectId,
      ...(params.memberId && { memberId: params.memberId }),
      ...(params.periodType && { periodType: params.periodType }),
    });
    return apiClient.get<TimesheetResponse>(
      `/api/v1/worklog/timesheet/${params.year}/${params.month}?${query}`
    );
  },

  saveAttendance: (data: SaveAttendanceRequest) =>
    apiClient.put<void>("/api/v1/worklog/timesheet/attendance", data),

  deleteAttendance: (memberId: string, date: string) =>
    apiClient.delete<void>(
      `/api/v1/worklog/timesheet/attendance/${memberId}/${date}`
    ),
},
```

**Step 3: Commit**

```
feat(frontend): add timesheet types and API client

Closes #114
```

---

## Task 10: Frontend Components (Issue #115)

**Files:**
- Create: `frontend/app/components/worklog/timesheet/TimesheetPeriodToggle.tsx`
- Create: `frontend/app/components/worklog/timesheet/TimesheetSummary.tsx`
- Create: `frontend/app/components/worklog/timesheet/TimesheetRow.tsx`
- Create: `frontend/app/components/worklog/timesheet/TimesheetTable.tsx`

Build bottom-up: toggle → summary → row → table.

**Step 1: TimesheetPeriodToggle** — simple two-button toggle for calendar/fiscal

**Step 2: TimesheetSummary** — renders totalWorkingHours, totalWorkingDays, totalBusinessDays

**Step 3: TimesheetRow** — the core inline-editing component:
- Props: `row: TimesheetRow`, `canEdit: boolean`, `isDirty: boolean`, `onUpdate(field, value)`, `onSave()`, `isSaving: boolean`
- Two `<input type="time">` for start/end
- One `<input type="text">` for remarks
- Read-only `workingHours` display
- Conditional Save button (only if `isDirty && canEdit`)
- Weekend/holiday background colors (`bg-gray-50` / `bg-amber-50`)
- Default time styling: if `!hasAttendanceRecord && defaultStartTime`, show in italic gray

**Step 4: TimesheetTable** — orchestrates rows:
- Manages `editedRows: Map<string, Partial<TimesheetRow>>` for dirty tracking
- Compares edited values to original via JSON.stringify per row
- Calls `api.worklog.timesheet.saveAttendance()` per row on Save
- Shows table header, maps over `rows`, renders `TimesheetRow` for each
- Footer with `TimesheetSummary`

**Step 5: Run lint + format**

Run: `cd frontend && npx biome check --write app/components/worklog/timesheet/`

**Step 6: Commit**

```
feat(frontend): add TimesheetTable, TimesheetRow, TimesheetPeriodToggle, TimesheetSummary

Closes #115
```

---

## Task 11: Frontend Page (Issue #116)

**Files:**
- Create: `frontend/app/worklog/timesheet/page.tsx`
- Modify: `frontend/app/services/worklogStore.ts`

**Step 1: Add Zustand store state**

Add `timesheetProjectId: string | null` and `timesheetPeriodType: PeriodType` to the store. Persist both to localStorage via `partialize`.

**Step 2: Create Timesheet page**

```typescript
"use client";

export default function TimesheetPage() {
  // State: year, month, projectId (from store), periodType (from store)
  // memberId from useAuth + optional member selector for managers
  // Load data via api.worklog.timesheet.get()
  // Render: header, ProjectSelector, month nav, period toggle, member selector, TimesheetTable
}
```

Key layout elements:
- Page title "月次勤務表" / "Monthly Timesheet"
- `ProjectSelector` (reuse existing component)
- Month navigation arrows (← / →) with current month display
- `TimesheetPeriodToggle`
- Member selector (only for managers/admins)
- `TimesheetTable` with data

**Step 3: Run lint + typecheck**

Run: `cd frontend && npx biome check --write app/worklog/timesheet/page.tsx`

**Step 4: Commit**

```
feat(frontend): add /worklog/timesheet page with store state

Closes #116
```

---

## Task 12: i18n + Navigation (Issue #117)

**Files:**
- Modify: `frontend/messages/en.json`
- Modify: `frontend/messages/ja.json`
- Modify: `frontend/app/worklog/page.tsx` (add navigation link)

**Step 1: Add i18n keys**

Add `worklog.timesheet.*` namespace to both en.json and ja.json:

```json
"timesheet": {
  "title": "Monthly Timesheet",
  "date": "Date",
  "dayOfWeek": "Day",
  "startTime": "Start",
  "endTime": "End",
  "workingHours": "Hours",
  "remarks": "Remarks",
  "save": "Save",
  "saving": "Saving...",
  "saved": "Saved",
  "saveError": "Failed to save",
  "totalHours": "Total Hours",
  "workingDays": "Working Days",
  "businessDays": "Business Days",
  "periodCalendar": "Calendar",
  "periodFiscal": "Fiscal",
  "selectProject": "Select project",
  "selectMember": "Select member",
  "noProject": "Please select a project",
  "readOnly": "View only",
  "defaultTime": "Default",
  "summary": "Summary",
  "daysCount": "{working} / {business} days"
}
```

Japanese counterparts in ja.json.

**Step 2: Add navigation link**

Add a link to `/worklog/timesheet` in the worklog page header alongside existing links (Proxy Mode, CSV Import, etc.).

**Step 3: Run lint/format on JSON files**

Run: `cd frontend && npx biome check --write messages/en.json messages/ja.json`

**Step 4: Commit**

```
feat(frontend): add timesheet i18n messages and navigation link

Closes #117
```

---

## Task 13: Admin UI — Default Times (Issue #118)

**Files:**
- Modify: Admin assignment components (identify exact file during implementation)
- Modify: Backend `AdminAssignmentController` or equivalent

**Step 1: Add time inputs to assignment form**

Add two `<input type="time">` fields for default start/end time to the existing assignment management form.

**Step 2: Update backend PATCH endpoint**

Ensure `PATCH /api/v1/admin/assignments/{id}` accepts and persists `defaultStartTime` and `defaultEndTime`.

**Step 3: Commit**

```
feat(admin): add default start/end time fields to assignment management

Closes #118
```

---

## Task 14: Frontend Tests (Issue #119)

**Files:**
- Create: `frontend/tests/components/worklog/timesheet/TimesheetTable.test.tsx`
- Create: `frontend/tests/components/worklog/timesheet/TimesheetRow.test.tsx`

**Tests to write:**
1. TimesheetTable renders correct number of rows
2. TimesheetRow shows default time in italic when no attendance record
3. TimesheetRow shows Save button only when row is dirty
4. TimesheetRow hides inputs when canEdit is false
5. TimesheetRow applies weekend/holiday background colors
6. Save button triggers API call with correct data
7. Period toggle switches between calendar and fiscal

Run: `cd frontend && npm test -- --run`

**Commit:**

```
test(frontend): add TimesheetTable and TimesheetRow component tests
```

---

## Task 15: E2E Tests (Issue #119)

**Files:**
- Create: `frontend/tests/e2e/timesheet.spec.ts`

**Reference:** Check `frontend/tests/e2e/fixtures/auth.ts` for `selectProject()` helper, login fixtures.

**Tests:**
1. Navigate to `/worklog/timesheet` and verify page loads
2. Select a project → table appears with 28-31 rows
3. Toggle calendar/fiscal → date range changes
4. Edit a row (change start time) → Save button appears → click Save → data persists
5. Manager views subordinate's timesheet → can edit
6. Admin views other member's timesheet → read-only

Run: `cd frontend && npx playwright test --project=chromium tests/e2e/timesheet.spec.ts`

**Commit:**

```
test: add E2E tests for timesheet page

Closes #119
```

---

## Verification Checklist

After all tasks complete:

1. `cd backend && ./gradlew test jacocoTestReport` — all pass, 80%+ coverage
2. `cd frontend && npm test -- --run` — all pass
3. `cd frontend && npx playwright test --project=chromium` — all pass
4. `cd backend && ./gradlew formatAll` — no changes
5. `cd frontend && npx biome ci` — no errors
