package com.worklog.infrastructure.persistence

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Integration tests for RoleRepository (T008).
 *
 * Verifies that Role entities are correctly hydrated from the database
 * after the @PersistenceCreator fix, with all fields populated and
 * domain invariants preserved.
 */
class RoleRepositoryTest : IntegrationTestBase() {

    @Autowired
    private lateinit var roleRepository: RoleRepository

    // ============================================================
    // T008: Role loaded from database has all fields correctly populated
    // ============================================================

    @Test
    fun `findByName USER returns Role with all fields populated and uppercase name`() {
        // When - load the seeded USER role from the database
        val roleOpt = roleRepository.findByName("USER")

        // Then - Role should be present with all fields correctly hydrated
        assertTrue(roleOpt.isPresent, "USER role should exist in database (seeded by Flyway)")

        val role = roleOpt.get()
        assertNotNull(role.id, "Role ID should not be null")
        assertNotNull(role.id.value(), "Role ID value should not be null")
        assertEquals("USER", role.name, "Role name should be uppercase")
        assertNotNull(role.description, "Role description should not be null for seeded data")
        assertNotNull(role.createdAt, "createdAt should not be null")
        assertNotNull(role.updatedAt, "updatedAt should not be null")
    }
}
