# Signup Member Removal & Login Response Extension — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove Member auto-creation from signup and add tenant affiliation state to login response (issue #49).

**Architecture:** Decouple signup from Member lifecycle (users start with no tenant). Login response enriched with `UserStatusService.getUserStatus()` result. Single-tenant users get auto-selected.

**Tech Stack:** Spring Boot 3.5.9, Java 21, Kotlin 2.3.0, MockK, JUnit 5, Testcontainers

---

### Task 1: Create TenantMembershipDto (API-layer DTO)

**Files:**
- Create: `backend/src/main/java/com/worklog/api/TenantMembershipDto.java`

**Step 1: Create the DTO record**

```java
package com.worklog.api;

/**
 * Tenant membership details for login response.
 *
 * @param memberId Member record ID
 * @param tenantId Tenant ID
 * @param tenantName Tenant display name
 * @param organizationId Organization ID (null if not assigned)
 * @param organizationName Organization display name (null if not assigned)
 */
public record TenantMembershipDto(
        String memberId, String tenantId, String tenantName, String organizationId, String organizationName) {}
```

**Step 2: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```
feat: add TenantMembershipDto for login response
```

---

### Task 2: Extend LoginResponseDto

**Files:**
- Modify: `backend/src/main/java/com/worklog/api/LoginResponseDto.java`

**Step 1: Add tenantAffiliationState and memberships fields**

Replace the record to:
```java
package com.worklog.api;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for user login endpoint.
 *
 * @param user User details (id, email, name, accountStatus)
 * @param sessionExpiresAt When the session will expire (ISO 8601 timestamp)
 * @param rememberMeToken Optional remember-me token (if user checked "Remember Me")
 * @param warning Optional warning message (e.g., account expiring soon)
 * @param tenantAffiliationState User's tenant affiliation state (UNAFFILIATED, AFFILIATED_NO_ORG, FULLY_ASSIGNED)
 * @param memberships List of tenant memberships for the user (empty if unaffiliated)
 */
public record LoginResponseDto(
        UserDto user,
        Instant sessionExpiresAt,
        String rememberMeToken,
        String warning,
        String tenantAffiliationState,
        List<TenantMembershipDto> memberships) {}
```

**Step 2: Verify compilation (will fail — AuthController needs update)**

Run: `cd backend && ./gradlew compileJava`
Expected: FAIL — constructor call in AuthController doesn't match new signature. This is expected and fixed in Task 4.

---

### Task 3: Remove Member creation from AuthServiceImpl.signup()

**Files:**
- Modify: `backend/src/main/java/com/worklog/application/auth/AuthServiceImpl.java`
- Modify: `backend/src/main/resources/application.yaml` (lines 95-101)
- Modify: `backend/src/main/resources/application-dev.yaml` (lines 26-28)

**Step 1: Update AuthServiceImpl**

Remove from fields:
- `private final JdbcMemberRepository memberRepository;`
- `private final UUID defaultTenantId;`
- `private final UUID defaultOrganizationId;`

Remove from constructor params:
- `JdbcMemberRepository memberRepository`
- `@Value("${worklog.auth.default-tenant-id}") UUID defaultTenantId`
- `@Value("${worklog.auth.default-organization-id}") UUID defaultOrganizationId`

Remove from constructor body:
- `this.memberRepository = memberRepository;`
- `this.defaultTenantId = defaultTenantId;`
- `this.defaultOrganizationId = defaultOrganizationId;`

Remove from signup() method (lines 105-115):
```java
        // Create corresponding member record so projections work
        Member member = new Member(
                MemberId.of(savedUser.getId().value()),
                TenantId.of(defaultTenantId),
                OrganizationId.of(defaultOrganizationId),
                savedUser.getEmail(),
                savedUser.getName(),
                null,
                true,
                Instant.now());
        memberRepository.save(member);
```

Remove unused imports:
- `com.worklog.domain.member.Member`
- `com.worklog.domain.member.MemberId`
- `com.worklog.domain.organization.OrganizationId`
- `com.worklog.domain.tenant.TenantId`
- `com.worklog.infrastructure.repository.JdbcMemberRepository`

**Step 2: Remove config from application.yaml**

