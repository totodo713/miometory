# Coverage Improvement Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Bring all backend packages to 80%+ LINE coverage and fix 5 failing TenantControllerTests.

**Architecture:** Domain-centric unit tests with MockK/Kotlin, following existing patterns from TenantTest.kt and UserTest.kt. Integration tests only where needed (infrastructure layer).

**Tech Stack:** Kotlin, JUnit5, MockK, Spring Boot Test, Testcontainers

---

### Task 1: Fix TenantControllerTest

**Files:**
- Modify: `backend/src/test/kotlin/com/worklog/api/TenantControllerTest.kt`

**Root cause:** `withReuse(true)` on Testcontainers persists data between test runs. Hardcoded tenant codes like `TEST_TENANT_001` cause unique constraint violations on re-runs.

**Step 1: Add unique code helper and fix all tests**

Replace all hardcoded tenant codes with UUID-based unique codes:

```kotlin
class TenantControllerTest : IntegrationTestBase() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private fun uniqueCode() = "T_${UUID.randomUUID().toString().take(8).uppercase()}"

    @Test
    fun `POST tenants should create new tenant and return 201`() {
        val code = uniqueCode()
        val request = mapOf("code" to code, "name" to "Test Tenant One")

        val response = restTemplate.postForEntity("/api/v1/tenants", request, Map::class.java)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertNotNull(body["id"])
        assertEquals(code, body["code"])
        assertEquals("Test Tenant One", body["name"])
    }

    @Test
    fun `GET tenants by id should return tenant details`() {
        val code = uniqueCode()
        val createRequest = mapOf("code" to code, "name" to "Test Tenant Two")
        val createResponse = restTemplate.postForEntity("/api/v1/tenants", createRequest, Map::class.java)
        val tenantId = (createResponse.body as Map<*, *>)["id"] as String

        val response = restTemplate.getForEntity("/api/v1/tenants/$tenantId", Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertEquals(tenantId, body["id"])
        assertEquals(code, body["code"])
        assertEquals("Test Tenant Two", body["name"])
        assertEquals("ACTIVE", body["status"])
    }

    // GET non-existent tenant test stays unchanged (no tenant creation)

    @Test
    fun `PATCH tenant should update name`() {
        val code = uniqueCode()
        val createRequest = mapOf("code" to code, "name" to "Original Name")
        val createResponse = restTemplate.postForEntity("/api/v1/tenants", createRequest, Map::class.java)
        val tenantId = (createResponse.body as Map<*, *>)["id"] as String

        val updateRequest = mapOf("name" to "Updated Name")
        val response = restTemplate.exchange(
            "/api/v1/tenants/$tenantId", HttpMethod.PATCH, HttpEntity(updateRequest), Void::class.java,
        )

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        val getResponse = restTemplate.getForEntity("/api/v1/tenants/$tenantId", Map::class.java)
        val body = getResponse.body as Map<*, *>
        assertEquals("Updated Name", body["name"])
    }

    @Test
    fun `POST deactivate should set tenant status to INACTIVE`() {
        val code = uniqueCode()
        val createRequest = mapOf("code" to code, "name" to "Test Tenant Four")
        val createResponse = restTemplate.postForEntity("/api/v1/tenants", createRequest, Map::class.java)
        val tenantId = (createResponse.body as Map<*, *>)["id"] as String

        val response = restTemplate.postForEntity(
            "/api/v1/tenants/$tenantId/deactivate", null, Void::class.java,
        )

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        val getResponse = restTemplate.getForEntity("/api/v1/tenants/$tenantId", Map::class.java)
        val body = getResponse.body as Map<*, *>
        assertEquals("INACTIVE", body["status"])
    }

    @Test
    fun `POST activate should set tenant status to ACTIVE`() {
        val code = uniqueCode()
        val createRequest = mapOf("code" to code, "name" to "Test Tenant Five")
        val createResponse = restTemplate.postForEntity("/api/v1/tenants", createRequest, Map::class.java)
        val tenantId = (createResponse.body as Map<*, *>)["id"] as String

        restTemplate.postForEntity("/api/v1/tenants/$tenantId/deactivate", null, Void::class.java)

        val response = restTemplate.postForEntity(
            "/api/v1/tenants/$tenantId/activate", null, Void::class.java,
        )

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        val getResponse = restTemplate.getForEntity("/api/v1/tenants/$tenantId", Map::class.java)
        val body = getResponse.body as Map<*, *>
        assertEquals("ACTIVE", body["status"])
    }

    @Test
    fun `POST tenant with invalid code should return error`() {
        val request = mapOf("code" to "INVALID-CODE!@#", "name" to "Test Tenant")
        val response = restTemplate.postForEntity("/api/v1/tenants", request, Map::class.java)
        assertTrue(response.statusCode.is4xxClientError || response.statusCode.is5xxServerError)
    }

    @Test
    fun `POST tenant with empty name should return error`() {
        val request = mapOf("code" to uniqueCode(), "name" to "")
        val response = restTemplate.postForEntity("/api/v1/tenants", request, Map::class.java)
        assertTrue(response.statusCode.is4xxClientError || response.statusCode.is5xxServerError)
    }
}
```

