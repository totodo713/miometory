package com.worklog.eventsourcing

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for JdbcSnapshotStore.
 * 
 * Tests snapshot save/load operations and upsert behavior.
 */
class SnapshotStoreTest : IntegrationTestBase() {

    @Autowired
    private lateinit var snapshotStore: SnapshotStore

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        // Clean up snapshot store before each test
        jdbcTemplate.execute("DELETE FROM snapshot_store")
    }

    @Test
    fun `save should store snapshot and load should retrieve it`() {
        val aggregateId = UUID.randomUUID()
        val aggregateType = "TestAggregate"
        val version = 5L
        val state = """{"name": "Test", "status": "ACTIVE"}"""

        snapshotStore.save(aggregateId, aggregateType, version, state)

        val snapshot = snapshotStore.load(aggregateId)
        
        assertTrue(snapshot.isPresent)
        assertEquals(aggregateId, snapshot.get().aggregateId)
        assertEquals(aggregateType, snapshot.get().aggregateType)
        assertEquals(version, snapshot.get().version)
        assertTrue(snapshot.get().state.contains("Test"))
    }

    @Test
    fun `save should update existing snapshot (upsert)`() {
        val aggregateId = UUID.randomUUID()
        val aggregateType = "TestAggregate"

        // Save initial snapshot
        snapshotStore.save(aggregateId, aggregateType, 5L, """{"name": "Initial"}""")
        
        // Save updated snapshot (should overwrite)
        snapshotStore.save(aggregateId, aggregateType, 10L, """{"name": "Updated"}""")

        val snapshot = snapshotStore.load(aggregateId)
        
        assertTrue(snapshot.isPresent)
        assertEquals(10L, snapshot.get().version)
        assertTrue(snapshot.get().state.contains("Updated"))
        
        // Verify only one record exists
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM snapshot_store WHERE aggregate_id = ?",
            Long::class.java,
            aggregateId
        )
        assertEquals(1L, count)
    }

    @Test
    fun `load should return empty for non-existent aggregate`() {
        val nonExistentId = UUID.randomUUID()
        
        val snapshot = snapshotStore.load(nonExistentId)
        
        assertTrue(snapshot.isEmpty)
    }

    @Test
    fun `save should handle complex JSON state`() {
        val aggregateId = UUID.randomUUID()
        val aggregateType = "Organization"
        val complexState = """
            {
                "id": "${UUID.randomUUID()}",
                "name": "Tech Department",
                "children": [
                    {"id": "${UUID.randomUUID()}", "name": "Dev Team"},
                    {"id": "${UUID.randomUUID()}", "name": "QA Team"}
                ],
                "metadata": {
                    "createdAt": "2024-01-01T00:00:00Z",
                    "level": 2
                }
            }
        """.trimIndent()

        snapshotStore.save(aggregateId, aggregateType, 15L, complexState)

        val snapshot = snapshotStore.load(aggregateId)
        
        assertTrue(snapshot.isPresent)
        assertTrue(snapshot.get().state.contains("Tech Department"))
        assertTrue(snapshot.get().state.contains("Dev Team"))
    }

    @Test
    fun `multiple aggregates should have independent snapshots`() {
        val aggregateId1 = UUID.randomUUID()
        val aggregateId2 = UUID.randomUUID()

        snapshotStore.save(aggregateId1, "Type1", 5L, """{"name": "First"}""")
        snapshotStore.save(aggregateId2, "Type2", 10L, """{"name": "Second"}""")

        val snapshot1 = snapshotStore.load(aggregateId1)
        val snapshot2 = snapshotStore.load(aggregateId2)
        
        assertTrue(snapshot1.isPresent)
        assertTrue(snapshot2.isPresent)
        assertEquals(5L, snapshot1.get().version)
        assertEquals(10L, snapshot2.get().version)
        assertEquals("Type1", snapshot1.get().aggregateType)
        assertEquals("Type2", snapshot2.get().aggregateType)
    }
}
