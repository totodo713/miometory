package com.worklog.domain.organization

import com.worklog.domain.shared.Code
import com.worklog.domain.shared.DomainException
import com.worklog.domain.tenant.TenantId
import com.worklog.fixtures.OrganizationFixtures.Companion.createOrganization
import com.worklog.fixtures.OrganizationFixtures.Companion.randomCode
import com.worklog.fixtures.OrganizationFixtures.Companion.randomOrganizationId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * Domain unit tests for Organization aggregate.
 * Tests business rules and invariants according to FR-002, FR-003, FR-004, FR-006.
 */
class OrganizationTest {
    @Test
    fun `create should return new Organization with level 1 when parentId is null`() {
        // Arrange
        val id = randomOrganizationId()
        val tenantId = TenantId(UUID.randomUUID())
        val code = randomCode("headquarters")
        val name = "Headquarters"

        // Act
        val organization = Organization.create(id, tenantId, null, code, name)

        // Assert
        assertNotNull(organization)
        assertEquals(id, organization.id)
        assertEquals(tenantId, organization.tenantId)
        assertNull(organization.parentId)
        assertEquals(code, organization.code)
        assertEquals(name, organization.name)
        assertEquals(1, organization.level)
        assertTrue(organization.isActive)
        assertEquals(1, organization.uncommittedEvents.size)
        assertTrue(organization.uncommittedEvents[0] is OrganizationCreated)

        val event = organization.uncommittedEvents[0] as OrganizationCreated
        assertEquals(id.value, event.aggregateId)
        assertEquals(tenantId.value, event.tenantId)
        assertNull(event.parentId)
        assertEquals(code.value, event.code)
        assertEquals(name, event.name)
        assertEquals(1, event.level)
    }

    @Test
    fun `create should return new Organization with level 2 when parentId is provided`() {
        // Arrange
        val id = randomOrganizationId()
        val tenantId = TenantId(UUID.randomUUID())
        val parentId = randomOrganizationId()
        val code = randomCode("DIV_A")
        val name = "Division A"

        // Act
        val organization = Organization.create(id, tenantId, parentId, code, name, 2)

        // Assert
        assertEquals(2, organization.level)
        assertEquals(parentId, organization.parentId)

        val event = organization.uncommittedEvents[0] as OrganizationCreated
        assertEquals(parentId.value, event.parentId)
        assertEquals(2, event.level)
    }

    @Test
    fun `create should throw exception when level is less than 1`() {
        // Arrange
        val id = randomOrganizationId()
        val tenantId = TenantId(UUID.randomUUID())
        val code = randomCode("invalid")
        val name = "Invalid Organization"

        // Act & Assert
        val exception =
            assertThrows<DomainException> {
                Organization.create(id, tenantId, null, code, name, 0)
            }
        assertTrue(exception.message!!.contains("level must be between 1 and 6"))
    }

    @Test
    fun `create should throw exception when level is greater than 6`() {
        // Arrange
        val id = randomOrganizationId()
        val tenantId = TenantId(UUID.randomUUID())
        val parentId = randomOrganizationId()
        val code = randomCode("invalid")
        val name = "Invalid Organization"

        // Act & Assert
        val exception =
            assertThrows<DomainException> {
                Organization.create(id, tenantId, parentId, code, name, 7)
            }
        assertTrue(exception.message!!.contains("level must be between 1 and 6"))
    }

    @Test
    fun `create should throw exception when parentId is provided but level is 1`() {
        // Arrange
        val id = randomOrganizationId()
        val tenantId = TenantId(UUID.randomUUID())
        val parentId = randomOrganizationId()
        val code = randomCode("invalid")
        val name = "Invalid Organization"

        // Act & Assert
        val exception =
            assertThrows<DomainException> {
                Organization.create(id, tenantId, parentId, code, name, 1)
            }
        assertTrue(exception.message!!.contains("Root organization") && exception.message!!.contains("cannot have a parent"))
    }

    @Test
    fun `create should throw exception when parentId is null but level is greater than 1`() {
        // Arrange
        val id = randomOrganizationId()
        val tenantId = TenantId(UUID.randomUUID())
        val code = randomCode("invalid")
        val name = "Invalid Organization"

        // Act & Assert
        val exception =
            assertThrows<DomainException> {
                Organization.create(id, tenantId, null, code, name, 2)
            }
        assertTrue(exception.message!!.contains("Non-root organization must have a parent"))
    }

    @Test
    fun `create should throw exception when code is empty`() {
        // Arrange
        val id = randomOrganizationId()
        val tenantId = TenantId(UUID.randomUUID())

        // Act & Assert
        assertThrows<DomainException> {
            Organization.create(id, tenantId, null, Code(""), "Test Organization")
        }
    }