**Step 2: Run TenantControllerTest**

Run: `cd backend && ./gradlew test --tests 'com.worklog.api.TenantControllerTest' --no-daemon`
Expected: All 8 tests PASS

**Step 3: Commit**

```bash
git add backend/src/test/kotlin/com/worklog/api/TenantControllerTest.kt
git commit -m "fix(test): use unique tenant codes to prevent constraint violations on container reuse"
```

---

### Task 2: DailyEntryApproval Domain Tests (0% -> 80%+)

**Files:**
- Create: `backend/src/test/kotlin/com/worklog/domain/dailyapproval/DailyEntryApprovalTest.kt`

**Step 1: Write test file**

```kotlin
package com.worklog.domain.dailyapproval

import com.worklog.domain.member.MemberId
import com.worklog.domain.shared.DomainException
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class DailyEntryApprovalTest {

    private val memberId = MemberId.of(UUID.randomUUID())
    private val supervisorId = MemberId.of(UUID.randomUUID())
    private val workLogEntryId = UUID.randomUUID()

    @Nested
    @DisplayName("DailyEntryApprovalId")
    inner class IdTests {
        @Test
        fun `generate should create unique IDs`() {
            val id1 = DailyEntryApprovalId.generate()
            val id2 = DailyEntryApprovalId.generate()
            assertNotNull(id1.value())
            assertNotEquals(id1, id2)
        }

        @Test
        fun `of UUID should create id with same value`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid, DailyEntryApprovalId.of(uuid).value())
        }

        @Test
        fun `of String should parse UUID string`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid, DailyEntryApprovalId.of(uuid.toString()).value())
        }

        @Test
        fun `constructor should reject null`() {
            assertFailsWith<IllegalArgumentException> {
                DailyEntryApprovalId(null)
            }
        }

        @Test
        fun `toString should return UUID string`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid.toString(), DailyEntryApprovalId.of(uuid).toString())
        }
    }

    @Nested
    @DisplayName("create")
    inner class CreateTests {
        @Test
        fun `should create APPROVED entry without comment`() {
            val approval = DailyEntryApproval.create(
                workLogEntryId, memberId, supervisorId, DailyApprovalStatus.APPROVED, null,
            )
            assertNotNull(approval.id)
            assertEquals(workLogEntryId, approval.workLogEntryId)
            assertEquals(memberId, approval.memberId)
            assertEquals(supervisorId, approval.supervisorId)
            assertEquals(DailyApprovalStatus.APPROVED, approval.status)
            assertNotNull(approval.createdAt)
            assertNotNull(approval.updatedAt)
        }

        @Test
        fun `should create REJECTED entry with comment`() {
            val approval = DailyEntryApproval.create(
                workLogEntryId, memberId, supervisorId, DailyApprovalStatus.REJECTED, "Fix hours",
            )
            assertEquals(DailyApprovalStatus.REJECTED, approval.status)
            assertEquals("Fix hours", approval.comment)
        }

        @Test
        fun `should reject REJECTED without comment`() {
            assertFailsWith<DomainException> {
                DailyEntryApproval.create(
                    workLogEntryId, memberId, supervisorId, DailyApprovalStatus.REJECTED, null,
                )
            }
        }

        @Test
        fun `should reject REJECTED with blank comment`() {
            assertFailsWith<DomainException> {
                DailyEntryApproval.create(
                    workLogEntryId, memberId, supervisorId, DailyApprovalStatus.REJECTED, "  ",
                )
            }
        }
    }

    @Nested
    @DisplayName("reconstitute")
    inner class ReconstituteTests {
        @Test
        fun `should restore all fields`() {
            val id = DailyEntryApprovalId.generate()
            val now = Instant.now()
            val approval = DailyEntryApproval.reconstitute(
                id, workLogEntryId, memberId, supervisorId,
                DailyApprovalStatus.APPROVED, "note", now, now,
            )
            assertEquals(id, approval.id)
            assertEquals(DailyApprovalStatus.APPROVED, approval.status)
            assertEquals("note", approval.comment)
            assertEquals(now, approval.createdAt)
        }
    }

    @Nested
    @DisplayName("recall")
    inner class RecallTests {
        @Test
        fun `should transition APPROVED to RECALLED`() {
            val approval = DailyEntryApproval.create(
                workLogEntryId, memberId, supervisorId, DailyApprovalStatus.APPROVED, null,
            )
            approval.recall()
            assertEquals(DailyApprovalStatus.RECALLED, approval.status)
        }

        @Test
        fun `should reject recall of REJECTED entry`() {
            val approval = DailyEntryApproval.create(
                workLogEntryId, memberId, supervisorId, DailyApprovalStatus.REJECTED, "reason",
            )
            assertFailsWith<DomainException> { approval.recall() }
        }
    }
}
```

**Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests 'com.worklog.domain.dailyapproval.DailyEntryApprovalTest' --no-daemon`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add backend/src/test/kotlin/com/worklog/domain/dailyapproval/DailyEntryApprovalTest.kt
git commit -m "test: add DailyEntryApproval domain tests (0% -> 80%+)"
```

---

### Task 3: Permission Domain Tests (0% -> 80%+)

**Files:**
- Create: `backend/src/test/kotlin/com/worklog/domain/permission/PermissionTest.kt`

**Step 1: Write test file**

```kotlin
package com.worklog.domain.permission

import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PermissionTest {

    @Nested
    @DisplayName("PermissionId")
    inner class IdTests {
        @Test
        fun `generate should create unique IDs`() {
            assertNotEquals(PermissionId.generate(), PermissionId.generate())
        }

        @Test
        fun `of UUID should wrap value`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid, PermissionId.of(uuid).value())
        }

        @Test
        fun `of String should parse UUID`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid, PermissionId.of(uuid.toString()).value())
        }

        @Test
        fun `constructor should reject null`() {
            assertFailsWith<IllegalArgumentException> { PermissionId(null) }
        }

        @Test
        fun `toString should return UUID string`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid.toString(), PermissionId.of(uuid).toString())
        }
    }

    @Nested
    @DisplayName("Permission.create")
    inner class CreateTests {
        @Test
        fun `should create permission with valid name`() {
            val perm = Permission.create("user.create", "Can create users")
            assertNotNull(perm.id)
            assertEquals("user.create", perm.name)
            assertEquals("Can create users", perm.description)
            assertNotNull(perm.createdAt)
        }

        @Test
        fun `should allow null description`() {
            val perm = Permission.create("report.view", null)
            assertNull(perm.description)
        }

        @Test
        fun `should reject null name`() {
            assertFailsWith<IllegalArgumentException> { Permission.create(null, "desc") }
        }

        @Test
        fun `should reject blank name`() {
            assertFailsWith<IllegalArgumentException> { Permission.create("  ", "desc") }
        }

        @Test
        fun `should reject name exceeding 100 chars`() {
            val longName = "a".repeat(50) + "." + "b".repeat(50)
            assertFailsWith<IllegalArgumentException> { Permission.create(longName, "desc") }
        }

        @Test
        fun `should reject name without dot pattern`() {
            assertFailsWith<IllegalArgumentException> { Permission.create("noDot", "desc") }
        }

        @Test
        fun `should reject name with uppercase`() {
            assertFailsWith<IllegalArgumentException> { Permission.create("User.Create", "desc") }
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    inner class EqualityTests {
        @Test
        fun `same id should be equal`() {
            val id = PermissionId.generate()
            val now = Instant.now()
            val p1 = Permission(id, "user.read", "desc1", now)
            val p2 = Permission(id, "user.read", "desc2", now)
            assertEquals(p1, p2)
            assertEquals(p1.hashCode(), p2.hashCode())
        }

        @Test
        fun `different id should not be equal`() {
            val now = Instant.now()
            val p1 = Permission(PermissionId.generate(), "user.read", "desc", now)
            val p2 = Permission(PermissionId.generate(), "user.read", "desc", now)
            assertNotEquals(p1, p2)
        }
    }

    @Test
    fun `toString should contain name`() {
        val perm = Permission.create("admin.access", "Admin access")
        val str = perm.toString()
        assert(str.contains("admin.access")) { "toString should contain name: $str" }
    }
}
```

**Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests 'com.worklog.domain.permission.PermissionTest' --no-daemon`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add backend/src/test/kotlin/com/worklog/domain/permission/PermissionTest.kt
git commit -m "test: add Permission domain tests (0% -> 80%+)"
```

---

### Task 4: InAppNotification Domain Tests (24% -> 80%+)

**Files:**
- Create: `backend/src/test/kotlin/com/worklog/domain/notification/InAppNotificationTest.kt`

**Step 1: Write test file**

```kotlin
package com.worklog.domain.notification

import com.worklog.domain.member.MemberId
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InAppNotificationTest {

    private val recipientId = MemberId.of(UUID.randomUUID())
    private val referenceId = UUID.randomUUID()

    @Nested
    @DisplayName("NotificationId")
    inner class IdTests {
        @Test
        fun `generate should create unique IDs`() {
            assertNotEquals(NotificationId.generate(), NotificationId.generate())
        }

        @Test
        fun `of UUID should wrap value`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid, NotificationId.of(uuid).value())
        }

        @Test
        fun `of String should parse UUID`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid, NotificationId.of(uuid.toString()).value())
        }

        @Test
        fun `constructor should reject null`() {
            assertFailsWith<IllegalArgumentException> { NotificationId(null) }
        }

        @Test
        fun `toString should return UUID string`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid.toString(), NotificationId.of(uuid).toString())
        }
    }

    @Nested
    @DisplayName("create")
    inner class CreateTests {
        @Test
        fun `should create unread notification`() {
            val notification = InAppNotification.create(
                recipientId, NotificationType.DAILY_APPROVED, referenceId, "Approved", "Your entry was approved",
            )
            assertNotNull(notification.id)
            assertEquals(recipientId, notification.recipientMemberId)
            assertEquals(NotificationType.DAILY_APPROVED, notification.type)
            assertEquals(referenceId, notification.referenceId)
            assertEquals("Approved", notification.title)
            assertEquals("Your entry was approved", notification.message)
            assertFalse(notification.isRead)
            assertNotNull(notification.createdAt)
        }
    }

    @Nested
    @DisplayName("reconstitute")
    inner class ReconstituteTests {
        @Test
        fun `should restore all fields including read state`() {
            val id = NotificationId.generate()
            val now = Instant.now()
            val notification = InAppNotification.reconstitute(
                id, recipientId, NotificationType.SYSTEM_ALERT, referenceId, "Alert", "msg", true, now,
            )
            assertEquals(id, notification.id)
            assertTrue(notification.isRead)
            assertEquals(now, notification.createdAt)
        }
    }

    @Test
    fun `markRead should set isRead to true`() {
        val notification = InAppNotification.create(
            recipientId, NotificationType.DAILY_SUBMITTED, referenceId, "Submitted", "Entry submitted",
        )
        assertFalse(notification.isRead)
        notification.markRead()
        assertTrue(notification.isRead)
    }
}
```

**Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests 'com.worklog.domain.notification.InAppNotificationTest' --no-daemon`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add backend/src/test/kotlin/com/worklog/domain/notification/InAppNotificationTest.kt
git commit -m "test: add InAppNotification domain tests (24% -> 80%+)"
```

---

### Task 5: UserSession Domain Tests (54% -> 80%+)

**Files:**
- Create: `backend/src/test/kotlin/com/worklog/domain/session/UserSessionTest.kt`

**Step 1: Write test file**

