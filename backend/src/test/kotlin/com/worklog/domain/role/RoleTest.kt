package com.worklog.domain.role

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
            val r1 = Role(id, "ADMIN", "desc1", now)
            val r2 = Role(id, "USER", "desc2", now)
            assertEquals(r1, r2)
            assertEquals(r1.hashCode(), r2.hashCode())
        }

        @Test
        fun `different id should not be equal`() {
            val now = Instant.now()
            assertNotEquals(
                Role(RoleId.generate(), "ADMIN", "d", now),
                Role(RoleId.generate(), "ADMIN", "d", now),
            )
        }
    }

    @Test
    fun `toString should contain name`() {
        val role = Role.create("viewer", "View only")
        assertTrue(role.toString().contains("VIEWER"))
    }
}
