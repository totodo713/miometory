# Monthly Timesheet (月次勤務表) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** プロジェクト単位の月次勤務表ページを新設し、日次の開始時間・終了時間・稼働時間・備考を閲覧・編集できるようにする。

**Architecture:** DailyAttendance を MemberProjectAssignment と同じ CRUD パターン（JdbcTemplate + UPSERT）で実装。TimesheetController が DailyAttendance + WorkLogEntry hours + デフォルト時間を結合する CQRS 読み取りモデルを提供。フロントエンドは Next.js + Zustand + next-intl で、既存 worklog ページパターンに従う。

**Tech Stack:** Java 21 records, JdbcTemplate (positional `?`), Kotlin test, Next.js 15, React 19, Zustand, next-intl, Vitest + RTL, Playwright

**Design Doc:** `docs/plans/2026-03-02-monthly-timesheet-design.md`

**Issues:** #109 (parent), #110–#119 (child)

---

## Task 1: DB Migrations (#110)

**Files:**
- Create: `backend/src/main/resources/db/migration/V32__daily_attendance.sql`
- Create: `backend/src/main/resources/db/migration/V33__assignment_default_times.sql`

### Step 1: Create V32 — daily_attendance table

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

### Step 2: Create V33 — assignment default times

```sql
-- V33__assignment_default_times.sql
ALTER TABLE member_project_assignments
    ADD COLUMN IF NOT EXISTS default_start_time TIME,
    ADD COLUMN IF NOT EXISTS default_end_time TIME;
```

### Step 3: Verify migrations run

Run: `cd backend && ./gradlew flywayMigrate -Dflyway.url=... 2>&1 | tail -5`
Expected: Both V32 and V33 migrate successfully.

Alternatively, integration tests in Task 4 will run migrations via Testcontainers.

### Step 4: Commit

```bash
git add backend/src/main/resources/db/migration/V32__daily_attendance.sql \
       backend/src/main/resources/db/migration/V33__assignment_default_times.sql
git commit -m "feat(backend): add daily_attendance table and assignment default times (V32, V33)

Closes #110"
```

---

## Task 2: DailyAttendance Domain Entity + ID (#111 part 1/3)

**Files:**
- Create: `backend/src/main/java/com/worklog/domain/attendance/DailyAttendanceId.java`
- Create: `backend/src/main/java/com/worklog/domain/attendance/DailyAttendance.java`
- Test: `backend/src/test/kotlin/com/worklog/domain/attendance/DailyAttendanceTest.kt`

### Step 1: Write the failing test

```kotlin
// DailyAttendanceTest.kt
package com.worklog.domain.attendance

import com.worklog.domain.member.MemberId
import com.worklog.domain.tenant.TenantId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DailyAttendanceTest {

    private val tenantId = TenantId.generate()
    private val memberId = MemberId.generate()
    private val date = LocalDate.of(2026, 3, 2)

    @Test
    fun `create should generate id and set defaults`() {
        val attendance = DailyAttendance.create(
            tenantId, memberId, date,
            LocalTime.of(9, 0), LocalTime.of(18, 0), "test"
        )
        assertNotNull(attendance.id)
        assertEquals(tenantId, attendance.tenantId)
        assertEquals(memberId, attendance.memberId)
        assertEquals(date, attendance.attendanceDate)
        assertEquals(LocalTime.of(9, 0), attendance.startTime)
        assertEquals(LocalTime.of(18, 0), attendance.endTime)
        assertEquals("test", attendance.remarks)
        assertEquals(0, attendance.version)
    }

    @Test
    fun `create should allow null times and remarks`() {
        val attendance = DailyAttendance.create(
            tenantId, memberId, date, null, null, null
        )
        assertNull(attendance.startTime)
        assertNull(attendance.endTime)
        assertNull(attendance.remarks)
    }

    @Test
    fun `should reject endTime before startTime`() {
        assertThrows<IllegalArgumentException> {
            DailyAttendance.create(
                tenantId, memberId, date,
                LocalTime.of(18, 0), LocalTime.of(9, 0), null
            )
        }
    }

    @Test
    fun `should reject remarks over 500 characters`() {
        assertThrows<IllegalArgumentException> {
            DailyAttendance.create(
                tenantId, memberId, date,
                null, null, "a".repeat(501)
            )
        }
    }

    @Test
    fun `should allow equal startTime and endTime`() {
        val attendance = DailyAttendance.create(
            tenantId, memberId, date,
            LocalTime.of(9, 0), LocalTime.of(9, 0), null
        )
        assertEquals(attendance.startTime, attendance.endTime)
    }
}
```

### Step 2: Run test to verify it fails

Run: `cd backend && ./gradlew test --tests "com.worklog.domain.attendance.DailyAttendanceTest" 2>&1 | tail -5`
Expected: FAIL — class not found

### Step 3: Implement DailyAttendanceId

