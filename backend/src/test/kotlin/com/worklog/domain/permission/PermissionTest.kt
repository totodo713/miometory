package com.worklog.domain.permission

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
            assertFailsWith<IllegalArgumentException> { Permission.create("nodot", "desc") }
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
        assertTrue(str.contains("admin.access"), "toString should contain name: $str")
    }
}