Remove lines 95-101 (default-tenant-id and default-organization-id entries including comments):
```yaml
    # Default tenant and organization for new signups.
    # IMPORTANT: In production, DEFAULT_TENANT_ID and DEFAULT_ORGANIZATION_ID
    # must be set to the UUIDs of existing tenant/organization records
    # (e.g. created via migrations or seed data). No hardcoded defaults are
    # provided here to avoid relying on specific magic UUIDs.
    default-tenant-id: ${DEFAULT_TENANT_ID:}
    default-organization-id: ${DEFAULT_ORGANIZATION_ID:}
```

**Step 3: Remove config from application-dev.yaml**

Remove lines 26-28:
```yaml
    default-tenant-id: 550e8400-e29b-41d4-a716-446655440001
    default-organization-id: 880e8400-e29b-41d4-a716-446655440001
```

---

### Task 4: Update AuthController.login() to use UserStatusService

**Files:**
- Modify: `backend/src/main/java/com/worklog/api/AuthController.java`

**Step 1: Replace UserContextService with UserStatusService**

In imports, add:
```java
import com.worklog.application.service.UserStatusService;
```

Remove import:
```java
import com.worklog.application.service.UserContextService;
```

Replace field and constructor:
```java
    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final UserStatusService userStatusService;

    public AuthController(
            AuthService authService, PasswordResetService passwordResetService, UserStatusService userStatusService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.userStatusService = userStatusService;
    }
```

**Step 2: Update login() method (lines 113-128)**

Replace the memberId resolution and response construction block with:
```java
        // Fetch tenant affiliation status for login response
        UserStatusService.UserStatusResponse userStatus =
                userStatusService.getUserStatus(response.user().getEmail());

        // Map service DTOs to API DTOs
        List<TenantMembershipDto> memberships = userStatus.memberships().stream()
                .map(m -> new TenantMembershipDto(
                        m.memberId(), m.tenantId(), m.tenantName(), m.organizationId(), m.organizationName()))
                .toList();

        // Auto-select tenant if user belongs to exactly one tenant
        if (memberships.size() == 1) {
            UUID tenantId = UUID.fromString(memberships.get(0).tenantId());
            UUID sessionUuid = UUID.fromString(response.sessionId());
            userStatusService.selectTenant(response.user().getEmail(), tenantId, sessionUuid);
        }

        // Derive memberId from first membership (null if no memberships)
        UUID memberId = memberships.isEmpty() ? null : UUID.fromString(memberships.get(0).memberId());

        return new LoginResponseDto(
                new UserDto(
                        response.user().getId().value(),
                        response.user().getEmail(),
                        response.user().getName(),
                        response.user().getAccountStatus().name(),
                        response.user().getPreferredLocale(),
                        memberId),
                sessionExpiresAt,
                response.rememberMeToken(),
                null,
                userStatus.state().name(),
                memberships);
```

**Step 3: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
feat: extend login response with tenant assignment state and memberships
```

---

### Task 5: Update SecurityConfig for /api/v1/user/**

**Files:**
- Modify: `backend/src/main/java/com/worklog/infrastructure/config/SecurityConfig.kt`

**Step 1: Add user endpoints to prod filter chain**

In `prodFilterChain`, after the line:
```kotlin
.requestMatchers("/api/v1/worklog/**", "/api/v1/notifications/**").authenticated()
```

Add:
```kotlin
// User status endpoints require authentication
.requestMatchers("/api/v1/user/**").authenticated()
```

**Step 2: Verify compilation**

Run: `cd backend && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```
feat: add /api/v1/user/** to authenticated routes in production
```

---

### Task 6: Update AuthServiceTest (unit test)

**Files:**
- Modify: `backend/src/test/kotlin/com/worklog/application/auth/AuthServiceTest.kt`

**Step 1: Remove memberRepository mock and update constructor**

Remove field:
```kotlin
private val memberRepository: JdbcMemberRepository = mockk(relaxed = true)
```

Update `setUp()` to remove memberRepository, defaultTenantId, defaultOrganizationId from AuthServiceImpl constructor:
```kotlin
authService =
    AuthServiceImpl(
        userRepository,
        sessionRepository,
        roleRepository,
        auditLogService,
        emailService,
        passwordEncoder,
        tokenStore,
        "USER", // defaultRoleName
    )
```

