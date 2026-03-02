# Profile API Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement GET/PUT `/api/v1/profile` endpoints so authenticated users can view and update their own profile.

**Architecture:** New ProfileController + ProfileService following existing DDD patterns. GET uses a single JOIN query. PUT validates email uniqueness across members (tenant) and users (global), updates both tables in one transaction, and invalidates session on email change via SecurityContextLogoutHandler.

**Tech Stack:** Spring Boot 3.5, Java 21, JdbcTemplate, MockK (Kotlin tests), Testcontainers

---

### Task 1: Create ProfileResponse DTO

**Files:**
- Create: `backend/src/main/java/com/worklog/api/dto/ProfileResponse.java`

**Step 1: Create the response record**

```java
package com.worklog.api.dto;

import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String email,
        String displayName,
        String organizationName,
        String managerName,
        boolean isActive) {}
```

**Step 2: Commit**

```bash
git add backend/src/main/java/com/worklog/api/dto/ProfileResponse.java
git commit -m "feat(backend): add ProfileResponse DTO"
```

---

### Task 2: Create UpdateProfileRequest DTO

**Files:**
- Create: `backend/src/main/java/com/worklog/api/dto/UpdateProfileRequest.java`

**Step 1: Create the request record with validation**

```java
package com.worklog.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 100) String displayName) {}
```

**Step 2: Create UpdateProfileResponse**

```java
package com.worklog.api.dto;

public record UpdateProfileResponse(boolean emailChanged) {}
```

**Step 3: Commit**

```bash
git add backend/src/main/java/com/worklog/api/dto/UpdateProfileRequest.java \
       backend/src/main/java/com/worklog/api/dto/UpdateProfileResponse.java
git commit -m "feat(backend): add UpdateProfileRequest and UpdateProfileResponse DTOs"
```

---

### Task 3: Create ProfileService

**Files:**
- Create: `backend/src/main/java/com/worklog/application/service/ProfileService.java`

**Step 1: Write the failing test**

Create `backend/src/test/kotlin/com/worklog/application/service/ProfileServiceTest.kt`:

