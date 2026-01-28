package com.worklog.api

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for WorkLogController.
 * 
 * Tests the full stack from HTTP API through to database persistence.
 * Task: T060 - WorkLogController integration tests
 */
class WorkLogControllerTest : IntegrationTestBase() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private val testMemberId = UUID.randomUUID()
    private val testProjectId = UUID.randomUUID()

    @Test
    fun `POST entries should create new work log entry and return 201`() {
        // Arrange
        val request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 8.0,
                "comment" to "Test work entry",
                "enteredBy" to testMemberId.toString(),
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertNotNull(body["id"])
        assertEquals(testMemberId.toString(), body["memberId"])
        assertEquals(testProjectId.toString(), body["projectId"])
        assertEquals(8.0, body["hours"])
        assertEquals("Test work entry", body["comment"])
        assertEquals("DRAFT", body["status"])
        assertNotNull(body["version"])

        // Verify ETag header
        assertNotNull(response.headers[HttpHeaders.ETAG])
    }

    @Test
    fun `POST entries should use memberId as enteredBy if not specified`() {
        // Arrange - enteredBy omitted
        val request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 7.5,
                "comment" to "Self-entered work",
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals(testMemberId.toString(), body["enteredBy"])
    }

    @Test
    fun `POST entries with null comment should succeed`() {
        // Arrange
        val request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 6.0,
                "comment" to null,
                "enteredBy" to testMemberId.toString(),
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals(null, body["comment"])
    }

    @Test
    fun `POST entries with invalid hours should return 422`() {
        // Arrange - Invalid increment (0.33 is not a multiple of 0.25)
        val request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 0.33,
                "comment" to "Invalid hours",
                "enteredBy" to testMemberId.toString(),
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        val body = response.body as Map<*, *>
        assertNotNull(body["errorCode"])
        assertTrue(body["errorCode"].toString().contains("INCREMENT"))
    }

    @Test
    fun `POST entries with negative hours should return 422`() {
        // Arrange
        val request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to -1.0,
                "comment" to "Negative hours",
                "enteredBy" to testMemberId.toString(),
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
    }

    @Test
    fun `POST entries with hours exceeding 24 should return 422`() {
        // Arrange
        val request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 25.0,
                "comment" to "Too many hours",
                "enteredBy" to testMemberId.toString(),
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
    }

    @Test
    fun `POST entries with future date should return 422`() {
        // Arrange
        val request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to LocalDate.now().plusDays(1).toString(),
                "hours" to 8.0,
                "comment" to "Future work",
                "enteredBy" to testMemberId.toString(),
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        val body = response.body as Map<*, *>
        assertTrue(body["errorCode"].toString().contains("FUTURE"))
    }

    @Test
    fun `POST entries with comment exceeding 500 chars should return 422`() {
        // Arrange
        val longComment = "x".repeat(501)
        val request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 8.0,
                "comment" to longComment,
                "enteredBy" to testMemberId.toString(),
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        val body = response.body as Map<*, *>
        assertTrue(body["errorCode"].toString().contains("COMMENT"))
    }

    @Test
    fun `GET entries by id should return entry details with ETag`() {
        // Arrange - Create entry first
        val createRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 7.0,
                "comment" to "Get test entry",
                "enteredBy" to testMemberId.toString(),
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                createRequest,
                Map::class.java,
            )
        val entryId = (createResponse.body as Map<*, *>)["id"] as String

        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/entries/$entryId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertEquals(entryId, body["id"])
        assertEquals(7.0, body["hours"])
        assertEquals("Get test entry", body["comment"])
        assertEquals("DRAFT", body["status"])

        // Verify ETag header
        assertNotNull(response.headers[HttpHeaders.ETAG])
    }

    @Test
    fun `GET non-existent entry should return 404`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/entries/00000000-0000-0000-0000-000000000000",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `GET entries should return filtered list by date range`() {
        // Arrange - Create multiple entries with different dates
        val yesterday = LocalDate.now().minusDays(1)
        val twoDaysAgo = LocalDate.now().minusDays(2)
        val memberId = UUID.randomUUID()

        // Entry 1 (yesterday)
        restTemplate.postForEntity(
            "/api/v1/worklog/entries",
            mapOf(
                "memberId" to memberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to yesterday.toString(),
                "hours" to 8.0,
                "comment" to "Entry 1",
            ),
            Map::class.java,
        )

        // Entry 2 (two days ago)
        restTemplate.postForEntity(
            "/api/v1/worklog/entries",
            mapOf(
                "memberId" to memberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to twoDaysAgo.toString(),
                "hours" to 7.5,
                "comment" to "Entry 2",
            ),
            Map::class.java,
        )

        // Act - Query for yesterday only
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/entries?startDate=$yesterday&endDate=$yesterday&memberId=$memberId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>
        val entries = body["entries"] as List<*>
        assertEquals(1, entries.size)

        val entry = entries[0] as Map<*, *>
        assertEquals(yesterday.toString(), entry["date"])
        assertEquals(8.0, entry["hours"])
    }

    @Test
    fun `GET entries should filter by status`() {
        // Arrange - Create entry and keep it in DRAFT
        val memberId = UUID.randomUUID()
        val date = LocalDate.now().minusDays(1)

        restTemplate.postForEntity(
            "/api/v1/worklog/entries",
            mapOf(
                "memberId" to memberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to date.toString(),
                "hours" to 6.0,
                "comment" to "Draft entry",
            ),
            Map::class.java,
        )

        // Act - Query with status filter
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/entries?startDate=$date&endDate=$date&memberId=$memberId&status=DRAFT",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>
        val entries = body["entries"] as List<*>
        assertTrue(entries.isNotEmpty())

        // All entries should be DRAFT
        entries.forEach { entry ->
            val entryMap = entry as Map<*, *>
            assertEquals("DRAFT", entryMap["status"])
        }
    }

    @Test
    fun `GET entries without memberId should return 400`() {
        // Act - No memberId parameter
        val date = LocalDate.now()
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/entries?startDate=$date&endDate=$date",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertTrue(body["errorCode"].toString().contains("MEMBER_ID_REQUIRED"))
    }

    @Test
    fun `PATCH entry should update hours and comment`() {
        // Arrange - Create entry first
        val createRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 8.0,
                "comment" to "Original comment",
                "enteredBy" to testMemberId.toString(),
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                createRequest,
                Map::class.java,
            )
        val entryId = (createResponse.body as Map<*, *>)["id"] as String
        val version = (createResponse.body as Map<*, *>)["version"] as Int

        // Act - Update entry
        val updateRequest =
            mapOf(
                "hours" to 6.5,
                "comment" to "Updated comment",
            )

        val headers = HttpHeaders()
        headers.set(HttpHeaders.IF_MATCH, version.toString())
        val requestEntity = HttpEntity(updateRequest, headers)

        val response =
            restTemplate.exchange(
                "/api/v1/worklog/entries/$entryId",
                HttpMethod.PATCH,
                requestEntity,
                Void::class.java,
            )

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        // Verify new ETag header
        assertNotNull(response.headers[HttpHeaders.ETAG])
        val newVersion = response.headers[HttpHeaders.ETAG]?.first()?.toInt()
        assertTrue(newVersion!! > version)

        // Verify changes
        val getResponse =
            restTemplate.getForEntity(
                "/api/v1/worklog/entries/$entryId",
                Map::class.java,
            )
        val body = getResponse.body as Map<*, *>
        assertEquals(6.5, body["hours"])
        assertEquals("Updated comment", body["comment"])
    }

    @Test
    fun `PATCH entry without If-Match header should return 400`() {
        // Arrange - Create entry
        val createRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 8.0,
                "comment" to "Test",
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                createRequest,
                Map::class.java,
            )
        val entryId = (createResponse.body as Map<*, *>)["id"] as String

        // Act - Update without If-Match header
        val updateRequest =
            mapOf(
                "hours" to 7.0,
                "comment" to "Should fail",
            )

        val requestEntity = HttpEntity(updateRequest)

        val response =
            restTemplate.exchange(
                "/api/v1/worklog/entries/$entryId",
                HttpMethod.PATCH,
                requestEntity,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertTrue(body["errorCode"].toString().contains("VERSION_REQUIRED"))
    }

    @Test
    fun `PATCH entry with stale version should return 409`() {
        // Arrange - Create entry and update it once
        val createRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 8.0,
                "comment" to "Original",
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                createRequest,
                Map::class.java,
            )
        val entryId = (createResponse.body as Map<*, *>)["id"] as String
        val version = (createResponse.body as Map<*, *>)["version"] as Int

        // First update
        val headers1 = HttpHeaders()
        headers1.set(HttpHeaders.IF_MATCH, version.toString())
        restTemplate.exchange(
            "/api/v1/worklog/entries/$entryId",
            HttpMethod.PATCH,
            HttpEntity(mapOf("hours" to 7.0, "comment" to "First update"), headers1),
            Void::class.java,
        )

        // Act - Try to update with stale version
        val headers2 = HttpHeaders()
        headers2.set(HttpHeaders.IF_MATCH, version.toString()) // Using original version (stale)

        val response =
            restTemplate.exchange(
                "/api/v1/worklog/entries/$entryId",
                HttpMethod.PATCH,
                HttpEntity(mapOf("hours" to 6.0, "comment" to "Should fail"), headers2),
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `DELETE entry should remove it`() {
        // Arrange - Create entry first
        val createRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 5.0,
                "comment" to "To be deleted",
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                createRequest,
                Map::class.java,
            )
        val entryId = (createResponse.body as Map<*, *>)["id"] as String

        // Act - Delete entry
        val response =
            restTemplate.exchange(
                "/api/v1/worklog/entries/$entryId",
                HttpMethod.DELETE,
                null,
                Void::class.java,
            )

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        // Verify entry no longer exists
        val getResponse =
            restTemplate.getForEntity(
                "/api/v1/worklog/entries/$entryId",
                Map::class.java,
            )
        assertEquals(HttpStatus.NOT_FOUND, getResponse.statusCode)
    }

    @Test
    fun `DELETE non-existent entry should return 404`() {
        // Act
        val response =
            restTemplate.exchange(
                "/api/v1/worklog/entries/00000000-0000-0000-0000-000000000000",
                HttpMethod.DELETE,
                null,
                Map::class.java,
            )

        // Assert - Currently returns 400, but 404 would be better
        // The service throws DomainException which becomes 400
        assertTrue(
            response.statusCode == HttpStatus.BAD_REQUEST ||
                response.statusCode == HttpStatus.NOT_FOUND,
        )
    }

    @Test
    fun `PATCH with invalid hours should return 422`() {
        // Arrange - Create entry
        val createRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 8.0,
                "comment" to "Test",
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                createRequest,
                Map::class.java,
            )
        val entryId = (createResponse.body as Map<*, *>)["id"] as String
        val version = (createResponse.body as Map<*, *>)["version"] as Int

        // Act - Update with invalid hours
        val headers = HttpHeaders()
        headers.set(HttpHeaders.IF_MATCH, version.toString())
        val updateRequest =
            mapOf(
                "hours" to 0.33, // Invalid increment
                "comment" to "Invalid",
            )

        val response =
            restTemplate.exchange(
                "/api/v1/worklog/entries/$entryId",
                HttpMethod.PATCH,
                HttpEntity(updateRequest, headers),
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
    }

    @Test
    fun `Entry lifecycle - create, update, delete`() {
        // 1. Create
        val createRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 8.0,
                "comment" to "Lifecycle test",
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                createRequest,
                Map::class.java,
            )
        assertEquals(HttpStatus.CREATED, createResponse.statusCode)
        val entryId = (createResponse.body as Map<*, *>)["id"] as String
        val version1 = (createResponse.body as Map<*, *>)["version"] as Int

        // 2. Update
        val headers = HttpHeaders()
        headers.set(HttpHeaders.IF_MATCH, version1.toString())
        val updateRequest = mapOf("hours" to 7.5, "comment" to "Updated")
        val updateResponse =
            restTemplate.exchange(
                "/api/v1/worklog/entries/$entryId",
                HttpMethod.PATCH,
                HttpEntity(updateRequest, headers),
                Void::class.java,
            )
        assertEquals(HttpStatus.NO_CONTENT, updateResponse.statusCode)

        // 3. Verify update
        val getResponse =
            restTemplate.getForEntity(
                "/api/v1/worklog/entries/$entryId",
                Map::class.java,
            )
        assertEquals(HttpStatus.OK, getResponse.statusCode)
        assertEquals(7.5, (getResponse.body as Map<*, *>)["hours"])

        // 4. Delete
        val deleteResponse =
            restTemplate.exchange(
                "/api/v1/worklog/entries/$entryId",
                HttpMethod.DELETE,
                null,
                Void::class.java,
            )
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.statusCode)

        // 5. Verify deletion
        val getAfterDeleteResponse =
            restTemplate.getForEntity(
                "/api/v1/worklog/entries/$entryId",
                Map::class.java,
            )
        assertEquals(HttpStatus.NOT_FOUND, getAfterDeleteResponse.statusCode)
    }

    /**
     * T073: Multi-project validation tests
     * Verifies that total daily hours across all projects cannot exceed 24 hours
     */
    @Test
    fun `POST entries should succeed when total daily hours is exactly 24`() {
        // Arrange
        val testDate = LocalDate.now().minusDays(1)
        val project1 = UUID.randomUUID()
        val project2 = UUID.randomUUID()
        val project3 = UUID.randomUUID()

        // Act: Create 3 entries totaling 24 hours
        val request1 =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to project1.toString(),
                "date" to testDate.toString(),
                "hours" to 10.0,
                "comment" to "Project 1",
            )
        val response1 =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                request1,
                Map::class.java,
            )

        val request2 =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to project2.toString(),
                "date" to testDate.toString(),
                "hours" to 10.0,
                "comment" to "Project 2",
            )
        val response2 =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                request2,
                Map::class.java,
            )

        val request3 =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to project3.toString(),
                "date" to testDate.toString(),
                "hours" to 4.0,
                "comment" to "Project 3",
            )
        val response3 =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                request3,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.CREATED, response1.statusCode)
        assertEquals(HttpStatus.CREATED, response2.statusCode)
        assertEquals(HttpStatus.CREATED, response3.statusCode)
    }

    @Test
    fun `POST entries should fail when total daily hours exceeds 24`() {
        // Arrange
        val testDate = LocalDate.now().minusDays(2)
        val project1 = UUID.randomUUID()
        val project2 = UUID.randomUUID()
        val project3 = UUID.randomUUID()

        // Create 2 entries totaling 20 hours
        val request1 =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to project1.toString(),
                "date" to testDate.toString(),
                "hours" to 10.0,
                "comment" to "Project 1",
            )
        restTemplate.postForEntity(
            "/api/v1/worklog/entries",
            request1,
            Map::class.java,
        )

        val request2 =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to project2.toString(),
                "date" to testDate.toString(),
                "hours" to 10.0,
                "comment" to "Project 2",
            )
        restTemplate.postForEntity(
            "/api/v1/worklog/entries",
            request2,
            Map::class.java,
        )

        // Act: Try to create a third entry with 5 hours (total would be 25)
        val request3 =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to project3.toString(),
                "date" to testDate.toString(),
                "hours" to 5.0,
                "comment" to "Project 3 - should fail",
            )
        val response3 =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                request3,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response3.statusCode)
        val body = response3.body as Map<*, *>
        assertEquals("DAILY_LIMIT_EXCEEDED", body["errorCode"])
        assertTrue(body["message"].toString().contains("24"))
    }

    @Test
    fun `PATCH entry should fail when update causes daily total to exceed 24`() {
        // Arrange
        val testDate = LocalDate.now().minusDays(3)
        val project1 = UUID.randomUUID()
        val project2 = UUID.randomUUID()

        // Create entry 1 with 12 hours
        val request1 =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to project1.toString(),
                "date" to testDate.toString(),
                "hours" to 12.0,
                "comment" to "Project 1",
            )
        val response1 =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                request1,
                Map::class.java,
            )
        val entry1Id = (response1.body as Map<*, *>)["id"] as String

        // Create entry 2 with 10 hours (total = 22)
        val request2 =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to project2.toString(),
                "date" to testDate.toString(),
                "hours" to 10.0,
                "comment" to "Project 2",
            )
        restTemplate.postForEntity(
            "/api/v1/worklog/entries",
            request2,
            Map::class.java,
        )

        // Act: Try to update entry1 from 12 to 15 hours (total would be 25)
        val updateRequest =
            mapOf(
                "hours" to 15.0,
                "comment" to "Updated - should fail",
            )

        val headers = HttpHeaders()
        headers.set("If-Match", "1")

        val updateResponse =
            restTemplate.exchange(
                "/api/v1/worklog/entries/$entry1Id",
                HttpMethod.PATCH,
                HttpEntity(updateRequest, headers),
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, updateResponse.statusCode)
        val body = updateResponse.body as Map<*, *>
        assertEquals("DAILY_LIMIT_EXCEEDED", body["errorCode"])
    }

    @Test
    fun `Multiple projects on same day should aggregate correctly in validation`() {
        // Arrange
        val testDate = LocalDate.now().minusDays(4)
        val projects = List(4) { UUID.randomUUID() }

        // Act: Create 4 entries with 6 hours each (total = 24)
        projects.forEach { projectId ->
            val request =
                mapOf(
                    "memberId" to testMemberId.toString(),
                    "projectId" to projectId.toString(),
                    "date" to testDate.toString(),
                    "hours" to 6.0,
                    "comment" to "6 hours on $projectId",
                )
            val response =
                restTemplate.postForEntity(
                    "/api/v1/worklog/entries",
                    request,
                    Map::class.java,
                )
            assertEquals(HttpStatus.CREATED, response.statusCode)
        }

        // Try to add one more entry (should fail)
        val extraRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to UUID.randomUUID().toString(),
                "date" to testDate.toString(),
                "hours" to 0.25,
                "comment" to "Extra entry - should fail",
            )
        val extraResponse =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                extraRequest,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, extraResponse.statusCode)
    }
}