```kotlin
package com.worklog.domain.session

import com.worklog.domain.user.UserId
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserSessionTest {

    private val userId = UserId.of(UUID.randomUUID())

    @Nested
    @DisplayName("create")
    inner class CreateTests {
        @Test
        fun `should create session with correct expiration`() {
            val session = UserSession.create(userId, "127.0.0.1", "TestAgent", 30)
            assertNotNull(session.sessionId)
            assertEquals(userId, session.userId)
            assertEquals("127.0.0.1", session.ipAddress)
            assertEquals("TestAgent", session.userAgent)
            assertNotNull(session.createdAt)
            assertTrue(session.expiresAt.isAfter(session.createdAt))
            assertEquals(session.createdAt, session.lastAccessedAt)
        }

        @Test
        fun `should allow null ipAddress and userAgent`() {
            val session = UserSession.create(userId, null, null, 60)
            assertNotNull(session.sessionId)
        }
    }

    @Nested
    @DisplayName("constructor validation")
    inner class ValidationTests {
        @Test
        fun `should reject null sessionId`() {
            assertFailsWith<NullPointerException> {
                UserSession(null, userId, "ip", "ua", Instant.now(), Instant.now().plusSeconds(60))
            }
        }

        @Test
        fun `should reject null userId`() {
            assertFailsWith<NullPointerException> {
                UserSession(UUID.randomUUID(), null, "ip", "ua", Instant.now(), Instant.now().plusSeconds(60))
            }
        }

        @Test
        fun `should reject expiresAt before createdAt`() {
            val now = Instant.now()
            assertFailsWith<IllegalArgumentException> {
                UserSession(UUID.randomUUID(), userId, "ip", "ua", now, now.minusSeconds(60))
            }
        }
    }

    @Nested
    @DisplayName("touch")
    inner class TouchTests {
        @Test
        fun `should update lastAccessedAt and expiresAt`() {
            val session = UserSession.create(userId, "127.0.0.1", "agent", 30)
            val originalExpires = session.expiresAt

            Thread.sleep(10)
            session.touch(60)

            assertTrue(session.lastAccessedAt.isAfter(session.createdAt))
            assertNotEquals(originalExpires, session.expiresAt)
        }
    }

    @Nested
    @DisplayName("expiration")
    inner class ExpirationTests {
        @Test
        fun `isExpired should return true for past expiration`() {
            val past = Instant.now().minus(1, ChronoUnit.HOURS)
            val session = UserSession(
                UUID.randomUUID(), userId, "ip", "ua", past.minusSeconds(3600), past,
            )
            assertTrue(session.isExpired())
            assertFalse(session.isValid())
        }

        @Test
        fun `isExpired should return false for future expiration`() {
            val session = UserSession.create(userId, "ip", "ua", 60)
            assertFalse(session.isExpired())
            assertTrue(session.isValid())
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    inner class EqualityTests {
        @Test
        fun `same sessionId should be equal`() {
            val id = UUID.randomUUID()
            val now = Instant.now()
            val s1 = UserSession(id, userId, "ip1", "ua1", now, now.plusSeconds(60))
            val s2 = UserSession(id, userId, "ip2", "ua2", now, now.plusSeconds(120))
            assertEquals(s1, s2)
            assertEquals(s1.hashCode(), s2.hashCode())
        }

        @Test
        fun `different sessionId should not be equal`() {
            val now = Instant.now()
            val s1 = UserSession(UUID.randomUUID(), userId, "ip", "ua", now, now.plusSeconds(60))
            val s2 = UserSession(UUID.randomUUID(), userId, "ip", "ua", now, now.plusSeconds(60))
            assertNotEquals(s1, s2)
        }
    }

    @Test
    fun `toString should contain sessionId`() {
        val session = UserSession.create(userId, "127.0.0.1", "agent", 30)
        val str = session.toString()
        assertTrue(str.contains(session.sessionId.toString())) { "toString should contain sessionId" }
    }
}
```

**Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests 'com.worklog.domain.session.UserSessionTest' --no-daemon`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add backend/src/test/kotlin/com/worklog/domain/session/UserSessionTest.kt
git commit -m "test: add UserSession domain tests (54% -> 80%+)"
```

---

### Task 6: Role Domain Tests (59% -> 80%+)

**Files:**
- Create: `backend/src/test/kotlin/com/worklog/domain/role/RoleTest.kt`

**Step 1: Write test file**

