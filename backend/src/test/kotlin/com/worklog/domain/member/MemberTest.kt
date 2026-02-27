package com.worklog.domain.member

import com.worklog.domain.organization.OrganizationId
import com.worklog.domain.tenant.TenantId
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for Member aggregate.
 */
class MemberTest {
    private val tenantId = TenantId(UUID.randomUUID())
    private val organizationId = OrganizationId(UUID.randomUUID())

    @Test
    fun `create should generate valid member`() {
        val member =
            Member.create(
                tenantId,
                organizationId,
                "user@example.com",
                "John Doe",
                null,
            )

        assertNotNull(member.id)
        assertEquals(tenantId, member.tenantId)
        assertEquals(organizationId, member.organizationId)
        assertEquals("user@example.com", member.email)
        assertEquals("John Doe", member.displayName)
        assertTrue(member.isActive)
        assertNull(member.managerId)
        assertFalse(member.hasManager())
    }

    @Test
    fun `create with manager should set managerId`() {
        val managerId = MemberId.generate()
        val member =
            Member.create(
                tenantId,
                organizationId,
                "user@example.com",
                "John Doe",
                managerId,
            )

        assertEquals(managerId, member.managerId)
        assertTrue(member.hasManager())
        assertTrue(member.isManagedBy(managerId))
    }

    @Test
    fun `assignManager should set manager and update timestamp`() {
        val member =
            Member.create(
                tenantId,
                organizationId,
                "user@example.com",
                "John Doe",
                null,
            )
        val createdAt = member.createdAt

        Thread.sleep(10) // Ensure time passes

        val managerId = MemberId.generate()
        member.assignManager(managerId)

        assertEquals(managerId, member.managerId)
        assertTrue(member.hasManager())
        assertTrue(member.isManagedBy(managerId))
        assertTrue(member.updatedAt.isAfter(createdAt))
    }

    @Test
    fun `removeManager should clear managerId`() {
        val managerId = MemberId.generate()
        val member =
            Member.create(
                tenantId,
                organizationId,
                "user@example.com",
                "John Doe",
                managerId,
            )

        member.removeManager()

        assertNull(member.managerId)
        assertFalse(member.hasManager())
        assertFalse(member.isManagedBy(managerId))
    }

    @Test
    fun `isManagedBy should return false for different manager`() {
        val managerId = MemberId.generate()
        val otherManagerId = MemberId.generate()

        val member =
            Member.create(
                tenantId,
                organizationId,
                "user@example.com",
                "John Doe",
                managerId,
            )

        assertFalse(member.isManagedBy(otherManagerId))
    }

    @Test
    fun `isManagedBy should return false when no manager`() {
        val member =
            Member.create(
                tenantId,
                organizationId,
                "user@example.com",
                "John Doe",
                null,
            )

        assertFalse(member.isManagedBy(MemberId.generate()))
    }

    @Test
    fun `activate should set isActive to true`() {
        val member =
            Member.create(
                tenantId,
                organizationId,
                "user@example.com",
                "John Doe",
                null,
            )
        member.deactivate()

        member.activate()

        assertTrue(member.isActive)
    }

    @Test
    fun `deactivate should set isActive to false`() {
        val member =
            Member.create(
                tenantId,
                organizationId,
                "user@example.com",
                "John Doe",
                null,
            )

        member.deactivate()

        assertFalse(member.isActive)
    }

    @Test
    fun `update should change email and displayName`() {
        val member =
            Member.create(
                tenantId,
                organizationId,
                "old@example.com",
                "Old Name",
                null,
            )

        member.update("new@example.com", "New Name", null)

        assertEquals("new@example.com", member.email)
        assertEquals("New Name", member.displayName)
    }

    @Test
    fun `update should change manager`() {
        val oldManagerId = MemberId.generate()
        val newManagerId = MemberId.generate()

        val member =
            Member.create(
                tenantId,
                organizationId,
                "user@example.com",
                "John Doe",
                oldManagerId,
            )

        member.update("user@example.com", "John Doe", newManagerId)

        assertEquals(newManagerId, member.managerId)
    }

    @Test
    fun `constructor should fail with null id`() {
        assertFailsWith<NullPointerException> {
            Member(
                null,
                tenantId,
                organizationId,
                "user@example.com",
                "John Doe",
                null,
                true,
                Instant.now(),
            )
        }
    }

    @Test
    fun `constructor should fail with null tenantId`() {
        assertFailsWith<NullPointerException> {
            Member(
                MemberId.generate(),
                null,
                organizationId,
                "user@example.com",
                "John Doe",
                null,
                true,
                Instant.now(),
            )
        }
    }

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

    @Test
    fun `constructor should fail with null email`() {
        assertFailsWith<NullPointerException> {
            Member(
                MemberId.generate(),
                tenantId,
                organizationId,
                null,
                "John Doe",
                null,
                true,
                Instant.now(),
            )
        }
    }

    @Test
    fun `constructor should fail with blank email`() {
        assertFailsWith<IllegalArgumentException> {
            Member(
                MemberId.generate(),
                tenantId,
                organizationId,
                "   ",
                "John Doe",
                null,
                true,
                Instant.now(),
            )
        }
    }

    @Test
    fun `constructor should fail with invalid email format`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                Member(
                    MemberId.generate(),
                    tenantId,
                    organizationId,
                    "not-an-email",
                    "John Doe",
                    null,
                    true,
                    Instant.now(),
                )
            }
        assertEquals("Invalid email format", exception.message)
    }

    @Test
    fun `constructor should fail with null displayName`() {
        assertFailsWith<NullPointerException> {
            Member(
                MemberId.generate(),
                tenantId,
                organizationId,
                "user@example.com",
                null,
                null,
                true,
                Instant.now(),
            )
        }
    }

    @Test
    fun `equals should return true for same id`() {
        val id = MemberId.generate()
        val member1 =
            Member(
                id,
                tenantId,
                organizationId,
                "user1@example.com",
                "User 1",
                null,
                true,
                Instant.now(),
            )
        val member2 =
            Member(
                id,
                tenantId,
                organizationId,
                "user2@example.com",
                "User 2",
                null,
                false,
                Instant.now(),
            )

        assertEquals(member1, member2)
    }

    @Test
    fun `hashCode should be based on id`() {
        val id = MemberId.generate()
        val member1 =
            Member(
                id,
                tenantId,
                organizationId,
                "user1@example.com",
                "User 1",
                null,
                true,
                Instant.now(),
            )
        val member2 =
            Member(
                id,
                tenantId,
                organizationId,
                "user2@example.com",
                "User 2",
                null,
                false,
                Instant.now(),
            )

        assertEquals(member1.hashCode(), member2.hashCode())
    }
}
