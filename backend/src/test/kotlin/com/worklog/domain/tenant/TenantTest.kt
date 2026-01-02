package com.worklog.domain.tenant

import com.worklog.domain.shared.DomainException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for Tenant aggregate.
 * Tests domain logic without any infrastructure dependencies.
 */
class TenantTest {
    @Test
    fun `create should generate valid tenant with TenantCreated event`() {
        val tenant = Tenant.create("TEST_CODE", "Test Tenant")

        assertNotNull(tenant.id)
        assertEquals("TEST_CODE", tenant.code.value())
        assertEquals("Test Tenant", tenant.name)
        assertEquals(Tenant.Status.ACTIVE, tenant.status)
        assertTrue(tenant.isActive)

        val events = tenant.uncommittedEvents
        assertEquals(1, events.size)
        assertTrue(events[0] is TenantCreated)

        val event = events[0] as TenantCreated
        assertEquals(tenant.id.value(), event.aggregateId())
        assertEquals("TEST_CODE", event.code)
        assertEquals("Test Tenant", event.name)
    }

    @Test
    fun `create should trim whitespace from name`() {
        val tenant = Tenant.create("CODE", "  Test Tenant  ")

        assertEquals("Test Tenant", tenant.name)
    }

    @Test
    fun `create should fail with empty code`() {
        val exception =
            assertFailsWith<DomainException> {
                Tenant.create("", "Name")
            }
        assertEquals("CODE_REQUIRED", exception.errorCode)
    }

    @Test
    fun `create should fail with invalid code format`() {
        val exception =
            assertFailsWith<DomainException> {
                Tenant.create("invalid code!", "Name")
            }
        assertEquals("CODE_FORMAT", exception.errorCode)
    }

    @Test
    fun `create should fail with code too long`() {
        val longCode = "a".repeat(33)
        val exception =
            assertFailsWith<DomainException> {
                Tenant.create(longCode, "Name")
            }
        assertEquals("CODE_LENGTH", exception.errorCode)
    }

    @Test
    fun `create should fail with empty name`() {
        val exception =
            assertFailsWith<DomainException> {
                Tenant.create("CODE", "")
            }
        assertEquals("NAME_REQUIRED", exception.errorCode)
    }

    @Test
    fun `create should fail with name too long`() {
        val longName = "a".repeat(257)
        val exception =
            assertFailsWith<DomainException> {
                Tenant.create("CODE", longName)
            }
        assertEquals("NAME_TOO_LONG", exception.errorCode)
    }

    @Test
    fun `update should change name and emit TenantUpdated event`() {
        val tenant = Tenant.create("CODE", "Original Name")
        tenant.clearUncommittedEvents()

        tenant.update("New Name")

        assertEquals("New Name", tenant.name)

        val events = tenant.uncommittedEvents
        assertEquals(1, events.size)
        assertTrue(events[0] is TenantUpdated)

        val event = events[0] as TenantUpdated
        assertEquals("New Name", event.name)
    }

    @Test
    fun `update should fail with empty name`() {
        val tenant = Tenant.create("CODE", "Name")
        tenant.clearUncommittedEvents()

        val exception =
            assertFailsWith<DomainException> {
                tenant.update("")
            }
        assertEquals("NAME_REQUIRED", exception.errorCode)
    }

    @Test
    fun `update should fail when tenant is inactive`() {
        val tenant = Tenant.create("CODE", "Name")
        tenant.deactivate()
        tenant.clearUncommittedEvents()

        val exception =
            assertFailsWith<DomainException> {
                tenant.update("New Name")
            }
        assertEquals("TENANT_INACTIVE", exception.errorCode)
    }

    @Test
    fun `deactivate should change status and emit TenantDeactivated event`() {
        val tenant = Tenant.create("CODE", "Name")
        tenant.clearUncommittedEvents()

        tenant.deactivate("User requested")

        assertEquals(Tenant.Status.INACTIVE, tenant.status)
        assertTrue(!tenant.isActive)

        val events = tenant.uncommittedEvents
        assertEquals(1, events.size)
        assertTrue(events[0] is TenantDeactivated)

        val event = events[0] as TenantDeactivated
        assertEquals("User requested", event.reason)
    }

    @Test
    fun `deactivate should work without reason`() {
        val tenant = Tenant.create("CODE", "Name")
        tenant.clearUncommittedEvents()

        tenant.deactivate()

        assertEquals(Tenant.Status.INACTIVE, tenant.status)
    }

    @Test
    fun `deactivate should fail when already inactive`() {
        val tenant = Tenant.create("CODE", "Name")
        tenant.deactivate()
        tenant.clearUncommittedEvents()

        val exception =
            assertFailsWith<DomainException> {
                tenant.deactivate()
            }
        assertEquals("TENANT_ALREADY_INACTIVE", exception.errorCode)
    }

    @Test
    fun `activate should change status and emit TenantActivated event`() {
        val tenant = Tenant.create("CODE", "Name")
        tenant.deactivate()
        tenant.clearUncommittedEvents()

        tenant.activate()

        assertEquals(Tenant.Status.ACTIVE, tenant.status)
        assertTrue(tenant.isActive)

        val events = tenant.uncommittedEvents
        assertEquals(1, events.size)
        assertTrue(events[0] is TenantActivated)
    }

    @Test
    fun `activate should fail when already active`() {
        val tenant = Tenant.create("CODE", "Name")
        tenant.clearUncommittedEvents()

        val exception =
            assertFailsWith<DomainException> {
                tenant.activate()
            }
        assertEquals("TENANT_ALREADY_ACTIVE", exception.errorCode)
    }

    @Test
    fun `tenant should support full lifecycle`() {
        // Create
        val tenant = Tenant.create("LIFECYCLE", "Lifecycle Test")
        assertTrue(tenant.isActive)

        // Update
        tenant.update("Updated Name")
        assertEquals("Updated Name", tenant.name)

        // Deactivate
        tenant.deactivate("Closing business")
        assertTrue(!tenant.isActive)

        // Reactivate
        tenant.activate()
        assertTrue(tenant.isActive)

        // Update again
        tenant.update("Reopened Business")
        assertEquals("Reopened Business", tenant.name)

        // Total events: Created, Updated, Deactivated, Activated, Updated
        assertEquals(5, tenant.uncommittedEvents.size)
    }
}
