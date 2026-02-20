package com.worklog.api;

import static org.junit.jupiter.api.Assertions.*;

import com.worklog.IntegrationTestBase;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Integration tests for POST /api/v1/worklog/entries/reject-daily
 * and GET /api/v1/worklog/rejections/daily endpoints.
 *
 * Tests the full stack: HTTP API -> Service -> Event Store -> Projections.
 * Verifies SUBMITTED-to-DRAFT transitions via manager rejection,
 * rejection reason persistence, and rejection log query functionality.
 */
@DisplayName("Reject Daily Entries")
public class WorkLogControllerRejectDailyTest extends IntegrationTestBase {

    private static final String ENTRIES_URL = "/api/v1/worklog/entries";
    private static final String SUBMIT_DAILY_URL = "/api/v1/worklog/entries/submit-daily";
    private static final String REJECT_DAILY_URL = "/api/v1/worklog/entries/reject-daily";
    private static final String REJECTIONS_DAILY_URL = "/api/v1/worklog/rejections/daily";

    @Autowired
    private TestRestTemplate restTemplate;

    private UUID memberId;
    private UUID managerId;
    private UUID projectId;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        testDate = LocalDate.now().minusDays(1);

        createTestMember(memberId, "member-" + memberId + "@example.com");
        createTestMember(managerId, "manager-" + managerId + "@example.com");
        setManagerForMember(memberId, managerId); // Establish manager-subordinate relationship
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
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Failed to submit entries");
    }

    /**
     * Builds the reject-daily request body.
     */
    private Map<String, Object> rejectRequest(UUID member, LocalDate date, UUID rejectedBy, String rejectionReason) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("memberId", member.toString());
        request.put("date", date.toString());
        request.put("rejectedBy", rejectedBy.toString());
        request.put("rejectionReason", rejectionReason);
        return request;
    }

    // ---------------------------------------------------------------
    // T039: POST /api/v1/worklog/entries/reject-daily
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/worklog/entries/reject-daily")
    class RejectDaily {

        @Nested
        @DisplayName("Happy path")
        class HappyPath {

            @Test
            @DisplayName("Should reject SUBMITTED entries and return 200 with rejectedCount")
            @SuppressWarnings("unchecked")
            void shouldRejectSubmittedEntriesAndReturn200() {
                // Arrange: create 2 entries, submit them
                UUID projectId2 = UUID.randomUUID();
                createTestProject(projectId2, "PROJ-" + projectId2.toString().substring(0, 8));

                createDraftEntry(memberId, projectId, testDate, 4.0);
                createDraftEntry(memberId, projectId2, testDate, 3.5);
                submitDailyEntries(memberId, testDate);

                // Act: reject as manager
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        REJECT_DAILY_URL, rejectRequest(memberId, testDate, managerId, "Incorrect hours"), Map.class);

                // Assert: 200 OK with rejection details
                assertEquals(HttpStatus.OK, response.getStatusCode());
                Map<String, Object> body = response.getBody();
                assertNotNull(body);
                assertEquals(2, body.get("rejectedCount"));
                assertEquals(testDate.toString(), body.get("date"));
                assertEquals("Incorrect hours", body.get("rejectionReason"));

                List<Map<String, Object>> entries = (List<Map<String, Object>>) body.get("entries");
                assertNotNull(entries);
                assertEquals(2, entries.size());
                for (Map<String, Object> entry : entries) {
                    assertEquals("DRAFT", entry.get("status"));
                    assertNotNull(entry.get("id"));
                    assertNotNull(entry.get("projectId"));
                    assertNotNull(entry.get("hours"));
                    assertNotNull(entry.get("version"));
                }

                // Verify entries are back to DRAFT by fetching them via GET
                String getUrl =
                        ENTRIES_URL + "?memberId=" + memberId + "&startDate=" + testDate + "&endDate=" + testDate;
                ResponseEntity<Map> getResponse = restTemplate.getForEntity(getUrl, Map.class);
                assertEquals(HttpStatus.OK, getResponse.getStatusCode());

                Map<String, Object> getBody = getResponse.getBody();
                assertNotNull(getBody);
                List<Map<String, Object>> fetchedEntries = (List<Map<String, Object>>) getBody.get("entries");
                assertNotNull(fetchedEntries);
                assertEquals(2, fetchedEntries.size());
                for (Map<String, Object> fetchedEntry : fetchedEntries) {
                    assertEquals("DRAFT", fetchedEntry.get("status"));
                }
            }
        }

        @Nested
        @DisplayName("Error cases")
        class ErrorCases {

            @Test
            @DisplayName("Should return 404 when no SUBMITTED entries exist for date")
            @SuppressWarnings("unchecked")
            void shouldReturn404WhenNoSubmittedEntries() {
                // Arrange: create DRAFT entries but do NOT submit them
                createDraftEntry(memberId, projectId, testDate, 4.0);

                // Act: try to reject — entries are still DRAFT, not SUBMITTED
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        REJECT_DAILY_URL, rejectRequest(memberId, testDate, managerId, "Incorrect hours"), Map.class);

                // Assert: 404 because no SUBMITTED entries exist
                assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
                Map<String, Object> body = response.getBody();
                assertNotNull(body);
                assertEquals("NO_SUBMITTED_ENTRIES_FOR_DATE", body.get("errorCode"));
            }

            @Test
            @DisplayName("Should return 400 when rejectionReason is missing")
            @SuppressWarnings("unchecked")
            void shouldReturn400WhenRejectionReasonMissing() {
                // Arrange: build request without rejectionReason
                Map<String, Object> request = new LinkedHashMap<>();
                request.put("memberId", memberId.toString());
                request.put("date", testDate.toString());
                request.put("rejectedBy", managerId.toString());
                // rejectionReason intentionally omitted

                // Act
                ResponseEntity<Map> response = restTemplate.postForEntity(REJECT_DAILY_URL, request, Map.class);

                // Assert: 400 Bad Request
                assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            }

            @Test
            @DisplayName("Should return 400 when rejectionReason exceeds 1000 characters")
            @SuppressWarnings("unchecked")
            void shouldReturn400WhenRejectionReasonTooLong() {
                // Arrange: build request with oversized rejectionReason
                String longReason = "x".repeat(1001);
                Map<String, Object> request = new LinkedHashMap<>();
                request.put("memberId", memberId.toString());
                request.put("date", testDate.toString());
                request.put("rejectedBy", managerId.toString());
                request.put("rejectionReason", longReason);

                // Act
                ResponseEntity<Map> response = restTemplate.postForEntity(REJECT_DAILY_URL, request, Map.class);

                // Assert: 400 Bad Request
                assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            }
        }
    }

    // ---------------------------------------------------------------
    // T040: GET /api/v1/worklog/rejections/daily
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/worklog/rejections/daily")
    class GetDailyRejections {

        @Test
        @DisplayName("Should return empty list when no rejections exist")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyListWhenNoRejections() {
            // Arrange: no rejections for this member/date range
            LocalDate startDate = testDate.minusDays(7);
            LocalDate endDate = testDate;
            String url =
                    REJECTIONS_DAILY_URL + "?memberId=" + memberId + "&startDate=" + startDate + "&endDate=" + endDate;

            // Act
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            // Assert: 200 OK with empty rejections list
            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);

            List<Map<String, Object>> rejections = (List<Map<String, Object>>) body.get("rejections");
            assertNotNull(rejections);
            assertTrue(rejections.isEmpty());
        }

        @Test
        @DisplayName("Should return rejection after daily entries are rejected")
        @SuppressWarnings("unchecked")
        void shouldReturnRejectionAfterDailyEntriesAreRejected() {
            // Arrange: create entries, submit them, then reject them
            createDraftEntry(memberId, projectId, testDate, 4.0);
            submitDailyEntries(memberId, testDate);

            restTemplate.postForEntity(
                    REJECT_DAILY_URL,
                    rejectRequest(memberId, testDate, managerId, "Please fix time allocation"),
                    Map.class);

            // Act: query rejections for the date range
            LocalDate startDate = testDate.minusDays(7);
            LocalDate endDate = testDate;
            String url =
                    REJECTIONS_DAILY_URL + "?memberId=" + memberId + "&startDate=" + startDate + "&endDate=" + endDate;

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            // Assert: 200 OK with 1 rejection item
            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);

            List<Map<String, Object>> rejections = (List<Map<String, Object>>) body.get("rejections");
            assertNotNull(rejections);
            assertEquals(1, rejections.size());

            Map<String, Object> rejection = rejections.get(0);
            assertEquals(testDate.toString(), rejection.get("date"));
            assertEquals("Please fix time allocation", rejection.get("rejectionReason"));
            assertEquals(managerId.toString(), rejection.get("rejectedBy"));
        }
    }
}
