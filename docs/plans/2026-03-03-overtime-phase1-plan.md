# Overtime Feature Phase 1 — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add standard working hours resolution + overtime calculation/display to the work-log application (Issues #89–#99, single PR).

**Architecture:** Scalar `standard_daily_hours` field on Member/Organization/Tenant with a 4-tier resolution chain (Member → Org hierarchy → Tenant → System default 8.0h). Overtime is a derived value calculated per-day in projections: `max(0, dailyTotal - standardDailyHours)`. No new domain aggregate—purely projection-layer computation.

**Tech Stack:** Java 21 (domain/projections), Kotlin (tests/repositories), Spring Boot 3.5, PostgreSQL/Flyway, Next.js 16/React 19/TypeScript, Vitest, Tailwind CSS.

**Design doc:** `docs/plans/2026-02-28-overtime-registration-design.md`

**IMPORTANT:** Migration version is **V31** (V30 already exists). Issue specs reference V29—ignore that.

---

## Task 1: DB Migration (V31)

> Issue #89 — DB schema changes

**Files:**
- Create: `backend/src/main/resources/db/migration/V31__standard_working_hours.sql`

**Step 1: Create the migration file**

```sql
-- V31: Add standard_daily_hours to members, organization, tenant
-- NULL means "inherit from parent level" in the resolution chain

ALTER TABLE members ADD COLUMN standard_daily_hours DECIMAL(4,2)
  CHECK (standard_daily_hours IS NULL OR (standard_daily_hours >= 0.25 AND standard_daily_hours <= 24.00));

ALTER TABLE organization ADD COLUMN standard_daily_hours DECIMAL(4,2)
  CHECK (standard_daily_hours IS NULL OR (standard_daily_hours >= 0.25 AND standard_daily_hours <= 24.00));

ALTER TABLE tenant ADD COLUMN standard_daily_hours DECIMAL(4,2)
  CHECK (standard_daily_hours IS NULL OR (standard_daily_hours >= 0.25 AND standard_daily_hours <= 24.00));

INSERT INTO system_default_settings (setting_key, setting_value) VALUES
  ('standard_daily_hours', '{"hours": 8.0}')
ON CONFLICT (setting_key) DO NOTHING;
```

**Step 2: Verify migration applies cleanly**

Run: `cd backend && ./gradlew test --tests "*.FlywayMigrationTest" -x detekt` (or full `./gradlew build`)
Expected: PASS — migration applies without errors.

**Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V31__standard_working_hours.sql
git commit -m "feat: add V31 migration for standard_daily_hours columns

Adds nullable standard_daily_hours DECIMAL(4,2) to members, organization,
and tenant tables with CHECK constraints (0.25-24.00). Seeds system default
of 8.0h in system_default_settings.

Closes #89"
```

---

## Task 2: Domain Events

> Issue #90 — New domain events for standard daily hours assignment

**Files:**
- Create: `backend/src/main/java/com/worklog/domain/member/StandardDailyHoursUpdated.java`
- Create: `backend/src/main/java/com/worklog/domain/organization/OrganizationStandardDailyHoursAssigned.java`
- Create: `backend/src/main/java/com/worklog/domain/tenant/TenantStandardDailyHoursAssigned.java`

**Step 1: Create Member event**

Follow the pattern from `OrganizationPatternAssigned.java`:

```java
package com.worklog.domain.member;

import com.worklog.eventsourcing.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StandardDailyHoursUpdated(UUID eventId, Instant occurredAt, UUID aggregateId, BigDecimal standardDailyHours)
        implements DomainEvent {

    public static StandardDailyHoursUpdated create(UUID memberId, BigDecimal standardDailyHours) {
        return new StandardDailyHoursUpdated(UUID.randomUUID(), Instant.now(), memberId, standardDailyHours);
    }

    @Override
    public String eventType() {
        return "StandardDailyHoursUpdated";
    }
}
```

**Step 2: Create Organization event**

```java
package com.worklog.domain.organization;

import com.worklog.eventsourcing.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrganizationStandardDailyHoursAssigned(
        UUID eventId, Instant occurredAt, UUID aggregateId, BigDecimal standardDailyHours) implements DomainEvent {

    public static OrganizationStandardDailyHoursAssigned create(UUID organizationId, BigDecimal standardDailyHours) {
        return new OrganizationStandardDailyHoursAssigned(
                UUID.randomUUID(), Instant.now(), organizationId, standardDailyHours);
    }

    @Override
    public String eventType() {
        return "OrganizationStandardDailyHoursAssigned";
    }
}
```

**Step 3: Create Tenant event**

```java
package com.worklog.domain.tenant;

import com.worklog.eventsourcing.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TenantStandardDailyHoursAssigned(
        UUID eventId, Instant occurredAt, UUID aggregateId, BigDecimal standardDailyHours) implements DomainEvent {

    public static TenantStandardDailyHoursAssigned create(UUID tenantId, BigDecimal standardDailyHours) {
        return new TenantStandardDailyHoursAssigned(UUID.randomUUID(), Instant.now(), tenantId, standardDailyHours);
    }

    @Override
    public String eventType() {
        return "TenantStandardDailyHoursAssigned";
    }
}
```

**Step 4: Commit**

```bash
git add backend/src/main/java/com/worklog/domain/member/StandardDailyHoursUpdated.java \
        backend/src/main/java/com/worklog/domain/organization/OrganizationStandardDailyHoursAssigned.java \
        backend/src/main/java/com/worklog/domain/tenant/TenantStandardDailyHoursAssigned.java
