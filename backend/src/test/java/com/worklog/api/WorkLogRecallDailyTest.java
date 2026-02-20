package com.worklog.api;

import static org.junit.jupiter.api.Assertions.*;

import com.worklog.IntegrationTestBase;
import com.worklog.domain.shared.FiscalMonthPeriod;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Integration tests for POST /api/v1/worklog/entries/recall-daily endpoint.
 *
 * Tests the full stack: HTTP API -> Service -> Event Store -> Projections.
 * Verifies SUBMITTED-to-DRAFT transitions, authorization, editability after recall,
 * and error conditions.
 */
@DisplayName("POST /api/v1/worklog/entries/recall-daily")
public class WorkLogRecallDailyTest extends IntegrationTestBase {

    private static final String ENTRIES_URL = "/api/v1/worklog/entries";
    private static final String SUBMIT_DAILY_URL = "/api/v1/worklog/entries/submit-daily";
    private static final String RECALL_DAILY_URL = "/api/v1/worklog/entries/recall-daily";
    private static final String SUBMISSIONS_URL = "/api/v1/worklog/submissions";
    private static final String APPROVALS_URL = "/api/v1/worklog/approvals";

    @Autowired
    private TestRestTemplate restTemplate;

    private UUID memberId;
    private UUID projectId;
    private UUID projectId2;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        projectId2 = UUID.randomUUID();
        testDate = LocalDate.now().minusDays(1);

