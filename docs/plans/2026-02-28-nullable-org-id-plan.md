# Nullable organization_id + member.assign_tenant Permission — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make `members.organization_id` nullable and add `member.assign_tenant` permission, preparing the foundation for public user registration.

**Architecture:** Backward-compatible DB schema change + domain model relaxation. The `OrganizationId` value object keeps its non-null invariant; nullability is expressed at the `Member` entity level. A new permission `member.assign_tenant` is seeded for SYSTEM_ADMIN and TENANT_ADMIN roles.

**Tech Stack:** Java 21 (domain), Kotlin (tests/infra), PostgreSQL (Flyway migration), JUnit 5, Testcontainers

---

### Task 1: DB Migration — V27__nullable_member_organization.sql

**Files:**
- Create: `backend/src/main/resources/db/migration/V27__nullable_member_organization.sql`

**Step 1: Create migration file**

```sql
-- ==========================================
-- Nullable Member Organization + assign_tenant Permission
-- Migration: V27__nullable_member_organization.sql
-- Feature: Public user registration foundation (Issue #47)
-- Date: 2026-02-28
-- ==========================================
-- Makes organization_id nullable on members table and adds member.assign_tenant permission.
-- FK constraint REFERENCES organization(id) is retained — NULL values skip FK validation per SQL standard.

-- 1. Drop NOT NULL constraint on organization_id
ALTER TABLE members ALTER COLUMN organization_id DROP NOT NULL;

-- 2. Add member.assign_tenant permission
INSERT INTO permissions (id, name, description, created_at) VALUES
    (gen_random_uuid(), 'member.assign_tenant', 'Assign a member to a tenant/organization', NOW())
ON CONFLICT (name) DO NOTHING;

-- 3. Grant to SYSTEM_ADMIN and TENANT_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('SYSTEM_ADMIN', 'TENANT_ADMIN')
  AND p.name = 'member.assign_tenant'
ON CONFLICT DO NOTHING;

-- Rollback:
-- ALTER TABLE members ALTER COLUMN organization_id SET NOT NULL;
-- DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE name = 'member.assign_tenant');
-- DELETE FROM permissions WHERE name = 'member.assign_tenant';
```

**Step 2: Verify migration applies cleanly**

Run: `cd backend && ./gradlew test --tests "com.worklog.api.HealthControllerTest.healthEndpointReturnsOK"`
Expected: PASS (Flyway applies migration on context startup)

**Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V27__nullable_member_organization.sql
git commit -m "feat(db): add V27 migration — nullable organization_id + assign_tenant permission

Refs #47"
```

---

### Task 2: Member Domain — Remove requireNonNull and Add Factory/Helper Methods

**Files:**
- Modify: `backend/src/main/java/com/worklog/domain/member/Member.java:57` (remove requireNonNull)
- Modify: `backend/src/main/java/com/worklog/domain/member/Member.java` (add createForTenant, hasOrganization)
- Modify: `backend/src/test/kotlin/com/worklog/domain/member/MemberTest.kt` (update + add tests)

**Step 1: Write the failing tests in MemberTest.kt**

Add to `MemberTest.kt`:

```kotlin
@Test
fun `constructor should allow null organizationId`() {
    val member = Member(
        MemberId.generate(),
        tenantId,
        null,
        "user@example.com",
        "John Doe",
        null,
        true,
        Instant.now(),
    )

    assertNull(member.organizationId)
}

@Test
fun `createForTenant should create member without organization`() {
    val member = Member.createForTenant(
        tenantId,
        "user@example.com",
        "John Doe",
    )

    assertNotNull(member.id)
    assertEquals(tenantId, member.tenantId)
    assertNull(member.organizationId)
    assertEquals("user@example.com", member.email)
    assertEquals("John Doe", member.displayName)
    assertTrue(member.isActive)
    assertNull(member.managerId)
}

@Test
fun `hasOrganization should return true when organization is set`() {
    val member = Member.create(
        tenantId,
        organizationId,
        "user@example.com",
        "John Doe",
        null,
    )

    assertTrue(member.hasOrganization())
}

@Test
fun `hasOrganization should return false when organization is null`() {
    val member = Member.createForTenant(
        tenantId,
        "user@example.com",
        "John Doe",
    )

    assertFalse(member.hasOrganization())
}
```

Also update the existing test `constructor should fail with null organizationId` — it should be **replaced** by the `constructor should allow null organizationId` test above. Delete the old test.

**Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "com.worklog.domain.member.MemberTest"`
Expected: FAIL — `createForTenant` and `hasOrganization` don't exist, and null organizationId still throws

**Step 3: Implement Member.java changes**

In `Member.java`:

1. **Line 57** — Remove `Objects.requireNonNull(organizationId, "Organization ID cannot be null");` and replace with `this.organizationId = organizationId; // Nullable: null = not yet assigned to organization`

2. **After `create()` method (after line 82)** — Add:

```java
/**
 * Factory method for creating a new Member without an organization assignment.
 * Used during public registration where tenant assignment happens later.
 */
public static Member createForTenant(TenantId tenantId, String email, String displayName) {
    return new Member(
            MemberId.generate(),
            tenantId,
            null, // No organization assigned yet
            email,
            displayName,
            null, // No manager
            true, // New members are active by default
            Instant.now());
}
```