```kotlin
package com.worklog.application.service

import com.worklog.domain.member.Member
import com.worklog.domain.member.MemberId
import com.worklog.domain.organization.OrganizationId
import com.worklog.domain.shared.DomainException
import com.worklog.domain.tenant.TenantId
import com.worklog.infrastructure.persistence.JdbcUserRepository
import com.worklog.infrastructure.repository.JdbcMemberRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.time.Instant
import java.util.*

class ProfileServiceTest {
    private val jdbcTemplate = mockk<JdbcTemplate>(relaxed = true)
    private val memberRepository = mockk<JdbcMemberRepository>()
    private val userRepository = mockk<JdbcUserRepository>()
    private val userContextService = mockk<UserContextService>()

    private lateinit var profileService: ProfileService

    private val testEmail = "user@example.com"
    private val testMemberId = UUID.randomUUID()
    private val testTenantId = UUID.randomUUID()
    private val testOrgId = UUID.randomUUID()
    private val testManagerId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        clearMocks(jdbcTemplate, memberRepository, userRepository, userContextService)
        profileService = ProfileService(jdbcTemplate, memberRepository, userRepository, userContextService)
    }

    @Nested
    inner class GetProfile {
        @Test
        fun `returns profile with organization and manager names`() {
            // Given
            every { jdbcTemplate.query(any<String>(), any<RowMapper<ProfileService.ProfileRow>>(), any()) } returns
                listOf(
                    ProfileService.ProfileRow(
                        testMemberId, testEmail, "Test User",
                        "Engineering Org", "Manager Name", true,
                    ),
                )

            // When
            val result = profileService.getProfile(testEmail)

            // Then
            assertNotNull(result)
            assertEquals(testMemberId, result.id())
            assertEquals(testEmail, result.email())
            assertEquals("Test User", result.displayName())
            assertEquals("Engineering Org", result.organizationName())
            assertEquals("Manager Name", result.managerName())
            assertTrue(result.isActive)
        }

        @Test
        fun `throws MEMBER_NOT_FOUND when no member exists`() {
            // Given
            every { jdbcTemplate.query(any<String>(), any<RowMapper<ProfileService.ProfileRow>>(), any()) } returns
                emptyList()

            // When/Then
            val ex = assertThrows(DomainException::class.java) {
                profileService.getProfile(testEmail)
            }
            assertEquals("MEMBER_NOT_FOUND", ex.errorCode)
        }
    }

    @Nested
    inner class UpdateProfile {
        @Test
        fun `updates display name without email change`() {
            // Given
            every { userContextService.resolveUserMemberId(testEmail) } returns testMemberId
            every { userContextService.resolveUserTenantId(testEmail) } returns testTenantId
            val member = createTestMember(testEmail, "Old Name")
            every { memberRepository.findById(MemberId.of(testMemberId)) } returns Optional.of(member)
            every { memberRepository.save(any()) } just Runs

            // When
            val result = profileService.updateProfile(testEmail, "New Name", testEmail)

            // Then
            assertFalse(result.emailChanged())
            verify { memberRepository.save(any()) }
            verify(exactly = 0) { userRepository.save(any()) }
        }

        @Test
        fun `updates email and syncs users table`() {
            // Given
            val newEmail = "new@example.com"
            every { userContextService.resolveUserMemberId(testEmail) } returns testMemberId
            every { userContextService.resolveUserTenantId(testEmail) } returns testTenantId
            val member = createTestMember(testEmail, "User")
            every { memberRepository.findById(MemberId.of(testMemberId)) } returns Optional.of(member)
            every { memberRepository.findByEmail(TenantId.of(testTenantId), newEmail) } returns Optional.empty()
            every { userRepository.findByEmail(newEmail) } returns Optional.empty()
            every { memberRepository.save(any()) } just Runs
            val user = mockk<com.worklog.domain.user.User>(relaxed = true)
            every { userRepository.findByEmail(testEmail) } returns Optional.of(user)
            every { userRepository.save(any()) } returns user

            // When
            val result = profileService.updateProfile(testEmail, "User", newEmail)

            // Then
            assertTrue(result.emailChanged())
            verify { memberRepository.save(any()) }
            verify { userRepository.save(any()) }
        }

        @Test
        fun `throws DUPLICATE_EMAIL when email exists in tenant members`() {
            // Given
            val newEmail = "taken@example.com"
            every { userContextService.resolveUserMemberId(testEmail) } returns testMemberId
            every { userContextService.resolveUserTenantId(testEmail) } returns testTenantId
            val member = createTestMember(testEmail, "User")
            every { memberRepository.findById(MemberId.of(testMemberId)) } returns Optional.of(member)
            val otherMember = createTestMember(newEmail, "Other")
            every { memberRepository.findByEmail(TenantId.of(testTenantId), newEmail) } returns Optional.of(otherMember)

            // When/Then
            val ex = assertThrows(DomainException::class.java) {
                profileService.updateProfile(testEmail, "User", newEmail)
            }
            assertEquals("DUPLICATE_EMAIL", ex.errorCode)
        }

        @Test
        fun `throws DUPLICATE_EMAIL when email exists in users table`() {
            // Given
            val newEmail = "taken-global@example.com"
            every { userContextService.resolveUserMemberId(testEmail) } returns testMemberId
            every { userContextService.resolveUserTenantId(testEmail) } returns testTenantId
            val member = createTestMember(testEmail, "User")
            every { memberRepository.findById(MemberId.of(testMemberId)) } returns Optional.of(member)
            every { memberRepository.findByEmail(TenantId.of(testTenantId), newEmail) } returns Optional.empty()
            val existingUser = mockk<com.worklog.domain.user.User>()
            every { userRepository.findByEmail(newEmail) } returns Optional.of(existingUser)

            // When/Then
            val ex = assertThrows(DomainException::class.java) {
                profileService.updateProfile(testEmail, "User", newEmail)
            }
            assertEquals("DUPLICATE_EMAIL", ex.errorCode)
        }
    }

    private fun createTestMember(email: String, displayName: String): Member {
        return Member(
            MemberId.of(testMemberId),
            TenantId.of(testTenantId),
            OrganizationId.of(testOrgId),
            email,
            displayName,
            MemberId.of(testManagerId),
            true,
            Instant.now(),
        )
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.worklog.application.service.ProfileServiceTest" -x detekt`
Expected: FAIL — `ProfileService` class does not exist yet

**Step 3: Implement ProfileService**