        createTestMember(memberId, "test-" + memberId + "@example.com");
        createTestProject(projectId, "PROJ-" + projectId.toString().substring(0, 8));
        createTestProject(projectId2, "PROJ-" + projectId2.toString().substring(0, 8));
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
     * Builds the submit-daily request body.
     */
    private Map<String, Object> submitRequest(UUID member, LocalDate date, UUID submittedBy) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("memberId", member.toString());
        request.put("date", date.toString());
        request.put("submittedBy", submittedBy.toString());
        return request;
    }

    /**
     * Builds the recall-daily request body.
     */
    private Map<String, Object> recallRequest(UUID member, LocalDate date, UUID recalledBy) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("memberId", member.toString());
        request.put("date", date.toString());
        request.put("recalledBy", recalledBy.toString());
        return request;
    }

    /**
     * Submits all DRAFT entries for the member on the given date and asserts success.
     */
    @SuppressWarnings("unchecked")
    private void submitEntries(UUID member, LocalDate date) {
        ResponseEntity<Map> response =
                restTemplate.postForEntity(SUBMIT_DAILY_URL, submitRequest(member, date, member), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Failed to submit entries");
    }

    // ---------------------------------------------------------------
    // Test cases
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("Should recall submitted entries to DRAFT and return 200")
        @SuppressWarnings("unchecked")
        void shouldRecallSubmittedEntriesToDraft() {
            // Arrange: create 2 entries, then submit them
            createDraftEntry(memberId, projectId, testDate, 4.0);
            createDraftEntry(memberId, projectId2, testDate, 3.5);
            submitEntries(memberId, testDate);

            // Act: recall the submitted entries
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    RECALL_DAILY_URL, recallRequest(memberId, testDate, memberId), Map.class);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertEquals(2, body.get("recalledCount"));
            assertEquals(testDate.toString(), body.get("date"));

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

            // Act: try to recall — entries are still DRAFT, not SUBMITTED
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    RECALL_DAILY_URL, recallRequest(memberId, testDate, memberId), Map.class);

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertEquals("NO_SUBMITTED_ENTRIES", body.get("errorCode"));
        }

        @Test
        @DisplayName("Should return 403 when recalledBy differs from memberId")
        @SuppressWarnings("unchecked")
        void shouldReturn403WhenRecalledByDifferentMember() {
            // Arrange: create and submit entries so we are not hitting 404 first
            createDraftEntry(memberId, projectId, testDate, 8.0);
            submitEntries(memberId, testDate);

            UUID differentMember = UUID.randomUUID();

            // Act
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    RECALL_DAILY_URL, recallRequest(memberId, testDate, differentMember), Map.class);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertEquals("SELF_RECALL_ONLY", body.get("errorCode"));
        }

        @Test
        @DisplayName("Should return 422 when entries are part of a non-PENDING MonthlyApproval")
        @SuppressWarnings("unchecked")
        void shouldReturn422WhenBlockedByApproval() {
            // Arrange: create entries and submit them daily
            createDraftEntry(memberId, projectId, testDate, 4.0);
            createDraftEntry(memberId, projectId2, testDate, 3.5);
            submitEntries(memberId, testDate);

            // Submit the fiscal month for approval (MonthlyApproval transitions to SUBMITTED status)
            // This is sufficient to block recall — any non-PENDING approval status blocks it.
            FiscalMonthPeriod fiscalMonth = FiscalMonthPeriod.forDate(testDate);
            Map<String, Object> monthRequest = new LinkedHashMap<>();
            monthRequest.put("memberId", memberId.toString());
            monthRequest.put("fiscalMonthStart", fiscalMonth.startDate().toString());
            monthRequest.put("fiscalMonthEnd", fiscalMonth.endDate().toString());
            monthRequest.put("submittedBy", memberId.toString());

            ResponseEntity<Map> submitMonthResponse =
                    restTemplate.postForEntity(SUBMISSIONS_URL, monthRequest, Map.class);
            assertEquals(HttpStatus.CREATED, submitMonthResponse.getStatusCode());

            // Act: try to recall the entries that are now part of a SUBMITTED MonthlyApproval
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    RECALL_DAILY_URL, recallRequest(memberId, testDate, memberId), Map.class);

            // Assert: 422 Unprocessable Entity — recall blocked by approval
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertEquals("RECALL_BLOCKED_BY_APPROVAL", body.get("errorCode"));
        }
    }

    @Nested
    @DisplayName("Post-recall editability")
    class PostRecallEditability {

        @Test
        @DisplayName("Should make entries editable after recall (PATCH should succeed)")
        @SuppressWarnings("unchecked")
        void shouldMakeEntriesEditableAfterRecall() {
            // Arrange: create a DRAFT entry, submit it, then recall it
            Map<String, Object> created = createDraftEntry(memberId, projectId, testDate, 6.0);
            String entryId = (String) created.get("id");

            submitEntries(memberId, testDate);

            ResponseEntity<Map> recallResponse = restTemplate.postForEntity(
                    RECALL_DAILY_URL, recallRequest(memberId, testDate, memberId), Map.class);
            assertEquals(HttpStatus.OK, recallResponse.getStatusCode());

            // Fetch the entry via GET to obtain the authoritative version
            ResponseEntity<Map> getResponse = restTemplate.getForEntity(ENTRIES_URL + "/" + entryId, Map.class);
            assertEquals(HttpStatus.OK, getResponse.getStatusCode());

            Map<String, Object> fetchedEntry = getResponse.getBody();
            assertNotNull(fetchedEntry);
            assertEquals("DRAFT", fetchedEntry.get("status"));

            int currentVersion = ((Number) fetchedEntry.get("version")).intValue();

            // Act: PATCH the recalled (now DRAFT) entry — should succeed
            Map<String, Object> patchRequest = new LinkedHashMap<>();
            patchRequest.put("hours", 4.0);
            patchRequest.put("comment", "Edited after recall");

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.IF_MATCH, String.valueOf(currentVersion));
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(patchRequest, headers);

            ResponseEntity<Void> patchResponse =
                    restTemplate.exchange(ENTRIES_URL + "/" + entryId, HttpMethod.PATCH, requestEntity, Void.class);

            // Assert: 204 No Content — entry is editable again after recall
            assertEquals(HttpStatus.NO_CONTENT, patchResponse.getStatusCode());

            // Verify the update was applied by fetching the entry again
            ResponseEntity<Map> verifyResponse = restTemplate.getForEntity(ENTRIES_URL + "/" + entryId, Map.class);
            assertEquals(HttpStatus.OK, verifyResponse.getStatusCode());
            Map<String, Object> verifiedEntry = verifyResponse.getBody();
            assertNotNull(verifiedEntry);
            assertEquals(4.0, ((Number) verifiedEntry.get("hours")).doubleValue(), 0.01);
        }
    }
}