git commit -m "feat: add domain events for standard daily hours assignment"
```

---

## Task 3: Domain Model Changes

> Issue #90 — Add `standardDailyHours` field + apply/command methods to aggregates

**Files:**
- Modify: `backend/src/main/java/com/worklog/domain/member/Member.java`
- Modify: `backend/src/main/java/com/worklog/domain/organization/Organization.java`
- Modify: `backend/src/main/java/com/worklog/domain/tenant/Tenant.java`

**Step 1: Add field + getter to Member.java**

Add after existing fields (around L24):
```java
private BigDecimal standardDailyHours; // Nullable: null = inherit from organization
```

Add getter:
```java
public BigDecimal getStandardDailyHours() {
    return standardDailyHours;
}
```

Add command method (after existing methods):
```java
public void updateStandardDailyHours(BigDecimal hours) {
    StandardDailyHoursUpdated event = StandardDailyHoursUpdated.create(this.id.value(), hours);
    raiseEvent(event);
}
```

Add to `apply()` switch statement:
```java
case StandardDailyHoursUpdated e -> {
    this.standardDailyHours = e.standardDailyHours();
}
```

**Step 2: Add field + getter to Organization.java**

Add after `monthlyPeriodPatternId` field (around L41):
```java
private BigDecimal standardDailyHours; // Nullable: null = inherit from parent org
```

Add getter:
```java
public BigDecimal getStandardDailyHours() {
    return standardDailyHours;
}
```

Add command method:
```java
public void assignStandardDailyHours(BigDecimal standardDailyHours) {
    OrganizationStandardDailyHoursAssigned event =
            OrganizationStandardDailyHoursAssigned.create(this.id.value(), standardDailyHours);
    raiseEvent(event);
}
```

Add to `apply()` switch:
```java
case OrganizationStandardDailyHoursAssigned e -> {
    this.standardDailyHours = e.standardDailyHours();
}
```

**Step 3: Add field + getter to Tenant.java**

Add after `defaultMonthlyPeriodPatternId` field (around L33):
```java
private BigDecimal standardDailyHours; // Nullable: null = use system default
```

Add getter:
```java
public BigDecimal getStandardDailyHours() {
    return standardDailyHours;
}
```

Add command method:
```java
public void assignStandardDailyHours(BigDecimal standardDailyHours) {
    if (this.status == Status.INACTIVE) {
        throw new DomainException("TENANT_INACTIVE", "Cannot update an inactive tenant");
    }
    TenantStandardDailyHoursAssigned event =
            TenantStandardDailyHoursAssigned.create(this.id.value(), standardDailyHours);
    raiseEvent(event);
}
```

Add to `apply()` switch:
```java
case TenantStandardDailyHoursAssigned e -> {
    this.standardDailyHours = e.standardDailyHours();
}
```

**Step 4: Run tests**

Run: `cd backend && ./gradlew test -x detekt`
Expected: Existing tests PASS (new fields are nullable, no constructor changes needed).

**Step 5: Commit**

```bash
git add backend/src/main/java/com/worklog/domain/member/Member.java \
        backend/src/main/java/com/worklog/domain/organization/Organization.java \
        backend/src/main/java/com/worklog/domain/tenant/Tenant.java
git commit -m "feat: add standardDailyHours field to Member, Organization, Tenant aggregates"
```

---

## Task 4: Repository Projection Updates

> Issue #90 — Update repositories to read/write standardDailyHours

**Files:**
- Modify: `backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberRepository.java`
- Modify: `backend/src/main/java/com/worklog/infrastructure/repository/OrganizationRepository.java`
- Modify: `backend/src/main/java/com/worklog/infrastructure/repository/TenantRepository.java`

**Step 1: Update JdbcMemberRepository**

In `findById()` SQL (around L49), add `standard_daily_hours` to the SELECT:
```sql
SELECT id, tenant_id, organization_id, email, display_name,
       manager_id, is_active, version, created_at, updated_at, standard_daily_hours
FROM members
WHERE id = ?
```

In `MemberRowMapper`, read the new column and set it on the Member:
```java
BigDecimal standardDailyHours = rs.getBigDecimal("standard_daily_hours"); // nullable
```

In `updateProjection()`, add `standard_daily_hours` to the INSERT/UPDATE SQL.

**Step 2: Update OrganizationRepository**

In `updateProjection()` (around L90-113), add `standard_daily_hours` column to the INSERT and ON CONFLICT UPDATE:
```sql
"INSERT INTO organization "
    + "(id, tenant_id, parent_id, code, name, level, status, version, "
    + "fiscal_year_pattern_id, monthly_period_pattern_id, standard_daily_hours, created_at, updated_at) "
    ...
    + "standard_daily_hours = EXCLUDED.standard_daily_hours, "
```

Add `organization.getStandardDailyHours()` as the corresponding parameter.

In `findById()` / row mapper, read `standard_daily_hours` column.

**Step 3: Update TenantRepository**

In `updateProjection()` (around L89-108), add `standard_daily_hours` to INSERT/UPDATE.
In row mapper, read `standard_daily_hours`.

**Step 4: Run tests**

Run: `cd backend && ./gradlew test -x detekt`
Expected: PASS.

**Step 5: Commit**

```bash
git add backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberRepository.java \
        backend/src/main/java/com/worklog/infrastructure/repository/OrganizationRepository.java \
        backend/src/main/java/com/worklog/infrastructure/repository/TenantRepository.java
git commit -m "feat: update repository projections to read/write standard_daily_hours"
```

---

## Task 5: SystemDefaultSettingsRepository Extension

> Issue #92 dependency — Read system default standard daily hours

**Files:**
- Modify: `backend/src/main/java/com/worklog/infrastructure/repository/SystemDefaultSettingsRepository.java`

**Step 1: Add constant and getter method**

```java
private static final String KEY_STANDARD_DAILY_HOURS = "standard_daily_hours";

@Transactional(readOnly = true)
public BigDecimal getDefaultStandardDailyHours() {
    String json = jdbcTemplate.queryForObject(
            "SELECT setting_value::text FROM system_default_settings WHERE setting_key = ?",
            String.class,
            KEY_STANDARD_DAILY_HOURS);
    return parseStandardDailyHours(json);
}

private BigDecimal parseStandardDailyHours(String json) {
    try {
        JsonNode node = objectMapper.readTree(json);
        return BigDecimal.valueOf(node.get("hours").asDouble());
    } catch (Exception e) {
        throw new RuntimeException("Failed to parse default standard daily hours", e);
    }
}
```

**Step 2: Run tests**

Run: `cd backend && ./gradlew test -x detekt`

**Step 3: Commit**

```bash
git add backend/src/main/java/com/worklog/infrastructure/repository/SystemDefaultSettingsRepository.java
git commit -m "feat: add getDefaultStandardDailyHours to SystemDefaultSettingsRepository"
```

---

## Task 6: StandardWorkingHoursService (TDD)

> Issue #92 — Resolution chain service

**Files:**
- Create: `backend/src/main/java/com/worklog/application/service/StandardHoursResolution.java`
- Create: `backend/src/main/java/com/worklog/application/service/StandardWorkingHoursService.java`
- Create: `backend/src/test/kotlin/com/worklog/application/service/StandardWorkingHoursServiceTest.kt`

**Step 1: Create the resolution record**

```java
package com.worklog.application.service;

import java.math.BigDecimal;

/**
 * Result of resolving a member's effective standard daily hours.
 *
 * @param hours the resolved standard daily hours
 * @param source where the value came from: "member", "organization:<uuid>", "tenant", or "system"
 */
