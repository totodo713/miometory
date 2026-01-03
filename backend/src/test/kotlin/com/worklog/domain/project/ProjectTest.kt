package com.worklog.domain.project

import com.worklog.domain.tenant.TenantId
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for Project aggregate.
 */
class ProjectTest {
    private val tenantId = TenantId(UUID.randomUUID())
    
    @Test
    fun `create should generate valid project`() {
        val project = Project.create(
            tenantId,
            "PROJ-001",
            "Test Project",
            null,
            null
        )
        
        assertNotNull(project.id)
        assertEquals(tenantId, project.tenantId)
        assertEquals("PROJ-001", project.code)
        assertEquals("Test Project", project.name)
        assertTrue(project.isActive)
        assertNull(project.validFrom)
        assertNull(project.validUntil)
    }
    
    @Test
    fun `create with validity period should set dates`() {
        val validFrom = LocalDate.of(2025, 1, 1)
        val validUntil = LocalDate.of(2025, 12, 31)
        
        val project = Project.create(
            tenantId,
            "PROJ-001",
            "Test Project",
            validFrom,
            validUntil
        )
        
        assertEquals(validFrom, project.validFrom)
        assertEquals(validUntil, project.validUntil)
    }
    
    @Test
    fun `setValidityPeriod should update dates`() {
        val project = Project.create(
            tenantId,
            "PROJ-001",
            "Test Project",
            null,
            null
        )
        
        val validFrom = LocalDate.of(2025, 1, 1)
        val validUntil = LocalDate.of(2025, 12, 31)
        
        project.setValidityPeriod(validFrom, validUntil)
        
        assertEquals(validFrom, project.validFrom)
        assertEquals(validUntil, project.validUntil)
    }
    
    @Test
    fun `setValidityPeriod should fail when validFrom is after validUntil`() {
        val project = Project.create(
            tenantId,
            "PROJ-001",
            "Test Project",
            null,
            null
        )
        
        val validFrom = LocalDate.of(2025, 12, 31)
        val validUntil = LocalDate.of(2025, 1, 1)
        
        val exception = assertFailsWith<IllegalArgumentException> {
            project.setValidityPeriod(validFrom, validUntil)
        }
        
        assertTrue(exception.message!!.contains("validFrom date"))
        assertTrue(exception.message!!.contains("cannot be after validUntil date"))
    }
    
    @Test
    fun `create should fail when validFrom is after validUntil`() {
        val validFrom = LocalDate.of(2025, 12, 31)
        val validUntil = LocalDate.of(2025, 1, 1)
        
        assertFailsWith<IllegalArgumentException> {
            Project.create(
                tenantId,
                "PROJ-001",
                "Test Project",
                validFrom,
                validUntil
            )
        }
    }
    
    @Test
    fun `isValidOn should return true when no validity period set`() {
        val project = Project.create(
            tenantId,
            "PROJ-001",
            "Test Project",
            null,
            null
        )
        
        assertTrue(project.isValidOn(LocalDate.of(2020, 1, 1)))
        assertTrue(project.isValidOn(LocalDate.of(2025, 6, 15)))
        assertTrue(project.isValidOn(LocalDate.of(2030, 12, 31)))
    }
    
    @Test
    fun `isValidOn should return true for date within validity period`() {
        val project = Project.create(
            tenantId,
            "PROJ-001",
            "Test Project",
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 12, 31)
        )
        