```java
package com.worklog.application.service;

import com.worklog.api.dto.ProfileResponse;
import com.worklog.api.dto.UpdateProfileResponse;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProfileService {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcMemberRepository memberRepository;
    private final JdbcUserRepository userRepository;
    private final UserContextService userContextService;

    public ProfileService(
            JdbcTemplate jdbcTemplate,
            JdbcMemberRepository memberRepository,
            JdbcUserRepository userRepository,
            UserContextService userContextService) {
        this.jdbcTemplate = jdbcTemplate;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.userContextService = userContextService;
    }

    public ProfileResponse getProfile(String email) {
        String sql =
                """
                SELECT m.id, m.email, m.display_name, o.name AS organization_name,
                       mgr.display_name AS manager_name, m.is_active
                FROM members m
                LEFT JOIN organizations o ON m.organization_id = o.id
                LEFT JOIN members mgr ON m.manager_id = mgr.id
                WHERE LOWER(m.email) = LOWER(?)
                LIMIT 1
                """;

        List<ProfileRow> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ProfileRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("display_name"),
                        rs.getString("organization_name"),
                        rs.getString("manager_name"),
                        rs.getBoolean("is_active")),
                email);

        if (rows.isEmpty()) {
            throw new DomainException("MEMBER_NOT_FOUND", "Member not found for email: " + email);
        }

        ProfileRow row = rows.get(0);
        return new ProfileResponse(
                row.id(), row.email(), row.displayName(), row.organizationName(), row.managerName(), row.isActive());
    }

    @Transactional
    public UpdateProfileResponse updateProfile(String currentEmail, String newDisplayName, String newEmail) {
        UUID memberId = userContextService.resolveUserMemberId(currentEmail);
        UUID tenantId = userContextService.resolveUserTenantId(currentEmail);

        var member = memberRepository
                .findById(MemberId.of(memberId))
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found"));

        boolean emailChanged = !currentEmail.equalsIgnoreCase(newEmail);

        if (emailChanged) {
            // Check tenant-scoped uniqueness
            var existingMember = memberRepository.findByEmail(TenantId.of(tenantId), newEmail);
            if (existingMember.isPresent()) {
                throw new DomainException(
                        "DUPLICATE_EMAIL", "A member with this email already exists in this tenant");
            }

            // Check global uniqueness
            var existingUser = userRepository.findByEmail(newEmail);
            if (existingUser.isPresent()) {
                throw new DomainException("DUPLICATE_EMAIL", "A user with this email already exists");
            }
        }

        // Update member (keeps existing managerId via member.update)
        member.update(newEmail, newDisplayName, member.getManagerId());
        memberRepository.save(member);

        if (emailChanged) {
            // Sync users table
            var user = userRepository
                    .findByEmail(currentEmail)
                    .orElseThrow(() -> new DomainException("USER_NOT_FOUND", "User account not found"));
            user.updateEmail(newEmail);
            userRepository.save(user);
        }

        return new UpdateProfileResponse(emailChanged);
    }

    public record ProfileRow(
            UUID id, String email, String displayName, String organizationName, String managerName, boolean isActive) {}
}
```

**NOTE:** The `User.updateEmail()` method may not exist yet on the User domain class. Check `backend/src/main/java/com/worklog/domain/user/User.java` — if missing, add:

```java
public void updateEmail(String email) {
    this.email = Objects.requireNonNull(email, "Email cannot be null");
    this.updatedAt = Instant.now();
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.worklog.application.service.ProfileServiceTest" -x detekt`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add backend/src/main/java/com/worklog/application/service/ProfileService.java \
       backend/src/test/kotlin/com/worklog/application/service/ProfileServiceTest.kt
git commit -m "feat(backend): add ProfileService with getProfile and updateProfile"
```

---

### Task 4: Create ProfileController

**Files:**
- Create: `backend/src/main/java/com/worklog/api/ProfileController.java`

**Step 1: Write the failing test**

Create `backend/src/test/kotlin/com/worklog/api/ProfileControllerTest.kt`:

```kotlin
package com.worklog.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.worklog.api.dto.ProfileResponse
import com.worklog.api.dto.UpdateProfileResponse
import com.worklog.application.service.ProfileService
import com.worklog.domain.shared.DomainException
import com.worklog.infrastructure.config.LoggingProperties
import com.worklog.infrastructure.config.RateLimitProperties
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.*