public record StandardHoursResolution(BigDecimal hours, String source) {}
```

**Step 2: Write the failing test**

```kotlin
package com.worklog.application.service

import com.worklog.domain.member.Member
import com.worklog.domain.member.MemberId
import com.worklog.domain.member.MemberRepository
import com.worklog.domain.organization.Organization
import com.worklog.domain.organization.OrganizationId
import com.worklog.domain.organization.OrganizationRepository
import com.worklog.domain.tenant.Tenant
import com.worklog.domain.tenant.TenantId
import com.worklog.domain.tenant.TenantRepository
import com.worklog.infrastructure.repository.SystemDefaultSettingsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class StandardWorkingHoursServiceTest {
    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var organizationRepository: OrganizationRepository
    @Mock private lateinit var tenantRepository: TenantRepository
    @Mock private lateinit var systemDefaultSettingsRepository: SystemDefaultSettingsRepository

    private lateinit var service: StandardWorkingHoursService

    private val memberId = UUID.randomUUID()
    private val tenantId = UUID.randomUUID()
    private val orgId = UUID.randomUUID()
    private val parentOrgId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        service = StandardWorkingHoursService(
            memberRepository, organizationRepository, tenantRepository, systemDefaultSettingsRepository,
        )
    }

    @Test
    fun `should return member standard daily hours when set`() {
        val member = createMember(standardDailyHours = BigDecimal("7.50"))
        `when`(memberRepository.findById(MemberId.of(memberId))).thenReturn(Optional.of(member))

        val result = service.resolveStandardDailyHours(memberId)

        assertEquals(BigDecimal("7.50"), result.hours())
        assertEquals("member", result.source())
    }

    @Test
    fun `should walk organization hierarchy when member has no setting`() {
        val member = createMember(standardDailyHours = null)
        val org = createOrganization(orgId, parentId = parentOrgId, standardDailyHours = null)
        val parentOrg = createOrganization(parentOrgId, parentId = null, standardDailyHours = BigDecimal("7.00"))

        `when`(memberRepository.findById(MemberId.of(memberId))).thenReturn(Optional.of(member))
        `when`(organizationRepository.findById(OrganizationId.of(orgId))).thenReturn(Optional.of(org))
        `when`(organizationRepository.findById(OrganizationId.of(parentOrgId))).thenReturn(Optional.of(parentOrg))

        val result = service.resolveStandardDailyHours(memberId)

        assertEquals(BigDecimal("7.00"), result.hours())
        assertEquals("organization:$parentOrgId", result.source())
    }

    @Test
    fun `should return tenant default when organization chain has no setting`() {
        val member = createMember(standardDailyHours = null)
        val org = createOrganization(orgId, parentId = null, standardDailyHours = null)
        val tenant = createTenant(standardDailyHours = BigDecimal("6.00"))

        `when`(memberRepository.findById(MemberId.of(memberId))).thenReturn(Optional.of(member))
        `when`(organizationRepository.findById(OrganizationId.of(orgId))).thenReturn(Optional.of(org))
        `when`(tenantRepository.findById(TenantId.of(tenantId))).thenReturn(Optional.of(tenant))

        val result = service.resolveStandardDailyHours(memberId)

        assertEquals(BigDecimal("6.00"), result.hours())
        assertEquals("tenant", result.source())
    }

    @Test
    fun `should return system default 8h when all levels are null`() {
        val member = createMember(standardDailyHours = null)
        val org = createOrganization(orgId, parentId = null, standardDailyHours = null)
        val tenant = createTenant(standardDailyHours = null)

        `when`(memberRepository.findById(MemberId.of(memberId))).thenReturn(Optional.of(member))
        `when`(organizationRepository.findById(OrganizationId.of(orgId))).thenReturn(Optional.of(org))
        `when`(tenantRepository.findById(TenantId.of(tenantId))).thenReturn(Optional.of(tenant))
        `when`(systemDefaultSettingsRepository.defaultStandardDailyHours).thenReturn(BigDecimal("8.0"))

        val result = service.resolveStandardDailyHours(memberId)

        assertEquals(BigDecimal("8.0"), result.hours())
        assertEquals("system", result.source())
    }

    @Test
    fun `should return system default when member has no organization`() {
        val member = createMember(standardDailyHours = null, orgId = null)
        val tenant = createTenant(standardDailyHours = null)

        `when`(memberRepository.findById(MemberId.of(memberId))).thenReturn(Optional.of(member))
        `when`(tenantRepository.findById(TenantId.of(tenantId))).thenReturn(Optional.of(tenant))
        `when`(systemDefaultSettingsRepository.defaultStandardDailyHours).thenReturn(BigDecimal("8.0"))

        val result = service.resolveStandardDailyHours(memberId)

        assertEquals(BigDecimal("8.0"), result.hours())
        assertEquals("system", result.source())
    }

    // --- Helper methods ---

    private fun createMember(
        standardDailyHours: BigDecimal?,
        orgId: UUID? = this.orgId,
    ): Member {
        // Use reflection or a test builder to create Member with standardDailyHours.
        // Implementation depends on Member constructor/builder availability.
        // Alternatively, mock the Member.
        val member = org.mockito.Mockito.mock(Member::class.java)
        `when`(member.id).thenReturn(MemberId.of(memberId))
        `when`(member.tenantId).thenReturn(TenantId.of(tenantId))
        `when`(member.organizationId).thenReturn(orgId?.let { OrganizationId.of(it) })
        `when`(member.standardDailyHours).thenReturn(standardDailyHours)
        return member
    }

    private fun createOrganization(
        id: UUID,
        parentId: UUID?,
        standardDailyHours: BigDecimal?,
    ): Organization {
        val org = org.mockito.Mockito.mock(Organization::class.java)
        `when`(org.id).thenReturn(OrganizationId.of(id))
        `when`(org.tenantId).thenReturn(TenantId.of(tenantId))
        `when`(org.parentId).thenReturn(parentId?.let { OrganizationId.of(it) })
        `when`(org.standardDailyHours).thenReturn(standardDailyHours)
        return org
    }

    private fun createTenant(standardDailyHours: BigDecimal?): Tenant {
        val tenant = org.mockito.Mockito.mock(Tenant::class.java)
        `when`(tenant.standardDailyHours).thenReturn(standardDailyHours)
        return tenant
    }
}
```

**Step 3: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.worklog.application.service.StandardWorkingHoursServiceTest" -x detekt`
Expected: FAIL — `StandardWorkingHoursService` class not found.

