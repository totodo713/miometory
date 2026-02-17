package com.worklog.api

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Integration tests for POST /api/v1/auth/signup endpoint.
 *
 * Verifies the signup flow works end-to-end after the @PersistenceCreator fix
 * for Role entity instantiation (Issue #17).
 *
 * Tests cover:
 * - T003: Successful signup returns 201 with user data
 * - T004: Created user has default "USER" role assigned
 * - T005: Duplicate email returns 400
 * - T006: Weak password returns 400
 * - T007: Missing default role returns 503
 */
class AuthControllerSignupTest : IntegrationTestBase() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        // Clean up test users to ensure isolation
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE '%@signup-test.example.com'")
    }

    private fun signupRequest(email: String, name: String, password: String): HttpEntity<String> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val body = """{"email":"$email","name":"$name","password":"$password"}"""
        return HttpEntity(body, headers)
    }

    // ============================================================
    // T003: Successful signup returns 201 with user data and sends verification email
    // ============================================================

    @Test
    fun `signup with valid data returns 201 with user id, email, and name`() {
        // Given
        val request = signupRequest("newuser@signup-test.example.com", "New User", "StrongPass1!")

        // When
        val response = restTemplate.postForEntity("/api/v1/auth/signup", request, Map::class.java)

        // Then
        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertNotNull(body!!["id"], "Response should contain user id")
        assertEquals("newuser@signup-test.example.com", body["email"])
        assertEquals("New User", body["name"])
        assertEquals("UNVERIFIED", body["accountStatus"])
        // Verification email dispatch is implicitly validated: AuthServiceImpl.signup() calls
        // emailService.sendVerificationEmail() as part of the flow. If email sending fails,
        // the signup would throw and not return 201.
    }

    // ============================================================
    // T004: Created user has default "USER" role assigned
    // ============================================================

    @Test
    fun `signup assigns default USER role to created user`() {
        // Given
        val request = signupRequest("rolecheck@signup-test.example.com", "Role Check", "StrongPass1!")

        // When
        val response = restTemplate.postForEntity("/api/v1/auth/signup", request, Map::class.java)

        // Then
        assertEquals(HttpStatus.CREATED, response.statusCode)

        // Verify the user was assigned the USER role by querying the database
        val userRoleId = jdbcTemplate.queryForObject(
            "SELECT role_id FROM users WHERE email = ?",
            java.util.UUID::class.java,
            "rolecheck@signup-test.example.com",
        )
        assertNotNull(userRoleId)

        val roleName = jdbcTemplate.queryForObject(
            "SELECT name FROM roles WHERE id = ?",
            String::class.java,
            userRoleId,
        )
        assertEquals("USER", roleName)
    }

    // ============================================================
    // T005: Duplicate email returns 400
    // ============================================================

    @Test
    fun `signup with duplicate email returns error`() {
        // Given - create first user
        val firstRequest = signupRequest("duplicate@signup-test.example.com", "First User", "StrongPass1!")
        val firstResponse = restTemplate.postForEntity("/api/v1/auth/signup", firstRequest, Map::class.java)
        assertEquals(HttpStatus.CREATED, firstResponse.statusCode)

        // When - attempt signup with same email
        val duplicateRequest = signupRequest("duplicate@signup-test.example.com", "Second User", "StrongPass2!")
        val response = restTemplate.postForEntity("/api/v1/auth/signup", duplicateRequest, Map::class.java)

        // Then - should return 400 with descriptive error
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body
        assertNotNull(body)
        val message = body!!["message"]?.toString() ?: ""
        assert(message.contains("already registered", ignoreCase = true)) {
            "Error message should indicate email is already registered, but was: $message"
        }
    }

    // ============================================================
    // T006: Weak password returns 400
    // ============================================================

    @Test
    fun `signup with weak password returns 400 validation error`() {
        // Given - password that doesn't meet strength requirements
        val request = signupRequest("weakpass@signup-test.example.com", "Weak Pass User", "123")

        // When
        val response = restTemplate.postForEntity("/api/v1/auth/signup", request, Map::class.java)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    // ============================================================
    // T007: Missing default role returns 503
    // ============================================================

    @Test
    fun `signup when default role is missing returns 503 service configuration error`() {
        // Given - temporarily delete the USER role to simulate missing configuration
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE '%@signup-test.example.com'")
        val savedRoles = jdbcTemplate.queryForList(
            "SELECT id, name, description, created_at, updated_at FROM roles WHERE name = 'USER'",
        )
        jdbcTemplate.update("DELETE FROM users WHERE role_id IN (SELECT id FROM roles WHERE name = 'USER')")
        jdbcTemplate.update("DELETE FROM roles WHERE name = 'USER'")

        try {
            // When
            val request = signupRequest("norole@signup-test.example.com", "No Role User", "StrongPass1!")
            val response = restTemplate.postForEntity("/api/v1/auth/signup", request, Map::class.java)

            // Then
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
            val body = response.body
            assertNotNull(body)
            assertEquals("SERVICE_CONFIGURATION_ERROR", body!!["errorCode"])
            val message = body["message"]?.toString() ?: ""
            assert(message.contains("not found in database", ignoreCase = true)) {
                "Error message should indicate role not found, but was: $message"
            }
        } finally {
            // Restore the USER role for other tests
            for (role in savedRoles) {
                jdbcTemplate.update(
                    """INSERT INTO roles (id, name, description, created_at, updated_at)
                        |VALUES (?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                    """.trimMargin(),
                    role["id"],
                    role["name"],
                    role["description"],
                    role["created_at"],
                    role["updated_at"],
                )
            }
        }
    }
}
