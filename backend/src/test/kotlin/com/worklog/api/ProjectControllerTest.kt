package com.worklog.api

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for ProjectController.
 *
 * Tests the "Copy from Previous Month" feature (FR-016).
 * Task: T152 - Integration test for copy previous month
 */
class ProjectControllerTest : IntegrationTestBase() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    fun `GET previous-month should return empty list when no previous entries exist`() {
        // Arrange
        val memberId = UUID.randomUUID()
        val year = 2026
        val month = 2

        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/projects/previous-month?year=$year&month=$month&memberId=$memberId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertEquals(0, body["count"])
        assertTrue((body["projectIds"] as List<*>).isEmpty())
        assertNotNull(body["previousMonthStart"])
        assertNotNull(body["previousMonthEnd"])
    }

    @Test
    fun `GET previous-month should return unique projects from previous fiscal month`() {
        // Arrange - Create entries in the previous fiscal month
        val memberId = UUID.randomUUID()
        val projectId1 = UUID.randomUUID()
        val projectId2 = UUID.randomUUID()

        // Current month: February 2026, so previous fiscal month is Dec 21, 2025 - Jan 20, 2026
        // Create entries in Jan 2026 (within previous fiscal month)
        val entryDate1 = LocalDate.of(2026, 1, 10)
        val entryDate2 = LocalDate.of(2026, 1, 15)

        createWorkLogEntry(memberId, projectId1, entryDate1, 4.0, "Entry 1")
        createWorkLogEntry(memberId, projectId2, entryDate2, 4.0, "Entry 2")
        createWorkLogEntry(memberId, projectId1, entryDate2, 2.0, "Entry 3 - same project")

        // Act — fiscal month for Feb 2026 is Jan 21 - Feb 20; previous is Dec 21 - Jan 20
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/projects/previous-month?year=2026&month=2&memberId=$memberId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertEquals(2, body["count"]) // Should have 2 unique projects
        val projectIds = body["projectIds"] as List<*>
        assertEquals(2, projectIds.size)
        assertTrue(
            projectIds.contains(projectId1.toString()) || projectIds.any {
                it.toString() == projectId1.toString()
            },
        )
        assertTrue(
            projectIds.contains(projectId2.toString()) || projectIds.any {
                it.toString() == projectId2.toString()
            },
        )
    }

    @Test
    fun `GET previous-month should not include deleted entries`() {
        // Arrange
        val memberId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val entryDate = LocalDate.of(2026, 1, 5) // Within Dec 21 - Jan 20 fiscal month
        createTestMember(memberId)
        createTestProject(projectId)

        // Create entry
        val createRequest =
            mapOf(
                "memberId" to memberId.toString(),
                "projectId" to projectId.toString(),
                "date" to entryDate.toString(),
                "hours" to 4.0,
                "comment" to "To be deleted",
                "enteredBy" to memberId.toString(),
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                createRequest,
                Map::class.java,
            )
        assertEquals(HttpStatus.CREATED, createResponse.statusCode)
        val entryId = (createResponse.body as Map<*, *>)["id"] as String

        // Delete the entry
        restTemplate.delete("/api/v1/worklog/entries/$entryId")

        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/projects/previous-month?year=2026&month=2&memberId=$memberId",
                Map::class.java,
            )

        // Assert - Deleted entry's project should not appear
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals(0, body["count"])
    }

    @Test
    fun `GET previous-month should return 400 for invalid month`() {
        // Arrange
        val memberId = UUID.randomUUID()

        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/projects/previous-month?year=2026&month=13&memberId=$memberId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `GET previous-month should return 400 for invalid year`() {
        // Arrange
        val memberId = UUID.randomUUID()

        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/projects/previous-month?year=1999&month=2&memberId=$memberId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `GET previous-month should not include entries from other members`() {
        // Arrange
        val memberId1 = UUID.randomUUID()
        val memberId2 = UUID.randomUUID()
        val projectId1 = UUID.randomUUID()
        val projectId2 = UUID.randomUUID()
        val entryDate = LocalDate.of(2026, 1, 8)
        createTestMember(memberId1)
        createTestMember(memberId2)
        createTestProject(projectId1)
        createTestProject(projectId2)

        // Create entry for member 1
        val request1 =
            mapOf(
                "memberId" to memberId1.toString(),
                "projectId" to projectId1.toString(),
                "date" to entryDate.toString(),
                "hours" to 4.0,
                "enteredBy" to memberId1.toString(),
            )
        restTemplate.postForEntity("/api/v1/worklog/entries", request1, Map::class.java)

        // Create entry for member 2
        val request2 =
            mapOf(
                "memberId" to memberId2.toString(),
                "projectId" to projectId2.toString(),
                "date" to entryDate.toString(),
                "hours" to 4.0,
                "enteredBy" to memberId2.toString(),
            )
        restTemplate.postForEntity("/api/v1/worklog/entries", request2, Map::class.java)

        // Act - Get projects for member 1 only
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/projects/previous-month?year=2026&month=2&memberId=$memberId1",
                Map::class.java,
            )

        // Assert - Should only have member 1's project
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals(1, body["count"])
        val projectIds = body["projectIds"] as List<*>
        assertTrue(projectIds.any { it.toString() == projectId1.toString() })
    }

    private fun createWorkLogEntry(memberId: UUID, projectId: UUID, date: LocalDate, hours: Double, comment: String) {
        createTestMember(memberId)
        createTestProject(projectId)
        val request =
            mapOf(
                "memberId" to memberId.toString(),
                "projectId" to projectId.toString(),
                "date" to date.toString(),
                "hours" to hours,
                "comment" to comment,
                "enteredBy" to memberId.toString(),
            )
        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                request,
                Map::class.java,
            )
        assertEquals(HttpStatus.CREATED, response.statusCode)
    }
}