**Step 4: Implement the service**

```java
package com.worklog.application.service;

import com.worklog.domain.member.Member;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.member.MemberRepository;
import com.worklog.domain.organization.Organization;
import com.worklog.domain.organization.OrganizationRepository;
import com.worklog.domain.tenant.Tenant;
import com.worklog.domain.tenant.TenantRepository;
import com.worklog.infrastructure.repository.SystemDefaultSettingsRepository;
import com.worklog.shared.exception.DomainException;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StandardWorkingHoursService {

    private final MemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final TenantRepository tenantRepository;
    private final SystemDefaultSettingsRepository systemDefaultSettingsRepository;

    public StandardWorkingHoursService(
            MemberRepository memberRepository,
            OrganizationRepository organizationRepository,
            TenantRepository tenantRepository,
            SystemDefaultSettingsRepository systemDefaultSettingsRepository) {
        this.memberRepository = memberRepository;
        this.organizationRepository = organizationRepository;
        this.tenantRepository = tenantRepository;
        this.systemDefaultSettingsRepository = systemDefaultSettingsRepository;
    }

    /**
     * Resolves the effective standard daily hours for a member.
     *
     * Resolution chain: Member → Organization hierarchy (child→parent→root) → Tenant → System default (8.0h)
     */
    public StandardHoursResolution resolveStandardDailyHours(UUID memberId) {
        Member member = memberRepository
                .findById(MemberId.of(memberId))
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found: " + memberId));

        // Step 1: Check member-level setting
        if (member.getStandardDailyHours() != null) {
            return new StandardHoursResolution(member.getStandardDailyHours(), "member");
        }

        // Step 2: Walk organization hierarchy
        if (member.getOrganizationId() != null) {
            Organization current =
                    organizationRepository.findById(member.getOrganizationId()).orElse(null);
            while (current != null) {
                if (current.getStandardDailyHours() != null) {
                    return new StandardHoursResolution(
                            current.getStandardDailyHours(),
                            "organization:" + current.getId().value());
                }
                if (current.getParentId() != null) {
                    current = organizationRepository.findById(current.getParentId()).orElse(null);
                } else {
                    current = null;
                }
            }
        }

        // Step 3: Check tenant default
        Tenant tenant =
                tenantRepository.findById(member.getTenantId()).orElse(null);
        if (tenant != null && tenant.getStandardDailyHours() != null) {
            return new StandardHoursResolution(tenant.getStandardDailyHours(), "tenant");
        }

        // Step 4: System default
        BigDecimal systemDefault = systemDefaultSettingsRepository.getDefaultStandardDailyHours();
        return new StandardHoursResolution(systemDefault, "system");
    }
}
```

**Step 5: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.worklog.application.service.StandardWorkingHoursServiceTest" -x detekt`
Expected: ALL PASS.

**Step 6: Commit**

```bash
git add backend/src/main/java/com/worklog/application/service/StandardHoursResolution.java \
        backend/src/main/java/com/worklog/application/service/StandardWorkingHoursService.java \
        backend/src/test/kotlin/com/worklog/application/service/StandardWorkingHoursServiceTest.kt
git commit -m "feat: add StandardWorkingHoursService with 4-tier resolution chain

Resolves effective standard daily hours: Member → Organization hierarchy →
Tenant → System default (8.0h). Follows DateInfoService.resolveFiscalYearPattern()
pattern.

Closes #92"
```

---

## Task 7: Projection Record Extensions

> Issues #93, #94 — Extend MonthlySummaryData and DailyEntryProjection records

**Files:**
- Modify: `backend/src/main/java/com/worklog/infrastructure/projection/MonthlySummaryData.java`
- Modify: `backend/src/main/java/com/worklog/infrastructure/projection/DailyEntryProjection.java`
- Modify: `backend/src/main/java/com/worklog/api/dto/DailyCalendarEntry.java`

**Step 1: Extend MonthlySummaryData**

Add 4 new fields at the end of the record:

```java
public record MonthlySummaryData(
        int year,
        int month,
        BigDecimal totalWorkHours,
        BigDecimal totalAbsenceHours,
        int totalBusinessDays,
        List<ProjectSummary> projects,
        String approvalStatus,
        String rejectionReason,
        BigDecimal standardDailyHours,    // Resolved standard hours/day
        BigDecimal standardMonthlyHours,  // standardDailyHours × businessDays
        BigDecimal overtimeHours,         // Sum of daily overtimes
        String standardHoursSource        // "member", "organization:<uuid>", "tenant", "system"
        ) {
    public record ProjectSummary(String projectId, String projectName, BigDecimal totalHours, BigDecimal percentage) {}
}
```

**Step 2: Extend DailyEntryProjection**

Add 2 new fields:

```java
public record DailyEntryProjection(
        LocalDate date,
        BigDecimal totalWorkHours,
        BigDecimal totalAbsenceHours,
        String status,
        boolean isWeekend,
        boolean isHoliday,
        boolean hasProxyEntries,
        BigDecimal standardDailyHours,  // Resolved standard hours for this day
        BigDecimal overtimeHours        // max(0, totalWorkHours - standardDailyHours)
        ) {
    public static DailyEntryProjection empty(LocalDate date, boolean isWeekend) {
        return new DailyEntryProjection(
                date, BigDecimal.ZERO, BigDecimal.ZERO, "DRAFT", isWeekend, false, false,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
```

**Step 3: Extend DailyCalendarEntry DTO**

Add `standardDailyHours` and `overtimeHours` fields to the primary constructor:

```java
public record DailyCalendarEntry(
        LocalDate date,
        BigDecimal totalWorkHours,
        BigDecimal totalAbsenceHours,
        String status,
        boolean isWeekend,
        boolean isHoliday,
        String holidayName,
        String holidayNameJa,
        boolean hasProxyEntries,
        String rejectionSource,
        String rejectionReason,
        BigDecimal standardDailyHours,  // NEW
        BigDecimal overtimeHours        // NEW
        ) {
    // Update backward-compatible constructors to pass null for new fields
}
```

Update existing backward-compatible constructors to pass `null, null` for the two new fields.

**Step 4: Run build (expect compilation errors in projections/controller/tests)**

Run: `cd backend && ./gradlew compileJava compileKotlin 2>&1 | head -50`
Expected: Compilation errors where MonthlySummaryData/DailyEntryProjection/DailyCalendarEntry are constructed.
These will be fixed in Tasks 8-10.

**Step 5: Commit (WIP)**