```java
// DailyAttendanceId.java
package com.worklog.domain.attendance;

import com.worklog.domain.shared.EntityId;
import java.util.UUID;

public record DailyAttendanceId(UUID value) implements EntityId {
    public DailyAttendanceId {
        if (value == null) {
            throw new IllegalArgumentException("DailyAttendanceId cannot be null");
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

### Step 4: Implement DailyAttendance

```java
// DailyAttendance.java
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
    private final LocalDate attendanceDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String remarks;
    private int version;

    public DailyAttendance(
            DailyAttendanceId id,
            TenantId tenantId,
            MemberId memberId,
            LocalDate attendanceDate,
            LocalTime startTime,
            LocalTime endTime,
            String remarks,
            int version) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(memberId, "memberId cannot be null");
        Objects.requireNonNull(attendanceDate, "attendanceDate cannot be null");
        validateTimes(startTime, endTime);
        validateRemarks(remarks);
        this.id = id;
        this.tenantId = tenantId;
        this.memberId = memberId;
        this.attendanceDate = attendanceDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.remarks = remarks;
        this.version = version;
    }

    public static DailyAttendance create(
            TenantId tenantId,
            MemberId memberId,
            LocalDate attendanceDate,
            LocalTime startTime,
            LocalTime endTime,
            String remarks) {
        return new DailyAttendance(
                DailyAttendanceId.generate(),
                tenantId,
                memberId,
                attendanceDate,
                startTime,
                endTime,
                remarks,
                0);
    }

    public void update(LocalTime startTime, LocalTime endTime, String remarks) {
        validateTimes(startTime, endTime);
        validateRemarks(remarks);
        this.startTime = startTime;
        this.endTime = endTime;
        this.remarks = remarks;
    }

    private static void validateTimes(LocalTime startTime, LocalTime endTime) {
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("endTime must not be before startTime");
        }
    }

    private static void validateRemarks(String remarks) {
        if (remarks != null && remarks.length() > 500) {
            throw new IllegalArgumentException("remarks must not exceed 500 characters");
        }
    }

    // Getters
    public DailyAttendanceId getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public MemberId getMemberId() { return memberId; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getRemarks() { return remarks; }
    public int getVersion() { return version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DailyAttendance that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
```

### Step 5: Run test to verify it passes

Run: `cd backend && ./gradlew test --tests "com.worklog.domain.attendance.DailyAttendanceTest" 2>&1 | tail -5`
Expected: PASS (5 tests)

### Step 6: Commit

```bash
git add backend/src/main/java/com/worklog/domain/attendance/ \
       backend/src/test/kotlin/com/worklog/domain/attendance/
git commit -m "feat(backend): add DailyAttendance domain entity and ID

#111"
```

---

## Task 3: DailyAttendance Repository (#111 part 2/3)

**Files:**
- Create: `backend/src/main/java/com/worklog/infrastructure/repository/JdbcDailyAttendanceRepository.java`
- Test: `backend/src/test/kotlin/com/worklog/infrastructure/repository/DailyAttendanceRepositoryTest.kt`

### Step 1: Write the failing integration test

```kotlin
// DailyAttendanceRepositoryTest.kt
package com.worklog.infrastructure.repository

import com.worklog.IntegrationTestBase
import com.worklog.domain.attendance.DailyAttendance
import com.worklog.domain.attendance.DailyAttendanceId
import com.worklog.domain.member.MemberId
import com.worklog.domain.tenant.TenantId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyAttendanceRepositoryTest : IntegrationTestBase() {

    @Autowired
    private lateinit var repository: JdbcDailyAttendanceRepository

    private val tenantId = TenantId.of(UUID.fromString(TEST_TENANT_ID))
    private val memberId = MemberId.of(UUID.randomUUID())
    private val date = LocalDate.of(2026, 3, 2)

    @BeforeEach
    fun setUp() {
        createTestMember(memberId.value())
    }

    @Test
    fun `save should insert new attendance`() {
        val attendance = DailyAttendance.create(
            tenantId, memberId, date,
            LocalTime.of(9, 0), LocalTime.of(18, 0), "test"
        )
        repository.save(attendance)

        val found = repository.findByMemberAndDate(memberId, date)
        assertNotNull(found)
        assertEquals(attendance.id, found.id)
        assertEquals(LocalTime.of(9, 0), found.startTime)
        assertEquals(LocalTime.of(18, 0), found.endTime)
        assertEquals("test", found.remarks)
    }

    @Test
    fun `save should upsert on conflict`() {
        val attendance = DailyAttendance.create(
            tenantId, memberId, date,
            LocalTime.of(9, 0), LocalTime.of(18, 0), "first"
        )
        repository.save(attendance)

        attendance.update(LocalTime.of(10, 0), LocalTime.of(19, 0), "updated")
        repository.save(attendance)

        val found = repository.findByMemberAndDate(memberId, date)
        assertNotNull(found)
        assertEquals(LocalTime.of(10, 0), found.startTime)
        assertEquals("updated", found.remarks)
    }

    @Test
    fun `findByMemberAndDateRange should return entries in range`() {
        val start = LocalDate.of(2026, 3, 1)
        for (i in 0L..4L) {
            val d = start.plusDays(i)
            repository.save(
                DailyAttendance.create(tenantId, memberId, d, LocalTime.of(9, 0), LocalTime.of(18, 0), null)
            )
        }
        val results = repository.findByMemberAndDateRange(memberId, start, start.plusDays(2))
        assertEquals(3, results.size)
    }

    @Test
    fun `deleteByMemberAndDate should remove entry`() {
        repository.save(
            DailyAttendance.create(tenantId, memberId, date, LocalTime.of(9, 0), LocalTime.of(18, 0), null)
        )
        repository.deleteByMemberAndDate(memberId, date)
        assertNull(repository.findByMemberAndDate(memberId, date))
    }

    @Test
    fun `findByMemberAndDate should return null when not found`() {
        assertNull(repository.findByMemberAndDate(memberId, date))
    }
}
```

### Step 2: Run test to verify it fails

Run: `cd backend && ./gradlew test --tests "com.worklog.infrastructure.repository.DailyAttendanceRepositoryTest" 2>&1 | tail -10`
Expected: FAIL — bean not found

### Step 3: Implement repository

```java
// JdbcDailyAttendanceRepository.java
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
        String sql =
                """
            INSERT INTO daily_attendance
                (id, tenant_id, member_id, attendance_date, start_time, end_time, remarks, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
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
                attendance.getAttendanceDate(),
                attendance.getStartTime() != null ? Time.valueOf(attendance.getStartTime()) : null,
                attendance.getEndTime() != null ? Time.valueOf(attendance.getEndTime()) : null,
                attendance.getRemarks(),
                attendance.getVersion());
    }

    public DailyAttendance findByMemberAndDate(MemberId memberId, LocalDate date) {
        String sql =
                """
            SELECT id, tenant_id, member_id, attendance_date, start_time, end_time, remarks, version
            FROM daily_attendance
            WHERE member_id = ? AND attendance_date = ?
            """;
        List<DailyAttendance> results =
                jdbcTemplate.query(sql, new DailyAttendanceRowMapper(), memberId.value(), date);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<DailyAttendance> findByMemberAndDateRange(
            MemberId memberId, LocalDate startDate, LocalDate endDate) {
        String sql =
                """
            SELECT id, tenant_id, member_id, attendance_date, start_time, end_time, remarks, version
            FROM daily_attendance
            WHERE member_id = ? AND attendance_date >= ? AND attendance_date <= ?
            ORDER BY attendance_date
            """;
        return jdbcTemplate.query(
                sql, new DailyAttendanceRowMapper(), memberId.value(), startDate, endDate);
    }

    public void deleteByMemberAndDate(MemberId memberId, LocalDate date) {
        jdbcTemplate.update(
                "DELETE FROM daily_attendance WHERE member_id = ? AND attendance_date = ?",
                memberId.value(),
                date);
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
                    rs.getDate("attendance_date").toLocalDate(),
                    startTime != null ? startTime.toLocalTime() : null,
                    endTime != null ? endTime.toLocalTime() : null,
                    rs.getString("remarks"),
                    rs.getInt("version"));
        }
    }
}
```

### Step 4: Run test to verify it passes

Run: `cd backend && ./gradlew test --tests "com.worklog.infrastructure.repository.DailyAttendanceRepositoryTest" 2>&1 | tail -5`
Expected: PASS (5 tests)

### Step 5: Commit

```bash
git add backend/src/main/java/com/worklog/infrastructure/repository/JdbcDailyAttendanceRepository.java \
       backend/src/test/kotlin/com/worklog/infrastructure/repository/DailyAttendanceRepositoryTest.kt
git commit -m "feat(backend): add JdbcDailyAttendanceRepository with UPSERT

#111"
```

---

## Task 4: DailyAttendanceService (#111 part 3/3)

**Files:**
- Create: `backend/src/main/java/com/worklog/application/service/DailyAttendanceService.java`
- Test: `backend/src/test/kotlin/com/worklog/application/service/DailyAttendanceServiceTest.kt`

### Step 1: Write the failing test

```kotlin
// DailyAttendanceServiceTest.kt
package com.worklog.application.service

import com.worklog.domain.attendance.DailyAttendance
import com.worklog.domain.attendance.DailyAttendanceId
import com.worklog.domain.member.MemberId
import com.worklog.domain.tenant.TenantId
import com.worklog.infrastructure.repository.JdbcDailyAttendanceRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DailyAttendanceServiceTest {

    private lateinit var repository: JdbcDailyAttendanceRepository
    private lateinit var service: DailyAttendanceService

    private val tenantId = TenantId.generate()
    private val memberId = MemberId.generate()
    private val date = LocalDate.of(2026, 3, 2)

    @BeforeEach
    fun setUp() {
        repository = mock()
        service = DailyAttendanceService(repository)
    }

    @Test
    fun `saveAttendance should create new attendance when none exists`() {
        whenever(repository.findByMemberAndDate(memberId, date)).thenReturn(null)

        val id = service.saveAttendance(
            tenantId, memberId, date,
            LocalTime.of(9, 0), LocalTime.of(18, 0), "test", null
        )

        assertNotNull(id)
        verify(repository).save(any())
    }

    @Test
    fun `saveAttendance should update existing attendance with matching version`() {
        val existing = DailyAttendance(
            DailyAttendanceId.generate(), tenantId, memberId, date,
            LocalTime.of(9, 0), LocalTime.of(18, 0), "old", 1
        )
        whenever(repository.findByMemberAndDate(memberId, date)).thenReturn(existing)

        service.saveAttendance(
            tenantId, memberId, date,
            LocalTime.of(10, 0), LocalTime.of(19, 0), "new", 1
        )

        verify(repository).save(any())
    }

    @Test
    fun `saveAttendance should throw on version mismatch`() {
        val existing = DailyAttendance(
            DailyAttendanceId.generate(), tenantId, memberId, date,
            LocalTime.of(9, 0), LocalTime.of(18, 0), "old", 2
        )
        whenever(repository.findByMemberAndDate(memberId, date)).thenReturn(existing)

        assertThrows<IllegalStateException> {
            service.saveAttendance(
                tenantId, memberId, date,
                LocalTime.of(10, 0), LocalTime.of(19, 0), "new", 1
            )
        }
    }

    @Test
    fun `deleteAttendance should delegate to repository`() {
        service.deleteAttendance(memberId, date)
        verify(repository).deleteByMemberAndDate(memberId, date)
    }
}
```

**Note:** If `mockito-kotlin` is not in the test dependencies, check `build.gradle` and add if needed. The existing codebase tests use `@SpringBootTest` for integration tests; this test uses plain mocks for unit testing the service logic.

### Step 2: Run test to verify it fails

Run: `cd backend && ./gradlew test --tests "com.worklog.application.service.DailyAttendanceServiceTest" 2>&1 | tail -10`
Expected: FAIL — class not found

### Step 3: Implement service

```java
// DailyAttendanceService.java
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

@Service
public class DailyAttendanceService {

    private final JdbcDailyAttendanceRepository repository;

    public DailyAttendanceService(JdbcDailyAttendanceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UUID saveAttendance(
            TenantId tenantId,
            MemberId memberId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            String remarks,
            Integer expectedVersion) {
        DailyAttendance existing = repository.findByMemberAndDate(memberId, date);

        if (existing != null) {
            if (expectedVersion != null && existing.getVersion() != expectedVersion) {
                throw new IllegalStateException(
                        "Version mismatch: expected " + expectedVersion + " but was " + existing.getVersion());
            }
            existing.update(startTime, endTime, remarks);
            repository.save(existing);
            return existing.getId().value();
        } else {
            DailyAttendance attendance =
                    DailyAttendance.create(tenantId, memberId, date, startTime, endTime, remarks);
            repository.save(attendance);
            return attendance.getId().value();
        }
    }

    @Transactional
    public void deleteAttendance(MemberId memberId, LocalDate date) {
        repository.deleteByMemberAndDate(memberId, date);
    }

    @Transactional(readOnly = true)
    public List<DailyAttendance> getAttendanceRange(
            MemberId memberId, LocalDate startDate, LocalDate endDate) {
        return repository.findByMemberAndDateRange(memberId, startDate, endDate);
    }
}
```

### Step 4: Run test to verify it passes

Run: `cd backend && ./gradlew test --tests "com.worklog.application.service.DailyAttendanceServiceTest" 2>&1 | tail -5`
Expected: PASS (4 tests)

### Step 5: Commit

```bash
git add backend/src/main/java/com/worklog/application/service/DailyAttendanceService.java \
       backend/src/test/kotlin/com/worklog/application/service/DailyAttendanceServiceTest.kt
git commit -m "feat(backend): add DailyAttendanceService with optimistic locking

Closes #111"
```

---

## Task 5: TimesheetController + DTOs + Projection (#112)

**Files:**
- Create: `backend/src/main/java/com/worklog/api/dto/TimesheetResponse.java`
- Create: `backend/src/main/java/com/worklog/api/dto/SaveAttendanceRequest.java`
- Create: `backend/src/main/java/com/worklog/infrastructure/projection/TimesheetProjection.java`
- Create: `backend/src/main/java/com/worklog/api/TimesheetController.java`
- Modify: `backend/src/main/java/com/worklog/domain/project/MemberProjectAssignment.java` (add default time fields)
- Modify: `backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberProjectAssignmentRepository.java` (add default time columns)

### Step 1: Create DTO records

```java
// TimesheetResponse.java
package com.worklog.api.dto;

import java.math.BigDecimal;
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
        List<TimesheetRow> rows,
        TimesheetSummary summary) {

    public record TimesheetRow(
            LocalDate date,
            String dayOfWeek,
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
            Integer attendanceVersion) {}

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

public record SaveAttendanceRequest(
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String remarks,
        Integer version) {}
```

### Step 2: Add default time fields to MemberProjectAssignment

In `MemberProjectAssignment.java`, add fields:
```java
private final LocalTime defaultStartTime; // nullable
private final LocalTime defaultEndTime;   // nullable
```

Update constructor to accept these (as last params, nullable). Update `create()` to pass null for both. Add getters.

In `JdbcMemberProjectAssignmentRepository.java`:
- Update INSERT SQL to include `default_start_time`, `default_end_time`
- Update RowMapper to read `default_start_time`, `default_end_time` (nullable Time → LocalTime)
- Update ON CONFLICT SET clause

### Step 3: Create TimesheetProjection

```java
// TimesheetProjection.java
package com.worklog.infrastructure.projection;

import com.worklog.api.dto.TimesheetResponse;
import com.worklog.application.service.DailyAttendanceService;
import com.worklog.domain.attendance.DailyAttendance;
import com.worklog.domain.member.MemberId;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TimesheetProjection {

    private final JdbcTemplate jdbcTemplate;
    private final DailyAttendanceService attendanceService;

    public TimesheetProjection(JdbcTemplate jdbcTemplate, DailyAttendanceService attendanceService) {
        this.jdbcTemplate = jdbcTemplate;
        this.attendanceService = attendanceService;
    }

    public List<TimesheetResponse.TimesheetRow> buildRows(
            UUID memberId, UUID projectId, LocalDate startDate, LocalDate endDate,
            LocalTime defaultStartTime, LocalTime defaultEndTime,
            Set<LocalDate> holidays, Map<LocalDate, String> holidayNames) {

        // 1. Get attendance records for the period
        List<DailyAttendance> attendances = attendanceService.getAttendanceRange(
                MemberId.of(memberId), startDate, endDate);
        Map<LocalDate, DailyAttendance> attendanceMap = new HashMap<>();
        for (DailyAttendance a : attendances) {
            attendanceMap.put(a.getAttendanceDate(), a);
        }

        // 2. Get work hours from work_log_entries_projection
        String hoursSql = """
            SELECT work_date, SUM(hours) as total_hours
            FROM work_log_entries_projection
            WHERE member_id = ? AND project_id = ? AND work_date >= ? AND work_date <= ?
            GROUP BY work_date
            """;
        List<Map<String, Object>> hoursRows =
                jdbcTemplate.queryForList(hoursSql, memberId, projectId, startDate, endDate);
        Map<LocalDate, BigDecimal> hoursMap = new HashMap<>();
        for (Map<String, Object> row : hoursRows) {
            LocalDate d = ((java.sql.Date) row.get("work_date")).toLocalDate();
            hoursMap.put(d, (BigDecimal) row.get("total_hours"));
        }

        // 3. Build rows for each day in the range
        List<TimesheetResponse.TimesheetRow> rows = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            boolean isHoliday = holidays.contains(d);
            DailyAttendance attendance = attendanceMap.get(d);

            rows.add(new TimesheetResponse.TimesheetRow(
                    d,
                    dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    isWeekend,
                    isHoliday,
                    holidayNames.getOrDefault(d, null),
                    attendance != null ? attendance.getStartTime() : null,
                    attendance != null ? attendance.getEndTime() : null,
                    hoursMap.getOrDefault(d, BigDecimal.ZERO),
                    attendance != null ? attendance.getRemarks() : null,
                    defaultStartTime,
                    defaultEndTime,
                    attendance != null,
                    attendance != null ? attendance.getId().value() : null,
                    attendance != null ? attendance.getVersion() : null));
        }
        return rows;
    }
}
```

### Step 4: Create TimesheetController

```java
// TimesheetController.java
package com.worklog.api;

import com.worklog.api.dto.SaveAttendanceRequest;
import com.worklog.api.dto.TimesheetResponse;
import com.worklog.application.service.DailyAttendanceService;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.projection.TimesheetProjection;
import com.worklog.infrastructure.repository.JdbcMemberProjectAssignmentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/worklog/timesheet")
public class TimesheetController {

    private final TimesheetProjection projection;
    private final DailyAttendanceService attendanceService;
    private final JdbcMemberProjectAssignmentRepository assignmentRepository;
    private final JdbcTemplate jdbcTemplate;

    public TimesheetController(
            TimesheetProjection projection,
            DailyAttendanceService attendanceService,
            JdbcMemberProjectAssignmentRepository assignmentRepository,
            JdbcTemplate jdbcTemplate) {
        this.projection = projection;
        this.attendanceService = attendanceService;
        this.assignmentRepository = assignmentRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/{year}/{month}")
    public ResponseEntity<TimesheetResponse> getTimesheet(
            @PathVariable int year,
            @PathVariable int month,
            @RequestParam UUID memberId,
            @RequestParam UUID projectId,
            @RequestParam(defaultValue = "calendar") String periodType) {

        // Calculate period bounds
        LocalDate periodStart;
        LocalDate periodEnd;
        if ("fiscal".equals(periodType)) {
            // Fiscal: 21st of previous month to 20th of current month
            periodStart = LocalDate.of(year, month, 1).minusMonths(1).withDayOfMonth(21);
            periodEnd = LocalDate.of(year, month, 20);
        } else {
            periodStart = LocalDate.of(year, month, 1);
            periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());
        }

        // Get member name
        String memberName = getMemberName(memberId);

        // Get project name
        String projectName = getProjectName(projectId);

        // Get default times from assignment (nullable)
        LocalTime defaultStart = null;
        LocalTime defaultEnd = null;
        var assignments = assignmentRepository.findByMemberAndProject(
                MemberId.of(memberId), projectId);
        if (assignments != null) {
            defaultStart = assignments.getDefaultStartTime();
            defaultEnd = assignments.getDefaultEndTime();
        }

        // Get holidays (reuse existing HolidayService or holiday table)
        Set<LocalDate> holidays = new HashSet<>();
        Map<LocalDate, String> holidayNames = new HashMap<>();
        loadHolidays(periodStart, periodEnd, holidays, holidayNames);

        // Build rows
        List<TimesheetResponse.TimesheetRow> rows = projection.buildRows(
                memberId, projectId, periodStart, periodEnd,
                defaultStart, defaultEnd, holidays, holidayNames);

        // Build summary
        BigDecimal totalHours = rows.stream()
                .map(TimesheetResponse.TimesheetRow::workingHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int workingDays = (int) rows.stream()
                .filter(r -> r.workingHours().compareTo(BigDecimal.ZERO) > 0)
                .count();
        int businessDays = (int) rows.stream()
                .filter(r -> !r.isWeekend() && !r.isHoliday())
                .count();

        return ResponseEntity.ok(new TimesheetResponse(
                memberId, memberName, projectId, projectName,
                periodType, periodStart, periodEnd, rows,
                new TimesheetResponse.TimesheetSummary(totalHours, workingDays, businessDays)));
    }

    @PutMapping("/attendance")
    public ResponseEntity<Map<String, Object>> saveAttendance(
            @RequestParam UUID memberId,
            @RequestParam UUID tenantId,
            @RequestBody SaveAttendanceRequest request) {
        UUID id = attendanceService.saveAttendance(
                TenantId.of(tenantId),
                MemberId.of(memberId),
                request.date(),
                request.startTime(),
                request.endTime(),
                request.remarks(),
                request.version());
        return ResponseEntity.ok(Map.of("id", id));
    }

    @DeleteMapping("/attendance/{date}")
    public ResponseEntity<Void> deleteAttendance(
            @RequestParam UUID memberId,
            @PathVariable LocalDate date) {
        attendanceService.deleteAttendance(MemberId.of(memberId), date);
        return ResponseEntity.noContent().build();
    }

    // Helper methods — will be refined based on actual repo interfaces
    private String getMemberName(UUID memberId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT display_name FROM members WHERE id = ?", memberId);
        return rows.isEmpty() ? "" : (String) rows.get(0).get("display_name");
    }

    private String getProjectName(UUID projectId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT name FROM projects WHERE id = ?", projectId);
        return rows.isEmpty() ? "" : (String) rows.get(0).get("name");
    }

    private void loadHolidays(
            LocalDate start, LocalDate end,
            Set<LocalDate> holidays, Map<LocalDate, String> holidayNames) {
        // Reuse the holiday logic from CalendarController/HolidayService
        // This will be adjusted to use the existing holiday resolution
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT holiday_date, name_en FROM holidays
                WHERE holiday_date >= ? AND holiday_date <= ?
                """,
                start, end);
        for (Map<String, Object> row : rows) {
            LocalDate d = ((java.sql.Date) row.get("holiday_date")).toLocalDate();
            holidays.add(d);
            holidayNames.put(d, (String) row.get("name_en"));
        }
    }
}
```

**Implementation notes:**
- `findByMemberAndProject` needs to be added to `JdbcMemberProjectAssignmentRepository`. This returns a single assignment with the default times.
- The holiday loading will be refined based on the actual holiday table schema (from `V30__holiday_name_ja.sql` and existing `CalendarController` logic). Read the actual schema and adjust.
- `tenantId` in the PUT endpoint: in the current codebase pattern, tenant context comes from `@RequestParam`. This should follow the same pattern as other controllers.

### Step 5: Commit

```bash
git add backend/src/main/java/com/worklog/api/TimesheetController.java \
       backend/src/main/java/com/worklog/api/dto/TimesheetResponse.java \
       backend/src/main/java/com/worklog/api/dto/SaveAttendanceRequest.java \
       backend/src/main/java/com/worklog/infrastructure/projection/TimesheetProjection.java \
       backend/src/main/java/com/worklog/domain/project/MemberProjectAssignment.java \
       backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberProjectAssignmentRepository.java
