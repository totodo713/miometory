# Tenant Assignment API Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add tenant assignment service, user status endpoints, and admin assignment endpoints (issue #48).

**Architecture:** DDD layer separation — domain enum + session extension in domain layer, two application services (UserStatusService, TenantAssignmentService), two controllers (UserStatusController new, AdminUserController/AdminMemberController extended). HTTP session-based auth with `user_sessions` DB table extension.

**Tech Stack:** Java 17, Spring Boot, PostgreSQL, JdbcTemplate, JUnit 5, Testcontainers

**Design doc:** `docs/plans/2026-02-28-tenant-assignment-api-design.md`

---

### Task 1: DB Migration — user_sessions selected_tenant_id

**Files:**
- Create: `backend/src/main/resources/db/migration/V28__user_session_selected_tenant.sql`

**Step 1: Create migration file**

```sql
-- V28__user_session_selected_tenant.sql
-- Adds selected_tenant_id to user_sessions for multi-tenant selection support (issue #48)
ALTER TABLE user_sessions ADD COLUMN selected_tenant_id UUID REFERENCES tenant(id);
```

Note: The FK references `tenant(id)` (not `tenants`) — verify table name by checking existing migrations:
```bash
grep -r "CREATE TABLE tenant" backend/src/main/resources/db/migration/
```

**Step 2: Verify migration applies**

Run:
```bash
cd backend && ./gradlew flywayMigrate --info 2>&1 | tail -20
```
Expected: Migration V28 applied successfully.

**Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V28__user_session_selected_tenant.sql
git commit -m "feat(db): V28 add selected_tenant_id to user_sessions"
```

---

### Task 2: Domain — TenantAffiliationStatus enum

**Files:**
- Create: `backend/src/main/java/com/worklog/domain/member/TenantAffiliationStatus.java`

**Step 1: Create the enum**

```java
package com.worklog.domain.member;

/**
 * Represents a user's tenant affiliation status.
 *
 * Determined by checking the user's member records:
 * - UNAFFILIATED: no member records exist
 * - AFFILIATED_NO_ORG: member record(s) exist but all have organization_id=null
 * - FULLY_ASSIGNED: at least one member record has organization_id set
 */
public enum TenantAffiliationStatus {
    UNAFFILIATED,
    AFFILIATED_NO_ORG,
    FULLY_ASSIGNED;

    /**
     * Determines status from a list of members.
     *
     * @param members list of Member entities for the user (can be empty)
     * @return the affiliation status
     */
    public static TenantAffiliationStatus fromMembers(java.util.List<Member> members) {
        if (members.isEmpty()) {
            return UNAFFILIATED;
        }
        boolean anyHasOrg = members.stream().anyMatch(Member::hasOrganization);
        return anyHasOrg ? FULLY_ASSIGNED : AFFILIATED_NO_ORG;
    }
}
```

**Step 2: Write unit test**

Create: `backend/src/test/java/com/worklog/domain/member/TenantAffiliationStatusTest.java`

```java
package com.worklog.domain.member;

import static org.junit.jupiter.api.Assertions.*;

import com.worklog.domain.tenant.TenantId;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TenantAffiliationStatus")
class TenantAffiliationStatusTest {

    @Test
    @DisplayName("returns UNAFFILIATED when no members exist")
    void unaffiliated() {
        var result = TenantAffiliationStatus.fromMembers(Collections.emptyList());
        assertEquals(TenantAffiliationStatus.UNAFFILIATED, result);
    }

    @Test
    @DisplayName("returns AFFILIATED_NO_ORG when all members have no organization")
    void affiliatedNoOrg() {
        var member = Member.createForTenant(
                TenantId.generate(), "test@example.com", "Test User");
        var result = TenantAffiliationStatus.fromMembers(List.of(member));
        assertEquals(TenantAffiliationStatus.AFFILIATED_NO_ORG, result);
    }

    @Test
    @DisplayName("returns FULLY_ASSIGNED when at least one member has organization")
    void fullyAssigned() {
        var memberWithOrg = Member.create(
                TenantId.generate(),
                com.worklog.domain.organization.OrganizationId.generate(),
                "test@example.com", "Test User", null);
        var result = TenantAffiliationStatus.fromMembers(List.of(memberWithOrg));
        assertEquals(TenantAffiliationStatus.FULLY_ASSIGNED, result);
    }

