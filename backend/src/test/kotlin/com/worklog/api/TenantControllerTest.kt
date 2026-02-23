package com.worklog.api

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TenantControllerTest : IntegrationTestBase() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private fun uniqueCode() = "T_${UUID.randomUUID().toString().take(8).uppercase()}"

    @Test
    fun `POST tenants should create new tenant and return 201`() {
        val code = uniqueCode()
        val request = mapOf("code" to code, "name" to "Test Tenant One")

        val response = restTemplate.postForEntity("/api/v1/tenants", request, Map::class.java)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertNotNull(body["id"])
        assertEquals(code, body["code"])
        assertEquals("Test Tenant One", body["name"])
    }

    @Test
    fun `GET tenants by id should return tenant details`() {
        val code = uniqueCode()
        val createRequest = mapOf("code" to code, "name" to "Test Tenant Two")
        val createResponse = restTemplate.postForEntity("/api/v1/tenants", createRequest, Map::class.java)
        val tenantId = (createResponse.body as Map<*, *>)["id"] as String

        val response = restTemplate.getForEntity("/api/v1/tenants/$tenantId", Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val body = response.body as Map<*, *>
        assertEquals(tenantId, body["id"])
        assertEquals(code, body["code"])
        assertEquals("Test Tenant Two", body["name"])
        assertEquals("ACTIVE", body["status"])
    }

    @Test
    fun `GET non-existent tenant should return 404`() {
        val response =
            restTemplate.getForEntity(
                "/api/v1/tenants/00000000-0000-0000-0000-000000000000",
                Map::class.java,
            )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `PATCH tenant should update name`() {
        val code = uniqueCode()
        val createRequest = mapOf("code" to code, "name" to "Original Name")
        val createResponse = restTemplate.postForEntity("/api/v1/tenants", createRequest, Map::class.java)
        val tenantId = (createResponse.body as Map<*, *>)["id"] as String

        val updateRequest = mapOf("name" to "Updated Name")
        val response = restTemplate.exchange(
            "/api/v1/tenants/$tenantId",
            HttpMethod.PATCH,
            HttpEntity(updateRequest),
            Void::class.java,
        )

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        val getResponse = restTemplate.getForEntity("/api/v1/tenants/$tenantId", Map::class.java)
        val body = getResponse.body as Map<*, *>
        assertEquals("Updated Name", body["name"])
    }

    @Test
    fun `POST deactivate should set tenant status to INACTIVE`() {
        val code = uniqueCode()
        val createRequest = mapOf("code" to code, "name" to "Test Tenant Four")
        val createResponse = restTemplate.postForEntity("/api/v1/tenants", createRequest, Map::class.java)
        val tenantId = (createResponse.body as Map<*, *>)["id"] as String

        val response = restTemplate.postForEntity(
            "/api/v1/tenants/$tenantId/deactivate",
            null,
            Void::class.java,
        )

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        val getResponse = restTemplate.getForEntity("/api/v1/tenants/$tenantId", Map::class.java)
        val body = getResponse.body as Map<*, *>
        assertEquals("INACTIVE", body["status"])
    }

    @Test
    fun `POST activate should set tenant status to ACTIVE`() {
        val code = uniqueCode()
        val createRequest = mapOf("code" to code, "name" to "Test Tenant Five")
        val createResponse = restTemplate.postForEntity("/api/v1/tenants", createRequest, Map::class.java)
        val tenantId = (createResponse.body as Map<*, *>)["id"] as String

        restTemplate.postForEntity("/api/v1/tenants/$tenantId/deactivate", null, Void::class.java)

        val response = restTemplate.postForEntity(
            "/api/v1/tenants/$tenantId/activate",
            null,
            Void::class.java,
        )

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        val getResponse = restTemplate.getForEntity("/api/v1/tenants/$tenantId", Map::class.java)
        val body = getResponse.body as Map<*, *>
        assertEquals("ACTIVE", body["status"])
    }

    @Test
    fun `POST tenant with invalid code should return error`() {
        val request = mapOf("code" to "INVALID-CODE!@#", "name" to "Test Tenant")
        val response = restTemplate.postForEntity("/api/v1/tenants", request, Map::class.java)
        assertTrue(response.statusCode.is4xxClientError || response.statusCode.is5xxServerError)
    }

    @Test
    fun `POST tenant with empty name should return error`() {
        val request = mapOf("code" to uniqueCode(), "name" to "")
        val response = restTemplate.postForEntity("/api/v1/tenants", request, Map::class.java)
        assertTrue(response.statusCode.is4xxClientError || response.statusCode.is5xxServerError)
    }
}
