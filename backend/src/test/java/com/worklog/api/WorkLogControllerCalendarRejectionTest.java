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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Integration tests for calendar API rejection enrichment.
 *
 * Tests the full stack: HTTP API -> Projections -> Event Store.
 * Verifies that the monthly calendar response correctly includes monthlyApproval
 * status and rejection enrichment (rejectionSource, rejectionReason) on daily entries
 * after a monthly rejection workflow.
 */
@DisplayName("Calendar API — Rejection Enrichment")
public class WorkLogControllerCalendarRejectionTest extends IntegrationTestBase {

    private static final String ENTRIES_URL = "/api/v1/worklog/entries";
    private static final String SUBMIT_DAILY_URL = "/api/v1/worklog/entries/submit-daily";
    private static final String SUBMISSIONS_URL = "/api/v1/worklog/submissions";

    @Autowired
    private TestRestTemplate restTemplate;

    private UUID memberId;
    private UUID managerId;
    private UUID projectId;
    private LocalDate testDate;
    private FiscalMonthPeriod fiscalMonth;
    /** Year component for the calendar URL (fiscal month end year). */
    private int calendarYear;
    /** Month component for the calendar URL (fiscal month end month). */
    private int calendarMonth;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        testDate = LocalDate.of(2026, 1, 15);
        fiscalMonth = FiscalMonthPeriod.forDate(testDate);

        // Calendar URL uses the year/month of the fiscal month end date (20th)
        calendarYear = fiscalMonth.endDate().getYear();
        calendarMonth = fiscalMonth.endDate().getMonthValue();

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
     * Builds the calendar URL for the configured fiscal month.
     */
    private String calendarUrl() {
        return "/api/v1/worklog/calendar/" + calendarYear + "/" + calendarMonth + "?memberId=" + memberId;
    }

    // ---------------------------------------------------------------
    // Test cases
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("monthlyApproval field in calendar response")
    class MonthlyApprovalField {

        @Test
        @DisplayName("Should return null monthlyApproval when no approval exists")
        @SuppressWarnings("unchecked")
        void shouldReturnNullMonthlyApprovalWhenNoApprovalExists() {
            // Arrange: create a draft entry but do not submit for approval
            createDraftEntry(memberId, projectId, testDate, 8.0);

            // Act
            ResponseEntity<Map> response = restTemplate.getForEntity(calendarUrl(), Map.class);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertEquals(memberId.toString(), body.get("memberId"));
            assertNull(body.get("monthlyApproval"), "monthlyApproval should be null when no approval exists");

            // Verify the calendar has dates with entries
            List<Map<String, Object>> dates = (List<Map<String, Object>>) body.get("dates");
            assertNotNull(dates);
            assertFalse(dates.isEmpty(), "Calendar should have date entries");
        }

        @Test
        @DisplayName("Should include monthlyApproval with REJECTED status after rejection")
        @SuppressWarnings("unchecked")
        void shouldIncludeMonthlyApprovalWithRejectedStatus() {
            // Arrange: create entries -> submit daily -> submit month -> reject month
            createDraftEntry(memberId, projectId, testDate, 8.0);
            submitDailyEntries(memberId, testDate);
            String approvalId = submitMonthForApproval(memberId, fiscalMonth);
            String rejectionReason = "Incorrect project codes used";
            rejectApproval(approvalId, managerId, rejectionReason);

            // Act
            ResponseEntity<Map> response = restTemplate.getForEntity(calendarUrl(), Map.class);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);

            Map<String, Object> approval = (Map<String, Object>) body.get("monthlyApproval");
            assertNotNull(approval, "monthlyApproval should be present after rejection");
            assertEquals("REJECTED", approval.get("status"));
            assertEquals(rejectionReason, approval.get("rejectionReason"));
            assertNotNull(approval.get("approvalId"));
            assertNotNull(approval.get("reviewedAt"));
            assertEquals(managerId.toString(), approval.get("reviewedBy"));
        }

        @Test
        @DisplayName("Should include rejectionSource=monthly on DRAFT entries after monthly rejection")
        @SuppressWarnings("unchecked")
        void shouldIncludeRejectionSourceOnDraftEntriesAfterMonthlyRejection() {
            // Arrange: create entries -> submit daily -> submit month -> reject month
            createDraftEntry(memberId, projectId, testDate, 8.0);
            submitDailyEntries(memberId, testDate);
            String approvalId = submitMonthForApproval(memberId, fiscalMonth);
            String rejectionReason = "Please revise time allocations";
            rejectApproval(approvalId, managerId, rejectionReason);

            // Act
            ResponseEntity<Map> response = restTemplate.getForEntity(calendarUrl(), Map.class);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);

            // Find the entry for the test date and verify rejection enrichment
            List<Map<String, Object>> dates = (List<Map<String, Object>>) body.get("dates");
            assertNotNull(dates);

            Map<String, Object> testDateEntry = dates.stream()
                    .filter(d -> testDate.toString().equals(d.get("date")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No calendar entry found for test date " + testDate));

            // After monthly rejection, entries return to DRAFT with rejection enrichment
            assertEquals("DRAFT", testDateEntry.get("status"));
            assertEquals("monthly", testDateEntry.get("rejectionSource"));
            assertEquals(rejectionReason, testDateEntry.get("rejectionReason"));
        }
    }
}