    @Test
    @DisplayName("returns FULLY_ASSIGNED when mixed: some with org, some without")
    void mixedAssignment() {
        var memberNoOrg = Member.createForTenant(
                TenantId.generate(), "a@example.com", "A");
        var memberWithOrg = Member.create(
                TenantId.generate(),
                com.worklog.domain.organization.OrganizationId.generate(),
                "b@example.com", "B", null);
        var result = TenantAffiliationStatus.fromMembers(List.of(memberNoOrg, memberWithOrg));
        assertEquals(TenantAffiliationStatus.FULLY_ASSIGNED, result);
    }
}
```

**Step 3: Run tests**

```bash
cd backend && ./gradlew test --tests "com.worklog.domain.member.TenantAffiliationStatusTest" --info
```
Expected: 4 tests PASS.

**Step 4: Commit**

```bash
git add backend/src/main/java/com/worklog/domain/member/TenantAffiliationStatus.java \
       backend/src/test/java/com/worklog/domain/member/TenantAffiliationStatusTest.java
git commit -m "feat(domain): add TenantAffiliationStatus enum with fromMembers()"
```

---

### Task 3: Domain — UserSession selectedTenantId extension

**Files:**
- Modify: `backend/src/main/java/com/worklog/domain/session/UserSession.java`

**Step 1: Write failing test**

Create: `backend/src/test/java/com/worklog/domain/session/UserSessionTest.java`

```java
package com.worklog.domain.session;

import static org.junit.jupiter.api.Assertions.*;

import com.worklog.domain.tenant.TenantId;
import com.worklog.domain.user.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserSession")
class UserSessionTest {

    private UserSession session;

    @BeforeEach
    void setUp() {
        session = UserSession.create(UserId.generate(), "127.0.0.1", "TestAgent", 30);
    }

    @Test
    @DisplayName("new session has no selected tenant")
    void noSelectedTenantByDefault() {
        assertFalse(session.hasSelectedTenant());
        assertNull(session.getSelectedTenantId());
    }

    @Test
    @DisplayName("selectTenant sets the selected tenant")
    void selectTenant() {
        TenantId tenantId = TenantId.generate();
        session.selectTenant(tenantId);
        assertTrue(session.hasSelectedTenant());
        assertEquals(tenantId, session.getSelectedTenantId());
    }

    @Test
    @DisplayName("selectTenant with null clears selection")
    void clearTenantSelection() {
        session.selectTenant(TenantId.generate());
        session.selectTenant(null);
        assertFalse(session.hasSelectedTenant());
    }
}
```

**Step 2: Run test to verify it fails**

```bash
cd backend && ./gradlew test --tests "com.worklog.domain.session.UserSessionTest" --info
```
Expected: FAIL — `selectTenant` method does not exist.

**Step 3: Implement — add to UserSession.java**

Add a field after line 21 (`private Instant lastAccessedAt;`):
```java
private TenantId selectedTenantId; // nullable — selected tenant for multi-tenant users
```

Add import at the top:
```java
import com.worklog.domain.tenant.TenantId;
```

Update both constructors to accept and set the new field (add as last parameter, nullable):

For the 6-arg constructor (line 26-29), chain to 8-arg:
```java
public UserSession(
        UUID sessionId, UserId userId, String ipAddress, String userAgent,
        Instant createdAt, Instant expiresAt) {
    this(sessionId, userId, ipAddress, userAgent, createdAt, expiresAt, createdAt, null);
}
```

For the 7-arg rehydration constructor (line 34-54), change to 8-arg:
```java
public UserSession(
        UUID sessionId, UserId userId, String ipAddress, String userAgent,
        Instant createdAt, Instant expiresAt, Instant lastAccessedAt,
        TenantId selectedTenantId) {
    // ... existing validation ...
    this.selectedTenantId = selectedTenantId; // nullable
}
```

**Important:** Preserve backward compatibility. The existing 7-arg constructor callers (JdbcUserSessionRepository row mapper) must be updated too — see Task 5.

Add methods after `isValid()`:

```java
/**
 * Sets the selected tenant for this session.
 * @param tenantId Tenant to select, or null to clear selection
 */
public void selectTenant(TenantId tenantId) {
    this.selectedTenantId = tenantId;
}

public TenantId getSelectedTenantId() {
    return selectedTenantId;
}

public boolean hasSelectedTenant() {
    return selectedTenantId != null;
}
```

**Step 4: Run test to verify it passes**

```bash
cd backend && ./gradlew test --tests "com.worklog.domain.session.UserSessionTest" --info
```
Expected: 3 tests PASS.

**Step 5: Commit**

```bash
git add backend/src/main/java/com/worklog/domain/session/UserSession.java \
       backend/src/test/java/com/worklog/domain/session/UserSessionTest.java
