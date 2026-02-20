package com.worklog.infrastructure

import com.worklog.IntegrationTestBase
import com.worklog.infrastructure.repository.JdbcDailyRejectionLogRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for JdbcDailyRejectionLogRepository (T042).
 *
 * Verifies:
 * - Save and retrieval of daily rejection log entries
 * - UPSERT semantics on (member_id, work_date) unique constraint
 * - Date range filtering for queries
 * - Empty result handling
 *
 * Uses Testcontainers PostgreSQL via IntegrationTestBase for real database interaction.
 */
@DisplayName("JdbcDailyRejectionLogRepository")
class JdbcDailyRejectionLogRepositoryTest : IntegrationTestBase() {

    @Autowired
    lateinit var repository: JdbcDailyRejectionLogRepository

    private lateinit var memberId: UUID

    @BeforeEach
    fun setUp() {
        memberId = UUID.randomUUID()
        createTestMember(memberId, "test-$memberId@example.com")
    }

    @Test
    @DisplayName("should save and retrieve daily rejection log")
    fun `should save and retrieve daily rejection log`() {
        val workDate = LocalDate.of(2026, 1, 15)
        val rejectedBy = UUID.randomUUID()
        val entryIds = setOf(UUID.randomUUID(), UUID.randomUUID())

        repository.save(memberId, workDate, rejectedBy, "Wrong hours", entryIds)

        val results = repository.findByMemberIdAndDateRange(memberId, workDate, workDate)
        assertEquals(1, results.size)
        assertEquals(workDate, results[0].workDate())
        assertEquals("Wrong hours", results[0].rejectionReason())
        assertEquals(rejectedBy, results[0].rejectedBy())
        assertEquals(entryIds, results[0].affectedEntryIds())
    }

    @Test
    @DisplayName("should upsert on same member and date")
    fun `should upsert on same member and date`() {
        val workDate = LocalDate.of(2026, 1, 16)
        val rejectedBy1 = UUID.randomUUID()
        val rejectedBy2 = UUID.randomUUID()
        val entryIds1 = setOf(UUID.randomUUID())
        val entryIds2 = setOf(UUID.randomUUID(), UUID.randomUUID())

        // First save
        repository.save(memberId, workDate, rejectedBy1, "First reason", entryIds1)

        // Second save on same member + date (should upsert, not insert new row)
        repository.save(memberId, workDate, rejectedBy2, "Updated reason", entryIds2)

        val results = repository.findByMemberIdAndDateRange(memberId, workDate, workDate)
        assertEquals(1, results.size, "Should have only one record after upsert")
        assertEquals("Updated reason", results[0].rejectionReason())
        assertEquals(rejectedBy2, results[0].rejectedBy())
        assertEquals(entryIds2, results[0].affectedEntryIds())
    }

    @Test
    @DisplayName("should filter by date range")
    fun `should filter by date range`() {
        val date1 = LocalDate.of(2026, 2, 1)
        val date2 = LocalDate.of(2026, 2, 10)
        val date3 = LocalDate.of(2026, 2, 20)
        val rejectedBy = UUID.randomUUID()

        // Save rejections on 3 different dates
        repository.save(memberId, date1, rejectedBy, "Reason for Feb 1", setOf(UUID.randomUUID()))
        repository.save(memberId, date2, rejectedBy, "Reason for Feb 10", setOf(UUID.randomUUID()))
        repository.save(memberId, date3, rejectedBy, "Reason for Feb 20", setOf(UUID.randomUUID()))

        // Query range that includes only the first 2 dates
        val results = repository.findByMemberIdAndDateRange(memberId, date1, date2)
        assertEquals(2, results.size, "Should return only 2 rejections within the date range")
        assertEquals(date1, results[0].workDate())
        assertEquals(date2, results[1].workDate())
    }

    @Test
    @DisplayName("should return empty list when no rejections exist")
    fun `should return empty list when no rejections exist`() {
        val startDate = LocalDate.of(2026, 3, 1)
        val endDate = LocalDate.of(2026, 3, 31)

        val results = repository.findByMemberIdAndDateRange(memberId, startDate, endDate)
        assertTrue(results.isEmpty(), "Should return empty list when no rejections exist for the range")
    }
}
