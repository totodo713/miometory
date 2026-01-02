package com.worklog

import org.instancio.Instancio
import org.instancio.Select
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test to verify the test infrastructure is working correctly:
 * - Testcontainers PostgreSQL starts and connects
 * - Flyway migrations run successfully
 * - Spring Boot context loads
 * - Instancio generates test data
 */
class InfrastructureVerificationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    fun `health endpoint returns OK`() {
        val response = restTemplate.getForEntity("/actuator/health", Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals("UP", response.body!!["status"])
    }

    @Test
    fun `Instancio generates test data correctly`() {
        // Test Instancio with a simple data class
        data class TestData(
            val name: String,
            val value: Int,
        )

        val generated =
            Instancio
                .of(TestData::class.java)
                .set(Select.field("name"), "test-name")
                .create()

        assertEquals("test-name", generated.name)
        assertTrue(generated.value != 0) // Instancio generates non-default values
    }

    @Test
    fun `Instancio generates list of test data`() {
        data class Item(
            val id: String,
            val description: String,
        )

        val items =
            Instancio
                .ofList(Item::class.java)
                .size(5)
                .create()

        assertEquals(5, items.size)
        items.forEach { item ->
            assertNotNull(item.id)
            assertNotNull(item.description)
        }
    }
}