```kotlin
package com.worklog.domain.role

import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class RoleTest {

    @Nested
    @DisplayName("RoleId")
    inner class IdTests {
        @Test
        fun `generate should create unique IDs`() {
            assertNotEquals(RoleId.generate(), RoleId.generate())
        }

        @Test
        fun `of UUID should wrap value`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid, RoleId.of(uuid).value())
        }

        @Test
        fun `of String should parse UUID`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid, RoleId.of(uuid.toString()).value())
        }

        @Test
        fun `constructor should reject null`() {
            assertFailsWith<IllegalArgumentException> { RoleId(null) }
        }

        @Test
        fun `toString should return UUID string`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid.toString(), RoleId.of(uuid).toString())
        }
    }

    @Nested
    @DisplayName("Role.create")
    inner class CreateTests {
        @Test
        fun `should create role and uppercase name`() {
            val role = Role.create("admin", "Administrator role")
            assertNotNull(role.id)
            assertEquals("ADMIN", role.name)
            assertEquals("Administrator role", role.description)
            assertNotNull(role.createdAt)
            assertEquals(role.createdAt, role.updatedAt)
        }

        @Test
        fun `should reject null name`() {
            assertFailsWith<IllegalArgumentException> { Role.create(null, "desc") }
        }

        @Test
        fun `should reject blank name`() {
            assertFailsWith<IllegalArgumentException> { Role.create("  ", "desc") }
        }

        @Test
        fun `should reject name over 50 chars`() {
            assertFailsWith<IllegalArgumentException> { Role.create("A".repeat(51), "desc") }
        }
    }

    @Nested
    @DisplayName("update")
    inner class UpdateTests {
        @Test
        fun `should update name and description`() {
            val role = Role.create("user", "User role")
            val originalUpdatedAt = role.updatedAt

            Thread.sleep(10)
            role.update("manager", "Manager role")

            assertEquals("MANAGER", role.name)
            assertEquals("Manager role", role.description)
            assertNotEquals(originalUpdatedAt, role.updatedAt)
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    inner class EqualityTests {
        @Test
        fun `same id should be equal`() {
            val id = RoleId.generate()
            val now = Instant.now()
            val r1 = Role(id, "admin", "desc1", now)
            val r2 = Role(id, "user", "desc2", now)
            assertEquals(r1, r2)
            assertEquals(r1.hashCode(), r2.hashCode())
        }

        @Test
        fun `different id should not be equal`() {
            val now = Instant.now()
            assertNotEquals(
                Role(RoleId.generate(), "admin", "d", now),
                Role(RoleId.generate(), "admin", "d", now),
            )
        }
    }

    @Test
    fun `toString should contain name`() {
        val role = Role.create("viewer", "View only")
        assertTrue(role.toString().contains("VIEWER"))
    }
}
```

**Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests 'com.worklog.domain.role.RoleTest' --no-daemon`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add backend/src/test/kotlin/com/worklog/domain/role/RoleTest.kt
git commit -m "test: add Role domain tests (59% -> 80%+)"
```

---

### Task 7: ID Converter Unit Tests (infrastructure/persistence 62% -> partial improvement)

**Files:**
- Create: `backend/src/test/kotlin/com/worklog/infrastructure/persistence/IdConverterTest.kt`

Reference existing pattern: `backend/src/test/kotlin/com/worklog/infrastructure/persistence/JsonbInetConverterTest.kt`

**Step 1: Write test file**

```kotlin
package com.worklog.infrastructure.persistence

import com.worklog.domain.role.RoleId
import com.worklog.domain.user.UserId
import java.util.UUID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IdConverterTest {

    @Nested
    @DisplayName("UserIdToUuidConverter")
    inner class UserIdToUuidTests {
        private val converter = UserIdToUuidConverter()

        @Test
        fun `should convert UserId to UUID`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid, converter.convert(UserId.of(uuid)))
        }
    }

    @Nested
    @DisplayName("UuidToUserIdConverter")
    inner class UuidToUserIdTests {
        private val converter = UuidToUserIdConverter()

        @Test
        fun `should convert UUID to UserId`() {
            val uuid = UUID.randomUUID()
            assertEquals(UserId.of(uuid), converter.convert(uuid))
        }
    }

    @Nested
    @DisplayName("RoleIdToUuidConverter")
    inner class RoleIdToUuidTests {
        private val converter = RoleIdToUuidConverter()

        @Test
        fun `should convert RoleId to UUID`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid, converter.convert(RoleId.of(uuid)))
        }
    }

    @Nested
    @DisplayName("UuidToRoleIdConverter")
    inner class UuidToRoleIdTests {
        private val converter = UuidToRoleIdConverter()

        @Test
        fun `should convert UUID to RoleId`() {
            val uuid = UUID.randomUUID()
            assertEquals(RoleId.of(uuid), converter.convert(uuid))
        }
    }
}
```

**Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests 'com.worklog.infrastructure.persistence.IdConverterTest' --no-daemon`
Expected: All 4 tests PASS

**Step 3: Commit**

```bash
git add backend/src/test/kotlin/com/worklog/infrastructure/persistence/IdConverterTest.kt
git commit -m "test: add ID converter unit tests for infrastructure/persistence"
```

---

### Task 8: ApprovalService Coverage Extension (62% -> 80%+)

**Files:**
- Modify: `backend/src/test/kotlin/com/worklog/application/approval/ApprovalServiceTest.kt`

**Step 1: Investigate uncovered branches**

Run JaCoCo HTML report and open `backend/build/reports/jacoco/index.html` to identify specific uncovered branches in:
- `ApprovalService.submitMonth()` - look for untested code paths
- `ApprovalService.getMonthlyApprovalDetail()` - look for uncovered projection logic
- Inner records: `MonthlyApprovalDetail`, `DailyApprovalSummary`, `UnresolvedEntry`, `ProjectBreakdown`

Run: `cd backend && ./gradlew --exclude-task :test jacocoTestReport --no-daemon`
Then check: `backend/build/reports/jacoco/com.worklog.application.approval/ApprovalService.java.html`

**Step 2: Add tests for uncovered paths**

Based on investigation, add tests to the existing `ApprovalServiceTest.kt` for:
1. Error path in `submitMonth()` when member is not found
2. Error path when approval already exists for the month
3. `getMonthlyApprovalDetail()` with daily entries
4. Inner record instantiation via `getMonthlyApprovalDetail()` result

Each test should follow the existing Mockito pattern in ApprovalServiceTest.kt.

**Step 3: Run tests**

Run: `cd backend && ./gradlew test --tests 'com.worklog.application.approval.*' --no-daemon`
Expected: All tests PASS

**Step 4: Commit**

```bash
git add backend/src/test/kotlin/com/worklog/application/approval/ApprovalServiceTest.kt
git commit -m "test: extend ApprovalService tests for edge cases (62% -> 80%+)"
```

---

### Task 9: Command Record Coverage (74% -> 80%+)

**Files:**
- Create: `backend/src/test/kotlin/com/worklog/application/command/CommandContractTest.kt`

Only 3 more lines need coverage. The gap is from auto-generated record methods (equals/hashCode/toString) on 4 commands.

**Step 1: Write minimal contract test**

```kotlin
package com.worklog.application.command

