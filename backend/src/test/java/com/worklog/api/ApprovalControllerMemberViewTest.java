package com.worklog.api;

import static org.junit.jupiter.api.Assertions.*;

import com.worklog.IntegrationTestBase;
import com.worklog.domain.shared.FiscalMonthPeriod;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Integration tests for GET /api/v1/worklog/approvals/member/{memberId}.
 *
 * Tests the full stack: HTTP API -> Repository -> Event Store.
 * Verifies member-facing approval status retrieval for various approval states
 * (no approval, SUBMITTED, REJECTED) and request validation.
 */
@DisplayName("GET /api/v1/worklog/approvals/member/{memberId}")
public class ApprovalControllerMemberViewTest extends IntegrationTestBase {

    private static final String ENTRIES_URL = "/api/v1/worklog/entries";
    private static final String SUBMIT_DAILY_URL = "/api/v1/worklog/entries/submit-daily";
    private static final String SUBMISSIONS_URL = "/api/v1/worklog/submissions";
    private static final String MEMBER_APPROVAL_URL = "/api/v1/worklog/approvals/member/";

    @Autowired
    private TestRestTemplate restTemplate;

    private UUID memberId;
    private UUID managerId;
    private UUID projectId;
    private LocalDate testDate;
    private FiscalMonthPeriod fiscalMonth;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        testDate = LocalDate.of(2026, 1, 15);
        fiscalMonth = FiscalMonthPeriod.forDate(testDate);