git commit -m "feat(backend): add TimesheetController, DTOs, and projection

Closes #112"
```

---

## Task 6: Backend Integration Tests (#113)

**Files:**
- Create: `backend/src/test/kotlin/com/worklog/api/TimesheetControllerTest.kt`

### Step 1: Write integration tests

```kotlin
// TimesheetControllerTest.kt
package com.worklog.api

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TimesheetControllerTest : IntegrationTestBase() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private val testMemberId = UUID.randomUUID()
    private val testProjectId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        createTestMember(testMemberId)
        createTestProject(testProjectId, "TST-001")
        // Insert assignment
        executeInNewTransaction {
            baseJdbcTemplate.update(
                """INSERT INTO member_project_assignments
                   (id, tenant_id, member_id, project_id, assigned_at, is_active)
                   VALUES (?, ?::uuid, ?, ?, CURRENT_TIMESTAMP, true)
                   ON CONFLICT DO NOTHING""",
                UUID.randomUUID(), TEST_TENANT_ID, testMemberId, testProjectId
            )
        }
    }

    @Test
    fun `GET timesheet should return 200 with calendar period`() {
        val response = restTemplate.getForEntity(
            "/api/v1/worklog/timesheet/2026/3?memberId=$testMemberId&projectId=$testProjectId&periodType=calendar",
            Map::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("2026-03-01", body["periodStart"])
        assertEquals("2026-03-31", body["periodEnd"])
        assertNotNull(body["rows"])
        assertNotNull(body["summary"])
    }

    @Test
    fun `GET timesheet should return 200 with fiscal period`() {
        val response = restTemplate.getForEntity(
            "/api/v1/worklog/timesheet/2026/3?memberId=$testMemberId&projectId=$testProjectId&periodType=fiscal",
            Map::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("2026-02-21", body["periodStart"])
        assertEquals("2026-03-20", body["periodEnd"])
    }

    @Test
    fun `PUT attendance should save and return id`() {
        val request = mapOf(
            "date" to "2026-03-02",
            "startTime" to "09:00:00",
            "endTime" to "18:00:00",
            "remarks" to "test",
            "version" to null
        )
        val response = restTemplate.exchange(
            "/api/v1/worklog/timesheet/attendance?memberId=$testMemberId&tenantId=$TEST_TENANT_ID",
            HttpMethod.PUT,
            HttpEntity(request),
            Map::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull((response.body as Map<*, *>)["id"])
    }

    @Test
    fun `DELETE attendance should return 204`() {
        // First save one
        val request = mapOf(
            "date" to "2026-03-02",
            "startTime" to "09:00:00",
            "endTime" to "18:00:00",
            "remarks" to null,
            "version" to null
        )
        restTemplate.exchange(
            "/api/v1/worklog/timesheet/attendance?memberId=$testMemberId&tenantId=$TEST_TENANT_ID",
            HttpMethod.PUT,
            HttpEntity(request),
            Map::class.java
        )

        val response = restTemplate.exchange(
            "/api/v1/worklog/timesheet/attendance/2026-03-02?memberId=$testMemberId",
            HttpMethod.DELETE,
            null,
            Void::class.java
        )
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    @Test
    fun `GET timesheet should include saved attendance data`() {
        // Save attendance for 3/2
        val saveReq = mapOf(
            "date" to "2026-03-02",
            "startTime" to "09:00:00",
            "endTime" to "18:00:00",
            "remarks" to "meeting",
            "version" to null
        )
        restTemplate.exchange(
            "/api/v1/worklog/timesheet/attendance?memberId=$testMemberId&tenantId=$TEST_TENANT_ID",
            HttpMethod.PUT,
            HttpEntity(saveReq),
            Map::class.java
        )

        val response = restTemplate.getForEntity(
            "/api/v1/worklog/timesheet/2026/3?memberId=$testMemberId&projectId=$testProjectId",
            Map::class.java
        )
        val body = response.body as Map<*, *>
        val rows = body["rows"] as List<*>
        // Find the row for 3/2
        val march2 = rows.find { (it as Map<*, *>)["date"] == "2026-03-02" } as Map<*, *>
        assertEquals(true, march2["hasAttendanceRecord"])
        assertEquals("09:00:00", march2["startTime"])
        assertEquals("meeting", march2["remarks"])
    }
}
```

### Step 2: Run tests

Run: `cd backend && ./gradlew test --tests "com.worklog.api.TimesheetControllerTest" 2>&1 | tail -10`
Expected: PASS (5 tests). Fix any issues that arise from schema differences.

### Step 3: Commit

```bash
git add backend/src/test/kotlin/com/worklog/api/TimesheetControllerTest.kt
git commit -m "test(backend): add TimesheetController integration tests

Closes #113"
```

---

## Task 7: Frontend Types + API Client (#114)

**Files:**
- Create: `frontend/app/types/timesheet.ts`
- Modify: `frontend/app/services/api.ts`

### Step 1: Create type definitions

```typescript
// timesheet.ts
export interface TimesheetRow {
  date: string;
  dayOfWeek: string;
  isWeekend: boolean;
  isHoliday: boolean;
  holidayName: string | null;
  startTime: string | null;
  endTime: string | null;
  workingHours: number;
  remarks: string | null;
  defaultStartTime: string | null;
  defaultEndTime: string | null;
  hasAttendanceRecord: boolean;
  attendanceId: string | null;
  attendanceVersion: number | null;
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
  periodType: "calendar" | "fiscal";
  periodStart: string;
  periodEnd: string;
  rows: TimesheetRow[];
  summary: TimesheetSummary;
}

export interface SaveAttendanceRequest {
  date: string;
  startTime: string | null;
  endTime: string | null;
  remarks: string | null;
  version: number | null;
}

export type PeriodType = "calendar" | "fiscal";
```

### Step 2: Add API client methods

In `frontend/app/services/api.ts`, add to the `api` object (inside the `worklog` namespace or as new `timesheet` namespace):

```typescript
timesheet: {
  get: (params: {
    year: number;
    month: number;
    memberId: string;
    projectId: string;
    periodType?: string;
  }) => {
    const query = new URLSearchParams({
      memberId: params.memberId,
      projectId: params.projectId,
      ...(params.periodType && { periodType: params.periodType }),
    });
    return apiClient.get<TimesheetResponse>(
      `/api/v1/worklog/timesheet/${params.year}/${params.month}?${query}`
    );
  },
  saveAttendance: (
    memberId: string,
    tenantId: string,
    data: SaveAttendanceRequest
  ) => {
    const query = new URLSearchParams({ memberId, tenantId });
    return apiClient.put<{ id: string }>(
      `/api/v1/worklog/timesheet/attendance?${query}`,
      data
    );
  },
  deleteAttendance: (memberId: string, date: string) => {
    const query = new URLSearchParams({ memberId });
    return apiClient.delete<void>(
      `/api/v1/worklog/timesheet/attendance/${date}?${query}`
    );
  },
},
```

Add imports at the top of `api.ts`:
```typescript
import type { TimesheetResponse, SaveAttendanceRequest } from "@/types/timesheet";
```

### Step 3: Run lint

Run: `cd frontend && npx biome check --write app/types/timesheet.ts app/services/api.ts`

### Step 4: Commit

```bash
git add frontend/app/types/timesheet.ts frontend/app/services/api.ts
git commit -m "feat(frontend): add timesheet types and API client

Closes #114"
```

---

## Task 8: Frontend Components (#115)

**Files:**
- Create: `frontend/app/components/worklog/timesheet/TimesheetTable.tsx`
- Create: `frontend/app/components/worklog/timesheet/TimesheetRow.tsx`
- Create: `frontend/app/components/worklog/timesheet/TimesheetPeriodToggle.tsx`
- Create: `frontend/app/components/worklog/timesheet/TimesheetSummary.tsx`

### Step 1: TimesheetPeriodToggle

```tsx
"use client";

import { useTranslations } from "next-intl";
import type { PeriodType } from "@/types/timesheet";

interface Props {
  value: PeriodType;
  onChange: (value: PeriodType) => void;
}

export function TimesheetPeriodToggle({ value, onChange }: Props) {
  const t = useTranslations("timesheet");
  return (
    <div className="inline-flex rounded-lg border border-gray-300" role="radiogroup" aria-label={t("periodType")}>
      <button
        type="button"
        role="radio"
        aria-checked={value === "calendar"}
        onClick={() => onChange("calendar")}
        className={`px-4 py-2 text-sm font-medium rounded-l-lg focus:outline-none focus:ring-2 focus:ring-blue-500 ${
          value === "calendar" ? "bg-blue-600 text-white" : "bg-white text-gray-700 hover:bg-gray-50"
        }`}
      >
        {t("calendarPeriod")}
      </button>
      <button
        type="button"
        role="radio"
        aria-checked={value === "fiscal"}
        onClick={() => onChange("fiscal")}
        className={`px-4 py-2 text-sm font-medium rounded-r-lg focus:outline-none focus:ring-2 focus:ring-blue-500 ${
          value === "fiscal" ? "bg-blue-600 text-white" : "bg-white text-gray-700 hover:bg-gray-50"
        }`}
      >
        {t("fiscalPeriod")}
      </button>
    </div>
  );
}
```

### Step 2: TimesheetRow

```tsx
"use client";

import { useTranslations } from "next-intl";
import { useCallback, useState } from "react";
import type { TimesheetRow as TimesheetRowType } from "@/types/timesheet";

interface Props {
  row: TimesheetRowType;
  readOnly: boolean;
  onSave: (date: string, startTime: string | null, endTime: string | null, remarks: string | null, version: number | null) => Promise<void>;
}

export function TimesheetRow({ row, readOnly, onSave }: Props) {
  const t = useTranslations("timesheet");
  const [startTime, setStartTime] = useState(row.startTime ?? row.defaultStartTime ?? "");
  const [endTime, setEndTime] = useState(row.endTime ?? row.defaultEndTime ?? "");
  const [remarks, setRemarks] = useState(row.remarks ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isDefault = !row.hasAttendanceRecord;
  const textStyle = isDefault ? "text-gray-400 italic" : "text-gray-900";

  const handleSave = useCallback(async () => {
    setSaving(true);
    setError(null);
    try {
      await onSave(
        row.date,
        startTime || null,
        endTime || null,
        remarks || null,
        row.attendanceVersion,
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : t("saveError"));
    } finally {
      setSaving(false);
    }
  }, [row.date, startTime, endTime, remarks, row.attendanceVersion, onSave, t]);

  const rowBg = row.isHoliday
    ? "bg-amber-50"
    : row.isWeekend
      ? "bg-gray-50"
      : "bg-white";

  return (
    <tr className={rowBg}>
      <td className="px-3 py-2 text-sm text-gray-900 whitespace-nowrap">
        {new Date(row.date).getDate()}
      </td>
      <td className="px-3 py-2 text-sm text-gray-500 whitespace-nowrap">
        {row.dayOfWeek}
        {row.isHoliday && row.holidayName && (
          <span className="ml-1 text-xs text-amber-600">({row.holidayName})</span>
        )}
      </td>
      <td className="px-3 py-2">
        {readOnly ? (
          <span className={`text-sm ${textStyle}`}>{startTime || "-"}</span>
        ) : (
          <input
            type="time"
            value={startTime}
            onChange={(e) => setStartTime(e.target.value)}
            className={`w-24 text-sm border border-gray-300 rounded px-2 py-1 focus:outline-none focus:ring-2 focus:ring-blue-500 ${textStyle}`}
            aria-label={t("startTimeLabel", { date: row.date })}
          />
        )}
      </td>
      <td className="px-3 py-2">
        {readOnly ? (
          <span className={`text-sm ${textStyle}`}>{endTime || "-"}</span>
        ) : (
          <input
            type="time"
            value={endTime}
            onChange={(e) => setEndTime(e.target.value)}
            className={`w-24 text-sm border border-gray-300 rounded px-2 py-1 focus:outline-none focus:ring-2 focus:ring-blue-500 ${textStyle}`}
            aria-label={t("endTimeLabel", { date: row.date })}
          />
        )}
      </td>
      <td className="px-3 py-2 text-sm text-gray-900 text-right whitespace-nowrap">
        {row.workingHours > 0 ? `${row.workingHours.toFixed(2)}h` : "-"}
      </td>
      <td className="px-3 py-2">
        {readOnly ? (
          <span className="text-sm text-gray-600">{remarks || ""}</span>
        ) : (
          <input
            type="text"
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            maxLength={500}
            placeholder={t("remarksPlaceholder")}
            className="w-full text-sm border border-gray-300 rounded px-2 py-1 focus:outline-none focus:ring-2 focus:ring-blue-500"
            aria-label={t("remarksLabel", { date: row.date })}
          />
        )}
      </td>
      <td className="px-3 py-2">
        {!readOnly && (
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={handleSave}
              disabled={saving}
              className="px-3 py-1 text-xs font-medium text-white bg-blue-600 rounded hover:bg-blue-700 disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {saving ? t("saving") : t("save")}
            </button>
            {error && <span className="text-xs text-red-600" role="alert">{error}</span>}
          </div>
        )}
      </td>
    </tr>
  );
}
```

### Step 3: TimesheetSummary

```tsx
"use client";

import { useTranslations } from "next-intl";
import type { TimesheetSummary as SummaryType } from "@/types/timesheet";

interface Props {
  summary: SummaryType;
}

export function TimesheetSummary({ summary }: Props) {
  const t = useTranslations("timesheet.summary");
  return (
    <div className="grid grid-cols-3 gap-4 mt-4">
      <div className="bg-blue-50 rounded-lg p-4 text-center">
        <p className="text-sm text-gray-600">{t("totalHours")}</p>
        <p className="text-2xl font-bold text-blue-700">{summary.totalWorkingHours.toFixed(1)}h</p>
      </div>
      <div className="bg-green-50 rounded-lg p-4 text-center">
        <p className="text-sm text-gray-600">{t("workingDays")}</p>
        <p className="text-2xl font-bold text-green-700">{summary.totalWorkingDays}</p>
      </div>
      <div className="bg-gray-50 rounded-lg p-4 text-center">
        <p className="text-sm text-gray-600">{t("businessDays")}</p>
        <p className="text-2xl font-bold text-gray-700">{summary.totalBusinessDays}</p>
      </div>
    </div>
  );
}
```

### Step 4: TimesheetTable

```tsx
"use client";

import { useTranslations } from "next-intl";
import type { TimesheetResponse } from "@/types/timesheet";
import { TimesheetRow } from "./TimesheetRow";

interface Props {
  data: TimesheetResponse;
  readOnly: boolean;
  onSave: (date: string, startTime: string | null, endTime: string | null, remarks: string | null, version: number | null) => Promise<void>;
}

export function TimesheetTable({ data, readOnly, onSave }: Props) {
  const t = useTranslations("timesheet.table");
  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-gray-200">
        <thead className="bg-gray-50">
          <tr>
            <th className="px-3 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{t("date")}</th>
            <th className="px-3 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{t("dayOfWeek")}</th>
            <th className="px-3 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{t("startTime")}</th>
            <th className="px-3 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{t("endTime")}</th>
            <th className="px-3 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">{t("workingHours")}</th>
            <th className="px-3 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{t("remarks")}</th>
            {!readOnly && (
              <th className="px-3 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                <span className="sr-only">{t("actions")}</span>
              </th>
            )}
          </tr>
        </thead>
        <tbody className="bg-white divide-y divide-gray-200">
          {data.rows.map((row) => (
            <TimesheetRow key={row.date} row={row} readOnly={readOnly} onSave={onSave} />
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

### Step 5: Run lint

Run: `cd frontend && npx biome check --write app/components/worklog/timesheet/`

### Step 6: Commit

```bash
git add frontend/app/components/worklog/timesheet/
git commit -m "feat(frontend): add Timesheet components (Table, Row, PeriodToggle, Summary)

Closes #115"
```

---

## Task 9: Frontend Page (#116)

**Files:**
- Create: `frontend/app/worklog/timesheet/page.tsx`
- Create: `frontend/app/worklog/timesheet/layout.tsx`

### Step 1: Create layout

```tsx
// layout.tsx
"use client";

import { AuthGuard } from "@/components/shared/AuthGuard";

export default function TimesheetLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard>{children}</AuthGuard>;
}
```

### Step 2: Create page

```tsx
// page.tsx
"use client";

import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/hooks/useAuth";
import { api } from "@/services/api";
import type { PeriodType, TimesheetResponse } from "@/types/timesheet";
import { TimesheetTable } from "@/components/worklog/timesheet/TimesheetTable";
import { TimesheetPeriodToggle } from "@/components/worklog/timesheet/TimesheetPeriodToggle";
import { TimesheetSummary } from "@/components/worklog/timesheet/TimesheetSummary";
import { ProjectSelector } from "@/components/worklog/ProjectSelector";
import { LoadingSpinner } from "@/components/shared/LoadingSpinner";

export default function TimesheetPage() {
  const t = useTranslations("timesheet");
  const { memberId, tenantId } = useAuth();

  const [data, setData] = useState<TimesheetResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // UI state
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [projectId, setProjectId] = useState<string | null>(null);
  const [periodType, setPeriodType] = useState<PeriodType>("calendar");

  const loadTimesheet = useCallback(async () => {
    if (!memberId || !projectId) return;
    setIsLoading(true);
    setError(null);
    try {
      const result = await api.timesheet.get({
        year, month, memberId, projectId, periodType,
      });
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : t("loadError"));
    } finally {
      setIsLoading(false);
    }
  }, [year, month, memberId, projectId, periodType, t]);

  useEffect(() => { loadTimesheet(); }, [loadTimesheet]);

  const handlePrevMonth = () => {
    if (month === 1) { setYear(year - 1); setMonth(12); }
    else { setMonth(month - 1); }
  };

  const handleNextMonth = () => {
    if (month === 12) { setYear(year + 1); setMonth(1); }
    else { setMonth(month + 1); }
  };

  const handleSave = useCallback(async (
    date: string,
    startTime: string | null,
    endTime: string | null,
    remarks: string | null,
    version: number | null,
  ) => {
    if (!memberId || !tenantId) return;
    await api.timesheet.saveAttendance(memberId, tenantId, {
      date, startTime, endTime, remarks, version,
    });
    await loadTimesheet(); // Reload to reflect saved data
  }, [memberId, tenantId, loadTimesheet]);

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-gray-900">{t("title")}</h1>
        </div>

        {/* Controls */}
        <div className="flex flex-wrap items-center gap-4 mb-6">
          <ProjectSelector
            memberId={memberId ?? ""}
            onSelect={(id) => setProjectId(id)}
          />
          <div className="flex items-center gap-2">
            <button type="button" onClick={handlePrevMonth}
              className="p-2 rounded hover:bg-gray-200 focus:outline-none focus:ring-2 focus:ring-blue-500"
              aria-label={t("prevMonth")}>
              &#9664;
            </button>
            <span className="text-lg font-medium">{year}{t("yearSuffix")}{month}{t("monthSuffix")}</span>
            <button type="button" onClick={handleNextMonth}
              className="p-2 rounded hover:bg-gray-200 focus:outline-none focus:ring-2 focus:ring-blue-500"
              aria-label={t("nextMonth")}>
              &#9654;
            </button>
          </div>
          <TimesheetPeriodToggle value={periodType} onChange={setPeriodType} />
        </div>

        {/* Content */}
        {isLoading && <LoadingSpinner />}
        {error && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-4">
            <p className="text-red-800" role="alert">{error}</p>
          </div>
        )}
        {!isLoading && !error && data && (
          <div className="bg-white rounded-lg shadow">
            <TimesheetTable data={data} readOnly={false} onSave={handleSave} />
            <div className="p-4">
              <TimesheetSummary summary={data.summary} />
            </div>
          </div>
        )}
        {!isLoading && !error && !projectId && (
          <div className="bg-white rounded-lg shadow p-8 text-center text-gray-500">
            {t("selectProjectPrompt")}
          </div>
        )}
      </div>
    </div>
  );
}
```

**Notes:**
- `useAuth()` hook — check its actual interface. It may return `{ user: { id, tenantId } }` rather than `{ memberId, tenantId }` directly. Adapt the destructuring to match.
- `ProjectSelector` — verify it exists in `frontend/app/components/worklog/ProjectSelector.tsx` and check its props interface (it may use `onProjectSelect` or similar). The E2E fixture shows it's a combobox (`role="combobox"` + `role="option"`).
- `LoadingSpinner` — verify its path and props.

### Step 3: Run lint and typecheck

Run: `cd frontend && npx biome check --write app/worklog/timesheet/ && npx tsc --noEmit 2>&1 | head -20`

### Step 4: Commit

```bash
git add frontend/app/worklog/timesheet/
git commit -m "feat(frontend): add Timesheet page with month/project navigation

Closes #116"
```

---

## Task 10: i18n + Navigation (#117)

**Files:**
- Modify: `frontend/messages/en.json`
- Modify: `frontend/messages/ja.json`
- Modify: Navigation component (Header or worklog page)

### Step 1: Add i18n keys to en.json

Add `"timesheet"` top-level key:
```json
"timesheet": {
  "title": "Monthly Timesheet",
  "loadError": "Failed to load timesheet",
  "prevMonth": "Previous month",
  "nextMonth": "Next month",
  "yearSuffix": " ",
  "monthSuffix": "",
  "calendarPeriod": "Calendar",
  "fiscalPeriod": "Fiscal",
  "periodType": "Period type",
  "selectProjectPrompt": "Select a project to view the timesheet.",
  "save": "Save",
  "saving": "Saving...",
  "saveError": "Failed to save",
  "startTimeLabel": "Start time for {date}",
  "endTimeLabel": "End time for {date}",
  "remarksLabel": "Remarks for {date}",
  "remarksPlaceholder": "Remarks",
  "table": {
    "date": "Date",
    "dayOfWeek": "Day",
    "startTime": "Start",
    "endTime": "End",
    "workingHours": "Hours",
    "remarks": "Remarks",
    "actions": "Actions"
  },
  "summary": {
    "totalHours": "Total Hours",
    "workingDays": "Working Days",
    "businessDays": "Business Days"
  }
}
```

### Step 2: Add i18n keys to ja.json

```json
"timesheet": {
  "title": "月次勤務表",
  "loadError": "勤務表の読み込みに失敗しました",
  "prevMonth": "前月",
  "nextMonth": "翌月",
  "yearSuffix": "年",
  "monthSuffix": "月",
  "calendarPeriod": "暦月",
  "fiscalPeriod": "締月",
  "periodType": "期間タイプ",
  "selectProjectPrompt": "プロジェクトを選択して勤務表を表示してください。",
  "save": "保存",
  "saving": "保存中...",
  "saveError": "保存に失敗しました",
  "startTimeLabel": "{date}の始業時間",
  "endTimeLabel": "{date}の終業時間",
  "remarksLabel": "{date}の備考",
  "remarksPlaceholder": "備考",
  "table": {
    "date": "日付",
    "dayOfWeek": "曜日",
    "startTime": "始業",
    "endTime": "終業",
    "workingHours": "稼働",
    "remarks": "備考",
    "actions": "操作"
  },
  "summary": {
    "totalHours": "合計稼働時間",
    "workingDays": "稼働日数",
    "businessDays": "営業日数"
  }
}
```

### Step 3: Add navigation link

Find the header/navigation component that has links to worklog pages. Add a "Timesheet" link:
- Read `frontend/app/components/shared/Header.tsx` (or wherever navigation lives)
- Add a link to `/worklog/timesheet`
- i18n key: `header.timesheet` = "Timesheet" / "勤務表"

### Step 4: Run lint

Run: `cd frontend && npx biome check --write messages/en.json messages/ja.json`

### Step 5: Commit

```bash
git add frontend/messages/en.json frontend/messages/ja.json frontend/app/components/shared/Header.tsx
git commit -m "feat(frontend): add timesheet i18n keys and navigation link

Closes #117"
```

---

## Task 11: Admin UI — Default Times (#118)

**Files:**
- Modify: `frontend/app/admin/assignments/` components (add default start/end time fields)
- Modify: Backend assignment update endpoint (if not already supporting the new fields)

### Step 1: Read admin assignment components

Read `frontend/app/admin/assignments/page.tsx` and the `AssignmentManager` component it renders. Understand the current form fields.

### Step 2: Add time fields to assignment form

Add two `<input type="time">` fields for `defaultStartTime` and `defaultEndTime` in the assignment creation/edit form.

```tsx
<div className="flex gap-4">
  <div>
    <label htmlFor="defaultStartTime" className="block text-sm font-medium text-gray-700">
      {t("defaultStartTime")}
    </label>
    <input
      id="defaultStartTime"
      type="time"
      value={defaultStartTime}
      onChange={(e) => setDefaultStartTime(e.target.value)}
      className="mt-1 block w-32 border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
    />
  </div>
  <div>
    <label htmlFor="defaultEndTime" className="block text-sm font-medium text-gray-700">
      {t("defaultEndTime")}
    </label>
    <input
      id="defaultEndTime"
      type="time"
      value={defaultEndTime}
      onChange={(e) => setDefaultEndTime(e.target.value)}
      className="mt-1 block w-32 border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
    />
  </div>
</div>
```

### Step 3: Add i18n keys

In `admin.assignments` namespace:
- `"defaultStartTime"`: "Default Start Time" / "デフォルト始業時間"
- `"defaultEndTime"`: "Default End Time" / "デフォルト終業時間"

### Step 4: Update backend API (if needed)

Ensure the assignment PATCH/PUT endpoint accepts `defaultStartTime` and `defaultEndTime` in the request body. Update the DTO and service accordingly.

### Step 5: Run lint and commit

```bash
cd frontend && npx biome check --write app/admin/assignments/ messages/en.json messages/ja.json
git add frontend/app/admin/assignments/ frontend/messages/en.json frontend/messages/ja.json \
       backend/src/main/java/com/worklog/api/dto/ backend/src/main/java/com/worklog/domain/project/
git commit -m "feat: add default start/end time fields to assignment management

Closes #118"
```

---

## Task 12: Frontend Unit Tests + E2E (#119)

**Files:**
- Create: `frontend/tests/unit/components/worklog/TimesheetTable.test.tsx`
- Create: `frontend/tests/e2e/timesheet.spec.ts`

### Step 1: Unit test for TimesheetTable

```tsx
// TimesheetTable.test.tsx
import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { IntlWrapper } from "../../../helpers/intl";
import { TimesheetTable } from "../../../../app/components/worklog/timesheet/TimesheetTable";
import type { TimesheetResponse } from "../../../../app/types/timesheet";

const mockData: TimesheetResponse = {
  memberId: "member-1",
  memberName: "Test User",
  projectId: "project-1",
  projectName: "Test Project",
  periodType: "calendar",
  periodStart: "2026-03-01",
  periodEnd: "2026-03-31",
  rows: [
    {
      date: "2026-03-02",
      dayOfWeek: "Mon",
      isWeekend: false,
      isHoliday: false,
      holidayName: null,
      startTime: "09:00",
      endTime: "18:00",
      workingHours: 8.0,
      remarks: "meeting",
      defaultStartTime: "09:00",
      defaultEndTime: "18:00",
      hasAttendanceRecord: true,
      attendanceId: "att-1",
      attendanceVersion: 0,
    },
    {
      date: "2026-03-07",
      dayOfWeek: "Sat",
      isWeekend: true,
      isHoliday: false,
      holidayName: null,
      startTime: null,
      endTime: null,
      workingHours: 0,
      remarks: null,
      defaultStartTime: null,
      defaultEndTime: null,
      hasAttendanceRecord: false,
      attendanceId: null,
      attendanceVersion: null,
    },
  ],
  summary: { totalWorkingHours: 8.0, totalWorkingDays: 1, totalBusinessDays: 22 },
};

describe("TimesheetTable", () => {
  it("should render table headers", () => {
    render(
      <IntlWrapper>
        <TimesheetTable data={mockData} readOnly={false} onSave={vi.fn()} />
      </IntlWrapper>,
    );
    // Check headers exist — exact text from ja.json
    expect(screen.getByRole("columnheader", { name: /日付/i })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: /曜日/i })).toBeInTheDocument();
  });

  it("should render rows for each date", () => {
    render(
      <IntlWrapper>
        <TimesheetTable data={mockData} readOnly={false} onSave={vi.fn()} />
      </IntlWrapper>,
    );
    const rows = screen.getAllByRole("row");
    // 1 header row + 2 data rows = 3
    expect(rows.length).toBe(3);
  });

  it("should hide save buttons in readOnly mode", () => {
    render(
      <IntlWrapper>
        <TimesheetTable data={mockData} readOnly={true} onSave={vi.fn()} />
      </IntlWrapper>,
    );
    expect(screen.queryByRole("button", { name: /保存/i })).not.toBeInTheDocument();
  });

  it("should show working hours", () => {
    render(
      <IntlWrapper>
        <TimesheetTable data={mockData} readOnly={true} onSave={vi.fn()} />
      </IntlWrapper>,
    );
    expect(screen.getByText("8.00h")).toBeInTheDocument();
  });
});
```

### Step 2: Run unit test

Run: `cd frontend && npm test -- --run tests/unit/components/worklog/TimesheetTable.test.tsx 2>&1 | tail -10`
Expected: PASS

### Step 3: E2E test

```typescript
// timesheet.spec.ts
import { expect, mockProjectsApi, selectProject, test } from "./fixtures/auth";

test.describe("Timesheet Page", () => {
  test.beforeEach(async ({ page }) => {
    // Mock timesheet API
    await page.route("**/api/v1/worklog/timesheet/**", async (route) => {
      if (route.request().method() === "GET") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            memberId: "test-member",
            memberName: "Test User",
            projectId: "test-project",
            projectName: "Test Project",
            periodType: "calendar",
            periodStart: "2026-03-01",
            periodEnd: "2026-03-31",
            rows: [
              {
                date: "2026-03-02",
                dayOfWeek: "Mon",
                isWeekend: false,
                isHoliday: false,
                holidayName: null,
                startTime: "09:00",
                endTime: "18:00",
                workingHours: 8.0,
                remarks: null,
                defaultStartTime: "09:00",
                defaultEndTime: "18:00",
                hasAttendanceRecord: true,
                attendanceId: "att-1",
                attendanceVersion: 0,
              },
            ],
            summary: {
              totalWorkingHours: 8.0,
              totalWorkingDays: 1,
              totalBusinessDays: 22,
            },
          }),
        });
      }
    });
    await mockProjectsApi(page);
  });

  test("should display timesheet page with table", async ({ page }) => {
    await page.goto("/worklog/timesheet", { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: /月次勤務表/i })).toBeVisible({ timeout: 10000 });
  });

  test("should show data after selecting project", async ({ page }) => {
    await page.goto("/worklog/timesheet", { waitUntil: "networkidle" });
    await selectProject(page, 0, "Project Alpha");
    await expect(page.locator("text=8.00h").first()).toBeVisible();
  });

  test("should toggle between calendar and fiscal periods", async ({ page }) => {
    await page.goto("/worklog/timesheet", { waitUntil: "networkidle" });
    await selectProject(page, 0, "Project Alpha");
    // Click fiscal toggle
    await page.getByRole("radio", { name: /締月/i }).click();
    await expect(page.getByRole("radio", { name: /締月/i })).toHaveAttribute("aria-checked", "true");
  });
});
```

### Step 4: Run E2E test

Run: `cd frontend && npx playwright test --project=chromium tests/e2e/timesheet.spec.ts 2>&1 | tail -10`
Expected: PASS

### Step 5: Commit

```bash
git add frontend/tests/unit/components/worklog/TimesheetTable.test.tsx \
       frontend/tests/e2e/timesheet.spec.ts
git commit -m "test(frontend): add Timesheet unit tests and E2E tests

Closes #119"
```

---

## Final Verification

### Backend
```bash
cd backend && ./gradlew test jacocoTestReport 2>&1 | tail -10
```
Expected: All tests PASS, coverage ≥ 80%

### Frontend
```bash
cd frontend && npm test -- --run 2>&1 | tail -10
cd frontend && npx playwright test --project=chromium 2>&1 | tail -10
```
Expected: All tests PASS

### Lint/Format
```bash
cd frontend && npx biome ci 2>&1 | tail -5
cd backend && ./gradlew spotlessCheck 2>&1 | tail -5
```
Expected: No errors

---

## Key Implementation Notes

1. **Migration versions**: V32 (daily_attendance), V33 (assignment defaults) — V31 is already taken by `V31__standard_working_hours.sql`
2. **JdbcTemplate**: Use positional `?` parameters (NOT NamedParameterJdbcTemplate)
3. **DTOs**: All Java records
4. **IDs**: Records implementing `EntityId` interface
5. **Backend tests**: Kotlin, extend `IntegrationTestBase` for integration tests, use `mockito-kotlin` for unit tests
6. **Frontend mocks**: Use `vi.hoisted()` for error classes, double-mock `../../../app/services/api` and `@/services/api`
7. **i18n**: Run `npx biome check --write` after editing JSON files
8. **E2E**: Import `{ test, expect }` from `./fixtures/auth`, use `selectProject()` helper, `.first()` for strict-mode
9. **Accessibility**: `role="dialog"`, `aria-modal`, `aria-labelledby`, `focus:ring-2`, `role="alert"` for errors
10. **Holiday loading**: Reuse existing holiday table/service from `CalendarController`. Read the actual schema before implementing `loadHolidays()`.