import com.worklog.domain.member.MemberId
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CommandContractTest {

    @Test
    fun `SubmitDailyEntriesCommand equality contract`() {
        val memberId = MemberId.of(UUID.randomUUID())
        val entryIds = listOf(UUID.randomUUID())
        val submittedBy = MemberId.of(UUID.randomUUID())

        val cmd1 = SubmitDailyEntriesCommand(memberId, entryIds, submittedBy)
        val cmd2 = SubmitDailyEntriesCommand(memberId, entryIds, submittedBy)
        val cmd3 = SubmitDailyEntriesCommand(MemberId.of(UUID.randomUUID()), entryIds, submittedBy)

        assertEquals(cmd1, cmd2)
        assertEquals(cmd1.hashCode(), cmd2.hashCode())
        assertNotEquals(cmd1, cmd3)
        cmd1.toString() // exercise toString
    }

    @Test
    fun `RejectDailyEntriesCommand equality contract`() {
        val memberId = MemberId.of(UUID.randomUUID())
        val entryIds = listOf(UUID.randomUUID())
        val rejectedBy = MemberId.of(UUID.randomUUID())

        val cmd = RejectDailyEntriesCommand(memberId, entryIds, rejectedBy, "reason")

        assertEquals(cmd, RejectDailyEntriesCommand(memberId, entryIds, rejectedBy, "reason"))
        cmd.toString()
    }

    @Test
    fun `RecallDailyEntriesCommand equality contract`() {
        val memberId = MemberId.of(UUID.randomUUID())
        val entryIds = listOf(UUID.randomUUID())
        val recalledBy = MemberId.of(UUID.randomUUID())

        val cmd = RecallDailyEntriesCommand(memberId, entryIds, recalledBy)

        assertEquals(cmd, RecallDailyEntriesCommand(memberId, entryIds, recalledBy))
        cmd.toString()
    }

    @Test
    fun `CopyFromPreviousMonthCommand equality contract`() {
        val memberId = UUID.randomUUID()
        val cmd = CopyFromPreviousMonthCommand(memberId, 2026, 2)

        assertEquals(cmd, CopyFromPreviousMonthCommand(memberId, 2026, 2))
        cmd.toString()
    }
}
```

Note: Check the actual constructor signatures of these commands before writing the test. The above is based on common patterns, but the exact fields may differ. Use `find_symbol` or read the files to verify.

**Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests 'com.worklog.application.command.CommandContractTest' --no-daemon`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add backend/src/test/kotlin/com/worklog/application/command/CommandContractTest.kt
git commit -m "test: add command record contract tests (74% -> 80%+)"
```

---

### Task 10: Infrastructure Persistence Gap (Integration Tests)

**Files:**
- Create: `backend/src/test/kotlin/com/worklog/infrastructure/persistence/JdbcUserSessionRepositoryTest.kt`

This is the largest remaining gap. JdbcUserSessionRepository is at 14.6% coverage with 35 missed lines.

**Step 1: Read JdbcUserSessionRepository source**

Read: `backend/src/main/java/com/worklog/infrastructure/persistence/JdbcUserSessionRepository.java`
Identify all methods that need testing.

**Step 2: Write integration test**

Write integration test extending `IntegrationTestBase()` that covers:
1. `save(UserSession)` - persists session to database
2. `findById(UUID)` - retrieves session by ID
3. `findByUserId(UserId)` - retrieves sessions for a user
4. `deleteById(UUID)` - removes session
5. `deleteExpired()` - removes expired sessions
6. Row mapper logic

Follow the pattern from existing integration tests (e.g., `RoleRepositoryTest`).

**Step 3: Run tests**

Run: `cd backend && ./gradlew test --tests 'com.worklog.infrastructure.persistence.JdbcUserSessionRepositoryTest' --no-daemon`
Expected: All tests PASS

**Step 4: Commit**

```bash
git add backend/src/test/kotlin/com/worklog/infrastructure/persistence/JdbcUserSessionRepositoryTest.kt
git commit -m "test: add JdbcUserSessionRepository integration tests for persistence coverage"
```

---

### Task 11: AuditLogService Coverage (for PR#33 changed files metric)

**Files:**
- Modify: `backend/src/test/kotlin/com/worklog/application/audit/AuditLogServiceTest.kt`

The `toJsonDetails` catch block (JsonProcessingException) is uncovered.

**Step 1: Add test for JsonProcessingException**

```kotlin
@Test
fun `logEvent should handle JsonProcessingException in toJsonDetails`() {
    // Create ObjectMapper that fails on serialization
    val failingMapper = mockk<ObjectMapper>()
    every { failingMapper.writeValueAsString(any()) } throws JsonProcessingException("mock error") {}
    val service = AuditLogService(auditLogRepository, failingMapper)

    service.logEvent(UserId.of(UUID.randomUUID()), AuditLog.LOGIN_SUCCESS, "10.0.0.1", "some details")

    // Should call insertAuditLog with null details (fallback)
    verify(exactly = 1) {
        auditLogRepository.insertAuditLog(any(), any(), any(), any(), any(), isNull(), any())
    }
}
```

Note: `JsonProcessingException` has a protected constructor. You may need to use a subclass or a real ObjectMapper configured to fail. Adjust the mock strategy based on what compiles.

**Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests 'com.worklog.application.audit.AuditLogServiceTest' --no-daemon`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add backend/src/test/kotlin/com/worklog/application/audit/AuditLogServiceTest.kt
git commit -m "test: cover AuditLogService toJsonDetails exception path"
```

---

### Task 12: Verify Full Coverage

**Step 1: Run all tests**

Run: `cd backend && ./gradlew test --no-daemon`
Expected: All 1069+ tests PASS (0 failures)

**Step 2: Generate JaCoCo report**

Run: `cd backend && ./gradlew --exclude-task :test jacocoTestReport --no-daemon`

**Step 3: Verify package coverage**

Parse `backend/build/reports/jacoco/test/jacocoTestReport.xml` and verify all packages are at 80%+ LINE coverage.

Focus on the 8 previously-failing packages:
- domain/dailyapproval: was 0%, target 80%+
- domain/permission: was 0%, target 80%+
- domain/notification: was 24%, target 80%+
- domain/session: was 54%, target 80%+
- domain/role: was 59%, target 80%+
- application/approval: was 62%, target 80%+
- infrastructure/persistence: was 62%, target 80%+
- application/command: was 74%, target 80%+

**Step 4: If any package still < 80%, investigate and add targeted tests**

Check the JaCoCo HTML report for specific uncovered lines and add tests as needed.

**Step 5: Final commit (if needed)**

```bash
git add -A
git commit -m "test: final coverage adjustments to reach 80%+ per package"
```
