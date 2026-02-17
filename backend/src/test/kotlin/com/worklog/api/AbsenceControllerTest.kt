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
 * Integration tests for AbsenceController.
 *
 * Tests the full stack from HTTP API through to database persistence,
 * including critical cross-aggregate validation (work + absence <= 24h).
 *
 * Task: T097 - AbsenceController integration tests
 */
@Suppress("LargeClass") // Integration test — splitting would fragment related endpoint tests
class AbsenceControllerTest : IntegrationTestBase() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private val testMemberId = UUID.randomUUID()
    private val testProjectId = UUID.randomUUID()

    // ================================================================================
    // POST /api/v1/absences - Create absence tests
    // ================================================================================

    @Test
    fun `POST absences should create new absence and return 201`() {
        // Arrange
        val request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 8.0,
                "absenceType" to "PAID_LEAVE",
                "reason" to "Annual vacation",
                "recordedBy" to testMemberId.toString(),
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/absences",
                request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertNotNull(body["id"])
        assertEquals(testMemberId.toString(), body["memberId"])
        assertEquals(8.0, body["hours"])
        assertEquals("PAID_LEAVE", body["absenceType"])
        assertEquals("Annual vacation", body["reason"])
        assertEquals("DRAFT", body["status"])
        assertNotNull(body["version"])

        // Verify ETag header
        assertNotNull(response.headers[HttpHeaders.ETAG])
    }

    @Test
    fun `POST absences should use memberId as recordedBy if not specified`() {
        // Arrange - recordedBy omitted
        val request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 4.0,
                "absenceType" to "SICK_LEAVE",
                "reason" to "Doctor appointment",
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/absences",
                request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals(testMemberId.toString(), body["recordedBy"])
    }

    @Test
    fun `POST absences with null reason should succeed`() {
        // Arrange
        val request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 8.0,
                "absenceType" to "PAID_LEAVE",
                "reason" to null,
                "recordedBy" to testMemberId.toString(),
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/absences",
                request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals(null, body["reason"])
    }

    @Test
    fun `POST absences with all absence types should succeed`() {
        val absenceTypes = listOf("PAID_LEAVE", "SICK_LEAVE", "SPECIAL_LEAVE", "OTHER")

        absenceTypes.forEachIndexed { index, type ->
            val date = LocalDate.now().minusDays(index.toLong() + 1)
            val request =
                mapOf(
                    "memberId" to testMemberId.toString(),
                    "date" to date.toString(),
                    "hours" to 4.0,
                    "absenceType" to type,
                    "reason" to "Testing $type",
                )

            val response =
                restTemplate.postForEntity(
                    "/api/v1/absences",
                    request,
                    Map::class.java,
                )

            assertEquals(HttpStatus.CREATED, response.statusCode)
            val body = response.body as Map<*, *>
            assertEquals(type, body["absenceType"])
        }
    }

    @Test
    fun `POST absences with invalid hours should return 422`() {
        // Arrange - Invalid increment (0.33 is not a multiple of 0.25)
        val request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 0.33,
                "absenceType" to "PAID_LEAVE",
                "reason" to "Invalid hours",
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/absences",
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
    fun `POST absences with negative hours should return 422`() {
        // Arrange
        val request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to -1.0,
                "absenceType" to "SICK_LEAVE",
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/absences",
                request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
    }

    @Test
    fun `POST absences with hours exceeding 24 should return 422`() {
        // Arrange
        val request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 25.0,
                "absenceType" to "PAID_LEAVE",
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/absences",
                request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
    }

    @Test
    fun `POST absences with future date should return 422`() {
        // Arrange
        val request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to LocalDate.now().plusDays(1).toString(),
                "hours" to 8.0,
                "absenceType" to "PAID_LEAVE",
                "reason" to "Future absence",
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/absences",
                request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        val body = response.body as Map<*, *>
        assertTrue(body["errorCode"].toString().contains("FUTURE"))
    }

    @Test
    fun `POST absences with reason exceeding 500 chars should return 422`() {
        // Arrange
        val longReason = "x".repeat(501)
        val request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 8.0,
                "absenceType" to "PAID_LEAVE",
                "reason" to longReason,
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/absences",
                request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        val body = response.body as Map<*, *>
        assertTrue(body["errorCode"].toString().contains("REASON"))
    }

    @Test
    fun `POST absences with reason exactly 500 chars should succeed`() {
        // Arrange
        val maxReason = "x".repeat(500)
        val request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 8.0,
                "absenceType" to "PAID_LEAVE",
                "reason" to maxReason,
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/absences",
                request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals(maxReason, body["reason"])
    }

    @Test
    fun `POST absences with quarter-hour increments should succeed`() {
        val validIncrements = listOf(0.25, 0.5, 0.75, 1.0, 4.25, 8.5)

        validIncrements.forEachIndexed { index, hours ->
            val date = LocalDate.now().minusDays(index.toLong() + 10)
            val request =
                mapOf(
                    "memberId" to testMemberId.toString(),
                    "date" to date.toString(),
                    "hours" to hours,
                    "absenceType" to "PAID_LEAVE",
                    "reason" to "Testing $hours hours",
                )

            val response =
                restTemplate.postForEntity(
                    "/api/v1/absences",
                    request,
                    Map::class.java,
                )

            assertEquals(HttpStatus.CREATED, response.statusCode, "Failed for $hours hours")
        }
    }

    // ================================================================================
    // Cross-aggregate validation: Work + Absence <= 24 hours
    // CRITICAL BUSINESS RULE
    // ================================================================================

    @Test
    fun `POST absences should fail when work + absence exceeds 24 hours`() {
        // Arrange - Create work entry with 20 hours
        val testDate = LocalDate.now().minusDays(5)
        val workRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to testDate.toString(),
                "hours" to 20.0,
                "comment" to "Work entry",
            )
        restTemplate.postForEntity(
            "/api/v1/worklog/entries",
            workRequest,
            Map::class.java,
        )

        // Act - Try to add 8 hours of absence (total = 28h > 24h)
        val absenceRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to testDate.toString(),
                "hours" to 8.0,
                "absenceType" to "PAID_LEAVE",
                "reason" to "Should fail - exceeds limit",
            )
        val response =
            restTemplate.postForEntity(
                "/api/v1/absences",
                absenceRequest,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("DAILY_LIMIT_EXCEEDED", body["errorCode"])
        assertTrue(body["message"].toString().contains("24"))
        assertTrue(body["message"].toString().contains("work"))
        assertTrue(body["message"].toString().contains("absence"))
    }

    @Test
    fun `POST absences should succeed when work + absence equals exactly 24 hours`() {
        // Arrange - Create work entry with 16 hours
        val testDate = LocalDate.now().minusDays(6)
        val workRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to testDate.toString(),
                "hours" to 16.0,
                "comment" to "Work entry",
            )
        restTemplate.postForEntity(
            "/api/v1/worklog/entries",
            workRequest,
            Map::class.java,
        )

        // Act - Add 8 hours of absence (total = 24h, should succeed)
        val absenceRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to testDate.toString(),
                "hours" to 8.0,
                "absenceType" to "PAID_LEAVE",
                "reason" to "Should succeed - exactly 24h",
            )
        val response =
            restTemplate.postForEntity(
                "/api/v1/absences",
                absenceRequest,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun `POST absences should succeed when work + absence is less than 24 hours`() {
        // Arrange - Create work entry with 4 hours
        val testDate = LocalDate.now().minusDays(7)
        val workRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to testDate.toString(),
                "hours" to 4.0,
                "comment" to "Work entry",
            )
        restTemplate.postForEntity(
            "/api/v1/worklog/entries",
            workRequest,
            Map::class.java,
        )

        // Act - Add 8 hours of absence (total = 12h, well under limit)
        val absenceRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to testDate.toString(),
                "hours" to 8.0,
                "absenceType" to "PAID_LEAVE",
                "reason" to "Should succeed",
            )
        val response =
            restTemplate.postForEntity(
                "/api/v1/absences",
                absenceRequest,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun `POST absences should aggregate multiple work entries when checking limit`() {
        // Arrange - Create 3 work entries totaling 20 hours
        val testDate = LocalDate.now().minusDays(8)
        val projects = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        val hours = listOf(8.0, 7.0, 5.0) // Total: 20h

        projects.forEachIndexed { index, projectId ->
            val workRequest =
                mapOf(
                    "memberId" to testMemberId.toString(),
                    "projectId" to projectId.toString(),
                    "date" to testDate.toString(),
                    "hours" to hours[index],
                    "comment" to "Project ${index + 1}",
                )
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                workRequest,
                Map::class.java,
            )
        }

        // Act - Try to add 8 hours of absence (total = 28h, should fail)
        val absenceRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to testDate.toString(),
                "hours" to 8.0,
                "absenceType" to "PAID_LEAVE",
                "reason" to "Should fail",
            )
        val response =
            restTemplate.postForEntity(
                "/api/v1/absences",
                absenceRequest,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("DAILY_LIMIT_EXCEEDED", body["errorCode"])
    }

    @Test
    fun `POST absences should aggregate multiple absence entries when checking limit`() {
        // Arrange - Create 2 absence entries totaling 20 hours
        val testDate = LocalDate.now().minusDays(9)

        val absence1Request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to testDate.toString(),
                "hours" to 12.0,
                "absenceType" to "PAID_LEAVE",
                "reason" to "First absence",
            )
        restTemplate.postForEntity(
            "/api/v1/absences",
            absence1Request,
            Map::class.java,
        )

        val absence2Request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to testDate.toString(),
                "hours" to 8.0,
                "absenceType" to "SICK_LEAVE",
                "reason" to "Second absence",
            )
        restTemplate.postForEntity(
            "/api/v1/absences",
            absence2Request,
            Map::class.java,
        )

        // Act - Try to add 5 more hours (total = 25h, should fail)
        val absence3Request =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to testDate.toString(),
                "hours" to 5.0,
                "absenceType" to "OTHER",
                "reason" to "Third absence - should fail",
            )
        val response =
            restTemplate.postForEntity(
                "/api/v1/absences",
                absence3Request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("DAILY_LIMIT_EXCEEDED", body["errorCode"])
    }

    // ================================================================================
    // GET /api/v1/absences/{id} - Get single absence tests
    // ================================================================================

    @Test
    fun `GET absences by id should return absence details with ETag`() {
        // Arrange - Create absence first
        val createRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 7.5,
                "absenceType" to "SICK_LEAVE",
                "reason" to "Doctor appointment",
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/absences",
                createRequest,
                Map::class.java,
            )
        val absenceId = (createResponse.body as Map<*, *>)["id"] as String

        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/absences/$absenceId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertEquals(absenceId, body["id"])
        assertEquals(7.5, body["hours"])
        assertEquals("SICK_LEAVE", body["absenceType"])
        assertEquals("Doctor appointment", body["reason"])
        assertEquals("DRAFT", body["status"])

        // Verify ETag header
        assertNotNull(response.headers[HttpHeaders.ETAG])
    }

    @Test
    fun `GET non-existent absence should return 404`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/absences/00000000-0000-0000-0000-000000000000",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    // ================================================================================
    // GET /api/v1/absences - List absences with filters
    // ================================================================================

    @Test
    fun `GET absences should return filtered list by date range`() {
        // Arrange - Create multiple absences with different dates
        val yesterday = LocalDate.now().minusDays(1)
        val twoDaysAgo = LocalDate.now().minusDays(2)
        val memberId = UUID.randomUUID()

        // Absence 1 (yesterday)
        restTemplate.postForEntity(
            "/api/v1/absences",
            mapOf(
                "memberId" to memberId.toString(),
                "date" to yesterday.toString(),
                "hours" to 8.0,
                "absenceType" to "PAID_LEAVE",
                "reason" to "Absence 1",
            ),
            Map::class.java,
        )

        // Absence 2 (two days ago)
        restTemplate.postForEntity(
            "/api/v1/absences",
            mapOf(
                "memberId" to memberId.toString(),
                "date" to twoDaysAgo.toString(),
                "hours" to 4.0,
                "absenceType" to "SICK_LEAVE",
                "reason" to "Absence 2",
            ),
            Map::class.java,
        )

        // Act - Query for yesterday only
        val response =
            restTemplate.getForEntity(
                "/api/v1/absences?startDate=$yesterday&endDate=$yesterday&memberId=$memberId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>
        val absences = body["absences"] as List<*>
        assertEquals(1, absences.size)

        val absence = absences[0] as Map<*, *>
        assertEquals(yesterday.toString(), absence["date"])
        assertEquals(8.0, absence["hours"])
        assertEquals("PAID_LEAVE", absence["absenceType"])
    }

    @Test
    fun `GET absences should filter by status`() {
        // Arrange - Create absence and keep it in DRAFT
        val memberId = UUID.randomUUID()
        val date = LocalDate.now().minusDays(1)

        restTemplate.postForEntity(
            "/api/v1/absences",
            mapOf(
                "memberId" to memberId.toString(),
                "date" to date.toString(),
                "hours" to 6.0,
                "absenceType" to "PAID_LEAVE",
                "reason" to "Draft absence",
            ),
            Map::class.java,
        )

        // Act - Query with status filter
        val response =
            restTemplate.getForEntity(
                "/api/v1/absences?startDate=$date&endDate=$date&memberId=$memberId&status=DRAFT",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>
        val absences = body["absences"] as List<*>
        assertTrue(absences.isNotEmpty())

        // All absences should be DRAFT
        absences.forEach { absence ->
            val absenceMap = absence as Map<*, *>
            assertEquals("DRAFT", absenceMap["status"])
        }
    }

    @Test
    fun `GET absences without memberId should return 400`() {
        // Act - No memberId parameter
        val date = LocalDate.now()
        val response =
            restTemplate.getForEntity(
                "/api/v1/absences?startDate=$date&endDate=$date",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertTrue(body["errorCode"].toString().contains("MEMBER_ID_REQUIRED"))
    }

    @Test
    fun `GET absences should return empty list when no matches`() {
        // Act - Query for a member with no absences
        val nonExistentMember = UUID.randomUUID()
        val date = LocalDate.now()
        val response =
            restTemplate.getForEntity(
                "/api/v1/absences?startDate=$date&endDate=$date&memberId=$nonExistentMember",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as Map<*, *>
        val absences = body["absences"] as List<*>
        assertEquals(0, absences.size)
        assertEquals(0, body["total"])
    }

    // ================================================================================
    // PATCH /api/v1/absences/{id} - Update absence tests
    // ================================================================================

    @Test
    fun `PATCH absence should update hours, type, and reason`() {
        // Arrange - Create absence first
        val createRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 8.0,
                "absenceType" to "PAID_LEAVE",
                "reason" to "Original reason",
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/absences",
                createRequest,
                Map::class.java,
            )
        val absenceId = (createResponse.body as Map<*, *>)["id"] as String
        val version = ((createResponse.body as Map<*, *>)["version"] as Number).toLong()

        // Act - Update absence
        val updateRequest =
            mapOf(
                "hours" to 4.5,
                "absenceType" to "SICK_LEAVE",
                "reason" to "Updated reason",
            )

        val headers = HttpHeaders()
        headers.set(HttpHeaders.IF_MATCH, version.toString())
        val requestEntity = HttpEntity(updateRequest, headers)

        val response =
            restTemplate.exchange(
                "/api/v1/absences/$absenceId",
                HttpMethod.PATCH,
                requestEntity,
                Void::class.java,
            )

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        // Verify new ETag header
        assertNotNull(response.headers[HttpHeaders.ETAG])
        val newVersion = response.headers[HttpHeaders.ETAG]?.first()?.toLong()
        assertTrue(newVersion!! > version)

        // Verify changes
        val getResponse =
            restTemplate.getForEntity(
                "/api/v1/absences/$absenceId",
                Map::class.java,
            )
        val body = getResponse.body as Map<*, *>
        assertEquals(4.5, body["hours"])
        assertEquals("SICK_LEAVE", body["absenceType"])
        assertEquals("Updated reason", body["reason"])
    }

    @Test
    fun `PATCH absence without If-Match header should return 400`() {
        // Arrange - Create absence
        val createRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 8.0,
                "absenceType" to "PAID_LEAVE",
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/absences",
                createRequest,
                Map::class.java,
            )
        val absenceId = (createResponse.body as Map<*, *>)["id"] as String

        // Act - Update without If-Match header
        val updateRequest =
            mapOf(
                "hours" to 7.0,
                "reason" to "Should fail",
            )

        val requestEntity = HttpEntity(updateRequest)

        val response =
            restTemplate.exchange(
                "/api/v1/absences/$absenceId",
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
    fun `PATCH absence with stale version should return 409`() {
        // Arrange - Create absence and update it once
        val createRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 8.0,
                "absenceType" to "PAID_LEAVE",
                "reason" to "Original",
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/absences",
                createRequest,
                Map::class.java,
            )
        val absenceId = (createResponse.body as Map<*, *>)["id"] as String
        val version = ((createResponse.body as Map<*, *>)["version"] as Number).toLong()

        // First update
        val headers1 = HttpHeaders()
        headers1.set(HttpHeaders.IF_MATCH, version.toString())
        restTemplate.exchange(
            "/api/v1/absences/$absenceId",
            HttpMethod.PATCH,
            HttpEntity(mapOf("hours" to 7.0, "reason" to "First update"), headers1),
            Void::class.java,
        )

        // Act - Try to update with stale version
        val headers2 = HttpHeaders()
        headers2.set(HttpHeaders.IF_MATCH, version.toString()) // Using original version (stale)

        val response =
            restTemplate.exchange(
                "/api/v1/absences/$absenceId",
                HttpMethod.PATCH,
                HttpEntity(mapOf("hours" to 6.0, "reason" to "Should fail"), headers2),
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `PATCH absence should fail when update causes daily total to exceed 24h`() {
        // Arrange - Create work entry with 16 hours
        val testDate = LocalDate.now().minusDays(10)
        val workRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "projectId" to testProjectId.toString(),
                "date" to testDate.toString(),
                "hours" to 16.0,
                "comment" to "Work entry",
            )
        restTemplate.postForEntity(
            "/api/v1/worklog/entries",
            workRequest,
            Map::class.java,
        )

        // Create absence with 4 hours (total = 20h, OK)
        val absenceRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to testDate.toString(),
                "hours" to 4.0,
                "absenceType" to "PAID_LEAVE",
                "reason" to "Initial absence",
            )
        val absenceResponse =
            restTemplate.postForEntity(
                "/api/v1/absences",
                absenceRequest,
                Map::class.java,
            )
        val absenceId = (absenceResponse.body as Map<*, *>)["id"] as String
        val version = ((absenceResponse.body as Map<*, *>)["version"] as Number).toLong()

        // Act - Try to update absence from 4h to 12h (total would be 28h, should fail)
        val updateRequest =
            mapOf(
                "hours" to 12.0,
                "reason" to "Should fail - exceeds limit",
            )

        val headers = HttpHeaders()
        headers.set(HttpHeaders.IF_MATCH, version.toString())

        val updateResponse =
            restTemplate.exchange(
                "/api/v1/absences/$absenceId",
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
    fun `PATCH with invalid hours should return 422`() {
        // Arrange - Create absence
        val createRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 8.0,
                "absenceType" to "PAID_LEAVE",
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/absences",
                createRequest,
                Map::class.java,
            )
        val absenceId = (createResponse.body as Map<*, *>)["id"] as String
        val version = ((createResponse.body as Map<*, *>)["version"] as Number).toLong()

        // Act - Update with invalid hours
        val headers = HttpHeaders()
        headers.set(HttpHeaders.IF_MATCH, version.toString())
        val updateRequest =
            mapOf(
                "hours" to 0.33, // Invalid increment
            )

        val response =
            restTemplate.exchange(
                "/api/v1/absences/$absenceId",
                HttpMethod.PATCH,
                HttpEntity(updateRequest, headers),
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
    }

    // ================================================================================
    // DELETE /api/v1/absences/{id} - Delete absence tests
    // ================================================================================

    @Test
    fun `DELETE absence should remove it`() {
        // Arrange - Create absence first
        val createRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 5.0,
                "absenceType" to "PAID_LEAVE",
                "reason" to "To be deleted",
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/absences",
                createRequest,
                Map::class.java,
            )
        val absenceId = (createResponse.body as Map<*, *>)["id"] as String

        // Act - Delete absence
        val response =
            restTemplate.exchange(
                "/api/v1/absences/$absenceId",
                HttpMethod.DELETE,
                null,
                Void::class.java,
            )

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        // Verify absence no longer exists
        val getResponse =
            restTemplate.getForEntity(
                "/api/v1/absences/$absenceId",
                Map::class.java,
            )
        assertEquals(HttpStatus.NOT_FOUND, getResponse.statusCode)
    }

    @Test
    fun `DELETE non-existent absence should return error`() {
        // Act
        val response =
            restTemplate.exchange(
                "/api/v1/absences/00000000-0000-0000-0000-000000000000",
                HttpMethod.DELETE,
                null,
                Map::class.java,
            )

        // Assert - Service throws DomainException which becomes 400
        assertTrue(
            response.statusCode == HttpStatus.BAD_REQUEST ||
                response.statusCode == HttpStatus.NOT_FOUND,
        )
    }

    // ================================================================================
    // Lifecycle test - create, update, delete
    // ================================================================================

    @Test
    fun `Absence lifecycle - create, update, delete`() {
        // 1. Create
        val createRequest =
            mapOf(
                "memberId" to testMemberId.toString(),
                "date" to LocalDate.now().minusDays(1).toString(),
                "hours" to 8.0,
                "absenceType" to "PAID_LEAVE",
                "reason" to "Lifecycle test",
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/absences",
                createRequest,
                Map::class.java,
            )
        assertEquals(HttpStatus.CREATED, createResponse.statusCode)
        val absenceId = (createResponse.body as Map<*, *>)["id"] as String
        val version1 = ((createResponse.body as Map<*, *>)["version"] as Number).toLong()

        // 2. Update
        val headers = HttpHeaders()
        headers.set(HttpHeaders.IF_MATCH, version1.toString())
        val updateRequest = mapOf("hours" to 4.5, "reason" to "Updated")
        val updateResponse =
            restTemplate.exchange(
                "/api/v1/absences/$absenceId",
                HttpMethod.PATCH,
                HttpEntity(updateRequest, headers),
                Void::class.java,
            )
        assertEquals(HttpStatus.NO_CONTENT, updateResponse.statusCode)

        // 3. Verify update
        val getResponse =
            restTemplate.getForEntity(
                "/api/v1/absences/$absenceId",
                Map::class.java,
            )
        assertEquals(HttpStatus.OK, getResponse.statusCode)
        assertEquals(4.5, (getResponse.body as Map<*, *>)["hours"])
        assertEquals("Updated", (getResponse.body as Map<*, *>)["reason"])

        // 4. Delete
        val deleteResponse =
            restTemplate.exchange(
                "/api/v1/absences/$absenceId",
                HttpMethod.DELETE,
                null,
                Void::class.java,
            )
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.statusCode)

        // 5. Verify deletion
        val getAfterDeleteResponse =
            restTemplate.getForEntity(
                "/api/v1/absences/$absenceId",
                Map::class.java,
            )
        assertEquals(HttpStatus.NOT_FOUND, getAfterDeleteResponse.statusCode)
    }
}