        assertTrue(project.isValidOn(LocalDate.of(2025, 1, 1)))  // Start boundary
        assertTrue(project.isValidOn(LocalDate.of(2025, 6, 15)))  // Middle
        assertTrue(project.isValidOn(LocalDate.of(2025, 12, 31)))  // End boundary
    }
    
    @Test
    fun `isValidOn should return false for date before validFrom`() {
        val project = Project.create(
            tenantId,
            "PROJ-001",
            "Test Project",
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 12, 31)
        )
        
        assertFalse(project.isValidOn(LocalDate.of(2024, 12, 31)))
        assertFalse(project.isValidOn(LocalDate.of(2024, 1, 1)))
    }
    
    @Test
    fun `isValidOn should return false for date after validUntil`() {
        val project = Project.create(
            tenantId,
            "PROJ-001",
            "Test Project",
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 12, 31)
        )
        
        assertFalse(project.isValidOn(LocalDate.of(2026, 1, 1)))
        assertFalse(project.isValidOn(LocalDate.of(2026, 12, 31)))
    }
    
    @Test
    fun `isValidOn should return true when only validFrom is set and date is after`() {
        val project = Project.create(
            tenantId,
            "PROJ-001",
            "Test Project",
            LocalDate.of(2025, 1, 1),
            null
        )
        
        assertTrue(project.isValidOn(LocalDate.of(2025, 1, 1)))
        assertTrue(project.isValidOn(LocalDate.of(2025, 6, 15)))
        assertTrue(project.isValidOn(LocalDate.of(2030, 12, 31)))
    }
    
    @Test
    fun `isValidOn should return false when only validFrom is set and date is before`() {
        val project = Project.create(
            tenantId,
            "PROJ-001",
            "Test Project",
            LocalDate.of(2025, 1, 1),
            null
        )
        
        assertFalse(project.isValidOn(LocalDate.of(2024, 12, 31)))
    }
    
    @Test
    fun `isValidOn should return true when only validUntil is set and date is before`() {
        val project = Project.create(
            tenantId,
            "PROJ-001",
            "Test Project",
            null,
            LocalDate.of(2025, 12, 31)
        )
        
        assertTrue(project.isValidOn(LocalDate.of(2020, 1, 1)))
        assertTrue(project.isValidOn(LocalDate.of(2025, 6, 15)))
        assertTrue(project.isValidOn(LocalDate.of(2025, 12, 31)))
    }
    
    @Test
    fun `isValidOn should return false when only validUntil is set and date is after`() {
        val project = Project.create(
            tenantId,
            "PROJ-001",
            "Test Project",
            null,
            LocalDate.of(2025, 12, 31)
        )
        
        assertFalse(project.isValidOn(LocalDate.of(2026, 1, 1)))
    }
    
    @Test
    fun `isActiveOn should return true when active and valid`() {
        val project = Project.create(
            tenantId,
            "PROJ-001",
            "Test Project",
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 12, 31)
        )
        
        assertTrue(project.isActiveOn(LocalDate.of(2025, 6, 15)))
    }
    
    @Test
    fun `isActiveOn should return false when inactive but valid`() {
        val project = Project.create(
            tenantId,
            "PROJ-001",
            "Test Project",
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 12, 31)
        )
        project.deactivate()
        
        assertFalse(project.isActiveOn(LocalDate.of(2025, 6, 15)))
    }
    
    @Test
    fun `isActiveOn should return false when active but not valid`() {
        val project = Project.create(
            tenantId,
            "PROJ-001",
            "Test Project",
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 12, 31)
        )
        
        assertFalse(project.isActiveOn(LocalDate.of(2024, 6, 15)))  // Before validFrom
        assertFalse(project.isActiveOn(LocalDate.of(2026, 6, 15)))  // After validUntil
    }
    
    @Test
    fun `activate should set isActive to true`() {
        val project = Project.create(
            tenantId,
            "PROJ-001",
            "Test Project",
            null,
            null
        )
        project.deactivate()
        
        project.activate()
        
        assertTrue(project.isActive)
    }
    
    @Test
    fun `deactivate should set isActive to false`() {
        val project = Project.create(
            tenantId,
            "PROJ-001",
            "Test Project",
            null,
            null
        )
        
        project.deactivate()
        
        assertFalse(project.isActive)
    }
    
    @Test
    fun `update should change code and name`() {
        val project = Project.create(
            tenantId,
            "OLD-001",
            "Old Name",
            null,
            null
        )
        
        project.update("NEW-001", "New Name")
        
        assertEquals("NEW-001", project.code)
        assertEquals("New Name", project.name)
    }
    
    @Test
    fun `constructor should fail with null id`() {
        assertFailsWith<NullPointerException> {
            Project(
                null,
                tenantId,
                "PROJ-001",
                "Test Project",
                true,
                null,
                null,
                Instant.now()
            )
        }
    }
    
    @Test
    fun `constructor should fail with null tenantId`() {
        assertFailsWith<NullPointerException> {
            Project(
                ProjectId.generate(),
                null,
                "PROJ-001",
                "Test Project",
                true,
                null,
                null,
                Instant.now()
            )
        }
    }
    
    @Test
    fun `constructor should fail with null code`() {
        assertFailsWith<NullPointerException> {
            Project(
                ProjectId.generate(),
                tenantId,
                null,
                "Test Project",
                true,
                null,
                null,
                Instant.now()
            )
        }
    }
    
    @Test
    fun `constructor should fail with blank code`() {
        assertFailsWith<IllegalArgumentException> {
            Project(
                ProjectId.generate(),
                tenantId,
                "   ",
                "Test Project",
                true,
                null,
                null,
                Instant.now()
            )
        }
    }
    
    @Test
    fun `constructor should fail with code exceeding 50 characters`() {
        val longCode = "A".repeat(51)
        
        val exception = assertFailsWith<IllegalArgumentException> {
            Project(
                ProjectId.generate(),
                tenantId,
                longCode,
                "Test Project",
                true,
                null,
                null,
                Instant.now()
            )
        }
        
        assertTrue(exception.message!!.contains("cannot exceed 50 characters"))
    }
    
    @Test
    fun `constructor should fail with null name`() {
        assertFailsWith<NullPointerException> {
            Project(
                ProjectId.generate(),
                tenantId,
                "PROJ-001",
                null,
                true,
                null,
                null,
                Instant.now()
            )
        }
    }
    
    @Test
    fun `constructor should fail with blank name`() {
        assertFailsWith<IllegalArgumentException> {
            Project(
                ProjectId.generate(),
                tenantId,
                "PROJ-001",
                "   ",
                true,
                null,
                null,
                Instant.now()
            )
        }
    }
    
    @Test
    fun `constructor should fail with name exceeding 200 characters`() {
        val longName = "A".repeat(201)
        
        val exception = assertFailsWith<IllegalArgumentException> {
            Project(
                ProjectId.generate(),
                tenantId,
                "PROJ-001",
                longName,
                true,
                null,
                null,
                Instant.now()
            )
        }
        
        assertTrue(exception.message!!.contains("cannot exceed 200 characters"))
    }
    
    @Test
    fun `equals should return true for same id`() {
        val id = ProjectId.generate()
        val project1 = Project(
            id,
            tenantId,
            "PROJ-001",
            "Project 1",
            true,
            null,
            null,
            Instant.now()
        )
        val project2 = Project(
            id,
            tenantId,
            "PROJ-002",
            "Project 2",
            false,
            null,
            null,
            Instant.now()
        )
        
        assertEquals(project1, project2)
    }
    
    @Test
    fun `hashCode should be based on id`() {
        val id = ProjectId.generate()
        val project1 = Project(
            id,
            tenantId,
            "PROJ-001",
            "Project 1",
            true,
            null,
            null,
            Instant.now()
        )
        val project2 = Project(
            id,
            tenantId,
            "PROJ-002",
            "Project 2",
            false,
            null,
            null,
            Instant.now()
        )
        
        assertEquals(project1.hashCode(), project2.hashCode())
    }
}