3. **After `hasManager()` method (after line 144)** — Add:

```java
/**
 * Checks if this member has been assigned to an organization.
 */
public boolean hasOrganization() {
    return organizationId != null;
}
```

**Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.worklog.domain.member.MemberTest"`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add backend/src/main/java/com/worklog/domain/member/Member.java
git add backend/src/test/kotlin/com/worklog/domain/member/MemberTest.kt
git commit -m "feat(domain): make Member.organizationId nullable + add createForTenant/hasOrganization

- Remove Objects.requireNonNull for organizationId in constructor
- Add createForTenant() factory for members without organization
- Add hasOrganization() helper method
- Update tests: allow null organizationId, test new methods

Refs #47"
```

---

### Task 3: JdbcMemberRepository — Null-Safe Persistence

**Files:**
- Modify: `backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberRepository.java:238` (save)
- Modify: `backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberRepository.java:332` (MemberRowMapper)
- Modify: `backend/src/test/java/com/worklog/infrastructure/repository/JdbcMemberRepositoryTest.java` (add tests)

**Step 1: Write the failing integration tests**

Add to `JdbcMemberRepositoryTest.java`:

```java
@Test
@DisplayName("save and findById should handle null organization_id")
void save_nullOrganizationId_roundTrips() {
    // Arrange — create member with null org via raw SQL (bypasses domain validation for test setup)
    UUID memberId = UUID.randomUUID();
    String email = "no-org-" + memberId + "@example.com";
    baseJdbcTemplate.update(
        """INSERT INTO members (id, tenant_id, organization_id, email, display_name, manager_id, is_active, version, created_at, updated_at)
           VALUES (?, '550e8400-e29b-41d4-a716-446655440001'::UUID, NULL, ?, ?, NULL, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""",
        memberId, email, "No Org User " + memberId
    );

    // Act
    var found = memberRepository.findById(MemberId.of(memberId));

    // Assert
    assertTrue(found.isPresent());
    assertNull(found.get().getOrganizationId());
    assertEquals(email, found.get().getEmail());
    assertFalse(found.get().hasOrganization());
}

@Test
@DisplayName("save should persist null organization_id via domain model")
void save_memberWithNullOrg_persists() {
    // Arrange
    var member = com.worklog.domain.member.Member.createForTenant(
        com.worklog.domain.tenant.TenantId.of(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")),
        "tenant-only-" + UUID.randomUUID() + "@example.com",
        "Tenant Only User"
    );

    // Act
    memberRepository.save(member, 0);
    var found = memberRepository.findById(member.getId());

    // Assert
    assertTrue(found.isPresent());
    assertNull(found.get().getOrganizationId());
    assertFalse(found.get().hasOrganization());
    assertEquals(member.getEmail(), found.get().getEmail());
}
```

**Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "com.worklog.infrastructure.repository.JdbcMemberRepositoryTest"`
Expected: FAIL — NullPointerException on `member.getOrganizationId().value()` in save, and `OrganizationId.of(null)` in row mapper

**Step 3: Fix JdbcMemberRepository.java**

1. **Line 238** — Change `member.getOrganizationId().value()` to null-safe:

```java
// Before:
member.getOrganizationId().value(),
// After:
member.getOrganizationId() != null ? member.getOrganizationId().value() : null,
```

2. **Lines 325-332 in MemberRowMapper** — Change to null-safe mapping:

```java
// Before:
UUID managerId = (UUID) rs.getObject("manager_id");
// After (add organizationId extraction):
UUID managerId = (UUID) rs.getObject("manager_id");
UUID orgId = rs.getObject("organization_id", UUID.class);
```

And in the Member constructor call:
```java
// Before:
OrganizationId.of(rs.getObject("organization_id", UUID.class)),
// After:
orgId != null ? OrganizationId.of(orgId) : null,
```

**Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.worklog.infrastructure.repository.JdbcMemberRepositoryTest"`
Expected: ALL PASS

**Step 5: Run all backend tests for regression check**

Run: `cd backend && ./gradlew test`
Expected: ALL PASS

**Step 6: Commit**

```bash
git add backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberRepository.java
git add backend/src/test/java/com/worklog/infrastructure/repository/JdbcMemberRepositoryTest.java
git commit -m "feat(infra): null-safe organization_id handling in JdbcMemberRepository

- save(): ternary null check for organizationId before calling .value()
- MemberRowMapper: null-safe OrganizationId mapping from ResultSet
- Add integration tests for null org_id round-trip (raw SQL + domain model)

Refs #47"
```

---

### Task 4: Format + Lint Check

**Files:** All modified files

**Step 1: Format all backend code**

Run: `cd backend && ./gradlew formatAll`

**Step 2: Check formatting and static analysis**

Run: `cd backend && ./gradlew checkFormat && ./gradlew detekt`
Expected: PASS

**Step 3: Run full backend test suite**

Run: `cd backend && ./gradlew test jacocoTestReport`
Expected: ALL PASS, coverage >= 80% for member and repository packages

**Step 4: Commit if any formatting changes**

```bash
git add -A
git commit -m "style: apply spotless formatting

Refs #47"
```
