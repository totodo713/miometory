package com.worklog.benchmark

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Performance benchmark tests for Miometry application.
 *
 * Tests verify:
 * - SC-006: Calendar view loads within 1 second for 30 entries
 * - SC-007: System supports 100 concurrent users without degradation
 * - SC-008: Time entry operations complete quickly (API portion of 2min mobile target)
 *
 * These tests are tagged with "performance" and excluded from the default test suite
 * to avoid flakiness in CI. Run with: ./gradlew test -PincludeTags=performance
 */
@Tag("performance")
class PerformanceBenchmarkTest : IntegrationTestBase() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    /**
     * SC-006: Calendar view loads within 1 second for 30 entries.
     */
    @Test
    fun `SC-006 calendar view loads within 1 second for 30 entries`() {
        val memberId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val year = 2026
        val month = 1

        // Create 30 entries for the month
        repeat(30) { i ->
            val date = LocalDate.of(year, month, (i % 28) + 1)
            createEntry(memberId, projectId, date, 8.0, "Entry $i")
        }

        // Warm-up request
        restTemplate.getForEntity(
            "/api/v1/worklog/calendar/$year/$month?memberId=$memberId",
            Map::class.java,
        )

        // Measure: Execute 10 requests and calculate average
        val times = mutableListOf<Long>()
        repeat(10) {
            val startTime = System.currentTimeMillis()
            val response =
                restTemplate.getForEntity(
                    "/api/v1/worklog/calendar/$year/$month?memberId=$memberId",
                    Map::class.java,
                )
            val elapsed = System.currentTimeMillis() - startTime
            times.add(elapsed)
            assertEquals(HttpStatus.OK, response.statusCode)
        }

        val avgTime = times.average()
        val maxTime = times.maxOrNull() ?: 0L
        val p95Time = times.sorted()[8]

        println("SC-006 Results:")
        println("  Average response time: ${avgTime}ms")
        println("  Max response time: ${maxTime}ms")
        println("  P95 response time: ${p95Time}ms")

        // Assert: All requests should complete in under 1 second
        assertTrue(maxTime < 1000, "Calendar load exceeded 1 second: ${maxTime}ms")
    }

    /**
     * SC-007: System supports 100 concurrent users without performance degradation.
     */
    @Test
    fun `SC-007 system supports 100 concurrent users`() {
        val concurrentUsers = 100
        val requestsPerUser = 3

        // Create a member for each user
        val memberIds = (0 until concurrentUsers).map { UUID.randomUUID() }

        val executor = Executors.newFixedThreadPool(concurrentUsers)
        val latch = CountDownLatch(concurrentUsers)
        val successCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        val totalTime = AtomicLong(0)

        val startTime = System.currentTimeMillis()

        // Launch concurrent users
        memberIds.forEachIndexed { _, memberId ->
            executor.submit {
                try {
                    repeat(requestsPerUser) {
                        val requestStart = System.currentTimeMillis()
                        try {
                            val response =
                                restTemplate.getForEntity(
                                    "/api/v1/worklog/calendar/2026/1?memberId=$memberId",
                                    Map::class.java,
                                )
                            if (response.statusCode == HttpStatus.OK) {
                                successCount.incrementAndGet()
                            } else {
                                errorCount.incrementAndGet()
                            }
                        } catch (e: Exception) {
                            errorCount.incrementAndGet()
                        }
                        totalTime.addAndGet(System.currentTimeMillis() - requestStart)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        // Wait for all users to complete
        val completed = latch.await(60, TimeUnit.SECONDS)
        val totalElapsed = System.currentTimeMillis() - startTime
        executor.shutdown()

        val totalRequests = concurrentUsers * requestsPerUser
        val successRate = (successCount.get().toDouble() / totalRequests) * 100
        val avgResponseTime = if (totalRequests > 0) totalTime.get().toDouble() / totalRequests else 0.0

        println("SC-007 Results:")
        println("  Concurrent users: $concurrentUsers")
        println("  Total requests: $totalRequests")
        println("  Successful: ${successCount.get()}")
        println("  Errors: ${errorCount.get()}")
        println("  Success rate: ${"%.2f".format(successRate)}%")
        println("  Average response time: ${"%.2f".format(avgResponseTime)}ms")
        println("  Total test duration: ${totalElapsed}ms")

        // Assert
        assertTrue(completed, "Test did not complete within timeout")
        assertTrue(successRate >= 95.0, "Success rate below 95%: $successRate%")
    }

    /**
     * SC-008: Mobile time entry API operations complete quickly.
     */
    @Test
    fun `SC-008 time entry API operations complete quickly`() {
        val memberId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val operations = mutableListOf<Pair<String, Long>>()

        // Operation 1: Create a work log entry
        var startTime = System.currentTimeMillis()
        val createResponse = createEntry(memberId, projectId, LocalDate.of(2026, 1, 15), 8.0, "Test")
        operations.add("Create entry" to (System.currentTimeMillis() - startTime))
        assertEquals(HttpStatus.CREATED, createResponse.statusCode)

        val entryId = (createResponse.body as Map<*, *>)["id"] as String

        // Operation 2: Read the entry
        startTime = System.currentTimeMillis()
        val readResponse =
            restTemplate.getForEntity(
                "/api/v1/worklog/entries/$entryId",
                Map::class.java,
            )
        operations.add("Read entry" to (System.currentTimeMillis() - startTime))
        assertEquals(HttpStatus.OK, readResponse.statusCode)

        // Operation 3: Load calendar
        startTime = System.currentTimeMillis()
        val calendarResponse =
            restTemplate.getForEntity(
                "/api/v1/worklog/calendar/2026/1?memberId=$memberId",
                Map::class.java,
            )
        operations.add("Load calendar" to (System.currentTimeMillis() - startTime))
        assertEquals(HttpStatus.OK, calendarResponse.statusCode)

        val totalApiTime = operations.sumOf { it.second }

        println("SC-008 Results (API portion of 2-minute mobile target):")
        operations.forEach { (op, time) ->
            println("  $op: ${time}ms")
        }
        println("  Total API time: ${totalApiTime}ms")
        println("  Remaining for UI (of 120s target): ${120000 - totalApiTime}ms")

        // Assert: API operations should complete in under 2 seconds total
        assertTrue(totalApiTime < 2000, "API operations too slow: ${totalApiTime}ms")
        operations.forEach { (op, time) ->
            assertTrue(time < 1000, "$op too slow: ${time}ms")
        }
    }

    private fun createEntry(memberId: UUID, projectId: UUID, date: LocalDate, hours: Double, comment: String) =
        restTemplate.postForEntity(
            "/api/v1/worklog/entries",
            mapOf(
                "memberId" to memberId.toString(),
                "projectId" to projectId.toString(),
                "date" to date.toString(),
                "hours" to hours,
                "comment" to comment,
            ),
            Map::class.java,
        )
}
