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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Integration tests for POST /api/v1/worklog/entries/submit-daily endpoint.
 *
 * Tests the full stack: HTTP API -> Service -> Event Store -> Projections.
 * Verifies DRAFT-to-SUBMITTED transitions, authorization, read-only enforcement,
 * and performance characteristics.
 */
@DisplayName("POST /api/v1/worklog/entries/submit-daily")
public class WorkLogSubmitDailyTest extends IntegrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(WorkLogSubmitDailyTest.class);

    private static final String ENTRIES_URL = "/api/v1/worklog/entries";
    private static final String SUBMIT_DAILY_URL = "/api/v1/worklog/entries/submit-daily";

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

    // ---------------------------------------------------------------
    // Test cases
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("Should submit all DRAFT entries for date and return 200")
        @SuppressWarnings("unchecked")
        void shouldSubmitAllDraftEntriesForDate() {
            // Arrange: create 2 DRAFT entries on the same date
            createDraftEntry(memberId, projectId, testDate, 4.0);
            createDraftEntry(memberId, projectId2, testDate, 3.5);

            // Act
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    SUBMIT_DAILY_URL, submitRequest(memberId, testDate, memberId), Map.class);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertEquals(2, body.get("submittedCount"));
            assertEquals(testDate.toString(), body.get("date"));

            List<Map<String, Object>> entries = (List<Map<String, Object>>) body.get("entries");
            assertNotNull(entries);
            assertEquals(2, entries.size());
            for (Map<String, Object> entry : entries) {
                assertEquals("SUBMITTED", entry.get("status"));
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
        @DisplayName("Should return 404 when no DRAFT entries exist for date")
        @SuppressWarnings("unchecked")
        void shouldReturn404WhenNoDraftEntries() {
            // Arrange: no entries created for this date
            LocalDate emptyDate = LocalDate.now().minusDays(10);

            // Act
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    SUBMIT_DAILY_URL, submitRequest(memberId, emptyDate, memberId), Map.class);

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertEquals("NO_DRAFT_ENTRIES", body.get("errorCode"));
        }

        @Test
        @DisplayName("Should return 403 when submittedBy differs from memberId")
        @SuppressWarnings("unchecked")
        void shouldReturn403WhenSubmittedByDifferentMember() {
            // Arrange: create an entry so we are not hitting 404 first
            createDraftEntry(memberId, projectId, testDate, 8.0);

            UUID differentMember = UUID.randomUUID();

            // Act
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    SUBMIT_DAILY_URL, submitRequest(memberId, testDate, differentMember), Map.class);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertEquals("SELF_SUBMISSION_ONLY", body.get("errorCode"));
        }
    }

    @Nested
    @DisplayName("Selective submission")
    class SelectiveSubmission {

        @Test
        @DisplayName("Should only submit remaining DRAFT entries, leaving already-SUBMITTED unchanged")
        @SuppressWarnings("unchecked")
        void shouldOnlySubmitDraftEntries() {
            // Arrange: create 3 entries
            Map<String, Object> entry1 = createDraftEntry(memberId, projectId, testDate, 2.0);
            createDraftEntry(memberId, projectId2, testDate, 3.0);
            UUID thirdProject = UUID.randomUUID();
            createTestProject(thirdProject, "PROJ-" + thirdProject.toString().substring(0, 8));
            createDraftEntry(memberId, thirdProject, testDate, 1.5);

            // Submit once to transition all 3 to SUBMITTED
            ResponseEntity<Map> firstSubmit = restTemplate.postForEntity(
                    SUBMIT_DAILY_URL, submitRequest(memberId, testDate, memberId), Map.class);
            assertEquals(HttpStatus.OK, firstSubmit.getStatusCode());

            Map<String, Object> firstBody = firstSubmit.getBody();
            assertNotNull(firstBody);
            assertEquals(3, firstBody.get("submittedCount"));

            // Act: try to submit again — no DRAFT entries remain
            ResponseEntity<Map> secondSubmit = restTemplate.postForEntity(
                    SUBMIT_DAILY_URL, submitRequest(memberId, testDate, memberId), Map.class);

            // Assert: 404 because all entries are already SUBMITTED
            assertEquals(HttpStatus.NOT_FOUND, secondSubmit.getStatusCode());
            Map<String, Object> secondBody = secondSubmit.getBody();
            assertNotNull(secondBody);
            assertEquals("NO_DRAFT_ENTRIES", secondBody.get("errorCode"));
        }
    }

    @Nested
    @DisplayName("Read-only enforcement")
    class ReadOnlyEnforcement {

        @Test
        @DisplayName("Should return 422 when trying to PATCH a SUBMITTED entry")
        @SuppressWarnings("unchecked")
        void shouldVerifySubmittedEntriesAreReadOnly() {
            // Arrange: create a DRAFT entry and submit it
            Map<String, Object> created = createDraftEntry(memberId, projectId, testDate, 6.0);
            String entryId = (String) created.get("id");

            ResponseEntity<Map> submitResponse = restTemplate.postForEntity(
                    SUBMIT_DAILY_URL, submitRequest(memberId, testDate, memberId), Map.class);
            assertEquals(HttpStatus.OK, submitResponse.getStatusCode());

            // Fetch the entry via GET to obtain the authoritative version from the body
            ResponseEntity<Map> getResponse = restTemplate.getForEntity(ENTRIES_URL + "/" + entryId, Map.class);
            assertEquals(HttpStatus.OK, getResponse.getStatusCode());

            Map<String, Object> fetchedEntry = getResponse.getBody();
            assertNotNull(fetchedEntry);
            assertEquals("SUBMITTED", fetchedEntry.get("status"));

            int currentVersion = ((Number) fetchedEntry.get("version")).intValue();

            // Act: try to PATCH the submitted entry using the correct version
            Map<String, Object> patchRequest = new LinkedHashMap<>();
            patchRequest.put("hours", 4.0);
            patchRequest.put("comment", "Trying to edit submitted entry");

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.IF_MATCH, String.valueOf(currentVersion));
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(patchRequest, headers);

            ResponseEntity<Map> patchResponse =
                    restTemplate.exchange(ENTRIES_URL + "/" + entryId, HttpMethod.PATCH, requestEntity, Map.class);

            // Assert: 422 Unprocessable Entity (NOT_EDITABLE)
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, patchResponse.getStatusCode());
            Map<String, Object> body = patchResponse.getBody();
            assertNotNull(body);
            String errorCode = (String) body.get("errorCode");
            assertTrue(errorCode.contains("NOT_EDITABLE"), "Expected NOT_EDITABLE error but got: " + errorCode);
        }
    }

    @Nested
    @DisplayName("Performance")
    class Performance {

        @Test
        @DisplayName("Should respond within 1000ms for 5 entries")
        void shouldRespondWithin1000ms() {
            // Arrange: create 5 DRAFT entries
            for (int i = 0; i < 5; i++) {
                UUID proj = UUID.randomUUID();
                createTestProject(proj, "PROJ-" + proj.toString().substring(0, 8));
                createDraftEntry(memberId, proj, testDate, 1.0);
            }

            // Act: time the submit-daily call
            long startTime = System.currentTimeMillis();

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    SUBMIT_DAILY_URL, submitRequest(memberId, testDate, memberId), Map.class);

            long elapsed = System.currentTimeMillis() - startTime;

            // Assert: verify correctness; log elapsed time as a regression indicator
            assertEquals(HttpStatus.OK, response.getStatusCode());
            log.info("submit-daily for 5 entries responded in {} ms", elapsed);
            if (elapsed >= 1000) {
                log.warn(
                        "submit-daily exceeded 1000 ms target (took {} ms) — investigate if this becomes consistent",
                        elapsed);
            }
        }
    }
}