```bash
git add backend/src/main/java/com/worklog/infrastructure/projection/MonthlySummaryData.java \
        backend/src/main/java/com/worklog/infrastructure/projection/DailyEntryProjection.java \
        backend/src/main/java/com/worklog/api/dto/DailyCalendarEntry.java
git commit -m "feat: extend projection records with overtime fields (WIP - compilation fixes pending)"
```

---

## Task 8: MonthlySummaryProjection Overtime Calculation

> Issue #93 — Add overtime computation to monthly summary

**Files:**
- Modify: `backend/src/main/java/com/worklog/infrastructure/projection/MonthlySummaryProjection.java`
- Modify: `backend/src/test/kotlin/com/worklog/infrastructure/projection/MonthlySummaryProjectionTest.kt`

**Step 1: Add StandardWorkingHoursService to constructor**

```java
private final JdbcTemplate jdbcTemplate;
private final StandardWorkingHoursService standardWorkingHoursService;

public MonthlySummaryProjection(JdbcTemplate jdbcTemplate, StandardWorkingHoursService standardWorkingHoursService) {
    this.jdbcTemplate = jdbcTemplate;
    this.standardWorkingHoursService = standardWorkingHoursService;
}
```

**Step 2: Add getDailyWorkTotals() private method**

```java
private Map<LocalDate, BigDecimal> getDailyWorkTotals(UUID memberId, LocalDate startDate, LocalDate endDate) {
    String sql = """
        SELECT work_date, SUM(hours) as total_hours
        FROM work_log_entries_projection
        WHERE member_id = ? AND work_date BETWEEN ? AND ?
        GROUP BY work_date
        """;
    List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, memberId, startDate, endDate);
    Map<LocalDate, BigDecimal> dailyTotals = new java.util.HashMap<>();
    for (Map<String, Object> row : results) {
        LocalDate date = ((java.sql.Date) row.get("work_date")).toLocalDate();
        BigDecimal hours = (BigDecimal) row.get("total_hours");
        dailyTotals.put(date, hours);
    }
    return dailyTotals;
}
```

**Step 3: Update getMonthlySummary() to calculate overtime**

After getting totalWorkHours and totalBusinessDays, add:

```java
// Resolve standard daily hours for this member
StandardHoursResolution resolution = standardWorkingHoursService.resolveStandardDailyHours(memberId);
BigDecimal standardDailyHours = resolution.hours();
BigDecimal standardMonthlyHours = standardDailyHours.multiply(BigDecimal.valueOf(totalBusinessDays));

// Calculate overtime: sum of max(0, dailyTotal - standardDailyHours) per day
Map<LocalDate, BigDecimal> dailyWorkTotals = getDailyWorkTotals(memberId, startDate, endDate);
BigDecimal overtimeHours = BigDecimal.ZERO;
for (BigDecimal dailyTotal : dailyWorkTotals.values()) {
    BigDecimal dailyOvertime = dailyTotal.subtract(standardDailyHours).max(BigDecimal.ZERO);
    overtimeHours = overtimeHours.add(dailyOvertime);
}
```

Update the return statement to include new fields:
```java
return new MonthlySummaryData(
        year, month, totalWorkHours, totalAbsenceHours, totalBusinessDays,
        projects, approvalStatus.status(), approvalStatus.rejectionReason(),
        standardDailyHours, standardMonthlyHours, overtimeHours, resolution.source());
```

**Step 4: Fix existing MonthlySummaryProjectionTest.kt**

Update `setUp()`:
```kotlin
@Mock
private lateinit var standardWorkingHoursService: StandardWorkingHoursService

@BeforeEach
fun setUp() {
    // Default: system default 8.0h for all tests
    `when`(standardWorkingHoursService.resolveStandardDailyHours(any()))
        .thenReturn(StandardHoursResolution(BigDecimal("8.0"), "system"))
    projection = MonthlySummaryProjection(jdbcTemplate, standardWorkingHoursService)
}
```

Also add mock for the new daily work totals SQL query in tests that use `anyString()`:
```kotlin
// For tests using anyString() matcher, add mock for daily totals query
`when`(jdbcTemplate.queryForList(contains("work_date, SUM"), any(), any(), any()))
    .thenReturn(emptyList<Map<String, Any>>())
```

**Step 5: Add overtime-specific tests**

```kotlin
@Test
fun `getMonthlySummary should calculate overtime for days exceeding standard hours`() {
    val dailyTotals = listOf(
        mapOf("work_date" to Date.valueOf(LocalDate.of(2025, 1, 6)), "total_hours" to BigDecimal("10.00")),
        mapOf("work_date" to Date.valueOf(LocalDate.of(2025, 1, 7)), "total_hours" to BigDecimal("6.00")),
        mapOf("work_date" to Date.valueOf(LocalDate.of(2025, 1, 8)), "total_hours" to BigDecimal("8.00")),
    )

    `when`(jdbcTemplate.queryForList(contains("WITH project_hours"), any(), any(), any()))
        .thenReturn(emptyList<Map<String, Any>>())
    `when`(jdbcTemplate.queryForList(contains("hours_per_day"), any(), any(), any()))
        .thenReturn(emptyList<Map<String, Any>>())
    `when`(jdbcTemplate.queryForList(contains("MonthlyApproval"), any(), any(), any()))
        .thenReturn(emptyList<Map<String, Any>>())
    `when`(jdbcTemplate.queryForList(contains("work_date, SUM"), any(), any(), any()))
        .thenReturn(dailyTotals)

    val result = projection.getMonthlySummary(memberId, 2025, 1)

    // Day 1: 10-8=2h overtime, Day 2: 6-8=0h, Day 3: 8-8=0h
    assertEquals(0, BigDecimal("2.00").compareTo(result.overtimeHours()))
    assertEquals(BigDecimal("8.0"), result.standardDailyHours())
    assertEquals("system", result.standardHoursSource())
}

@Test
fun `getMonthlySummary should return zero overtime when all days under standard`() {
    `when`(jdbcTemplate.queryForList(contains("WITH project_hours"), any(), any(), any()))
        .thenReturn(emptyList<Map<String, Any>>())
    `when`(jdbcTemplate.queryForList(contains("hours_per_day"), any(), any(), any()))
        .thenReturn(emptyList<Map<String, Any>>())
    `when`(jdbcTemplate.queryForList(contains("MonthlyApproval"), any(), any(), any()))
        .thenReturn(emptyList<Map<String, Any>>())
    `when`(jdbcTemplate.queryForList(contains("work_date, SUM"), any(), any(), any()))
        .thenReturn(emptyList<Map<String, Any>>())

    val result = projection.getMonthlySummary(memberId, 2025, 1)

    assertEquals(0, BigDecimal.ZERO.compareTo(result.overtimeHours()))
    // January 2025: 23 business days × 8.0h = 184.0h
    assertEquals(0, BigDecimal("184.0").compareTo(result.standardMonthlyHours()))
}
```

