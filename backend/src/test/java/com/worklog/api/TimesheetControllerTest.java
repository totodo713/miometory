package com.worklog.api;

import static org.junit.jupiter.api.Assertions.*;

import com.worklog.IntegrationTestBase;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Integration tests for TimesheetController.
 *
 * Covers GET timesheet (calendar/fiscal period), PUT/DELETE attendance,
 * optimistic locking on update, and access control (403 for unrelated users).
 *
 * <p>Since TimesheetController uses {@code Authentication auth} parameters,
 * HTTP Basic authentication must be active. The dev/test SecurityConfig enables
 * httpBasic, and the nested {@link TestSecurityConfig} provides an in-memory
 * user so that {@link TestRestTemplate#withBasicAuth} populates the SecurityContext.
 * The username matches the member email so {@code UserContextService.resolveUserMemberId}
 * can look up the correct member record.
 */
@DisplayName("TimesheetController Integration Tests")
public class TimesheetControllerTest extends IntegrationTestBase {

    private static final String AUTH_EMAIL = "test@example.com";
    private static final String AUTH_PASSWORD = "test";

    /**
     * Provides an in-memory UserDetailsService for HTTP Basic authentication.
     * The password is BCrypt-encoded using the application's PasswordEncoder bean.
     */
    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        UserDetailsService testUserDetailsService(PasswordEncoder passwordEncoder) {
            var user = User.withUsername(AUTH_EMAIL)
                    .password(passwordEncoder.encode(AUTH_PASSWORD))
                    .roles("USER")
                    .build();
            return new InMemoryUserDetailsManager(user);
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    /** Authenticated rest template — sends HTTP Basic credentials matching the test user. */
    private TestRestTemplate authRestTemplate;

    private UUID selfMemberId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        authRestTemplate = restTemplate.withBasicAuth(AUTH_EMAIL, AUTH_PASSWORD);

        selfMemberId = UUID.randomUUID();
        projectId = UUID.randomUUID();

        // Clean up any existing member with email matching AUTH_EMAIL from previous test runs
        // (Testcontainers reuse keeps data across runs; uk_member_tenant_email would conflict)
        executeInNewTransaction(() -> {
            baseJdbcTemplate.update(
                    "DELETE FROM daily_attendance WHERE member_id IN (SELECT id FROM members WHERE email = ?)",
                    AUTH_EMAIL);
            baseJdbcTemplate.update(
                    "DELETE FROM member_project_assignments WHERE member_id IN (SELECT id FROM members WHERE email = ?)",
                    AUTH_EMAIL);
            baseJdbcTemplate.update("DELETE FROM members WHERE email = ?", AUTH_EMAIL);
            return kotlin.Unit.INSTANCE;
        });

        // Create self member with email matching auth username so resolveUserMemberId works
        createTestMember(selfMemberId, AUTH_EMAIL);
        createTestProject(projectId, "PROJ-" + projectId.toString().substring(0, 8));

        // Create assignment for member-project
        executeInNewTransaction(() -> {
            baseJdbcTemplate.update("""
                    INSERT INTO member_project_assignments
                    (id, tenant_id, member_id, project_id, assigned_at, is_active)
                    VALUES (?, ?::UUID, ?, ?, CURRENT_TIMESTAMP, true)
                    ON CONFLICT (tenant_id, member_id, project_id) DO NOTHING""", UUID.randomUUID(), TEST_TENANT_ID, selfMemberId, projectId);
            return kotlin.Unit.INSTANCE;
        });
    }

    // ---------------------------------------------------------------
    // GET timesheet
    // ---------------------------------------------------------------

    @Test
    @DisplayName("GET timesheet returns 31 rows for March 2026 calendar period")
    @SuppressWarnings("unchecked")
    void getTimesheet_returnsCorrectRowCount_forCalendarPeriod() {
        String url = "/api/v1/worklog/timesheet/2026/3?projectId=" + projectId;

        ResponseEntity<Map> response = authRestTemplate.getForEntity(url, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
        assertEquals(31, rows.size(), "March 2026 has 31 days");
        assertEquals("2026-03-01", body.get("periodStart").toString());
        assertEquals("2026-03-31", body.get("periodEnd").toString());
        assertEquals("calendar", body.get("periodType"));
        assertTrue((Boolean) body.get("canEdit"), "Self should be able to edit own timesheet");
    }

    @Test
    @DisplayName("GET timesheet returns correct period for fiscal mode (Feb 21 - Mar 20)")
    @SuppressWarnings("unchecked")
    void getTimesheet_returnsCorrectRowCount_forFiscalPeriod() {
        String url = "/api/v1/worklog/timesheet/2026/3?projectId=" + projectId + "&periodType=fiscal";

        ResponseEntity<Map> response = authRestTemplate.getForEntity(url, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        assertEquals("2026-02-21", body.get("periodStart").toString());
        assertEquals("2026-03-20", body.get("periodEnd").toString());
        assertEquals("fiscal", body.get("periodType"));

        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
        // Feb 21-28 = 8 days + Mar 1-20 = 20 days = 28 days total
        assertEquals(28, rows.size(), "Fiscal period Feb 21 - Mar 20 should have 28 days");
    }

    // ---------------------------------------------------------------
    // PUT attendance (create / update)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PUT attendance creates new record, verifiable via GET timesheet")
    @SuppressWarnings("unchecked")
    void saveAttendance_createsNewRecord() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("memberId", selfMemberId.toString());
        request.put("date", "2026-03-15");
        request.put("startTime", "09:00");
        request.put("endTime", "18:00");
        request.put("remarks", "Test day");
        request.put("version", 0);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        ResponseEntity<Void> putResponse =
                authRestTemplate.exchange("/api/v1/worklog/timesheet/attendance", HttpMethod.PUT, entity, Void.class);

        assertEquals(HttpStatus.OK, putResponse.getStatusCode(), "PUT attendance should succeed");

        // Verify via GET timesheet
        String url = "/api/v1/worklog/timesheet/2026/3?projectId=" + projectId;
        ResponseEntity<Map> getResponse = authRestTemplate.getForEntity(url, Map.class);
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());

        List<Map<String, Object>> rows =
                (List<Map<String, Object>>) getResponse.getBody().get("rows");
        Map<String, Object> march15 = rows.stream()
                .filter(r -> "2026-03-15".equals(r.get("date").toString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No row found for 2026-03-15"));

        assertTrue((Boolean) march15.get("hasAttendanceRecord"), "Row should have attendance record");
        assertEquals("09:00:00", march15.get("startTime").toString());
        assertEquals("18:00:00", march15.get("endTime").toString());
        assertEquals("Test day", march15.get("remarks"));
        assertNotNull(march15.get("attendanceId"), "Attendance ID should be present");
    }

    @Test
    @DisplayName("PUT attendance updates existing record with correct version")
    void saveAttendance_updatesExistingWithVersion() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // First save: create attendance
        Map<String, Object> createRequest = new LinkedHashMap<>();
        createRequest.put("memberId", selfMemberId.toString());
        createRequest.put("date", "2026-03-16");
        createRequest.put("startTime", "09:00");
        createRequest.put("endTime", "17:00");
        createRequest.put("remarks", "Original");
        createRequest.put("version", 0);

        HttpEntity<Map<String, Object>> createEntity = new HttpEntity<>(createRequest, headers);
        ResponseEntity<Void> firstPut = authRestTemplate.exchange(
                "/api/v1/worklog/timesheet/attendance", HttpMethod.PUT, createEntity, Void.class);
        assertEquals(HttpStatus.OK, firstPut.getStatusCode(), "First PUT should succeed");

        // Second save: update with version=0 (initial save sets version to 0, update increments to 1)
        Map<String, Object> updateRequest = new LinkedHashMap<>();
        updateRequest.put("memberId", selfMemberId.toString());
        updateRequest.put("date", "2026-03-16");
        updateRequest.put("startTime", "10:00");
        updateRequest.put("endTime", "19:00");
        updateRequest.put("remarks", "Updated");
        updateRequest.put("version", 0);

        HttpEntity<Map<String, Object>> updateEntity = new HttpEntity<>(updateRequest, headers);
        ResponseEntity<Void> secondPut = authRestTemplate.exchange(
                "/api/v1/worklog/timesheet/attendance", HttpMethod.PUT, updateEntity, Void.class);

        assertEquals(HttpStatus.OK, secondPut.getStatusCode(), "Second PUT with correct version should succeed");
    }

    // ---------------------------------------------------------------
    // DELETE attendance
    // ---------------------------------------------------------------

    @Test
    @DisplayName("DELETE attendance removes record, verifiable via GET timesheet")
    @SuppressWarnings("unchecked")
    void deleteAttendance_removesRecord() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // First create an attendance record
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("memberId", selfMemberId.toString());
        request.put("date", "2026-03-17");
        request.put("startTime", "08:30");
        request.put("endTime", "17:30");
        request.put("remarks", "To be deleted");
        request.put("version", 0);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        ResponseEntity<Void> putResponse =
                authRestTemplate.exchange("/api/v1/worklog/timesheet/attendance", HttpMethod.PUT, entity, Void.class);
        assertEquals(HttpStatus.OK, putResponse.getStatusCode());

        // Delete the attendance record using exchange to include auth headers
        authRestTemplate.exchange(
                "/api/v1/worklog/timesheet/attendance/" + selfMemberId + "/2026-03-17",
                HttpMethod.DELETE,
                null,
                Void.class);

        // Verify via GET timesheet that attendance is gone
        String url = "/api/v1/worklog/timesheet/2026/3?projectId=" + projectId;
        ResponseEntity<Map> getResponse = authRestTemplate.getForEntity(url, Map.class);
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());

        List<Map<String, Object>> rows =
                (List<Map<String, Object>>) getResponse.getBody().get("rows");
        Map<String, Object> march17 = rows.stream()
                .filter(r -> "2026-03-17".equals(r.get("date").toString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No row found for 2026-03-17"));

        assertFalse((Boolean) march17.get("hasAttendanceRecord"), "Attendance should be removed after DELETE");
        assertNull(march17.get("startTime"), "Start time should be null after DELETE");
        assertNull(march17.get("endTime"), "End time should be null after DELETE");
    }

    // ---------------------------------------------------------------
    // Access control
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PUT attendance returns 403 when saving for unrelated member")
    void saveAttendance_forbiddenForUnrelatedUser() {
        // Create another member with a different email
        UUID otherId = UUID.randomUUID();
        createTestMember(otherId, "other-" + otherId + "@example.com");

        // No manager relationship set up between selfMemberId and otherId

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("memberId", otherId.toString());
        request.put("date", "2026-03-15");
        request.put("startTime", "09:00");
        request.put("endTime", "18:00");
        request.put("remarks", "Unauthorized");
        request.put("version", 0);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        ResponseEntity<Void> response =
                authRestTemplate.exchange("/api/v1/worklog/timesheet/attendance", HttpMethod.PUT, entity, Void.class);

        assertEquals(
                HttpStatus.FORBIDDEN,
                response.getStatusCode(),
                "Should return 403 when saving attendance for unrelated member");
    }
}
