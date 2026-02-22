package com.worklog.api

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for OrganizationController.
 *
 * Tests the full stack from HTTP API through to database persistence.
 */
class OrganizationControllerTest : IntegrationTestBase() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private lateinit var tenantId: String

    @BeforeEach
    fun setup() {
        // Create a tenant for organization tests (unique code per test to avoid projection conflicts)
        val uniqueCode = "ORG_T_${java.util.UUID.randomUUID().toString().take(8)}"
        val tenantRequest =
            mapOf(
                "code" to uniqueCode,
                "name" to "Organization Test Tenant",
            )
        val tenantResponse =
            restTemplate.postForEntity(
                "/api/v1/tenants",
                tenantRequest,
                Map::class.java,
            )
        tenantId = (tenantResponse.body as Map<*, *>)["id"] as String
    }

    @Test
    fun `POST organizations should create new organization and return 201`() {
        // Arrange
        val request =
            mapOf(
                "code" to "ORG_ROOT_001",
                "name" to "Root Organization",
                "parentId" to null,
                "fiscalYearPatternId" to null,
                "monthlyPeriodPatternId" to null,
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations",
                request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertNotNull(body["id"])
        assertEquals(tenantId, body["tenantId"])
        assertEquals("ORG_ROOT_001", body["code"])
        assertEquals("Root Organization", body["name"])
        assertEquals(1, body["level"])
    }

    @Test
    fun `POST organizations with parent should create child organization`() {
        // Arrange - Create parent organization first
        val parentRequest =
            mapOf(
                "code" to "ORG_PARENT_001",
                "name" to "Parent Organization",
                "parentId" to null,
                "fiscalYearPatternId" to null,
                "monthlyPeriodPatternId" to null,
            )
        val parentResponse =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations",
                parentRequest,
                Map::class.java,
            )
        val parentId = (parentResponse.body as Map<*, *>)["id"] as String

        // Act - Create child
        val childRequest =
            mapOf(
                "code" to "ORG_CHILD_001",
                "name" to "Child Organization",
                "parentId" to parentId,
                "fiscalYearPatternId" to null,
                "monthlyPeriodPatternId" to null,
            )
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations",
                childRequest,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals(parentId, body["parentId"])
        assertEquals(2, body["level"])
    }

    @Test
    fun `GET organization by id should return organization details`() {
        // Arrange - Create organization first
        val createRequest =
            mapOf(
                "code" to "ORG_GET_001",
                "name" to "Test Organization",

                "parentId" to null,
                "fiscalYearPatternId" to null,
                "monthlyPeriodPatternId" to null,
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations",
                createRequest,
                Map::class.java,
            )
        val organizationId = (createResponse.body as Map<*, *>)["id"] as String

        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/tenants/$tenantId/organizations/$organizationId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertEquals(organizationId, body["id"])
        assertEquals(tenantId, body["tenantId"])
        assertEquals("ORG_GET_001", body["code"])
        assertEquals("Test Organization", body["name"])
        assertEquals(1, body["level"])
        assertEquals(true, body["isActive"])
    }

    @Test
    fun `GET non-existent organization should return 404`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/tenants/$tenantId/organizations/00000000-0000-0000-0000-000000000000",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `PATCH organization should update name`() {
        // Arrange - Create organization first
        val createRequest =
            mapOf(
                "code" to "ORG_UPDATE_001",
                "name" to "Original Name",

                "parentId" to null,
                "fiscalYearPatternId" to null,
                "monthlyPeriodPatternId" to null,
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations",
                createRequest,
                Map::class.java,
            )
        val organizationId = (createResponse.body as Map<*, *>)["id"] as String

        // Act
        val updateRequest = mapOf("name" to "Updated Name")
        val response =
            restTemplate.exchange(
                "/api/v1/tenants/$tenantId/organizations/$organizationId",
                HttpMethod.PATCH,
                HttpEntity(updateRequest),
                Void::class.java,
            )

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        // Verify the update
        val getResponse =
            restTemplate.getForEntity(
                "/api/v1/tenants/$tenantId/organizations/$organizationId",
                Map::class.java,
            )
        val body = getResponse.body as Map<*, *>
        assertEquals("Updated Name", body["name"])
    }

    @Test
    fun `POST deactivate should set organization to inactive`() {
        // Arrange - Create organization first
        val createRequest =
            mapOf(
                "code" to "ORG_DEACT_001",
                "name" to "Test Organization",

                "parentId" to null,
                "fiscalYearPatternId" to null,
                "monthlyPeriodPatternId" to null,
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations",
                createRequest,
                Map::class.java,
            )
        val organizationId = (createResponse.body as Map<*, *>)["id"] as String

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations/$organizationId/deactivate",
                null,
                Void::class.java,
            )

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        // Verify the status
        val getResponse =
            restTemplate.getForEntity(
                "/api/v1/tenants/$tenantId/organizations/$organizationId",
                Map::class.java,
            )
        val body = getResponse.body as Map<*, *>
        assertEquals(false, body["isActive"])
    }

    @Test
    fun `POST activate should set organization to active`() {
        // Arrange - Create and deactivate organization
        val createRequest =
            mapOf(
                "code" to "ORG_ACT_001",
                "name" to "Test Organization",

                "parentId" to null,
                "fiscalYearPatternId" to null,
                "monthlyPeriodPatternId" to null,
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations",
                createRequest,
                Map::class.java,
            )
        val organizationId = (createResponse.body as Map<*, *>)["id"] as String

        // Deactivate first
        restTemplate.postForEntity(
            "/api/v1/tenants/$tenantId/organizations/$organizationId/deactivate",
            null,
            Void::class.java,
        )

        // Act - Activate
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations/$organizationId/activate",
                null,
                Void::class.java,
            )

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        // Verify the status
        val getResponse =
            restTemplate.getForEntity(
                "/api/v1/tenants/$tenantId/organizations/$organizationId",
                Map::class.java,
            )
        val body = getResponse.body as Map<*, *>
        assertEquals(true, body["isActive"])
    }

    @Test
    fun `POST organization with level greater than 6 should return error`() {
        // Arrange - Create a 6-level deep hierarchy
        var currentParentId: String? = null
        for (i in 1..6) {
            val request =
                mapOf(
                    "code" to "ORG_LVL_${i}",
                    "name" to "Level $i Organization",
                    "parentId" to currentParentId,
                    "fiscalYearPatternId" to null,
                    "monthlyPeriodPatternId" to null,
                )
            val createResponse =
                restTemplate.postForEntity(
                    "/api/v1/tenants/$tenantId/organizations",
                    request,
                    Map::class.java,
                )
            assertEquals(HttpStatus.CREATED, createResponse.statusCode)
            currentParentId = (createResponse.body as Map<*, *>)["id"] as String
        }

        // Act - Try to create a 7th level (exceeds max hierarchy level of 6)
        val request =
            mapOf(
                "code" to "ORG_LVL_7",
                "name" to "Level 7 Organization",
                "parentId" to currentParentId,
                "fiscalYearPatternId" to null,
                "monthlyPeriodPatternId" to null,
            )
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations",
                request,
                Map::class.java,
            )

        // Assert
        assertTrue(response.statusCode.is4xxClientError || response.statusCode.is5xxServerError)
    }

    @Test
    fun `POST organization with non-existent parentId should return error`() {
        // Act - Try to create organization with a non-existent parent
        val request =
            mapOf(
                "code" to "ORG_INVALID_002",
                "name" to "Invalid Organization",
                "parentId" to "00000000-0000-0000-0000-000000000000",
                "fiscalYearPatternId" to null,
                "monthlyPeriodPatternId" to null,
            )
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/organizations",
                request,
                Map::class.java,
            )

        // Assert
        assertTrue(response.statusCode.is4xxClientError || response.statusCode.is5xxServerError)
    }
}
