package com.worklog.api

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * Integration tests for MonthlyPeriodPatternController.
 */
class MonthlyPeriodPatternControllerTest : IntegrationTestBase() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var jdbcTemplate: org.springframework.jdbc.core.JdbcTemplate

    @Test
    fun `POST should create monthly period pattern and return 201`() {
        // Create tenant first
        val tenantId = createTenant()

        // Create monthly period pattern
        val request =
            mapOf(
                "name" to "21st Cutoff",
                "startDay" to 21,
            )

        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/monthly-period-patterns",
                request,
                Map::class.java,
            )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body)

        val body = response.body as Map<*, *>
        assertNotNull(body["id"])
        assertEquals(tenantId.toString(), body["tenantId"])
        assertEquals("21st Cutoff", body["name"])
        assertEquals(21, body["startDay"])
    }

    @Test
    fun `POST should reject invalid start day below 1`() {
        val tenantId = createTenant()

        val request =
            mapOf(
                "name" to "Invalid Pattern",
                "startDay" to 0, // Invalid - must be 1-28
            )

        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/monthly-period-patterns",
                request,
                Map::class.java,
            )

        // Should fail (not return 201)
        assertNotEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun `POST should reject invalid start day above 28`() {
        val tenantId = createTenant()

        val request =
            mapOf(
                "name" to "Invalid Date",
                "startDay" to 29, // Invalid - must be 1-28 to handle February
            )

        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/monthly-period-patterns",
                request,
                Map::class.java,
            )

        // Should fail (not return 201)
        assertNotEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun `GET by ID should return pattern when found`() {
        val tenantId = createTenant()

        // Create pattern
        val createRequest =
            mapOf(
                "name" to "15th Cutoff",
                "startDay" to 15,
            )

        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/monthly-period-patterns",
                createRequest,
                Map::class.java,
            )

        val patternId = (createResponse.body as Map<*, *>)["id"] as String

        // Get pattern by ID
        val response =
            restTemplate.getForEntity(
                "/api/v1/tenants/$tenantId/monthly-period-patterns/$patternId",
                Map::class.java,
            )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)

        val body = response.body as Map<*, *>
        assertEquals(patternId, body["id"])
        assertEquals("15th Cutoff", body["name"])
        assertEquals(15, body["startDay"])
    }

    @Test
    fun `GET by ID should return 404 when not found`() {
        val tenantId = createTenant()
        val nonExistentId = UUID.randomUUID()

        val response =
            restTemplate.getForEntity(
                "/api/v1/tenants/$tenantId/monthly-period-patterns/$nonExistentId",
                Map::class.java,
            )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `GET list should return empty list when no patterns exist`() {
        val tenantId = createTenant()

        val response =
            restTemplate.getForEntity(
                "/api/v1/tenants/$tenantId/monthly-period-patterns",
                List::class.java,
            )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(0, (response.body as List<*>).size)
    }

    @Test
    fun `GET list should return all patterns for tenant`() {
        val tenantId = createTenant()

        // Create multiple patterns
        val pattern1 =
            mapOf(
                "name" to "1st Cutoff",
                "startDay" to 1,
            )

        val pattern2 =
            mapOf(
                "name" to "21st Cutoff",
                "startDay" to 21,
            )

        restTemplate.postForEntity(
            "/api/v1/tenants/$tenantId/monthly-period-patterns",
            pattern1,
            Map::class.java,
        )

        restTemplate.postForEntity(
            "/api/v1/tenants/$tenantId/monthly-period-patterns",
            pattern2,
            Map::class.java,
        )

        // Get list
        val response =
            restTemplate.getForEntity(
                "/api/v1/tenants/$tenantId/monthly-period-patterns",
                List::class.java,
            )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(2, (response.body as List<*>).size)
    }

    @Test
    fun `GET list should not return patterns from other tenants`() {
        val tenant1Id = createTenant()
        val tenant2Id = createTenant()

        // Create pattern for tenant1
        val pattern1 =
            mapOf(
                "name" to "Tenant1 Pattern",
                "startDay" to 1,
            )

        restTemplate.postForEntity(
            "/api/v1/tenants/$tenant1Id/monthly-period-patterns",
            pattern1,
            Map::class.java,
        )

        // Create pattern for tenant2
        val pattern2 =
            mapOf(
                "name" to "Tenant2 Pattern",
                "startDay" to 15,
            )

        restTemplate.postForEntity(
            "/api/v1/tenants/$tenant2Id/monthly-period-patterns",
            pattern2,
            Map::class.java,
        )

        // Get list for tenant1
        val response =
            restTemplate.getForEntity(
                "/api/v1/tenants/$tenant1Id/monthly-period-patterns",
                List::class.java,
            )

        assertEquals(HttpStatus.OK, response.statusCode)
        val patterns = response.body as List<*>
        assertEquals(1, patterns.size)
        assertEquals("Tenant1 Pattern", (patterns[0] as Map<*, *>)["name"])
    }

    private fun createTenant(): UUID {
        // Generate a unique short code (max 32 chars)
        val shortCode = "T${System.nanoTime()}"
        val request =
            mapOf(
                "code" to shortCode,
                "name" to "Test Tenant",
            )
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants",
                request,
                Map::class.java,
            )
        assertEquals(HttpStatus.CREATED, response.statusCode, "Tenant creation should return 201")
        assertNotNull(response.body, "Tenant creation response body should not be null")
        val tenantId = UUID.fromString((response.body as Map<*, *>)["id"] as String)

        // WORKAROUND: Create tenant projection manually
        // In future, this should be handled by an event listener/projector
        jdbcTemplate.update(
            "INSERT INTO tenant (id, code, name, status, created_at) VALUES (?, ?, ?, 'ACTIVE', NOW()) ON CONFLICT (id) DO NOTHING",
            tenantId,
            shortCode,
            "Test Tenant",
        )

        return tenantId
    }
}