        createTestMember(memberId, "test-" + memberId + "@example.com");
        createTestMember(managerId, "manager-" + managerId + "@example.com");
        createTestProject(projectId, "PROJ-" + projectId.toString().substring(0, 8));
    }

    // ---------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------

    /**
     * Creates a DRAFT work log entry via the API and returns the response body.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> createDraftEntry(UUID member, UUID project, LocalDate date, double hours) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("memberId", member.toString());
        request.put("projectId", project.toString());
        request.put("date", date.toString());
        request.put("hours", hours);
        request.put("comment", "Test entry " + UUID.randomUUID());
        request.put("enteredBy", member.toString());

        ResponseEntity<Map> response = restTemplate.postForEntity(ENTRIES_URL, request, Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(), "Failed to create draft entry");
        return response.getBody();
    }

    /**
     * Submits all DRAFT entries for the member on the given date and asserts success.
     */
    @SuppressWarnings("unchecked")
    private void submitDailyEntries(UUID member, LocalDate date) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("memberId", member.toString());
        request.put("date", date.toString());
        request.put("submittedBy", member.toString());

        ResponseEntity<Map> response = restTemplate.postForEntity(SUBMIT_DAILY_URL, request, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Failed to submit daily entries");
    }

    /**
     * Submits a fiscal month for approval and returns the approval ID.
     */
    @SuppressWarnings("unchecked")
    private String submitMonthForApproval(UUID member, FiscalMonthPeriod period) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("memberId", member.toString());
        request.put("fiscalMonthStart", period.startDate().toString());
        request.put("fiscalMonthEnd", period.endDate().toString());
        request.put("submittedBy", member.toString());

        ResponseEntity<Map> response = restTemplate.postForEntity(SUBMISSIONS_URL, request, Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(), "Failed to submit month for approval");
        return (String) response.getBody().get("approvalId");
    }

    /**
     * Rejects an approval via the approval reject endpoint.
     */
    @SuppressWarnings("unchecked")
    private void rejectApproval(String approvalId, UUID reviewer, String reason) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reviewedBy", reviewer.toString());
        request.put("rejectionReason", reason);

        ResponseEntity<Void> response =
                restTemplate.postForEntity("/api/v1/worklog/approvals/" + approvalId + "/reject", request, Void.class);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "Failed to reject approval");
    }

    /**
     * Builds the member approval URL with query parameters.
     */
    private String memberApprovalUrl(UUID member, FiscalMonthPeriod period) {
        return MEMBER_APPROVAL_URL + member
                + "?fiscalMonthStart=" + period.startDate()
                + "&fiscalMonthEnd=" + period.endDate();
    }

    // ---------------------------------------------------------------
    // Test cases
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("Should return 404 when no approval exists")
        @SuppressWarnings("unchecked")
        void shouldReturn404WhenNoApprovalExists() {
            // Act: GET member approval for a member with no approval record
            ResponseEntity<Map> response =
                    restTemplate.getForEntity(memberApprovalUrl(memberId, fiscalMonth), Map.class);

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }

        @Test
        @DisplayName("Should return approval with SUBMITTED status")
        @SuppressWarnings("unchecked")
        void shouldReturnApprovalWithSubmittedStatus() {
            // Arrange: create entries, submit daily, then submit month for approval
            createDraftEntry(memberId, projectId, testDate, 8.0);
            submitDailyEntries(memberId, testDate);
            String approvalId = submitMonthForApproval(memberId, fiscalMonth);

            // Act
            ResponseEntity<Map> response =
                    restTemplate.getForEntity(memberApprovalUrl(memberId, fiscalMonth), Map.class);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertEquals("SUBMITTED", body.get("status"));
            assertEquals(memberId.toString(), body.get("memberId"));
            assertEquals(fiscalMonth.startDate().toString(), body.get("fiscalMonthStart"));
            assertEquals(fiscalMonth.endDate().toString(), body.get("fiscalMonthEnd"));
            assertNotNull(body.get("approvalId"));
            assertNotNull(body.get("submittedAt"));
        }

        @Test
        @DisplayName("Should return approval with REJECTED status and rejection reason")
        @SuppressWarnings("unchecked")
        void shouldReturnApprovalWithRejectedStatusAndReason() {
            // Arrange: create entries, submit daily, submit month, then reject
            createDraftEntry(memberId, projectId, testDate, 8.0);
            submitDailyEntries(memberId, testDate);
            String approvalId = submitMonthForApproval(memberId, fiscalMonth);
            String rejectionReason = "Hours do not match project allocation";
            rejectApproval(approvalId, managerId, rejectionReason);

            // Act
            ResponseEntity<Map> response =
                    restTemplate.getForEntity(memberApprovalUrl(memberId, fiscalMonth), Map.class);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertEquals("REJECTED", body.get("status"));
            assertEquals(rejectionReason, body.get("rejectionReason"));
            assertEquals(memberId.toString(), body.get("memberId"));
            assertNotNull(body.get("reviewedAt"));
            assertEquals(managerId.toString(), body.get("reviewedBy"));
        }
    }

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("Should return non-2xx when fiscalMonthStart is missing")
        @SuppressWarnings("unchecked")
        void shouldReturnErrorWhenFiscalMonthStartMissing() {
            // Act: GET without fiscalMonthStart parameter
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    MEMBER_APPROVAL_URL + memberId + "?fiscalMonthEnd=" + fiscalMonth.endDate(), Map.class);

            // Assert: Spring returns 400 or 500 for missing required @RequestParam
            assertTrue(
                    response.getStatusCode().is4xxClientError()
                            || response.getStatusCode().is5xxServerError(),
                    "Expected error response but got " + response.getStatusCode());
        }

        @Test
        @DisplayName("Should return non-2xx when fiscalMonthEnd is missing")
        @SuppressWarnings("unchecked")
        void shouldReturnErrorWhenFiscalMonthEndMissing() {
            // Act: GET without fiscalMonthEnd parameter
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    MEMBER_APPROVAL_URL + memberId + "?fiscalMonthStart=" + fiscalMonth.startDate(), Map.class);

            // Assert: Spring returns 400 or 500 for missing required @RequestParam
            assertTrue(
                    response.getStatusCode().is4xxClientError()
                            || response.getStatusCode().is5xxServerError(),
                    "Expected error response but got " + response.getStatusCode());
        }
    }
}