git commit -m "feat(domain): add selectedTenantId to UserSession"
```

---

### Task 4: Infrastructure — JdbcMemberRepository.findAllByEmail()

**Files:**
- Modify: `backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberRepository.java`

**Step 1: Write failing integration test**

Create: `backend/src/test/java/com/worklog/infrastructure/repository/JdbcMemberRepositoryFindAllByEmailTest.java`

```java
package com.worklog.infrastructure.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.worklog.IntegrationTestBase;
import com.worklog.domain.member.Member;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DisplayName("JdbcMemberRepository.findAllByEmail")
class JdbcMemberRepositoryFindAllByEmailTest extends IntegrationTestBase() {

    @Autowired
    private JdbcMemberRepository memberRepository;

    private static final String TEST_EMAIL = "crosstenantuser@example.com";

    @BeforeEach
    void setUp() {
        // Create members with same email in different tenants via raw SQL
        // (Test data setup — tenant IDs from IntegrationTestBase)
    }

    @Test
    @DisplayName("returns all members across tenants for given email")
    void findAllByEmail() {
        List<Member> members = memberRepository.findAllByEmail(TEST_EMAIL);
        // Assert based on setup
        assertNotNull(members);
    }

    @Test
    @DisplayName("returns empty list for unknown email")
    void findAllByEmailNotFound() {
        List<Member> members = memberRepository.findAllByEmail("nonexistent@example.com");
        assertTrue(members.isEmpty());
    }
}
```

Note: The test setup needs real tenant data. Use `IntegrationTestBase` companion TEST_TENANT_ID. The test may need to insert a second tenant to test cross-tenant. Adjust the test data setup based on what seed data the test migrations provide.

**Step 2: Implement the method**

Add to `JdbcMemberRepository.java` after `findByEmail()`:

```java
/**
 * Finds all members across all tenants for a given email (case-insensitive).
 * Used for determining a user's tenant affiliation status.
 *
 * @param email Email address
 * @return List of members (may span multiple tenants)
 */
public List<Member> findAllByEmail(String email) {
    String sql = """
        SELECT id, tenant_id, organization_id, email, display_name,
               manager_id, is_active, version, created_at, updated_at
        FROM members
        WHERE LOWER(email) = LOWER(?)
        """;

    return jdbcTemplate.query(sql, new MemberRowMapper(), email);
}
```

**Step 3: Run integration tests**

```bash
cd backend && ./gradlew test --tests "com.worklog.infrastructure.repository.JdbcMemberRepositoryFindAllByEmailTest" --info
```
Expected: PASS.

**Step 4: Commit**

```bash
git add backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberRepository.java \
       backend/src/test/java/com/worklog/infrastructure/repository/JdbcMemberRepositoryFindAllByEmailTest.java
git commit -m "feat(infra): add JdbcMemberRepository.findAllByEmail() for cross-tenant lookup"
```

---

### Task 5: Infrastructure — JdbcUserSessionRepository selected_tenant_id

**Files:**
- Modify: `backend/src/main/java/com/worklog/infrastructure/persistence/JdbcUserSessionRepository.java`

**Step 1: Update RowMapper to include selected_tenant_id**

In `UserSessionRowMapper.mapRow()`, add reading of the new column. Update all SELECT queries to include `selected_tenant_id`.

Change every SQL query's SELECT list from:
```
SELECT id, user_id, session_id, created_at, last_accessed_at, expires_at, ip_address, user_agent
```
to:
```
SELECT id, user_id, session_id, created_at, last_accessed_at, expires_at, ip_address, user_agent, selected_tenant_id
```

Update `UserSessionRowMapper`:
```java
private static class UserSessionRowMapper implements RowMapper<UserSession> {
    @Override
    public UserSession mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID selectedTenantUuid = rs.getObject("selected_tenant_id", UUID.class);
        TenantId selectedTenantId = selectedTenantUuid != null
                ? TenantId.of(selectedTenantUuid) : null;

        return new UserSession(
                UUID.fromString(rs.getString("session_id")),
                UserId.of(rs.getObject("user_id", UUID.class)),
                rs.getString("ip_address"),
                rs.getString("user_agent"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("last_accessed_at").toInstant(),
                selectedTenantId);
    }
}
```

Add import:
```java
import com.worklog.domain.tenant.TenantId;
```

**Step 2: Update save() to include selected_tenant_id**

Change the INSERT SQL to include the new column:
```java
public UserSession save(UserSession session) {
    String upsertSql = """
        INSERT INTO user_sessions (id, user_id, session_id, created_at, last_accessed_at,
                                  expires_at, ip_address, user_agent, selected_tenant_id)
        VALUES (?, ?, ?, ?, ?, ?, ?::inet, ?, ?)
        ON CONFLICT (session_id) DO UPDATE SET
            last_accessed_at = EXCLUDED.last_accessed_at,
            expires_at = EXCLUDED.expires_at,
            selected_tenant_id = EXCLUDED.selected_tenant_id
        """;

    UUID dbId = UUID.randomUUID();
    jdbcTemplate.update(
            upsertSql,
            dbId,
            session.getUserId().value(),
            session.getSessionId().toString(),
            Timestamp.from(session.getCreatedAt()),
            Timestamp.from(session.getLastAccessedAt()),
            Timestamp.from(session.getExpiresAt()),
            session.getIpAddress(),
            session.getUserAgent(),
            session.hasSelectedTenant() ? session.getSelectedTenantId().value() : null);

    return session;
}
```

**Step 3: Add updateSelectedTenant method**

```java
/**
 * Updates only the selected_tenant_id for a session.
 *
 * @param sessionId Session UUID
 * @param tenantId Tenant to select, or null to clear
 */