**Step 6: Run tests**

Run: `cd backend && ./gradlew test --tests "com.worklog.infrastructure.projection.MonthlySummaryProjectionTest" -x detekt`
Expected: ALL PASS.

**Step 7: Commit**

```bash
git add backend/src/main/java/com/worklog/infrastructure/projection/MonthlySummaryProjection.java \
        backend/src/test/kotlin/com/worklog/infrastructure/projection/MonthlySummaryProjectionTest.kt
git commit -m "feat: add overtime calculation to MonthlySummaryProjection

Calculates daily overtime as max(0, dailyTotal - standardDailyHours) and
sums for monthly total. Injects StandardWorkingHoursService for resolution.

Closes #93"
```

---

## Task 9: MonthlyCalendarProjection Extension

> Issue #94 — Add overtime to daily calendar entries

**Files:**
- Modify: `backend/src/main/java/com/worklog/infrastructure/projection/MonthlyCalendarProjection.java`
- Modify: `backend/src/test/kotlin/com/worklog/infrastructure/projection/MonthlyCalendarProjectionTest.kt`

**Step 1: Add StandardWorkingHoursService to constructor**

```java
private final JdbcTemplate jdbcTemplate;
private final StandardWorkingHoursService standardWorkingHoursService;

public MonthlyCalendarProjection(JdbcTemplate jdbcTemplate, StandardWorkingHoursService standardWorkingHoursService) {
    this.jdbcTemplate = jdbcTemplate;
    this.standardWorkingHoursService = standardWorkingHoursService;
}
```

**Step 2: Update getDailyEntries() to compute overtime**

At the start of the method, resolve standard hours:
```java
StandardHoursResolution resolution = standardWorkingHoursService.resolveStandardDailyHours(memberId);
BigDecimal standardDailyHours = resolution.hours();
```

In the while loop where entries are created, compute overtime:
```java
BigDecimal overtimeHours = totalHours.subtract(standardDailyHours).max(BigDecimal.ZERO);

entries.add(new DailyEntryProjection(
        current, totalHours, absenceHours, status, isWeekend, false, hasProxyEntries,
        standardDailyHours, overtimeHours));
```

**Step 3: Fix MonthlyCalendarProjectionTest.kt**

Update `setUp()`:
```kotlin
@Mock
private lateinit var standardWorkingHoursService: StandardWorkingHoursService

@BeforeEach
fun setUp() {
    `when`(standardWorkingHoursService.resolveStandardDailyHours(any()))
        .thenReturn(StandardHoursResolution(BigDecimal("8.0"), "system"))
    projection = MonthlyCalendarProjection(jdbcTemplate, standardWorkingHoursService)
}
```

Update any tests that construct `DailyEntryProjection` or check its fields to include the new overtime fields.

**Step 4: Run tests**

Run: `cd backend && ./gradlew test --tests "com.worklog.infrastructure.projection.MonthlyCalendarProjectionTest" -x detekt`
Expected: ALL PASS.

**Step 5: Commit**

```bash
git add backend/src/main/java/com/worklog/infrastructure/projection/MonthlyCalendarProjection.java \
        backend/src/test/kotlin/com/worklog/infrastructure/projection/MonthlyCalendarProjectionTest.kt
git commit -m "feat: add overtime fields to daily calendar projection

Resolves standardDailyHours once per request and computes per-day overtime
in getDailyEntries().

Closes #94"
```

---

## Task 10: CalendarController Updates

> Issue #95 — Update API response to include overtime fields

**Files:**
- Modify: `backend/src/main/java/com/worklog/api/CalendarController.java`
- Modify: `backend/src/test/kotlin/com/worklog/api/CalendarControllerTest.kt`
- Modify: `backend/src/test/kotlin/com/worklog/api/dto/DailyCalendarEntryTest.kt`

**Step 1: Update DailyEntryProjection → DailyCalendarEntry mapping**

In `getMonthlyCalendar()` (around L159-170), add overtime fields to the mapping:

```java
return new DailyCalendarEntry(
        p.date(),
        p.totalWorkHours(),
        p.totalAbsenceHours(),
        p.status(),
        p.isWeekend(),
        isHoliday,
        holiday != null ? holiday.name() : null,
        holiday != null ? holiday.nameJa() : null,
        p.hasProxyEntries(),
        rejectionSource,
        rejectionReason,
        p.standardDailyHours(),    // NEW
        p.overtimeHours());        // NEW
```

**Step 2: Fix DailyCalendarEntryTest.kt**

Update test assertions to account for new fields. The backward-compatible constructors should still work but pass `null` for new fields.

**Step 3: Fix CalendarControllerTest.kt**

Integration tests should automatically include new fields in responses. Verify assertions don't break. Add a test that checks overtime fields are present in the response.

**Step 4: Run all backend tests**

Run: `cd backend && ./gradlew test -x detekt`
Expected: ALL PASS.

**Step 5: Commit**

```bash
git add backend/src/main/java/com/worklog/api/CalendarController.java \
        backend/src/test/kotlin/com/worklog/api/CalendarControllerTest.kt \
        backend/src/test/kotlin/com/worklog/api/dto/DailyCalendarEntryTest.kt
git commit -m "feat: include overtime fields in calendar API responses

Passes standardDailyHours and overtimeHours from projection through to
DailyCalendarEntry DTO. Backward-compatible (new fields are additive).

Closes #95"
```

---

## Task 11: Frontend Type Definitions + API Client

> Issue #96 — Update TypeScript interfaces

**Files:**
- Modify: `frontend/app/types/worklog.ts`
- Modify: `frontend/app/components/worklog/MonthlySummary.tsx` (MonthlySummaryData interface)
- Modify: `frontend/app/services/api.ts`

**Step 1: Update DailyCalendarEntry in worklog.ts**

Add after `rejectionReason` (L70):
```typescript
export interface DailyCalendarEntry {
  // ... existing fields ...
  rejectionSource: "monthly" | "daily" | null;
  rejectionReason: string | null;
  standardDailyHours: number | null;   // NEW
  overtimeHours: number | null;        // NEW
}
```

**Step 2: Update MonthlySummaryData in MonthlySummary.tsx**