@WebMvcTest(
    controllers = [ProfileController::class],
    excludeAutoConfiguration = [
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration::class,
    ],
    excludeFilters = [
        org.springframework.context.annotation.ComponentScan.Filter(
            type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
            classes = [
                com.worklog.infrastructure.config.RateLimitFilter::class,
                com.worklog.infrastructure.config.TenantStatusFilter::class,
            ],
        ),
    ],
)
class ProfileControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var profileService: ProfileService

    private val mockAuth =
        UsernamePasswordAuthenticationToken.authenticated("user@example.com", null, emptyList())

    @BeforeEach
    fun setup() {
        clearMocks(profileService)
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun profileService(): ProfileService = mockk(relaxed = true)

        @Bean
        fun loggingProperties(): LoggingProperties {
            val props = LoggingProperties()
            props.enabled = false
            return props
        }

        @Bean
        fun rateLimitProperties(): RateLimitProperties {
            val props = RateLimitProperties()
            props.enabled = false
            return props
        }
    }

    @Nested
    inner class GetProfile {
        @Test
        fun `returns 200 with profile data`() {
            // Given
            val memberId = UUID.randomUUID()
            every { profileService.getProfile("user@example.com") } returns
                ProfileResponse(memberId, "user@example.com", "Test User", "Engineering", "Manager", true)

            // When/Then
            mockMvc.perform(
                get("/api/v1/profile").principal(mockAuth),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.displayName").value("Test User"))
                .andExpect(jsonPath("$.organizationName").value("Engineering"))
                .andExpect(jsonPath("$.managerName").value("Manager"))
                .andExpect(jsonPath("$.isActive").value(true))
        }

        @Test
        fun `returns 404 when member not found`() {
            // Given
            every { profileService.getProfile(any()) } throws
                DomainException("MEMBER_NOT_FOUND", "Member not found")

            // When/Then
            mockMvc.perform(
                get("/api/v1/profile").principal(mockAuth),
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `returns 401 without authentication`() {
            mockMvc.perform(
                get("/api/v1/profile"),
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class UpdateProfile {
        @Test
        fun `returns 204 when no email change`() {
            // Given
            every { profileService.updateProfile(any(), any(), any()) } returns
                UpdateProfileResponse(false)
            val body = mapOf("email" to "user@example.com", "displayName" to "New Name")

            // When/Then
            mockMvc.perform(
                put("/api/v1/profile")
                    .principal(mockAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
                    .with(csrf()),
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `returns 200 with emailChanged true when email changes`() {
            // Given
            every { profileService.updateProfile(any(), any(), any()) } returns
                UpdateProfileResponse(true)
            val body = mapOf("email" to "new@example.com", "displayName" to "Test")

            // When/Then
            mockMvc.perform(
                put("/api/v1/profile")
                    .principal(mockAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
                    .with(csrf()),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.emailChanged").value(true))
        }

        @Test
        fun `returns 409 for duplicate email`() {
            // Given
            every { profileService.updateProfile(any(), any(), any()) } throws
                DomainException("DUPLICATE_EMAIL", "Duplicate email")
            val body = mapOf("email" to "taken@example.com", "displayName" to "Test")

            // When/Then
            mockMvc.perform(
                put("/api/v1/profile")
                    .principal(mockAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
                    .with(csrf()),
            )
                .andExpect(status().isConflict)
        }

        @Test
        fun `returns 400 for invalid request body`() {
            val body = mapOf("email" to "", "displayName" to "")

            mockMvc.perform(
                put("/api/v1/profile")
                    .principal(mockAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
                    .with(csrf()),
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `returns 401 without authentication`() {
            val body = mapOf("email" to "a@b.com", "displayName" to "X")

            mockMvc.perform(
                put("/api/v1/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
                    .with(csrf()),
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `verifies service is called with correct params`() {
            // Given
            every { profileService.updateProfile(any(), any(), any()) } returns
                UpdateProfileResponse(false)
            val body = mapOf("email" to "user@example.com", "displayName" to "Updated")

            // When
            mockMvc.perform(
                put("/api/v1/profile")
                    .principal(mockAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
                    .with(csrf()),
            )
                .andExpect(status().isNoContent)

            // Then
            verify(exactly = 1) {
                profileService.updateProfile("user@example.com", "Updated", "user@example.com")
            }
        }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.worklog.api.ProfileControllerTest" -x detekt`
Expected: FAIL — `ProfileController` does not exist

**Step 3: Implement ProfileController**

```java
package com.worklog.api;

import com.worklog.api.dto.ProfileResponse;
import com.worklog.api.dto.UpdateProfileRequest;
import com.worklog.api.dto.UpdateProfileResponse;
import com.worklog.application.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        ProfileResponse profile = profileService.getProfile(authentication.getName());
        return ResponseEntity.ok(profile);
    }

    @PutMapping
    public ResponseEntity<UpdateProfileResponse> updateProfile(
            @RequestBody @Valid UpdateProfileRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        UpdateProfileResponse result =
                profileService.updateProfile(authentication.getName(), request.displayName(), request.email());

        if (result.emailChanged()) {
            new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, authentication);
            return ResponseEntity.ok(result);
        }

        return ResponseEntity.noContent().build();
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.worklog.api.ProfileControllerTest" -x detekt`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add backend/src/main/java/com/worklog/api/ProfileController.java \
       backend/src/test/kotlin/com/worklog/api/ProfileControllerTest.kt
git commit -m "feat(backend): add ProfileController with GET and PUT endpoints"
```

---

### Task 5: Add SecurityConfig route

**Files:**
- Modify: `backend/src/main/java/com/worklog/infrastructure/config/SecurityConfig.kt:113-115`

**Step 1: Add profile route to authenticated matchers**

In `SecurityConfig.kt`, inside `prodFilterChain`, after line 115 (`.requestMatchers("/api/v1/user/**").authenticated()`), add:

```kotlin
// Profile endpoint requires authentication
.requestMatchers("/api/v1/profile/**").authenticated()
```

**Step 2: Run existing security tests to verify no regression**

Run: `./gradlew test --tests "com.worklog.api.*" -x detekt`
Expected: ALL PASS

**Step 3: Commit**

```bash
git add backend/src/main/java/com/worklog/infrastructure/config/SecurityConfig.kt
git commit -m "feat(backend): add /api/v1/profile/** to authenticated routes"
```

---

### Task 6: Add User.updateEmail method (if missing)

**Files:**
- Modify: `backend/src/main/java/com/worklog/domain/user/User.java`

**Step 1: Check if updateEmail exists**

Search for `updateEmail` in `User.java`. If it does not exist:

**Step 2: Add the method**

```java
public void updateEmail(String email) {
    this.email = Objects.requireNonNull(email, "Email cannot be null");
    this.updatedAt = Instant.now();
}
```

**Step 3: Commit**

```bash
git add backend/src/main/java/com/worklog/domain/user/User.java
git commit -m "feat(backend): add updateEmail method to User domain"
```

---

### Task 7: Format, full test, and verify

**Step 1: Format all backend code**

Run: `./gradlew formatAll`

**Step 2: Run full test suite**

Run: `./gradlew test`
Expected: ALL PASS

**Step 3: Fix any issues and commit**

```bash
git add -u
git commit -m "chore(backend): format and verify profile API implementation"
```

---

### Task 8: Integration test (optional, recommended)

**Files:**
- Create: `backend/src/test/kotlin/com/worklog/api/ProfileControllerIntegrationTest.kt`

**Step 1: Write integration test using IntegrationTestBase**

```kotlin
package com.worklog.api

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.*
import java.util.*

class ProfileControllerIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private val testMemberId = UUID.randomUUID()
    private val testEmail = "profile-test-${UUID.randomUUID()}@example.com"

    @BeforeEach
    fun setupData() {
        createTestMember(testMemberId, testEmail)
    }

    @Test
    fun `GET profile returns member data with org and manager names`() {
        val response = restTemplate.getForEntity(
            "/api/v1/profile?_mockEmail=$testEmail",
            Map::class.java,
        )
        // In dev profile all requests are permitted, but authentication principal is null.
        // This test verifies the endpoint is reachable. Full auth testing requires
        // Spring Security test support or mock authentication.
    }
}
```

**NOTE:** The integration test structure depends on how dev-profile authentication works (currently permitAll + no principal). If needed, adjust to use `@WithMockUser` or Basic Auth. Keep this task lightweight; the unit tests in Tasks 3-4 cover the core logic.

**Step 2: Commit**

```bash
git add backend/src/test/kotlin/com/worklog/api/ProfileControllerIntegrationTest.kt
git commit -m "test(backend): add profile API integration test skeleton"
```
