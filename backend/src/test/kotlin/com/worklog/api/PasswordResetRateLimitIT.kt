package com.worklog.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.util.LinkedMultiValueMap

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = [
    "worklog.rate-limit.enabled=true",
    "worklog.rate-limit.auth-requests-per-second=2",
    "worklog.rate-limit.auth-burst-size=2"
])
@ActiveProfiles("test")
class PasswordResetRateLimitIT {
    @LocalServerPort
    var port: Int = 0

    lateinit var webTestClient: WebTestClient

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:${port}")
            .build()
    }

    companion object {
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer<Nothing>("postgres:16.1").apply {
            withDatabaseName("test")
            withUsername("test")
            withPassword("test")
        }
        init { postgres.start() }

        @JvmStatic
        @DynamicPropertySource
        fun registerDataSource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }
    @Test
    fun `パスワードリセット依頼APIがレートリミット超過で429を返す`() {
        val requestBody = """{"email":"rate_limit_test@example.com"}"""
        val uri = "/api/v1/auth/password-reset/request"
        // 制限値2回を超える3回リクエストを一気に送る
        repeat(2) {
            webTestClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk
        }
        // 3回目は429となることを検証
        webTestClient.post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
    }
}