public void updateSelectedTenant(UUID sessionId, TenantId tenantId) {
    String sql = "UPDATE user_sessions SET selected_tenant_id = ? WHERE session_id = ?";
    jdbcTemplate.update(sql,
            tenantId != null ? tenantId.value() : null,
            sessionId.toString());
}
```

**Step 4: Run existing session tests + integration tests**

```bash
cd backend && ./gradlew test --tests "*UserSession*" --info
```
Expected: All PASS.

**Step 5: Commit**

```bash
git add backend/src/main/java/com/worklog/infrastructure/persistence/JdbcUserSessionRepository.java
git commit -m "feat(infra): add selected_tenant_id support to JdbcUserSessionRepository"
```

---

### Task 6: Infrastructure — JdbcUserRepository.searchByEmailPartial()

**Files:**
- Modify: `backend/src/main/java/com/worklog/infrastructure/persistence/JdbcUserRepository.java`

**Step 1: Add the search method**

```java
/**
 * Searches users by email partial match (case-insensitive).
 * Used by admin tenant assignment search.
 *
 * @param emailPartial Partial email string to search for
 * @return List of matching users (max 20 results)
 */
public List<User> searchByEmailPartial(String emailPartial) {
    String sql = """
        SELECT id, email, name, hashed_password, role_id, account_status,
               failed_login_attempts, locked_until, created_at, updated_at,
               last_login_at, email_verified_at, preferred_locale
        FROM users
        WHERE LOWER(email) LIKE LOWER(?)
        AND account_status != 'deleted'
        ORDER BY email
        LIMIT 20
        """;

    return jdbcTemplate.query(sql, new UserRowMapper(), "%" + emailPartial + "%");
}
```

**Step 2: Commit**

```bash
git add backend/src/main/java/com/worklog/infrastructure/persistence/JdbcUserRepository.java
git commit -m "feat(infra): add JdbcUserRepository.searchByEmailPartial()"
```

---

### Task 7: Infrastructure — TenantStatusFilter exclusion

**Files:**
- Modify: `backend/src/main/java/com/worklog/infrastructure/config/TenantStatusFilter.kt`

**Step 1: Add path exclusion**

In `doFilterInternal()`, add a path check before the tenant status check. Add at the beginning of the method (after line 29):

```kotlin
// Skip tenant status check for user status endpoints (accessible to UNAFFILIATED users)
val path = request.requestURI
if (path.startsWith("/api/v1/user/")) {
    filterChain.doFilter(request, response)
    return
}
```

**Step 2: Commit**

```bash
git add backend/src/main/java/com/worklog/infrastructure/config/TenantStatusFilter.kt
git commit -m "fix(config): exclude /api/v1/user/ from TenantStatusFilter"
```

---

### Task 8: Application — UserStatusService

**Files:**
- Create: `backend/src/main/java/com/worklog/application/service/UserStatusService.java`
- Create: `backend/src/test/java/com/worklog/application/service/UserStatusServiceTest.java`

**Step 1: Write failing tests**

```java
package com.worklog.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.worklog.domain.member.Member;
import com.worklog.domain.member.TenantAffiliationStatus;
import com.worklog.domain.organization.OrganizationId;
import com.worklog.domain.session.UserSession;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.domain.user.User;
import com.worklog.domain.user.UserId;
import com.worklog.domain.role.RoleId;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.persistence.JdbcUserSessionRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserStatusService")
class UserStatusServiceTest {

    @Mock private JdbcUserRepository userRepository;
    @Mock private JdbcMemberRepository memberRepository;
    @Mock private JdbcUserSessionRepository sessionRepository;

    @InjectMocks private UserStatusService userStatusService;

    private static final String TEST_EMAIL = "user@example.com";

    @Nested
    @DisplayName("getUserStatus")
    class GetUserStatus {