Add to the interface:
```typescript
export interface MonthlySummaryData {
  // ... existing fields ...
  approvalStatus: "PENDING" | "SUBMITTED" | "APPROVED" | "REJECTED" | null;
  rejectionReason: string | null;
  standardDailyHours: number;          // NEW
  standardMonthlyHours: number;        // NEW
  overtimeHours: number;               // NEW
  standardHoursSource: string;         // NEW
}
```

**Step 3: Update API client inline types in api.ts**

Update `getMonthlySummary` response type (around L437-454) to include new fields:
```typescript
getMonthlySummary: (params: { year: number; month: number; memberId: string }) => {
  const query = new URLSearchParams({ memberId: params.memberId });
  return apiClient.get<{
    // ... existing fields ...
    approvalStatus: "PENDING" | "SUBMITTED" | "APPROVED" | "REJECTED" | null;
    rejectionReason: string | null;
    standardDailyHours: number;
    standardMonthlyHours: number;
    overtimeHours: number;
    standardHoursSource: string;
  }>(`/api/v1/worklog/calendar/${params.year}/${params.month}/summary?${query}`);
},
```

**Step 4: Run TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: PASS (or show warnings that need fixing in components).

**Step 5: Commit**

```bash
git add frontend/app/types/worklog.ts \
        frontend/app/components/worklog/MonthlySummary.tsx \
        frontend/app/services/api.ts
git commit -m "feat: update frontend type definitions for overtime fields

Closes #96"
```

---

## Task 12: MonthlySummary Overtime Card

> Issue #97 — Add overtime stats card to monthly summary component

**Files:**
- Modify: `frontend/app/components/worklog/MonthlySummary.tsx`

**Step 1: Update grid layout from 3-col to 4-col**

Change grid class:
```tsx
<div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
```

**Step 2: Add overtime card after the third card**

```tsx
{/* Overtime Hours Card */}
<div className="bg-orange-50 rounded-lg p-4 border border-orange-100">
  <div className="text-sm text-orange-600 font-medium">{t("overtimeHours")}</div>
  <div className="text-2xl font-bold text-orange-900 mt-1">{summary.overtimeHours}h</div>
  <div className="text-xs text-orange-600 mt-1">
    {t("requiredHours")}: {summary.standardMonthlyHours}h
  </div>
</div>
```

**Step 3: Run lint + type check**

Run: `cd frontend && npx biome check app/components/worklog/MonthlySummary.tsx && npx tsc --noEmit`
Expected: PASS.

**Step 4: Commit**

```bash
git add frontend/app/components/worklog/MonthlySummary.tsx
git commit -m "feat: add overtime card to MonthlySummary stats grid

Displays monthly overtime hours with orange styling. Shows required hours
as subtitle.

Closes #97"
```

---

## Task 13: DailyEntryForm Overtime Indicator

> Issue #98 — Show overtime when daily hours exceed standard

**Files:**
- Modify: `frontend/app/components/worklog/DailyEntryForm.tsx`
- Modify: `frontend/app/worklog/[date]/page.tsx`

**Step 1: Add standardDailyHours prop to DailyEntryForm**

```typescript
interface DailyEntryFormProps {
  // ... existing props ...
  standardDailyHours?: number;  // Resolved standard hours/day for overtime indicator
}
```

**Step 2: Add overtime indicator to total hours display**

In the total hours display section (around L482-515), after the existing content, add:

```tsx
{/* Overtime indicator */}
{standardDailyHours != null && totalWorkHours > standardDailyHours && (
  <div className="flex justify-between text-orange-600">
    <span>{t("overtime")}:</span>
    <span className="font-medium">{(totalWorkHours - standardDailyHours).toFixed(2)}h</span>
  </div>
)}
{standardDailyHours != null && (
  <div className="flex justify-between text-gray-400 text-xs">
    <span>{t("requiredHours")}:</span>
    <span>{standardDailyHours}h</span>
  </div>
)}
```

**Step 3: Update [date]/page.tsx to fetch and pass standardDailyHours**

Add a `useState` + `useEffect` to fetch monthly summary and extract `standardDailyHours`:

```tsx
const [standardDailyHours, setStandardDailyHours] = useState<number | undefined>(undefined);

useEffect(() => {
  if (!memberId || !parsedDate) return;
  const dateObj = parsedDate;
  const y = dateObj.getFullYear();
  const m = dateObj.getMonth() + 1;
  api.worklog
    .getMonthlySummary({ year: y, month: m, memberId })
    .then((data) => setStandardDailyHours(data.standardDailyHours))
    .catch(() => {}); // Non-critical — overtime indicator is a nice-to-have
}, [memberId, parsedDate]);
```

Pass to DailyEntryForm:
```tsx
<DailyEntryForm
  // ... existing props ...
  standardDailyHours={standardDailyHours}
/>
```

**Step 4: Run lint + type check**

Run: `cd frontend && npx biome check app/components/worklog/DailyEntryForm.tsx app/worklog/\\[date\\]/page.tsx && npx tsc --noEmit`
Expected: PASS.

**Step 5: Commit**

```bash
git add frontend/app/components/worklog/DailyEntryForm.tsx \
        frontend/app/worklog/\\[date\\]/page.tsx
git commit -m "feat: add overtime indicator to DailyEntryForm

Shows overtime hours in orange when daily work exceeds standard hours.
Fetches standardDailyHours from monthly summary API.

Closes #98"
```

---

## Task 14: Frontend Tests

> Issue #99 — Component tests for overtime display

**Files:**
- Modify: `frontend/tests/unit/components/DailyEntryForm.test.tsx`
- Create or modify: `frontend/tests/unit/components/worklog/MonthlySummary.test.tsx`

**Step 1: Add MonthlySummary overtime card tests**

Create or update `MonthlySummary.test.tsx`:

```tsx
describe("MonthlySummary overtime display", () => {
  it("should display overtime hours card with correct value", async () => {
    // Mock API to return summary with overtime
    mockGetMonthlySummary.mockResolvedValue({
      year: 2026, month: 3,
      totalWorkHours: 180, totalAbsenceHours: 0, totalBusinessDays: 22,
      projects: [], approvalStatus: null, rejectionReason: null,
      standardDailyHours: 8, standardMonthlyHours: 176,
      overtimeHours: 4, standardHoursSource: "system",
    });

    render(<MonthlySummary year={2026} month={3} memberId="test-id" />, { wrapper: IntlWrapper });

    await waitFor(() => {
      expect(screen.getByText("4h")).toBeInTheDocument();
    });
  });

  it("should display zero overtime when no excess hours", async () => {
    mockGetMonthlySummary.mockResolvedValue({
      // ... summary with overtimeHours: 0
    });

    render(<MonthlySummary year={2026} month={3} memberId="test-id" />, { wrapper: IntlWrapper });

    await waitFor(() => {
      expect(screen.getByText("0h")).toBeInTheDocument();
    });
  });
});
```