Remove unused import:
```kotlin
import com.worklog.infrastructure.repository.JdbcMemberRepository
```

**Step 2: Update signup test to verify no Member creation**

In `signup should create user with hashed password`:
- Remove: `verify(exactly = 1) { memberRepository.save(any()) }`
- Add: No memberRepository interaction expected (no field to verify against, so this is implicit)

**Step 3: Run unit tests**

Run: `cd backend && ./gradlew test --tests "com.worklog.application.auth.AuthServiceTest"`
Expected: All tests PASS

**Step 4: Commit**

```
test: update AuthServiceTest to reflect signup without Member creation
```

---

### Task 7: Update AuthControllerTest (WebMvc test)

**Files:**
- Modify: `backend/src/test/kotlin/com/worklog/api/AuthControllerTest.kt`

**Step 1: Replace UserContextService mock with UserStatusService mock**

In `TestConfig`, replace:
```kotlin
@Bean
@Primary
fun userContextService(): UserContextService = mockk(relaxed = true)
```
with:
```kotlin
@Bean
@Primary
fun userStatusService(): UserStatusService {
    val mock = mockk<UserStatusService>(relaxed = true)
    every { mock.getUserStatus(any()) } returns UserStatusService.UserStatusResponse(
        "test-user-id",
        "test@example.com",
        com.worklog.domain.member.TenantAffiliationStatus.UNAFFILIATED,
        emptyList(),
    )
    return mock
}
```

Update imports:
- Remove: `import com.worklog.application.service.UserContextService`
- Add: `import com.worklog.application.service.UserStatusService`
- Add: `import io.mockk.every`

**Step 2: Update login test assertions**

In `login should return 200 with session cookie and user details`, add:
```kotlin
.andExpect(jsonPath("$.tenantAffiliationState").value("UNAFFILIATED"))
.andExpect(jsonPath("$.memberships").isArray)
```

**Step 3: Run tests**

Run: `cd backend && ./gradlew test --tests "com.worklog.api.AuthControllerTest"`
Expected: All tests PASS

**Step 4: Commit**

```
test: update AuthControllerTest for login response with tenant state
```

---

### Task 8: Update AuthControllerSignupTest (integration test)

**Files:**
- Modify: `backend/src/test/kotlin/com/worklog/api/AuthControllerSignupTest.kt`

**Step 1: Remove or update the Member creation test**

The test `signup creates member record with correct tenant and organization` (lines 83-120) must be updated because signup no longer creates Member records.

Replace with a test verifying NO member is created:
```kotlin
@Test
fun `signup should NOT create member record`() {
    // Given
    val request = signupRequest("member-check@signup-test.example.com", "Member Check", "StrongPass1!")

    // When
    val response = restTemplate.postForEntity("/api/v1/auth/signup", request, Map::class.java)

    // Then
    assertEquals(HttpStatus.CREATED, response.statusCode)

    // Verify NO member record was created
    val memberCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM members WHERE email = ?",
        Int::class.java,
        "member-check@signup-test.example.com",
    )
    assertEquals(0, memberCount, "No member record should be created during signup")
}
```

**Step 2: Remove cleanup of members in setUp**

The line `jdbcTemplate.update("DELETE FROM members WHERE email LIKE '%@signup-test.example.com'")` can be kept for safety but is no longer strictly needed.

**Step 3: Run integration tests**

Run: `cd backend && ./gradlew test --tests "com.worklog.api.AuthControllerSignupTest"`
Expected: All tests PASS

**Step 4: Commit**

```
test: update signup integration test to verify no Member creation
```

---

### Task 9: Format and verify all tests pass

**Step 1: Format all code**

Run: `cd backend && ./gradlew formatAll`
Expected: BUILD SUCCESSFUL

**Step 2: Run detekt**

Run: `cd backend && ./gradlew detekt`
Expected: BUILD SUCCESSFUL

**Step 3: Run all backend tests**

Run: `cd backend && ./gradlew test`
Expected: All tests PASS

**Step 4: Run coverage report**

Run: `cd backend && ./gradlew test jacocoTestReport`
Expected: 80%+ line coverage on changed packages

**Step 5: Commit (if format changes)**

```
style: apply formatting
```