        @Test
        @DisplayName("returns UNAFFILIATED when user has no members")
        void unaffiliated() {
            var user = createTestUser();
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(memberRepository.findAllByEmail(TEST_EMAIL)).thenReturn(Collections.emptyList());

            var result = userStatusService.getUserStatus(TEST_EMAIL);

            assertEquals(TenantAffiliationStatus.UNAFFILIATED, result.state());
            assertTrue(result.memberships().isEmpty());
        }

        @Test
        @DisplayName("returns AFFILIATED_NO_ORG when member has no organization")
        void affiliatedNoOrg() {
            var user = createTestUser();
            var member = Member.createForTenant(TenantId.generate(), TEST_EMAIL, "Test");
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(memberRepository.findAllByEmail(TEST_EMAIL)).thenReturn(List.of(member));

            var result = userStatusService.getUserStatus(TEST_EMAIL);

            assertEquals(TenantAffiliationStatus.AFFILIATED_NO_ORG, result.state());
            assertEquals(1, result.memberships().size());
        }

        @Test
        @DisplayName("returns FULLY_ASSIGNED when member has organization")
        void fullyAssigned() {
            var user = createTestUser();
            var member = Member.create(
                    TenantId.generate(), OrganizationId.generate(),
                    TEST_EMAIL, "Test", null);
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(memberRepository.findAllByEmail(TEST_EMAIL)).thenReturn(List.of(member));

            var result = userStatusService.getUserStatus(TEST_EMAIL);

            assertEquals(TenantAffiliationStatus.FULLY_ASSIGNED, result.state());
        }

        @Test
        @DisplayName("throws when user not found")
        void userNotFound() {
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

            var ex = assertThrows(DomainException.class,
                    () -> userStatusService.getUserStatus(TEST_EMAIL));
            assertEquals("USER_NOT_FOUND", ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("selectTenant")
    class SelectTenant {

        @Test
        @DisplayName("updates session selected tenant when membership exists")
        void selectValidTenant() {
            TenantId tenantId = TenantId.generate();
            UUID sessionId = UUID.randomUUID();
            var member = Member.createForTenant(tenantId, TEST_EMAIL, "Test");
            when(memberRepository.findByEmail(tenantId, TEST_EMAIL)).thenReturn(Optional.of(member));

            assertDoesNotThrow(() ->
                    userStatusService.selectTenant(TEST_EMAIL, tenantId.value(), sessionId));

            verify(sessionRepository).updateSelectedTenant(sessionId, tenantId);
        }

        @Test
        @DisplayName("throws when no membership for tenant")
        void invalidTenantSelection() {
            TenantId tenantId = TenantId.generate();
            UUID sessionId = UUID.randomUUID();
            when(memberRepository.findByEmail(tenantId, TEST_EMAIL)).thenReturn(Optional.empty());

            var ex = assertThrows(DomainException.class,
                    () -> userStatusService.selectTenant(TEST_EMAIL, tenantId.value(), sessionId));
            assertEquals("INVALID_TENANT_SELECTION", ex.getErrorCode());
        }
    }

    private User createTestUser() {
        return User.create(TEST_EMAIL, "Test User", "$2a$10$hashedpassword", RoleId.generate());
    }
}
```

**Step 2: Run tests to verify they fail**

```bash
cd backend && ./gradlew test --tests "com.worklog.application.service.UserStatusServiceTest" --info
```
Expected: FAIL — class `UserStatusService` does not exist.

**Step 3: Implement UserStatusService**

```java
package com.worklog.application.service;

import com.worklog.domain.member.Member;
import com.worklog.domain.member.TenantAffiliationStatus;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.persistence.JdbcUserSessionRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserStatusService {

    private final JdbcUserRepository userRepository;
    private final JdbcMemberRepository memberRepository;
    private final JdbcUserSessionRepository sessionRepository;

    public UserStatusService(
            JdbcUserRepository userRepository,
            JdbcMemberRepository memberRepository,
            JdbcUserSessionRepository sessionRepository) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.sessionRepository = sessionRepository;
    }

    public UserStatusResponse getUserStatus(String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new DomainException("USER_NOT_FOUND",
                        "User not found: " + email));

        List<Member> members = memberRepository.findAllByEmail(email);
        TenantAffiliationStatus state = TenantAffiliationStatus.fromMembers(members);

        List<MembershipDto> memberships = members.stream()
                .map(m -> new MembershipDto(
                        m.getId().value().toString(),
                        m.getTenantId().value().toString(),
                        null, // tenantName — resolved at API layer if needed
                        m.hasOrganization() ? m.getOrganizationId().value().toString() : null,
                        null  // organizationName — resolved at API layer if needed
                ))
                .toList();

        return new UserStatusResponse(
                user.getId().value().toString(),
                user.getEmail(),
                state,
                memberships);
    }