**Step 2: Add DailyEntryForm overtime indicator tests**

Add to `DailyEntryForm.test.tsx`:

```tsx
describe("overtime indicator", () => {
  it("should show overtime when totalWorkHours exceeds standardDailyHours", async () => {
    // Render form with standardDailyHours=8 and mock entries totaling 10h
    // Assert overtime indicator shows "2.00h"
  });

  it("should not show overtime when totalWorkHours is under standardDailyHours", async () => {
    // Render form with standardDailyHours=8 and mock entries totaling 6h
    // Assert overtime indicator is not present
  });

  it("should handle undefined standardDailyHours gracefully", async () => {
    // Render without standardDailyHours prop
    // Assert no overtime section renders
  });
});
```

**Step 3: Run frontend tests**

Run: `cd frontend && npm test -- --run`
Expected: ALL PASS.

**Step 4: Commit**

```bash
git add frontend/tests/
git commit -m "test: add frontend tests for overtime display components

Covers MonthlySummary overtime card and DailyEntryForm overtime indicator.

Closes #99"
```

---

## Task 15: Final Verification

**Step 1: Run all backend tests**

Run: `cd backend && ./gradlew test`
Expected: ALL PASS.

**Step 2: Run all frontend tests**

Run: `cd frontend && npm test -- --run`
Expected: ALL PASS.

**Step 3: Run format checks**

Run: `cd backend && ./gradlew formatAll && cd ../frontend && npx biome check --write .`

**Step 4: Run type check**

Run: `cd frontend && npx tsc --noEmit`
Expected: PASS.

**Step 5: Run E2E tests (if applicable)**

Run: `cd frontend && npx playwright test --project=chromium`
Expected: Existing E2E tests PASS. New nullable fields don't break existing mocks (undefined is falsy).

**Step 6: Final commit with formatting**

```bash
git add -A
git commit -m "chore: apply formatting after overtime feature implementation"
```

---

## File Change Summary

| File | Action | Task |
|------|--------|------|
| `backend/src/main/resources/db/migration/V31__standard_working_hours.sql` | Create | 1 |
| `backend/src/main/java/com/worklog/domain/member/StandardDailyHoursUpdated.java` | Create | 2 |
| `backend/src/main/java/com/worklog/domain/organization/OrganizationStandardDailyHoursAssigned.java` | Create | 2 |
| `backend/src/main/java/com/worklog/domain/tenant/TenantStandardDailyHoursAssigned.java` | Create | 2 |
| `backend/src/main/java/com/worklog/domain/member/Member.java` | Modify | 3 |
| `backend/src/main/java/com/worklog/domain/organization/Organization.java` | Modify | 3 |
| `backend/src/main/java/com/worklog/domain/tenant/Tenant.java` | Modify | 3 |
| `backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberRepository.java` | Modify | 4 |
| `backend/src/main/java/com/worklog/infrastructure/repository/OrganizationRepository.java` | Modify | 4 |
| `backend/src/main/java/com/worklog/infrastructure/repository/TenantRepository.java` | Modify | 4 |
| `backend/src/main/java/com/worklog/infrastructure/repository/SystemDefaultSettingsRepository.java` | Modify | 5 |
| `backend/src/main/java/com/worklog/application/service/StandardHoursResolution.java` | Create | 6 |
| `backend/src/main/java/com/worklog/application/service/StandardWorkingHoursService.java` | Create | 6 |
| `backend/src/test/kotlin/com/worklog/application/service/StandardWorkingHoursServiceTest.kt` | Create | 6 |
| `backend/src/main/java/com/worklog/infrastructure/projection/MonthlySummaryData.java` | Modify | 7 |
| `backend/src/main/java/com/worklog/infrastructure/projection/DailyEntryProjection.java` | Modify | 7 |
| `backend/src/main/java/com/worklog/api/dto/DailyCalendarEntry.java` | Modify | 7 |
| `backend/src/main/java/com/worklog/infrastructure/projection/MonthlySummaryProjection.java` | Modify | 8 |
| `backend/src/test/kotlin/com/worklog/infrastructure/projection/MonthlySummaryProjectionTest.kt` | Modify | 8 |
| `backend/src/main/java/com/worklog/infrastructure/projection/MonthlyCalendarProjection.java` | Modify | 9 |
| `backend/src/test/kotlin/com/worklog/infrastructure/projection/MonthlyCalendarProjectionTest.kt` | Modify | 9 |
| `backend/src/main/java/com/worklog/api/CalendarController.java` | Modify | 10 |
| `backend/src/test/kotlin/com/worklog/api/CalendarControllerTest.kt` | Modify | 10 |
| `backend/src/test/kotlin/com/worklog/api/dto/DailyCalendarEntryTest.kt` | Modify | 10 |
| `frontend/app/types/worklog.ts` | Modify | 11 |
| `frontend/app/services/api.ts` | Modify | 11 |
| `frontend/app/components/worklog/MonthlySummary.tsx` | Modify | 11, 12 |
| `frontend/app/components/worklog/DailyEntryForm.tsx` | Modify | 13 |
| `frontend/app/worklog/[date]/page.tsx` | Modify | 13 |
| `frontend/tests/unit/components/worklog/MonthlySummary.test.tsx` | Create/Modify | 14 |
| `frontend/tests/unit/components/DailyEntryForm.test.tsx` | Modify | 14 |

## Notes for Implementer

- **Mock pattern for Kotlin tests**: Use Mockito mocks for domain objects (Member, Organization, Tenant) rather than constructing them directly—constructors may require many arguments.
- **AGENTS.md testing gotchas**: Review the "Testing Gotchas" section, especially: Mockito+Kotlin varargs, MockK+Java varargs, fake timers, and IntlWrapper locale defaults.
- **Record field ordering matters**: Java records use positional constructors. New fields must be appended at the end to minimize changes in backward-compatible constructors.
- **Biome formatting**: Run `npx biome format --write` after editing TSX files. Watch for trailing zeros in CSS values.
- **Spotless formatting**: Run `./gradlew formatAll` before committing backend changes. Multi-arg constructors get reformatted to one-arg-per-line.
