package com.worklog.api

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for ApprovalController.
 *
 * Tests the full approval workflow stack from HTTP API through to database persistence.
 * Task: T126 - Integration tests for approval workflow
 *
 * Note: Tests use @Transactional to ensure isolation between tests.
 */
class ApprovalControllerTest : IntegrationTestBase() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    /**
     * Helper: Get fiscal month period (21st to 20th)
     */
    private fun getFiscalMonthPeriod(date: LocalDate): Pair<LocalDate, LocalDate> {
        val dayOfMonth = date.dayOfMonth
        return if (dayOfMonth >= 21) {
            val start = date.withDayOfMonth(21)
            val end = date.plusMonths(1).withDayOfMonth(20)
            start to end
        } else {
            val start = date.minusMonths(1).withDayOfMonth(21)
            val end = date.withDayOfMonth(20)
            start to end
        }
    }

    /**
     * Helper: Create work log entry (ensures member exists first)
     */
    private fun createWorkLogEntry(memberId: UUID, projectId: UUID, date: LocalDate, hours: Double = 8.0): String {
        createTestMember(memberId)
        createTestProject(projectId)
        val request =
            mapOf(
                "memberId" to memberId.toString(),
                "projectId" to projectId.toString(),
                "date" to date.toString(),
                "hours" to hours,
                "comment" to "Test work",
            )

        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/entries",
                request,
                Map::class.java,
            )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        return (response.body as Map<*, *>)["id"] as String
    }

    // =========================================================================
    // POST /api/v1/worklog/submissions - Submit Month Tests
    // =========================================================================

    @Test
    @Transactional
    fun `POST submissions should create approval and transition entries to SUBMITTED`() {
        val memberId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val date = LocalDate.now().minusDays(10)
        val (fiscalStart, fiscalEnd) = getFiscalMonthPeriod(date)

        val entryId1 = createWorkLogEntry(memberId, projectId, fiscalStart.plusDays(1), 8.0)
        val entryId2 = createWorkLogEntry(memberId, projectId, fiscalStart.plusDays(2), 7.5)

        val submitRequest =
            mapOf(
                "memberId" to memberId.toString(),
                "fiscalMonthStart" to fiscalStart.toString(),
                "fiscalMonthEnd" to fiscalEnd.toString(),
                "submittedBy" to memberId.toString(),
            )

        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/submissions",
                submitRequest,
                Map::class.java,
            )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body)
        assertNotNull((response.body as Map<*, *>)["approvalId"])

        // Verify entries are SUBMITTED
        val entry1 = restTemplate.getForEntity("/api/v1/worklog/entries/$entryId1", Map::class.java)
        assertEquals("SUBMITTED", (entry1.body as Map<*, *>)["status"])

        val entry2 = restTemplate.getForEntity("/api/v1/worklog/entries/$entryId2", Map::class.java)
        assertEquals("SUBMITTED", (entry2.body as Map<*, *>)["status"])
    }

    @Test
    @Transactional
    fun `POST submissions should fail with missing memberId`() {
        val date = LocalDate.now().minusDays(15)
        val (fiscalStart, fiscalEnd) = getFiscalMonthPeriod(date)

        val submitRequest =
            mapOf(
                "fiscalMonthStart" to fiscalStart.toString(),
                "fiscalMonthEnd" to fiscalEnd.toString(),
            )

        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/submissions",
                submitRequest,
                Map::class.java,
            )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertTrue(body["errorCode"].toString().contains("MEMBER_ID_REQUIRED"))
    }

    @Test
    @Transactional
    fun `POST submissions should fail with missing fiscal month dates`() {
        val memberId = UUID.randomUUID()

        val submitRequest =
            mapOf(
                "memberId" to memberId.toString(),
            )

        val response =
            restTemplate.postForEntity(
                "/api/v1/worklog/submissions",
                submitRequest,
                Map::class.java,
            )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertTrue(body["errorCode"].toString().contains("FISCAL_MONTH_REQUIRED"))
    }

    @Test
    @Transactional
    fun `POST submissions should fail when submitting already submitted month`() {
        val memberId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val date = LocalDate.now().minusDays(20)
        val (fiscalStart, fiscalEnd) = getFiscalMonthPeriod(date)

        createWorkLogEntry(memberId, projectId, fiscalStart.plusDays(3), 8.0)

        val submitRequest =
            mapOf(
                "memberId" to memberId.toString(),
                "fiscalMonthStart" to fiscalStart.toString(),
                "fiscalMonthEnd" to fiscalEnd.toString(),
                "submittedBy" to memberId.toString(),
            )

        // First submission
        val response1 = restTemplate.postForEntity("/api/v1/worklog/submissions", submitRequest, Map::class.java)
        assertEquals(HttpStatus.CREATED, response1.statusCode)

        // Second submission (should fail)
        val response2 = restTemplate.postForEntity("/api/v1/worklog/submissions", submitRequest, Map::class.java)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response2.statusCode)
    }

    // =========================================================================
    // GET /api/v1/worklog/approvals/queue - Approval Queue Tests
    // =========================================================================

    @Test
    @Transactional
    fun `GET approvals queue should return pending submissions`() {
        val memberId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val managerId = UUID.randomUUID()
        val date = LocalDate.now().minusDays(25)
        val (fiscalStart, fiscalEnd) = getFiscalMonthPeriod(date)

        createWorkLogEntry(memberId, projectId, fiscalStart.plusDays(4), 8.0)

        val submitRequest =
            mapOf(
                "memberId" to memberId.toString(),
                "fiscalMonthStart" to fiscalStart.toString(),
                "fiscalMonthEnd" to fiscalEnd.toString(),
                "submittedBy" to memberId.toString(),
            )

        val submitResponse = restTemplate.postForEntity("/api/v1/worklog/submissions", submitRequest, Map::class.java)
        val approvalId = (submitResponse.body as Map<*, *>)["approvalId"] as String

        val queueResponse =
            restTemplate.getForEntity(
                "/api/v1/worklog/approvals/queue?managerId=$managerId",
                Map::class.java,
            )

        assertEquals(HttpStatus.OK, queueResponse.statusCode)
        val queueBody = queueResponse.body as Map<*, *>
        val pendingApprovals = queueBody["pendingApprovals"] as List<*>

        // Should find our approval
        val approval = pendingApprovals.find { (it as Map<*, *>)["approvalId"] == approvalId }
        assertNotNull(approval)
    }

    @Test
    @Transactional
    fun `GET approvals queue should fail without managerId parameter`() {
        val response =
            restTemplate.getForEntity(
                "/api/v1/worklog/approvals/queue",
                Map::class.java,
            )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertTrue(body["errorCode"].toString().contains("MANAGER_ID_REQUIRED"))
    }

    // =========================================================================
    // POST /api/v1/worklog/approvals/{id}/approve - Approve Month Tests
    // =========================================================================

    @Test
    @Transactional
    fun `POST approve should transition entries to APPROVED and make them read-only`() {
        val memberId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val managerId = UUID.randomUUID()
        val date = LocalDate.now().minusDays(30)
        val (fiscalStart, fiscalEnd) = getFiscalMonthPeriod(date)

        val entryId = createWorkLogEntry(memberId, projectId, fiscalStart.plusDays(5), 8.0)

        // Submit
        val submitRequest =
            mapOf(
                "memberId" to memberId.toString(),
                "fiscalMonthStart" to fiscalStart.toString(),
                "fiscalMonthEnd" to fiscalEnd.toString(),
                "submittedBy" to memberId.toString(),
            )
        val submitResponse = restTemplate.postForEntity("/api/v1/worklog/submissions", submitRequest, Map::class.java)
        val approvalId = (submitResponse.body as Map<*, *>)["approvalId"] as String

        // Approve
        val approveRequest = mapOf("reviewedBy" to managerId.toString())
        val approveResponse =
            restTemplate.postForEntity(
                "/api/v1/worklog/approvals/$approvalId/approve",
                approveRequest,
                Void::class.java,
            )

        assertEquals(HttpStatus.NO_CONTENT, approveResponse.statusCode)

        // Verify APPROVED status
        val entryResponse = restTemplate.getForEntity("/api/v1/worklog/entries/$entryId", Map::class.java)
        assertEquals("APPROVED", (entryResponse.body as Map<*, *>)["status"])

        // Verify read-only (cannot update)
        val version = (entryResponse.body as Map<*, *>)["version"]
        val updateRequest = mapOf("hours" to 7.0, "comment" to "Should fail")
        val headers = HttpHeaders().apply { set("If-Match", version.toString()) }

        val updateResponse =
            restTemplate.exchange(
                "/api/v1/worklog/entries/$entryId",
                HttpMethod.PATCH,
                HttpEntity(updateRequest, headers),
                Map::class.java,
            )

        assertTrue(
            updateResponse.statusCode == HttpStatus.BAD_REQUEST ||
                updateResponse.statusCode == HttpStatus.UNPROCESSABLE_ENTITY,
        )
    }

    @Test
    @Transactional
    fun `POST approve should fail with missing reviewedBy`() {
        val memberId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val date = LocalDate.now().minusDays(35)
        val (fiscalStart, fiscalEnd) = getFiscalMonthPeriod(date)

        createWorkLogEntry(memberId, projectId, fiscalStart.plusDays(6), 8.0)

        val submitRequest =
            mapOf(
                "memberId" to memberId.toString(),
                "fiscalMonthStart" to fiscalStart.toString(),
                "fiscalMonthEnd" to fiscalEnd.toString(),
                "submittedBy" to memberId.toString(),
            )
        val submitResponse = restTemplate.postForEntity("/api/v1/worklog/submissions", submitRequest, Map::class.java)
        val approvalId = (submitResponse.body as Map<*, *>)["approvalId"] as String

        val approveRequest = mapOf<String, String>()
        val approveResponse =
            restTemplate.postForEntity(
                "/api/v1/worklog/approvals/$approvalId/approve",
                approveRequest,
                Map::class.java,
            )

        assertEquals(HttpStatus.BAD_REQUEST, approveResponse.statusCode)
        val body = approveResponse.body as Map<*, *>
        assertTrue(body["errorCode"].toString().contains("REVIEWED_BY_REQUIRED"))
    }

    @Test
    @Transactional
    fun `POST approve should fail when trying to approve twice`() {
        val memberId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val managerId = UUID.randomUUID()
        val date = LocalDate.now().minusDays(40)
        val (fiscalStart, fiscalEnd) = getFiscalMonthPeriod(date)

        createWorkLogEntry(memberId, projectId, fiscalStart.plusDays(7), 8.0)

        val submitRequest =
            mapOf(
                "memberId" to memberId.toString(),
                "fiscalMonthStart" to fiscalStart.toString(),
                "fiscalMonthEnd" to fiscalEnd.toString(),
                "submittedBy" to memberId.toString(),
            )
        val submitResponse = restTemplate.postForEntity("/api/v1/worklog/submissions", submitRequest, Map::class.java)
        val approvalId = (submitResponse.body as Map<*, *>)["approvalId"] as String

        val approveRequest = mapOf("reviewedBy" to managerId.toString())

        // First approval
        val response1 =
            restTemplate.postForEntity(
                "/api/v1/worklog/approvals/$approvalId/approve",
                approveRequest,
                Void::class.java,
            )
        assertEquals(HttpStatus.NO_CONTENT, response1.statusCode)

        // Second approval (should fail)
        val response2 =
            restTemplate.postForEntity(
                "/api/v1/worklog/approvals/$approvalId/approve",
                approveRequest,
                Map::class.java,
            )

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response2.statusCode)
    }

    // =========================================================================
    // POST /api/v1/worklog/approvals/{id}/reject - Reject Month Tests
    // =========================================================================

    @Test
    @Transactional
    fun `POST reject should transition entries back to DRAFT with rejection reason`() {
        val memberId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val managerId = UUID.randomUUID()
        val date = LocalDate.now().minusDays(45)
        val (fiscalStart, fiscalEnd) = getFiscalMonthPeriod(date)

        val entryId = createWorkLogEntry(memberId, projectId, fiscalStart.plusDays(8), 8.0)

        // Submit
        val submitRequest =
            mapOf(
                "memberId" to memberId.toString(),
                "fiscalMonthStart" to fiscalStart.toString(),
                "fiscalMonthEnd" to fiscalEnd.toString(),
                "submittedBy" to memberId.toString(),
            )
        val submitResponse = restTemplate.postForEntity("/api/v1/worklog/submissions", submitRequest, Map::class.java)
        val approvalId = (submitResponse.body as Map<*, *>)["approvalId"] as String

        // Reject
        val rejectRequest =
            mapOf(
                "reviewedBy" to managerId.toString(),
                "rejectionReason" to "Please add more detail",
            )
        val rejectResponse =
            restTemplate.postForEntity(
                "/api/v1/worklog/approvals/$approvalId/reject",
                rejectRequest,
                Void::class.java,
            )

        assertEquals(HttpStatus.NO_CONTENT, rejectResponse.statusCode)

        // Verify DRAFT status
        val entryResponse = restTemplate.getForEntity("/api/v1/worklog/entries/$entryId", Map::class.java)
        assertEquals("DRAFT", (entryResponse.body as Map<*, *>)["status"])

        // Verify editable (can update)
        val version = (entryResponse.body as Map<*, *>)["version"]
        val updateRequest = mapOf("hours" to 7.5, "comment" to "Updated")
        val headers = HttpHeaders().apply { set("If-Match", version.toString()) }

        val updateResponse =
            restTemplate.exchange(
                "/api/v1/worklog/entries/$entryId",
                HttpMethod.PATCH,
                HttpEntity(updateRequest, headers),
                Void::class.java,
            )

        assertEquals(HttpStatus.NO_CONTENT, updateResponse.statusCode)
    }

    @Test
    @Transactional
    fun `POST reject should fail with missing rejectionReason`() {
        val memberId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val managerId = UUID.randomUUID()
        val date = LocalDate.now().minusDays(50)
        val (fiscalStart, fiscalEnd) = getFiscalMonthPeriod(date)

        createWorkLogEntry(memberId, projectId, fiscalStart.plusDays(9), 8.0)

        val submitRequest =
            mapOf(
                "memberId" to memberId.toString(),
                "fiscalMonthStart" to fiscalStart.toString(),
                "fiscalMonthEnd" to fiscalEnd.toString(),
                "submittedBy" to memberId.toString(),
            )
        val submitResponse = restTemplate.postForEntity("/api/v1/worklog/submissions", submitRequest, Map::class.java)
        val approvalId = (submitResponse.body as Map<*, *>)["approvalId"] as String

        val rejectRequest = mapOf("reviewedBy" to managerId.toString())
        val rejectResponse =
            restTemplate.postForEntity(
                "/api/v1/worklog/approvals/$approvalId/reject",
                rejectRequest,
                Map::class.java,
            )

        assertEquals(HttpStatus.BAD_REQUEST, rejectResponse.statusCode)
        val body = rejectResponse.body as Map<*, *>
        assertTrue(body["errorCode"].toString().contains("REJECTION_REASON_REQUIRED"))
    }

    @Test
    @Transactional
    fun `POST reject should fail with rejection reason exceeding 1000 chars`() {
        val memberId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val managerId = UUID.randomUUID()
        val date = LocalDate.now().minusDays(55)
        val (fiscalStart, fiscalEnd) = getFiscalMonthPeriod(date)

        createWorkLogEntry(memberId, projectId, fiscalStart.plusDays(10), 8.0)

        val submitRequest =
            mapOf(
                "memberId" to memberId.toString(),
                "fiscalMonthStart" to fiscalStart.toString(),
                "fiscalMonthEnd" to fiscalEnd.toString(),
                "submittedBy" to memberId.toString(),
            )
        val submitResponse = restTemplate.postForEntity("/api/v1/worklog/submissions", submitRequest, Map::class.java)
        val approvalId = (submitResponse.body as Map<*, *>)["approvalId"] as String

        val longReason = "x".repeat(1001)
        val rejectRequest =
            mapOf(
                "reviewedBy" to managerId.toString(),
                "rejectionReason" to longReason,
            )
        val rejectResponse =
            restTemplate.postForEntity(
                "/api/v1/worklog/approvals/$approvalId/reject",
                rejectRequest,
                Map::class.java,
            )

        assertEquals(HttpStatus.BAD_REQUEST, rejectResponse.statusCode)
    }

    // =========================================================================
    // Full Workflow Integration Tests
    // =========================================================================

    @Test
    @Transactional
    fun `Full approval workflow - submit, approve, verify read-only`() {
        val memberId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val managerId = UUID.randomUUID()
        val date = LocalDate.now().minusDays(60)
        val (fiscalStart, fiscalEnd) = getFiscalMonthPeriod(date)

        // 1. Create entries
        val entryId1 = createWorkLogEntry(memberId, projectId, fiscalStart.plusDays(1), 8.0)
        val entryId2 = createWorkLogEntry(memberId, projectId, fiscalStart.plusDays(2), 7.5)

        // Verify initial DRAFT status
        val initialCheck = restTemplate.getForEntity("/api/v1/worklog/entries/$entryId1", Map::class.java)
        assertEquals("DRAFT", (initialCheck.body as Map<*, *>)["status"])

        // 2. Submit
        val submitRequest =
            mapOf(
                "memberId" to memberId.toString(),
                "fiscalMonthStart" to fiscalStart.toString(),
                "fiscalMonthEnd" to fiscalEnd.toString(),
                "submittedBy" to memberId.toString(),
            )
        val submitResponse = restTemplate.postForEntity("/api/v1/worklog/submissions", submitRequest, Map::class.java)
        assertEquals(HttpStatus.CREATED, submitResponse.statusCode)
        val approvalId = (submitResponse.body as Map<*, *>)["approvalId"] as String

        // Verify SUBMITTED status
        val submittedCheck = restTemplate.getForEntity("/api/v1/worklog/entries/$entryId1", Map::class.java)
        assertEquals("SUBMITTED", (submittedCheck.body as Map<*, *>)["status"])

        // 3. Approve
        val approveRequest = mapOf("reviewedBy" to managerId.toString())
        val approveResponse =
            restTemplate.postForEntity(
                "/api/v1/worklog/approvals/$approvalId/approve",
                approveRequest,
                Void::class.java,
            )
        assertEquals(HttpStatus.NO_CONTENT, approveResponse.statusCode)

        // 4. Verify APPROVED and read-only
        val approvedCheck = restTemplate.getForEntity("/api/v1/worklog/entries/$entryId1", Map::class.java)
        assertEquals("APPROVED", (approvedCheck.body as Map<*, *>)["status"])

        // Try to update - should fail
        val version = (approvedCheck.body as Map<*, *>)["version"]
        val headers = HttpHeaders().apply { set("If-Match", version.toString()) }
        val updateAttempt =
            restTemplate.exchange(
                "/api/v1/worklog/entries/$entryId1",
                HttpMethod.PATCH,
                HttpEntity(mapOf("hours" to 6.0, "comment" to "Should fail"), headers),
                Map::class.java,
            )

        assertTrue(
            updateAttempt.statusCode == HttpStatus.BAD_REQUEST ||
                updateAttempt.statusCode == HttpStatus.UNPROCESSABLE_ENTITY,
        )
    }

    @Test
    @Transactional
    fun `Full rejection workflow - submit, reject, edit, resubmit, approve`() {
        val memberId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val managerId = UUID.randomUUID()
        val date = LocalDate.now().minusDays(65)
        val (fiscalStart, fiscalEnd) = getFiscalMonthPeriod(date)

        // 1. Create entry
        val entryId = createWorkLogEntry(memberId, projectId, fiscalStart.plusDays(3), 8.0)

        // 2. Submit
        val submitRequest =
            mapOf(
                "memberId" to memberId.toString(),
                "fiscalMonthStart" to fiscalStart.toString(),
                "fiscalMonthEnd" to fiscalEnd.toString(),
                "submittedBy" to memberId.toString(),
            )
        val submitResponse1 = restTemplate.postForEntity("/api/v1/worklog/submissions", submitRequest, Map::class.java)
        val approvalId1 = (submitResponse1.body as Map<*, *>)["approvalId"] as String

        // 3. Reject
        val rejectRequest =
            mapOf(
                "reviewedBy" to managerId.toString(),
                "rejectionReason" to "Please add more detail",
            )
        val rejectResponse =
            restTemplate.postForEntity(
                "/api/v1/worklog/approvals/$approvalId1/reject",
                rejectRequest,
                Void::class.java,
            )
        assertEquals(HttpStatus.NO_CONTENT, rejectResponse.statusCode)

        // 4. Verify DRAFT and editable
        val draftCheck = restTemplate.getForEntity("/api/v1/worklog/entries/$entryId", Map::class.java)
        assertEquals("DRAFT", (draftCheck.body as Map<*, *>)["status"])
        val version = (draftCheck.body as Map<*, *>)["version"]

        // 5. Edit entry
        val headers = HttpHeaders().apply { set("If-Match", version.toString()) }
        val updateRequest = mapOf("hours" to 7.5, "comment" to "Updated with more detail")
        val updateResponse =
            restTemplate.exchange(
                "/api/v1/worklog/entries/$entryId",
                HttpMethod.PATCH,
                HttpEntity(updateRequest, headers),
                Void::class.java,
            )
        assertEquals(HttpStatus.NO_CONTENT, updateResponse.statusCode)

        // 6. Resubmit
        val submitResponse2 = restTemplate.postForEntity("/api/v1/worklog/submissions", submitRequest, Map::class.java)
        assertEquals(HttpStatus.CREATED, submitResponse2.statusCode)
        val approvalId2 = (submitResponse2.body as Map<*, *>)["approvalId"] as String

        // 7. Approve second submission
        val approveRequest = mapOf("reviewedBy" to managerId.toString())
        val approveResponse =
            restTemplate.postForEntity(
                "/api/v1/worklog/approvals/$approvalId2/approve",
                approveRequest,
                Void::class.java,
            )
        assertEquals(HttpStatus.NO_CONTENT, approveResponse.statusCode)

        // 8. Verify final APPROVED status
        val finalCheck = restTemplate.getForEntity("/api/v1/worklog/entries/$entryId", Map::class.java)
        assertEquals("APPROVED", (finalCheck.body as Map<*, *>)["status"])
        assertEquals(7.5, (finalCheck.body as Map<*, *>)["hours"]) // Updated hours preserved
    }
}