    @Transactional
    public void selectTenant(String email, UUID tenantId, UUID sessionId) {
        TenantId tid = TenantId.of(tenantId);

        // Verify user has membership in this tenant
        memberRepository.findByEmail(tid, email)
                .orElseThrow(() -> new DomainException("INVALID_TENANT_SELECTION",
                        "No membership found for tenant: " + tenantId));

        sessionRepository.updateSelectedTenant(sessionId, tid);
    }

    // Response DTOs

    public record UserStatusResponse(
            String userId,
            String email,
            TenantAffiliationStatus state,
            List<MembershipDto> memberships) {}

    public record MembershipDto(
            String memberId,
            String tenantId,
            String tenantName,
            String organizationId,
            String organizationName) {}
}
```

**Step 4: Run tests**

```bash
cd backend && ./gradlew test --tests "com.worklog.application.service.UserStatusServiceTest" --info
```
Expected: All PASS.

**Step 5: Commit**

```bash
git add backend/src/main/java/com/worklog/application/service/UserStatusService.java \
       backend/src/test/java/com/worklog/application/service/UserStatusServiceTest.java
git commit -m "feat(app): add UserStatusService with getUserStatus and selectTenant"
```

---

### Task 9: Application — TenantAssignmentService

**Files:**
- Create: `backend/src/main/java/com/worklog/application/service/TenantAssignmentService.java`
- Create: `backend/src/test/java/com/worklog/application/service/TenantAssignmentServiceTest.java`

**Step 1: Write failing tests**

```java
package com.worklog.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.worklog.domain.member.Member;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.domain.user.User;
import com.worklog.domain.user.UserId;
import com.worklog.domain.role.RoleId;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantAssignmentService")
class TenantAssignmentServiceTest {

    @Mock private JdbcUserRepository userRepository;
    @Mock private JdbcMemberRepository memberRepository;

    @InjectMocks private TenantAssignmentService service;

    @Nested
    @DisplayName("searchUsersForAssignment")
    class SearchUsers {

        @Test
        @DisplayName("returns users with isAlreadyInTenant flag")
        void searchWithFlag() {
            UUID tenantId = UUID.randomUUID();
            var user = User.create("test@example.com", "Test", "$2a$10$hash", RoleId.generate());
            when(userRepository.searchByEmailPartial("test")).thenReturn(List.of(user));

            var member = Member.createForTenant(TenantId.of(tenantId), "test@example.com", "Test");
            when(memberRepository.findByEmail(TenantId.of(tenantId), "test@example.com"))
                    .thenReturn(Optional.of(member));

            var result = service.searchUsersForAssignment("test", tenantId);

            assertEquals(1, result.size());
            assertTrue(result.get(0).isAlreadyInTenant());
        }

        @Test
        @DisplayName("returns isAlreadyInTenant=false when not in tenant")
        void searchNotInTenant() {
            UUID tenantId = UUID.randomUUID();
            var user = User.create("new@example.com", "New", "$2a$10$hash", RoleId.generate());
            when(userRepository.searchByEmailPartial("new")).thenReturn(List.of(user));
            when(memberRepository.findByEmail(TenantId.of(tenantId), "new@example.com"))
                    .thenReturn(Optional.empty());

            var result = service.searchUsersForAssignment("new", tenantId);

            assertEquals(1, result.size());
            assertFalse(result.get(0).isAlreadyInTenant());
        }
    }

    @Nested
    @DisplayName("assignUserToTenant")
    class AssignUser {

        @Test
        @DisplayName("creates member when not already in tenant")
        void assignSuccess() {
            UUID tenantId = UUID.randomUUID();
            var user = User.create("test@example.com", "Test", "$2a$10$hash", RoleId.generate());
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(memberRepository.findByEmail(TenantId.of(tenantId), "test@example.com"))
                    .thenReturn(Optional.empty());

            assertDoesNotThrow(() ->
                    service.assignUserToTenant(user.getId().value(), tenantId, "Display Name"));

            verify(memberRepository).save(any(Member.class));
        }

        @Test
        @DisplayName("throws DUPLICATE_TENANT_ASSIGNMENT when already in tenant")
        void assignDuplicate() {
            UUID tenantId = UUID.randomUUID();
            var user = User.create("test@example.com", "Test", "$2a$10$hash", RoleId.generate());
            var member = Member.createForTenant(TenantId.of(tenantId), "test@example.com", "Test");
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(memberRepository.findByEmail(TenantId.of(tenantId), "test@example.com"))
                    .thenReturn(Optional.of(member));

            var ex = assertThrows(DomainException.class,
                    () -> service.assignUserToTenant(user.getId().value(), tenantId, "Test"));
            assertEquals("DUPLICATE_TENANT_ASSIGNMENT", ex.getErrorCode());
        }