    @Test
    fun `create should throw exception when name is empty`() {
        // Arrange
        val id = randomOrganizationId()
        val tenantId = TenantId(UUID.randomUUID())
        val code = randomCode("test")

        // Act & Assert
        val exception =
            assertThrows<DomainException> {
                Organization.create(id, tenantId, null, code, "")
            }
        assertTrue(exception.message!!.contains("Name cannot be empty"))
    }

    @Test
    fun `update should change name and emit OrganizationUpdated event`() {
        // Arrange
        val organization = createOrganization()
        organization.clearUncommittedEvents()
        val newName = "Updated Organization Name"

        // Act
        organization.update(newName)

        // Assert
        assertEquals(newName, organization.name)
        assertEquals(1, organization.uncommittedEvents.size)
        assertTrue(organization.uncommittedEvents[0] is OrganizationUpdated)

        val event = organization.uncommittedEvents[0] as OrganizationUpdated
        assertEquals(organization.id.value, event.aggregateId)
        assertEquals(newName, event.name)
    }

    @Test
    fun `update should throw exception when name is empty`() {
        // Arrange
        val organization = createOrganization()

        // Act & Assert
        val exception =
            assertThrows<DomainException> {
                organization.update("")
            }
        assertTrue(exception.message!!.contains("Name cannot be empty"))
    }

    @Test
    fun `deactivate should set isActive to false and emit OrganizationDeactivated event`() {
        // Arrange
        val organization = createOrganization()
        organization.clearUncommittedEvents()

        // Act
        organization.deactivate()

        // Assert
        assertFalse(organization.isActive)
        assertEquals(1, organization.uncommittedEvents.size)
        assertTrue(organization.uncommittedEvents[0] is OrganizationDeactivated)

        val event = organization.uncommittedEvents[0] as OrganizationDeactivated
        assertEquals(organization.id.value, event.aggregateId)
    }

    @Test
    fun `deactivate should throw exception when already inactive`() {
        // Arrange
        val organization = createOrganization()
        organization.deactivate()

        // Act & Assert
        val exception =
            assertThrows<DomainException> {
                organization.deactivate()
            }
        assertTrue(exception.message!!.contains("Organization is already inactive"))
    }

    @Test
    fun `activate should set isActive to true and emit OrganizationActivated event`() {
        // Arrange
        val organization = createOrganization()
        organization.deactivate()
        organization.clearUncommittedEvents()

        // Act
        organization.activate()

        // Assert
        assertTrue(organization.isActive)
        assertEquals(1, organization.uncommittedEvents.size)
        assertTrue(organization.uncommittedEvents[0] is OrganizationActivated)

        val event = organization.uncommittedEvents[0] as OrganizationActivated
        assertEquals(organization.id.value, event.aggregateId)
    }

    @Test
    fun `activate should throw exception when already active`() {
        // Arrange
        val organization = createOrganization()

        // Act & Assert
        val exception =
            assertThrows<DomainException> {
                organization.activate()
            }
        assertTrue(exception.message!!.contains("Organization is already active"))
    }

    @Test
    fun `assignPatterns should set fiscal year and monthly period pattern ids`() {
        // Arrange
        val organization = createOrganization()
        organization.clearUncommittedEvents()
        val fiscalYearPatternId = UUID.randomUUID()
        val monthlyPeriodPatternId = UUID.randomUUID()

        // Act
        organization.assignPatterns(fiscalYearPatternId, monthlyPeriodPatternId)

        // Assert
        assertEquals(fiscalYearPatternId, organization.fiscalYearPatternId)
        assertEquals(monthlyPeriodPatternId, organization.monthlyPeriodPatternId)
        assertEquals(1, organization.uncommittedEvents.size)
        assertTrue(organization.uncommittedEvents[0] is OrganizationPatternAssigned)

        val event = organization.uncommittedEvents[0] as OrganizationPatternAssigned
        assertEquals(organization.id.value, event.aggregateId)
        assertEquals(fiscalYearPatternId, event.fiscalYearPatternId)
        assertEquals(monthlyPeriodPatternId, event.monthlyPeriodPatternId)
    }

    @Test
    fun `assignPatterns should allow null patterns for non-root organizations`() {
        // Arrange
        val organization = createOrganization(parentId = randomOrganizationId(), level = 2)
        organization.clearUncommittedEvents()

        // Act
        organization.assignPatterns(null, null)

        // Assert
        assertNull(organization.fiscalYearPatternId)
        assertNull(organization.monthlyPeriodPatternId)
        assertEquals(1, organization.uncommittedEvents.size)
    }
}
