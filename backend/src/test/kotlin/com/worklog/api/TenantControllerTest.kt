package com.worklog.api

import com.worklog.IntegrationTestBase
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
 * Integration tests for TenantController.
 *
 * Tests the full stack from HTTP API through to database persistence.
 */
class TenantControllerTest : IntegrationTestBase() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    fun `POST tenants should create new tenant and return 201`() {
        // Arrange
        val request =
            mapOf(
                "code" to "TEST_TENANT_001",
                "name" to "Test Tenant One",
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants",
                request,
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertNotNull(body["id"])
        assertEquals("TEST_TENANT_001", body["code"])
        assertEquals("Test Tenant One", body["name"])
    }

    @Test
    fun `GET tenants by id should return tenant details`() {
        // Arrange - Create a tenant first
        val createRequest =
            mapOf(
                "code" to "TEST_TENANT_002",
                "name" to "Test Tenant Two",
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/tenants",
                createRequest,
                Map::class.java,
            )
        val tenantId = (createResponse.body as Map<*, *>)["id"] as String

        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/tenants/$tenantId",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertEquals(tenantId, body["id"])
        assertEquals("TEST_TENANT_002", body["code"])
        assertEquals("Test Tenant Two", body["name"])
        assertEquals("ACTIVE", body["status"])
    }

    @Test
    fun `GET non-existent tenant should return 404`() {
        // Act
        val response =
            restTemplate.getForEntity(
                "/api/v1/tenants/00000000-0000-0000-0000-000000000000",
                Map::class.java,
            )

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `PATCH tenant should update name`() {
        // Arrange - Create a tenant first
        val createRequest =
            mapOf(
                "code" to "TEST_TENANT_003",
                "name" to "Original Name",
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/tenants",
                createRequest,
                Map::class.java,
            )
        val tenantId = (createResponse.body as Map<*, *>)["id"] as String

        // Act
        val updateRequest = mapOf("name" to "Updated Name")
        val response =
            restTemplate.exchange(
                "/api/v1/tenants/$tenantId",
                HttpMethod.PATCH,
                HttpEntity(updateRequest),
                Void::class.java,
            )

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        // Verify the update
        val getResponse =
            restTemplate.getForEntity(
                "/api/v1/tenants/$tenantId",
                Map::class.java,
            )
        val body = getResponse.body as Map<*, *>
        assertEquals("Updated Name", body["name"])
    }

    @Test
    fun `POST deactivate should set tenant status to INACTIVE`() {
        // Arrange - Create a tenant first
        val createRequest =
            mapOf(
                "code" to "TEST_TENANT_004",
                "name" to "Test Tenant Four",
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/tenants",
                createRequest,
                Map::class.java,
            )
        val tenantId = (createResponse.body as Map<*, *>)["id"] as String

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/deactivate",
                null,
                Void::class.java,
            )

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        // Verify the status
        val getResponse =
            restTemplate.getForEntity(
                "/api/v1/tenants/$tenantId",
                Map::class.java,
            )
        val body = getResponse.body as Map<*, *>
        assertEquals("INACTIVE", body["status"])
    }

    @Test
    fun `POST activate should set tenant status to ACTIVE`() {
        // Arrange - Create and deactivate a tenant
        val createRequest =
            mapOf(
                "code" to "TEST_TENANT_005",
                "name" to "Test Tenant Five",
            )
        val createResponse =
            restTemplate.postForEntity(
                "/api/v1/tenants",
                createRequest,
                Map::class.java,
            )
        val tenantId = (createResponse.body as Map<*, *>)["id"] as String

        // Deactivate first
        restTemplate.postForEntity(
            "/api/v1/tenants/$tenantId/deactivate",
            null,
            Void::class.java,
        )

        // Act - Activate
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants/$tenantId/activate",
                null,
                Void::class.java,
            )

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        // Verify the status
        val getResponse =
            restTemplate.getForEntity(
                "/api/v1/tenants/$tenantId",
                Map::class.java,
            )
        val body = getResponse.body as Map<*, *>
        assertEquals("ACTIVE", body["status"])
    }

    @Test
    fun `POST tenant with invalid code should return error`() {
        // Arrange
        val request =
            mapOf(
                "code" to "INVALID-CODE!@#",
                "name" to "Test Tenant",
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants",
                request,
                Map::class.java,
            )

        // Assert
        assertTrue(response.statusCode.is4xxClientError || response.statusCode.is5xxServerError)
    }

    @Test
    fun `POST tenant with empty name should return error`() {
        // Arrange
        val request =
            mapOf(
                "code" to "TEST_TENANT_006",
                "name" to "",
            )

        // Act
        val response =
            restTemplate.postForEntity(
                "/api/v1/tenants",
                request,
                Map::class.java,
            )

        // Assert
        assertTrue(response.statusCode.is4xxClientError || response.statusCode.is5xxServerError)
    }
}