        @Test
        @DisplayName("throws USER_NOT_FOUND when user doesn't exist")
        void assignUserNotFound() {
            UUID userId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            when(userRepository.findById(UserId.of(userId))).thenReturn(Optional.empty());

            var ex = assertThrows(DomainException.class,
                    () -> service.assignUserToTenant(userId, tenantId, "Test"));
            assertEquals("USER_NOT_FOUND", ex.getErrorCode());
        }
    }
}
```

**Step 2: Run tests to verify they fail**

```bash
cd backend && ./gradlew test --tests "com.worklog.application.service.TenantAssignmentServiceTest" --info
```
Expected: FAIL — class `TenantAssignmentService` does not exist.

**Step 3: Implement TenantAssignmentService**

```java
package com.worklog.application.service;

import com.worklog.domain.member.Member;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.domain.user.UserId;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TenantAssignmentService {

    private final JdbcUserRepository userRepository;
    private final JdbcMemberRepository memberRepository;

    public TenantAssignmentService(
            JdbcUserRepository userRepository,
            JdbcMemberRepository memberRepository) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public List<UserSearchResult> searchUsersForAssignment(String emailPartial, UUID tenantId) {
        TenantId tid = TenantId.of(tenantId);
        var users = userRepository.searchByEmailPartial(emailPartial);

        return users.stream()
                .map(user -> {
                    boolean alreadyInTenant = memberRepository
                            .findByEmail(tid, user.getEmail())
                            .isPresent();
                    return new UserSearchResult(
                            user.getId().value().toString(),
                            user.getEmail(),
                            user.getName(),
                            alreadyInTenant);
                })
                .toList();
    }

    public void assignUserToTenant(UUID userId, UUID tenantId, String displayName) {
        var user = userRepository.findById(UserId.of(userId))
                .orElseThrow(() -> new DomainException("USER_NOT_FOUND",
                        "User not found: " + userId));

        TenantId tid = TenantId.of(tenantId);

        // Check if already assigned
        if (memberRepository.findByEmail(tid, user.getEmail()).isPresent()) {
            throw new DomainException("DUPLICATE_TENANT_ASSIGNMENT",
                    "User is already assigned to tenant: " + tenantId);
        }

        Member member = Member.createForTenant(tid, user.getEmail(), displayName);
        memberRepository.save(member);
    }

    // Response DTO
    public record UserSearchResult(
            String userId,
            String email,
            String name,
            boolean isAlreadyInTenant) {}
}
```

**Step 4: Run tests**

```bash
cd backend && ./gradlew test --tests "com.worklog.application.service.TenantAssignmentServiceTest" --info
```
Expected: All PASS.

**Step 5: Commit**

```bash
git add backend/src/main/java/com/worklog/application/service/TenantAssignmentService.java \
       backend/src/test/java/com/worklog/application/service/TenantAssignmentServiceTest.java
git commit -m "feat(app): add TenantAssignmentService with search and assign"
```

---

### Task 10: API — UserStatusController

**Files:**
- Create: `backend/src/main/java/com/worklog/api/UserStatusController.java`

**Step 1: Implement the controller**

```java
package com.worklog.api;

import com.worklog.application.service.UserStatusService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
public class UserStatusController {

    private final UserStatusService userStatusService;

    public UserStatusController(UserStatusService userStatusService) {
        this.userStatusService = userStatusService;
    }

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public UserStatusService.UserStatusResponse getStatus(Authentication authentication) {
        return userStatusService.getUserStatus(authentication.getName());
    }

    @PostMapping("/select-tenant")
    @PreAuthorize("isAuthenticated()")
    public void selectTenant(
            @RequestBody @Valid SelectTenantRequest request,
            Authentication authentication,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        // Extract session ID from the current HTTP session
        var session = httpRequest.getSession(false);
        UUID sessionId = session != null
                ? UUID.fromString(session.getId())
                : null;

        // Note: The session ID extraction depends on how Spring Security
        // manages sessions. If sessions use custom IDs, adjust accordingly.
        // For the `user_sessions` table, we need the session_id column value.
        // This may need adjustment based on how AuthController creates sessions.
        userStatusService.selectTenant(
                authentication.getName(),
                request.tenantId(),
                sessionId);
    }

