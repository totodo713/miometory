package com.worklog.eventsourcing

import com.worklog.IntegrationTestBase
import com.worklog.domain.shared.DomainEvent
import com.worklog.domain.shared.OptimisticLockException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Integration tests for JdbcEventStore.
 *
 * Tests event append/load operations and optimistic locking behavior.
 */
class JdbcEventStoreTest : IntegrationTestBase() {
    @Autowired
    private lateinit var eventStore: EventStore

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        // Clean up event store before each test
        jdbcTemplate.execute("DELETE FROM event_store")
    }

    @Test
    fun `append should store events and load should retrieve them`() {
        val aggregateId = UUID.randomUUID()
        val aggregateType = "TestAggregate"
        val events =
            listOf(
                TestEvent(UUID.randomUUID(), "TestCreated", Instant.now(), aggregateId, "Test Name"),
            )

        eventStore.append(aggregateId, aggregateType, events, 0)

        val storedEvents = eventStore.load(aggregateId)

        assertEquals(1, storedEvents.size)
        assertEquals(aggregateType, storedEvents[0].aggregateType)
        assertEquals(aggregateId, storedEvents[0].aggregateId)
        assertEquals("TestCreated", storedEvents[0].eventType)
        assertEquals(1L, storedEvents[0].version)
        assertTrue(storedEvents[0].payload.contains("Test Name"))
    }

    @Test
    fun `append multiple events should increment versions`() {
        val aggregateId = UUID.randomUUID()
        val aggregateType = "TestAggregate"
        val events =
            listOf(
                TestEvent(UUID.randomUUID(), "TestCreated", Instant.now(), aggregateId, "Created"),
                TestEvent(UUID.randomUUID(), "TestUpdated", Instant.now(), aggregateId, "Updated"),
                TestEvent(UUID.randomUUID(), "TestUpdated", Instant.now(), aggregateId, "Updated Again"),
            )

        eventStore.append(aggregateId, aggregateType, events, 0)

        val storedEvents = eventStore.load(aggregateId)

        assertEquals(3, storedEvents.size)
        assertEquals(1L, storedEvents[0].version)
        assertEquals(2L, storedEvents[1].version)
        assertEquals(3L, storedEvents[2].version)
    }

    @Test
    fun `append should throw OptimisticLockException when version mismatch`() {
        val aggregateId = UUID.randomUUID()
        val aggregateType = "TestAggregate"
        val events =
            listOf(
                TestEvent(UUID.randomUUID(), "TestCreated", Instant.now(), aggregateId, "Created"),
            )

        // First append succeeds
        eventStore.append(aggregateId, aggregateType, events, 0)

        // Second append with wrong expected version should fail
        val moreEvents =
            listOf(
                TestEvent(UUID.randomUUID(), "TestUpdated", Instant.now(), aggregateId, "Updated"),
            )

        val exception =
            assertFailsWith<OptimisticLockException> {
                eventStore.append(aggregateId, aggregateType, moreEvents, 0) // Wrong! Should be 1
            }

        assertEquals(aggregateType, exception.aggregateType)
        assertEquals(aggregateId.toString(), exception.aggregateId)
        assertEquals(0L, exception.expectedVersion)
        assertEquals(1L, exception.actualVersion)
    }

    @Test
    fun `append with correct expected version should succeed`() {
        val aggregateId = UUID.randomUUID()
        val aggregateType = "TestAggregate"

        // First append
        eventStore.append(
            aggregateId,
            aggregateType,
            listOf(
                TestEvent(UUID.randomUUID(), "TestCreated", Instant.now(), aggregateId, "Created"),
            ),
            0,
        )

        // Second append with correct expected version
        eventStore.append(
            aggregateId,
            aggregateType,
            listOf(
                TestEvent(UUID.randomUUID(), "TestUpdated", Instant.now(), aggregateId, "Updated"),
            ),
            1,
        ) // Correct expected version

        val storedEvents = eventStore.load(aggregateId)
        assertEquals(2, storedEvents.size)
    }

    @Test
    fun `loadFromVersion should return events starting from specified version`() {
        val aggregateId = UUID.randomUUID()
        val aggregateType = "TestAggregate"
        val events =
            listOf(
                TestEvent(UUID.randomUUID(), "Event1", Instant.now(), aggregateId, "First"),
                TestEvent(UUID.randomUUID(), "Event2", Instant.now(), aggregateId, "Second"),
                TestEvent(UUID.randomUUID(), "Event3", Instant.now(), aggregateId, "Third"),
            )

        eventStore.append(aggregateId, aggregateType, events, 0)

        val eventsFromVersion2 = eventStore.loadFromVersion(aggregateId, 2)

        assertEquals(2, eventsFromVersion2.size)
        assertEquals(2L, eventsFromVersion2[0].version)
        assertEquals(3L, eventsFromVersion2[1].version)
    }

    @Test
    fun `getCurrentVersion should return 0 for non-existent aggregate`() {
        val nonExistentId = UUID.randomUUID()

        val version = eventStore.getCurrentVersion(nonExistentId)

        assertEquals(0L, version)
    }

    @Test
    fun `getCurrentVersion should return latest version for existing aggregate`() {
        val aggregateId = UUID.randomUUID()
        val aggregateType = "TestAggregate"
        val events =
            listOf(
                TestEvent(UUID.randomUUID(), "Event1", Instant.now(), aggregateId, "First"),
                TestEvent(UUID.randomUUID(), "Event2", Instant.now(), aggregateId, "Second"),
            )

        eventStore.append(aggregateId, aggregateType, events, 0)

        val version = eventStore.getCurrentVersion(aggregateId)

        assertEquals(2L, version)
    }

    @Test
    fun `load should return empty list for non-existent aggregate`() {
        val nonExistentId = UUID.randomUUID()

        val events = eventStore.load(nonExistentId)

        assertTrue(events.isEmpty())
    }

    /**
     * Test event implementation for testing purposes.
     */
    private data class TestEvent(
        private val id: UUID,
        private val type: String,
        private val occurred: Instant,
        private val aggregate: UUID,
        val data: String,
    ) : DomainEvent {
        override fun eventId(): UUID = id

        override fun eventType(): String = type

        override fun occurredAt(): Instant = occurred

        override fun aggregateId(): UUID = aggregate
    }
}