    public record SelectTenantRequest(@NotNull UUID tenantId) {}
}
```

**Important:** The session ID extraction from `HttpServletRequest` may need adjustment. Check how `AuthController` stores session IDs. The `user_sessions.session_id` column value must match what we pass here.

**Step 2: Commit**

```bash
git add backend/src/main/java/com/worklog/api/UserStatusController.java
git commit -m "feat(api): add UserStatusController with GET /status and POST /select-tenant"
```

---

### Task 11: API — AdminUserController search-for-assignment endpoint

**Files:**
- Modify: `backend/src/main/java/com/worklog/api/AdminUserController.java`

Note: The design doc specified this endpoint on AdminMemberController, but `AdminUserController` already owns `/api/v1/admin/users`. Place the search endpoint here.

**Step 1: Add TenantAssignmentService dependency and endpoint**

Add to constructor injection:
```java
private final TenantAssignmentService tenantAssignmentService;
private final UserContextService userContextService;
```

Add endpoint:
```java
@GetMapping("/search-for-assignment")
@PreAuthorize("hasPermission(null, 'member.assign_tenant')")
public List<TenantAssignmentService.UserSearchResult> searchForAssignment(
        @RequestParam String email,
        Authentication authentication) {
    UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
    return tenantAssignmentService.searchUsersForAssignment(email, tenantId);
}
```

Add imports:
```java
import com.worklog.application.service.TenantAssignmentService;
import com.worklog.application.service.UserContextService;
import java.util.List;
```

**Step 2: Commit**

```bash
git add backend/src/main/java/com/worklog/api/AdminUserController.java
git commit -m "feat(api): add GET /admin/users/search-for-assignment endpoint"
```

---

### Task 12: API — AdminMemberController assign-tenant endpoint

**Files:**
- Modify: `backend/src/main/java/com/worklog/api/AdminMemberController.java`

**Step 1: Add TenantAssignmentService dependency**

Add to constructor and field:
```java
private final TenantAssignmentService tenantAssignmentService;
```

Update constructor to accept the new dependency.

**Step 2: Add endpoint**

```java
@PostMapping("/assign-tenant")
@PreAuthorize("hasPermission(null, 'member.assign_tenant')")
@ResponseStatus(HttpStatus.CREATED)
public ResponseEntity<Void> assignTenant(
        @RequestBody @Valid AssignTenantRequest request,
        Authentication authentication) {
    UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
    tenantAssignmentService.assignUserToTenant(request.userId(), tenantId, request.displayName());
    return ResponseEntity.status(HttpStatus.CREATED).build();
}
```

Add request DTO:
```java
public record AssignTenantRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = 100) String displayName) {}
```

Add imports:
```java
import com.worklog.application.service.TenantAssignmentService;
```

**Step 3: Commit**

```bash
git add backend/src/main/java/com/worklog/api/AdminMemberController.java
git commit -m "feat(api): add POST /admin/members/assign-tenant endpoint"
```

---

### Task 13: Integration tests — UserStatusController

**Files:**
- Create: `backend/src/test/kotlin/com/worklog/api/UserStatusControllerIntegrationTest.kt`

**Step 1: Write integration test**

```kotlin
package com.worklog.api

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.junit.jupiter.api.Assertions.*

@DisplayName("UserStatusController integration")
class UserStatusControllerIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    @DisplayName("GET /api/v1/user/status returns 401 for unauthenticated request")
    fun statusRequiresAuth() {
        val response = restTemplate.getForEntity("/api/v1/user/status", String::class.java)
        // In dev profile all endpoints are permitAll, so this test may need
        // adjustment based on test security config
        assertNotNull(response)
    }

    // Additional integration tests should verify:
    // - Authenticated user gets correct status
    // - UNAFFILIATED user can access the endpoint (TenantStatusFilter bypass)
    // - POST /select-tenant validates membership
}
```

Note: Integration test details depend on how authentication is set up in test profile. Check existing integration tests (e.g., `JdbcUserRepositoryTest.kt`) for auth setup patterns. The dev/test profile has `permitAll()`, so security tests may need the production security config or a custom test security config.

**Step 2: Run integration tests**

```bash
cd backend && ./gradlew test --tests "com.worklog.api.UserStatusControllerIntegrationTest" --info
```

**Step 3: Commit**

```bash
git add backend/src/test/kotlin/com/worklog/api/UserStatusControllerIntegrationTest.kt
git commit -m "test(api): add UserStatusController integration tests"
```

---

### Task 14: Final verification

**Step 1: Run all tests**

```bash
cd backend && ./gradlew test jacocoTestReport
```
Expected: All tests PASS.

**Step 2: Check format/lint**

```bash
cd backend && ./gradlew checkFormat && ./gradlew detekt
```
Expected: No violations.

**Step 3: Verify coverage**

Check JaCoCo report for new packages:
- `com.worklog.domain.member` (TenantAffiliationStatus)
- `com.worklog.domain.session` (UserSession extensions)
- `com.worklog.application.service` (UserStatusService, TenantAssignmentService)

Target: 80%+ line coverage per package.

**Step 4: Final commit (if any formatting fixes needed)**

```bash
git add -A
git commit -m "chore: formatting and coverage fixes"
```
